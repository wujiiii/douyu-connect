package io.github.douyuconnect.protocol;

import java.util.Arrays;
import java.util.HexFormat;

/** Safe packet-header diagnostic. The message is only the code name; bytes stay in {@link #header()}. */
public final class PacketFormatException extends IllegalArgumentException {
    public enum Code { LENGTH, HEADER, UTF8 }

    private final Code code;
    private final int declaredLength;
    private final int packetType;
    private final int bufferedLength;
    private final byte[] header;

    public PacketFormatException(Code code, int declaredLength, int packetType, int bufferedLength, byte[] header, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.declaredLength = declaredLength;
        this.packetType = packetType;
        this.bufferedLength = bufferedLength;
        this.header = Arrays.copyOf(header, Math.min(header.length, 16));
    }

    public Code code() { return code; }
    public int declaredLength() { return declaredLength; }
    public int packetType() { return packetType; }
    public int bufferedLength() { return bufferedLength; }
    public byte[] header() { return header.clone(); }

    /** Fixed suffix for a client event. No exception message and no packet body. */
    public String diagnostic() {
        return " | packet=" + code.name() + "," + declaredLength + "," + packetType + "," + bufferedLength + "," + HexFormat.of().formatHex(header);
    }
}
