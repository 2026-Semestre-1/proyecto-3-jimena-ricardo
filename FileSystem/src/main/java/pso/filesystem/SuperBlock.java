package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class SuperBlock {

    public static final int MAGIC_SIZE = 4;
    public static final int BINARY_SIZE = 1024;
    public static final byte[] MAGIC = new byte[] {'J', 'R', 'F', 'S'};

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

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
        BinaryFormatValidator.requirePositive("totalBlocks", totalBlocks);
        BinaryFormatValidator.requirePositive("blockSize", blockSize);
        BinaryFormatValidator.requireNonNegative("usedBlocks", usedBlocks);
        BinaryFormatValidator.requireNonNegative("freeBlocks", freeBlocks);
        if (usedBlocks + freeBlocks != totalBlocks) {
            throw new IllegalArgumentException("usedBlocks + freeBlocks must equal totalBlocks");
        }
        BinaryFormatValidator.requirePositive("rootInodeId", rootInodeId);
        if (nextInodeId <= rootInodeId) {
            throw new IllegalArgumentException("nextInodeId must be greater than rootInodeId");
        }
        BinaryFormatValidator.requireBlockRange("bitmap", bitmapStartBlock, bitmapBlockCount, totalBlocks);
        BinaryFormatValidator.requireBlockRange("inode table", inodeTableStartBlock, inodeTableBlockCount, totalBlocks);
        BinaryFormatValidator.requireBlockIndex("userTableBlock", userTableBlock, totalBlocks);
        BinaryFormatValidator.requireBlockIndex("groupTableBlock", groupTableBlock, totalBlocks);
        BinaryFormatValidator.requireBlockIndex("dataRegionStartBlock", dataRegionStartBlock, totalBlocks);

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
        BinaryFormatValidator.requireBytesAtLeast("SuperBlock", bytes, BINARY_SIZE);

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
