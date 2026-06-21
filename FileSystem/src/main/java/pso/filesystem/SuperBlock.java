package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class SuperBlock {

    public static final int DEFAULT_BLOCK_SIZE = 1024;
    public static final int MAGIC_SIZE = 4;
    public static final int BINARY_SIZE = DEFAULT_BLOCK_SIZE;
    public static final byte[] MAGIC = new byte[] {'J', 'R', 'F', 'S'};

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final int totalBlocks;
    private final int blockSize;
    private final int usedBlocks;
    private final int freeBlocks;
    private final int rootInodeId;
    private final int nextInodeId;
    private final int bitmapStartBlock;
    private final int bitmapBlockCount;
    private final int inodeTableStartBlock;
    private final int inodeTableBlockCount;
    private final int userTableBlock;
    private final int groupTableBlock;
    private final int dataRegionStartBlock;

    public SuperBlock(
            int totalBlocks,
            int blockSize,
            int usedBlocks,
            int freeBlocks,
            int rootInodeId,
            int nextInodeId,
            int bitmapStartBlock,
            int bitmapBlockCount,
            int inodeTableStartBlock,
            int inodeTableBlockCount,
            int userTableBlock,
            int groupTableBlock,
            int dataRegionStartBlock
    ) {
        if (totalBlocks <= 0) {
            throw new IllegalArgumentException("totalBlocks must be positive");
        }
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        if (usedBlocks < 0) {
            throw new IllegalArgumentException("usedBlocks cannot be negative");
        }
        if (freeBlocks < 0) {
            throw new IllegalArgumentException("freeBlocks cannot be negative");
        }
        if (usedBlocks + freeBlocks != totalBlocks) {
            throw new IllegalArgumentException("usedBlocks + freeBlocks must equal totalBlocks");
        }
        if (rootInodeId <= 0) {
            throw new IllegalArgumentException("rootInodeId must be positive");
        }
        if (nextInodeId <= rootInodeId) {
            throw new IllegalArgumentException("nextInodeId must be greater than rootInodeId");
        }
        validateBlockIndex("bitmapStartBlock", bitmapStartBlock, totalBlocks);
        validatePositiveCount("bitmapBlockCount", bitmapBlockCount);
        validateBlockRange("bitmap", bitmapStartBlock, bitmapBlockCount, totalBlocks);
        validateBlockIndex("inodeTableStartBlock", inodeTableStartBlock, totalBlocks);
        validatePositiveCount("inodeTableBlockCount", inodeTableBlockCount);
        validateBlockRange("inode table", inodeTableStartBlock, inodeTableBlockCount, totalBlocks);
        validateBlockIndex("userTableBlock", userTableBlock, totalBlocks);
        validateBlockIndex("groupTableBlock", groupTableBlock, totalBlocks);
        validateBlockIndex("dataRegionStartBlock", dataRegionStartBlock, totalBlocks);

        this.totalBlocks = totalBlocks;
        this.blockSize = blockSize;
        this.usedBlocks = usedBlocks;
        this.freeBlocks = freeBlocks;
        this.rootInodeId = rootInodeId;
        this.nextInodeId = nextInodeId;
        this.bitmapStartBlock = bitmapStartBlock;
        this.bitmapBlockCount = bitmapBlockCount;
        this.inodeTableStartBlock = inodeTableStartBlock;
        this.inodeTableBlockCount = inodeTableBlockCount;
        this.userTableBlock = userTableBlock;
        this.groupTableBlock = groupTableBlock;
        this.dataRegionStartBlock = dataRegionStartBlock;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.put(MAGIC);
        buffer.putInt(totalBlocks);
        buffer.putInt(blockSize);
        buffer.putInt(usedBlocks);
        buffer.putInt(freeBlocks);
        buffer.putInt(rootInodeId);
        buffer.putInt(nextInodeId);
        buffer.putInt(bitmapStartBlock);
        buffer.putInt(bitmapBlockCount);
        buffer.putInt(inodeTableStartBlock);
        buffer.putInt(inodeTableBlockCount);
        buffer.putInt(userTableBlock);
        buffer.putInt(groupTableBlock);
        buffer.putInt(dataRegionStartBlock);

        return buffer.array();
    }

    public static SuperBlock fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < BINARY_SIZE) {
            throw new IllegalArgumentException("SuperBlock requires at least " + BINARY_SIZE + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        byte[] magic = new byte[MAGIC_SIZE];
        buffer.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("Invalid superblock magic number");
        }

        int totalBlocks = buffer.getInt();
        int blockSize = buffer.getInt();
        int usedBlocks = buffer.getInt();
        int freeBlocks = buffer.getInt();
        int rootInodeId = buffer.getInt();
        int nextInodeId = buffer.getInt();
        int bitmapStartBlock = buffer.getInt();
        int bitmapBlockCount = buffer.getInt();
        int inodeTableStartBlock = buffer.getInt();
        int inodeTableBlockCount = buffer.getInt();
        int userTableBlock = buffer.getInt();
        int groupTableBlock = buffer.getInt();
        int dataRegionStartBlock = buffer.getInt();

        return new SuperBlock(
                totalBlocks,
                blockSize,
                usedBlocks,
                freeBlocks,
                rootInodeId,
                nextInodeId,
                bitmapStartBlock,
                bitmapBlockCount,
                inodeTableStartBlock,
                inodeTableBlockCount,
                userTableBlock,
                groupTableBlock,
                dataRegionStartBlock
        );
    }

    private static void validateBlockIndex(String fieldName, int value, int totalBlocks) {
        if (value < 0 || value >= totalBlocks) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and " + (totalBlocks - 1));
        }
    }

    private static void validatePositiveCount(String fieldName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void validateBlockRange(String fieldName, int startBlock, int blockCount, int totalBlocks) {
        long endExclusive = (long) startBlock + blockCount;
        if (endExclusive > totalBlocks) {
            throw new IllegalArgumentException(fieldName + " block range exceeds total block count");
        }
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int blockSize() {
        return blockSize;
    }

    public int usedBlocks() {
        return usedBlocks;
    }

    public int freeBlocks() {
        return freeBlocks;
    }

    public int rootInodeId() {
        return rootInodeId;
    }

    public int nextInodeId() {
        return nextInodeId;
    }

    public int bitmapStartBlock() {
        return bitmapStartBlock;
    }

    public int bitmapBlockCount() {
        return bitmapBlockCount;
    }

    public int inodeTableStartBlock() {
        return inodeTableStartBlock;
    }

    public int inodeTableBlockCount() {
        return inodeTableBlockCount;
    }

    public int userTableBlock() {
        return userTableBlock;
    }

    public int groupTableBlock() {
        return groupTableBlock;
    }

    public int dataRegionStartBlock() {
        return dataRegionStartBlock;
    }
}
