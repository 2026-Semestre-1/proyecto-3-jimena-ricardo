package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class BootBlock {

    public static final int DEFAULT_BLOCK_SIZE = 1024;
    public static final int MAGIC_SIZE = 4;
    public static final int VOLUME_NAME_SIZE = 32;
    public static final int BINARY_SIZE = DEFAULT_BLOCK_SIZE;
    public static final byte[] MAGIC = new byte[] {'B', 'O', 'O', 'T'};

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final int version;
    private final long diskSizeBytes;
    private final int blockSize;
    private final int totalBlocks;
    private final int superBlockIndex;
    private final long creationTimeMillis;
    private final String volumeName;

    public BootBlock(
            int version,
            long diskSizeBytes,
            int blockSize,
            int totalBlocks,
            int superBlockIndex,
            long creationTimeMillis,
            String volumeName
    ) {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (diskSizeBytes <= 0) {
            throw new IllegalArgumentException("diskSizeBytes must be positive");
        }
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        if (totalBlocks <= 0) {
            throw new IllegalArgumentException("totalBlocks must be positive");
        }
        if (superBlockIndex < 0) {
            throw new IllegalArgumentException("superBlockIndex cannot be negative");
        }
        Objects.requireNonNull(volumeName, "volumeName cannot be null");

        this.version = version;
        this.diskSizeBytes = diskSizeBytes;
        this.blockSize = blockSize;
        this.totalBlocks = totalBlocks;
        this.superBlockIndex = superBlockIndex;
        this.creationTimeMillis = creationTimeMillis;
        this.volumeName = volumeName;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.put(MAGIC);
        buffer.putInt(version);
        buffer.putLong(diskSizeBytes);
        buffer.putInt(blockSize);
        buffer.putInt(totalBlocks);
        buffer.putInt(superBlockIndex);
        buffer.putLong(creationTimeMillis);
        putFixedString(buffer, volumeName, VOLUME_NAME_SIZE);

        return buffer.array();
    }

    public static BootBlock fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < BINARY_SIZE) {
            throw new IllegalArgumentException("BootBlock requires at least " + BINARY_SIZE + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        byte[] magic = new byte[MAGIC_SIZE];
        buffer.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("Invalid filesystem magic number");
        }

        int version = buffer.getInt();
        long diskSizeBytes = buffer.getLong();
        int blockSize = buffer.getInt();
        int totalBlocks = buffer.getInt();
        int superBlockIndex = buffer.getInt();
        long creationTimeMillis = buffer.getLong();
        String volumeName = readFixedString(buffer, VOLUME_NAME_SIZE);

        return new BootBlock(
                version,
                diskSizeBytes,
                blockSize,
                totalBlocks,
                superBlockIndex,
                creationTimeMillis,
                volumeName
        );
    }

    private static void putFixedString(ByteBuffer buffer, String value, int fieldSize) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        if (raw.length > fieldSize) {
            throw new IllegalArgumentException("String is too long for fixed field of " + fieldSize + " bytes: " + value);
        }

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

    public int version() {
        return version;
    }

    public long diskSizeBytes() {
        return diskSizeBytes;
    }

    public int blockSize() {
        return blockSize;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int superBlockIndex() {
        return superBlockIndex;
    }

    public long creationTimeMillis() {
        return creationTimeMillis;
    }

    public String volumeName() {
        return volumeName;
    }
}
