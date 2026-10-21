package io.github.douyuconnect.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void roundTripsEmptyEscapedAndNestedFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", "chatmsg"); fields.put("txt", " 中文😀/@S@= "); fields.put("empty", "");
        assertEquals(fields, Stt.decode(Stt.encode(fields)));
        String nested = Stt.encode(Map.of("nn", "昵称/一", "ic", "abc"));
        assertEquals("昵称/一", Stt.decode(Stt.decode(Stt.encode(Map.of("chatmsg", nested))).get("chatmsg")).get("nn"));
    }

    @Test void reassemblesEveryPossibleSplitAndCoalescedPackets() {
        byte[] first = PacketCodec.encode("type@=chatmsg/txt@=你好😀/", 690);
        byte[] second = PacketCodec.encode("type@=dgb/gfid@=1/", 690);
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        for (int split = 0; split <= joined.length; split++) {
            PacketCodec.Decoder decoder = new PacketCodec.Decoder(1024);
            List<String> actual = new ArrayList<>(decoder.feed(ByteBuffer.wrap(joined, 0, split)));
            actual.addAll(decoder.feed(ByteBuffer.wrap(joined, split, joined.length - split)));
            assertEquals(List.of("type@=chatmsg/txt@=你好😀/", "type@=dgb/gfid@=1/"), actual);
        }
    }

    @Test void acceptsDirectBuffersAndPreservesWhitespace() {
        byte[] packet = PacketCodec.encode("type@=chatmsg/txt@= x /", 690);
        ByteBuffer direct = ByteBuffer.allocateDirect(packet.length).put(packet).flip();
        assertEquals(List.of("type@=chatmsg/txt@= x /"), new PacketCodec.Decoder(1024).feed(direct));
    }

    @Test void rejectsBadLengthsHeadersAndTerminators() {
        for (int length : new int[]{-1, 0, 8, 1025, Integer.MAX_VALUE}) {
            byte[] bad = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(length).array();
            assertThrows(IllegalArgumentException.class, () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(bad)));
        }
        byte[] mismatch = PacketCodec.encode("type@=mrkl/", 690);
        mismatch[4]++;
        assertThrows(IllegalArgumentException.class, () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(mismatch)));
        byte[] unterminated = PacketCodec.encode("type@=mrkl/", 690);
        unterminated[unterminated.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(unterminated)));
    }

    @Test void packetFailuresKeepHeaderBytesAndOmitTheBody() {
        byte[] badLength = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(8).array();
        PacketFormatException length = assertThrows(PacketFormatException.class, () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(badLength)));
        assertEquals(PacketFormatException.Code.LENGTH, length.code());
        assertEquals(8, length.declaredLength());
        assertEquals(-1, length.packetType());
        assertEquals(4, length.bufferedLength());
        assertEquals("08000000", length.diagnostic().substring(length.diagnostic().lastIndexOf(',') + 1));
        assertFalse(length.getMessage().contains("secret-body"));

        byte[] packet = PacketCodec.encode("type@=chatmsg/txt@=secret-body/", 690);
        packet[4]++;
        PacketFormatException header = assertThrows(PacketFormatException.class, () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(packet)));
        assertEquals(PacketFormatException.Code.HEADER, header.code());
        assertEquals(690, header.packetType());
        assertFalse(header.getMessage().contains("secret-body"));
        assertFalse(header.diagnostic().contains("secret-body"));
        assertTrue(header.header().length <= 16);
    }

    @Test void discardsFramedInvalidUtf8AndContinuesWithFollowingPacket() throws Exception {
        byte[] first = PacketCodec.encode("type@=mrkl/", 690);
        byte[] malformed = malformedUtf8Packet();
        byte[] last = PacketCodec.encode("type@=dgb/gfid@=1/", 690);
        byte[] joined = join(first, malformed, last);
        List<PacketFormatException> discarded = new ArrayList<>();

        PacketCodec.Decoder decoder = new PacketCodec.Decoder(1024, discarded::add);
        assertEquals(List.of("type@=mrkl/", "type@=dgb/gfid@=1/"), decoder.feed(ByteBuffer.wrap(joined)));
        assertEquals(List.of("type@=mrkl/"), decoder.feed(ByteBuffer.wrap(first)));

        assertEquals(1, discarded.size());
        PacketFormatException failure = discarded.get(0);
        assertEquals(PacketFormatException.Code.UTF8, failure.code());
        assertEquals(malformed.length - 4, failure.declaredLength());
        assertEquals(690, failure.packetType());
        assertEquals(malformed.length, failure.bufferedLength());
        assertEquals(18, failure.bodyOffset());
        byte[] body = Arrays.copyOfRange(malformed, 12, malformed.length - 1);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)), failure.bodySha256());
        assertTrue(failure.diagnostic().contains(",offset=18,sha256=" + failure.bodySha256()));
        assertFalse(failure.diagnostic().contains("secret"));

        PacketFormatException strict = assertThrows(PacketFormatException.class,
            () -> new PacketCodec.Decoder(1024).feed(ByteBuffer.wrap(malformed)));
        assertEquals(PacketFormatException.Code.UTF8, strict.code());
    }

    @Test void fragmentedInvalidUtf8PacketResetsBeforeFollowingPacket() {
        byte[] malformed = malformedUtf8Packet();
        byte[] valid = PacketCodec.encode("type@=chatmsg/txt@=after/", 690);
        byte[] joined = join(malformed, valid);
        List<PacketFormatException> discarded = new ArrayList<>();
        PacketCodec.Decoder decoder = new PacketCodec.Decoder(1024, discarded::add);

        assertTrue(decoder.feed(ByteBuffer.wrap(joined, 0, malformed.length - 1)).isEmpty());
        assertEquals(List.of("type@=chatmsg/txt@=after/"),
            decoder.feed(ByteBuffer.wrap(joined, malformed.length - 1, joined.length - malformed.length + 1)));
        assertEquals(1, discarded.size());
    }

    @Test void recoveryModeStillRejectsInvalidLengthsAndHeaders() {
        List<PacketFormatException> discarded = new ArrayList<>();
        byte[] badLength = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(8).array();
        PacketFormatException length = assertThrows(PacketFormatException.class,
            () -> new PacketCodec.Decoder(1024, discarded::add).feed(ByteBuffer.wrap(badLength)));
        assertEquals(PacketFormatException.Code.LENGTH, length.code());

        byte[] badHeader = PacketCodec.encode("type@=mrkl/", 690);
        badHeader[4]++;
        PacketFormatException header = assertThrows(PacketFormatException.class,
            () -> new PacketCodec.Decoder(1024, discarded::add).feed(ByteBuffer.wrap(badHeader)));
        assertEquals(PacketFormatException.Code.HEADER, header.code());
        assertTrue(discarded.isEmpty());
    }

    private static byte[] malformedUtf8Packet() {
        byte[] packet = PacketCodec.encode("type@=chatmsg/txt@=secret/", 690);
        packet[12 + 18] = (byte) 0xc3;
        packet[12 + 19] = 0x28;
        return packet;
    }

    private static byte[] join(byte[]... packets) {
        int length = Arrays.stream(packets).mapToInt(packet -> packet.length).sum();
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] packet : packets) {
            System.arraycopy(packet, 0, joined, offset, packet.length);
            offset += packet.length;
        }
        return joined;
    }
}
