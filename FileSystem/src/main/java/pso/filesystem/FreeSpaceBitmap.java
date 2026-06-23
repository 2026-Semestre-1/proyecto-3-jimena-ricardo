package pso.filesystem;

import java.util.BitSet;

public final class FreeSpaceBitmap {

    private static final int BITS_PER_BYTE = 8;

    private final int totalBlocks;
    private final BitSet usedBlocks;

    public FreeSpaceBitmap(int totalBlocks) {
        BinaryFormatValidator.requirePositive("totalBlocks", totalBlocks);
        this.totalBlocks = totalBlocks;
        this.usedBlocks = new BitSet(totalBlocks);
    }

    private FreeSpaceBitmap(int totalBlocks, BitSet usedBlocks) {
        BinaryFormatValidator.requirePositive("totalBlocks", totalBlocks);
        this.totalBlocks = totalBlocks;
        this.usedBlocks = (BitSet) usedBlocks.clone();
    }

    public void markUsed(int blockIndex) {
        requireValidBlockIndex(blockIndex);
        usedBlocks.set(blockIndex);
    }

    public void markFree(int blockIndex) {
        requireValidBlockIndex(blockIndex);
        usedBlocks.clear(blockIndex);
    }

    public boolean isUsed(int blockIndex) {
        requireValidBlockIndex(blockIndex);
        return usedBlocks.get(blockIndex);
    }

    public boolean isFree(int blockIndex) {
        return !isUsed(blockIndex);
    }

    public int usedCount() {
        return usedBlocks.cardinality();
    }

    public int freeCount() {
        return totalBlocks - usedCount();
    }

    public byte[] toBytes(int byteCount) {
        BinaryFormatValidator.requirePositive("byteCount", byteCount);

        int requiredBytes = requiredBytes(totalBlocks);
        if (byteCount < requiredBytes) {
            throw new IllegalArgumentException("byteCount must be at least " + requiredBytes + " for " + totalBlocks + " blocks");
        }

        byte[] bytes = new byte[byteCount];
        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            if (usedBlocks.get(blockIndex)) {
                int byteIndex = blockIndex / BITS_PER_BYTE;
                int bitIndex = 7 - (blockIndex % BITS_PER_BYTE);
                bytes[byteIndex] |= (byte) (1 << bitIndex);
            }
        }

        return bytes;
    }

    public static FreeSpaceBitmap fromBytes(byte[] bytes, int totalBlocks) {
        BinaryFormatValidator.requirePositive("totalBlocks", totalBlocks);
        BinaryFormatValidator.requireBytesAtLeast("FreeSpaceBitmap", bytes, requiredBytes(totalBlocks));

        BitSet usedBlocks = new BitSet(totalBlocks);
        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int byteIndex = blockIndex / BITS_PER_BYTE;
            int bitIndex = 7 - (blockIndex % BITS_PER_BYTE);
            boolean used = (bytes[byteIndex] & (1 << bitIndex)) != 0;
            if (used) {
                usedBlocks.set(blockIndex);
            }
        }

        return new FreeSpaceBitmap(totalBlocks, usedBlocks);
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    private void requireValidBlockIndex(int blockIndex) {
        BinaryFormatValidator.requireBlockIndex("blockIndex", blockIndex, totalBlocks);
    }

    private static int requiredBytes(int totalBlocks) {
        return BinaryFormatValidator.ceilDiv(totalBlocks, BITS_PER_BYTE);
    }
}
