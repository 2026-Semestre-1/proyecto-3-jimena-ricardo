package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DirectoryEntry {

    public static final int BINARY_SIZE = 72;
    public static final int NAME_SIZE = 64;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final int inodeId;
    private final InodeType type;
    private final String name;

    public DirectoryEntry(int inodeId, InodeType type, String name) {
        BinaryFormatValidator.requirePositive("inodeId", inodeId);
        BinaryFormatValidator.requireFixedUtf8Length("name", name, NAME_SIZE);
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("name cannot contain path separators");
        }

        this.inodeId = inodeId;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        if (type == InodeType.UNUSED) {
            throw new IllegalArgumentException("directory entry type cannot be UNUSED");
        }
        this.name = name;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.putInt(inodeId);
        buffer.put(type.code());
        buffer.put(new byte[3]);
        putFixedString(buffer, name, NAME_SIZE);

        return buffer.array();
    }

    public static DirectoryEntry fromBytes(byte[] bytes) {
        BinaryFormatValidator.requireBytesAtLeast("DirectoryEntry", bytes, BINARY_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        int inodeId = buffer.getInt();
        InodeType type = InodeType.fromCode(buffer.get());
        buffer.position(buffer.position() + 3);
        String name = readFixedString(buffer, NAME_SIZE);

        return new DirectoryEntry(inodeId, type, name);
    }

    private static void putFixedString(ByteBuffer buffer, String value, int fieldSize) {
        BinaryFormatValidator.requireFixedUtf8Length("value", value, fieldSize);
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);

        buffer.put(raw);
        for (int i = raw.length; i < fieldSize; i++) {
            buffer.put((byte) 0);
        }
    }

    private static String readFixedString(ByteBuffer buffer, int fieldSize) {
        byte[] raw = new byte[fieldSize];
        buffer.get(raw);

        int length = 0;
        while (length < raw.length && raw[length] != 0) {
            length++;
        }

        return new String(raw, 0, length, StandardCharsets.UTF_8);
    }

    public int inodeId() {
        return inodeId;
    }

    public InodeType type() {
        return type;
    }

    public String name() {
        return name;
    }
}
