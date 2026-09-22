package io.github.douyuconnect.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
}
