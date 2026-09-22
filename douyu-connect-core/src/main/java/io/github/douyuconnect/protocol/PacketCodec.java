package io.github.douyuconnect.protocol;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
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
        private int expected = 4;
        public Decoder(int maxLength) {
            if (maxLength < 9 || maxLength > 16 * 1024 * 1024) throw new IllegalArgumentException("Invalid packet limit");
            this.maxLength = maxLength;
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
                    if (length < 9 || length > maxLength) throw new IllegalArgumentException("Invalid packet length: " + length);
                    expected = length + 4;
                    continue;
                }
                int length = pending.getInt(0);
                int type = Short.toUnsignedInt(pending.getShort(8));
                if (pending.getInt(4) != length || (type != 689 && type != 690)
                    || pending.getShort(10) != 0 || pending.get(expected - 1) != 0) {
                    throw new IllegalArgumentException("Invalid Douyu packet header or terminator");
                }
                try {
                    ByteBuffer content = pending.duplicate(); content.position(12); content.limit(expected - 1);
                    output.add(StandardCharsets.UTF_8.newDecoder().decode(content).toString());
                } catch (CharacterCodingException e) {
                    throw new IllegalArgumentException("Invalid UTF-8", e);
                }
                pending.clear(); expected = 4;
            }
            return output;
        }
    }
}
