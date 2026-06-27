package pso.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public final class VirtualDisk implements AutoCloseable {

    private final RandomAccessFile file;
    private final int blockSize;

    private VirtualDisk(RandomAccessFile file, int blockSize) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        this.file = file;
        this.blockSize = blockSize;
    }

    public static VirtualDisk createOrOverwrite(String fileName, long diskSizeBytes, int blockSize) throws IOException {
        if (diskSizeBytes <= 0) {
            throw new IllegalArgumentException("diskSizeBytes must be positive");
        }

        RandomAccessFile randomAccessFile = new RandomAccessFile(new File(fileName), "rw");
        randomAccessFile.setLength(diskSizeBytes);
        return new VirtualDisk(randomAccessFile, blockSize);
    }

    public static VirtualDisk openReadOnly(String fileName, int blockSize) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(new File(fileName), "r");
        return new VirtualDisk(randomAccessFile, blockSize);
    }

    public static VirtualDisk openReadWrite(String fileName, int blockSize) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(new File(fileName), "rw");
        return new VirtualDisk(randomAccessFile, blockSize);
    }

    public void writeBlock(int blockNumber, byte[] bytes) throws IOException {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber cannot be negative");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        if (bytes.length > blockSize) {
            throw new IllegalArgumentException("block data is larger than block size");
        }

        byte[] block = bytes.length == blockSize ? bytes : Arrays.copyOf(bytes, blockSize);
        file.seek((long) blockNumber * blockSize);
        file.write(block);
    }

    public byte[] readBlock(int blockNumber) throws IOException {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber cannot be negative");
        }

        byte[] block = new byte[blockSize];
        file.seek((long) blockNumber * blockSize);
        file.readFully(block);
        return block;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
