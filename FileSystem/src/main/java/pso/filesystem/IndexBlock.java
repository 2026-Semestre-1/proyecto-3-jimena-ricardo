package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class IndexBlock {

    public static final int UNUSED_BLOCK_POINTER = -1;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final int BYTES_PER_BLOCK_POINTER = Integer.BYTES;

    private final List<Integer> blockPointers;

    public IndexBlock(List<Integer> blockPointers) {
        if (blockPointers == null) {
            throw new IllegalArgumentException("blockPointers cannot be null");
        }
        for (int blockPointer : blockPointers) {
            BinaryFormatValidator.requireNonNegative("blockPointer", blockPointer);
        }

        this.blockPointers = List.copyOf(blockPointers);
    }

    public byte[] toBytes(int blockSize) {
        BinaryFormatValidator.requirePositive("blockSize", blockSize);
        if (blockSize % BYTES_PER_BLOCK_POINTER != 0) {
            throw new IllegalArgumentException("blockSize must be divisible by " + BYTES_PER_BLOCK_POINTER);
        }

        int capacity = blockSize / BYTES_PER_BLOCK_POINTER;
        if (blockPointers.size() > capacity) {
            throw new IllegalArgumentException("too many block pointers for index block capacity");
        }

        ByteBuffer buffer = ByteBuffer.allocate(blockSize);
        buffer.order(BYTE_ORDER);

        for (int blockPointer : blockPointers) {
            buffer.putInt(blockPointer);
        }
        for (int i = blockPointers.size(); i < capacity; i++) {
            buffer.putInt(UNUSED_BLOCK_POINTER);
        }

        return buffer.array();
    }

    public static IndexBlock fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("IndexBlock bytes cannot be null or empty");
        }
        if (bytes.length % BYTES_PER_BLOCK_POINTER != 0) {
            throw new IllegalArgumentException("IndexBlock byte length must be divisible by " + BYTES_PER_BLOCK_POINTER);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        List<Integer> blockPointers = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int blockPointer = buffer.getInt();
            if (blockPointer != UNUSED_BLOCK_POINTER) {
                BinaryFormatValidator.requireNonNegative("blockPointer", blockPointer);
                blockPointers.add(blockPointer);
            }
        }

        return new IndexBlock(blockPointers);
    }

    public List<Integer> blockPointers() {
        return blockPointers;
    }
}
