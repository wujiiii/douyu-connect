package io.github.douyuconnect.protocol;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
public final class PacketCodec {
    private PacketCodec() {}
    public static byte[] encode(String text, int type) {
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("Embedded NUL");
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        int length = Math.addExact(content.length, 9);
        return ByteBuffer.allocate(length + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(length).putInt(length).putShort((short) type).putShort((short) 0)
            .put(content).put((byte) 0).array();
    }
    /** One decoder per connection; buffers at most one bounded packet. */
    public static final class Decoder {
        private final ByteBuffer pending;
        private final int maxLength;
        private final Consumer<PacketFormatException> discarded;
        private int expected = 4;
        public Decoder(int maxLength) {
            this(maxLength, error -> { throw error; });
        }
        /** Discards only complete packets whose validated body is not strict UTF-8. */
        public Decoder(int maxLength, Consumer<PacketFormatException> discarded) {
            if (maxLength < 9 || maxLength > 16 * 1024 * 1024) throw new IllegalArgumentException("Invalid packet limit");
            this.maxLength = maxLength;
            this.discarded = Objects.requireNonNull(discarded, "discarded");
            pending = ByteBuffer.allocate(maxLength + 4).order(ByteOrder.LITTLE_ENDIAN);
        }
        public List<String> feed(ByteBuffer input) {
            List<String> output = new ArrayList<>();
            while (input.hasRemaining()) {
                int n = Math.min(input.remaining(), expected - pending.position());
                ByteBuffer slice = input.slice(); slice.limit(n);
                pending.put(slice); input.position(input.position() + n);
                if (pending.position() != expected) continue;
                if (expected == 4) {
                    int length = pending.getInt(0);
                    if (length < 9 || length > maxLength) throw malformed(PacketFormatException.Code.LENGTH, length, -1, null);
                    expected = length + 4;
                    continue;
                }
                int length = pending.getInt(0);
                int type = Short.toUnsignedInt(pending.getShort(8));
                if (pending.getInt(4) != length || (type != 689 && type != 690)
                    || pending.getShort(10) != 0 || pending.get(expected - 1) != 0) {
                    throw malformed(PacketFormatException.Code.HEADER, length, type, null);
                }
                ByteBuffer content = pending.duplicate(); content.position(12); content.limit(expected - 1);
                try {
                    output.add(StandardCharsets.UTF_8.newDecoder().decode(content).toString());
                } catch (CharacterCodingException e) {
                    PacketFormatException error = malformed(PacketFormatException.Code.UTF8, length, type,
                        content.position() - 12, sha256Body(), e);
                    pending.clear(); expected = 4;
                    discarded.accept(error);
                    continue;
                }
                pending.clear(); expected = 4;
            }
            return output;
        }
        private PacketFormatException malformed(PacketFormatException.Code code, int declaredLength, int packetType, Throwable cause) {
            return malformed(code, declaredLength, packetType, -1, "", cause);
        }
        private PacketFormatException malformed(PacketFormatException.Code code, int declaredLength, int packetType,
                                                int bodyOffset, String bodySha256, Throwable cause) {
            int buffered = pending.position();
            byte[] prefix = new byte[Math.min(16, buffered)];
            ByteBuffer view = pending.duplicate(); view.position(0); view.limit(prefix.length); view.get(prefix);
            return new PacketFormatException(code, declaredLength, packetType, buffered, prefix, bodyOffset, bodySha256, cause);
        }
        private String sha256Body() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                ByteBuffer body = pending.duplicate(); body.position(12); body.limit(expected - 1);
                digest.update(body);
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("SHA-256 unavailable", e);
            }
        }
    }
}
