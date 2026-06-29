package pso.filesystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OpenFileTable {

    public static final int RESERVED_BLOCK_COUNT = 16;

    private OpenFileTable() {
    }

    public static final class Handle implements AutoCloseable {
        private final FileSystem fileSystem;
        private final long handleId;
        private boolean closed;

        private Handle(FileSystem fileSystem, long handleId) {
            this.fileSystem = fileSystem;
            this.handleId = handleId;
        }

        public long handleId() {
            return handleId;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                OpenFileTable.close(fileSystem, handleId);
            }
        }
    }

    public static Handle open(
            FileSystem fileSystem,
            int sessionId,
            int userId,
            String username,
            OpenFileRecord.Mode mode,
            int inodeId,
            String requestedPath,
            String targetPath
    ) throws IOException {
        validateTableLayout(fileSystem);

        List<OpenFileRecord> records = cleanupDeadProcesses(readAll(fileSystem));
        long handleId = newHandleId();
        OpenFileRecord newRecord = new OpenFileRecord(
                true,
                mode,
                handleId,
                ProcessHandle.current().pid(),
                sessionId,
                userId,
                inodeId,
                System.currentTimeMillis(),
                username,
                requestedPath,
                targetPath
        );

        boolean inserted = false;
        for (int i = 0; i < records.size(); i++) {
            if (!records.get(i).used()) {
                records.set(i, newRecord);
                inserted = true;
                break;
            }
        }

        if (!inserted) {
            throw new IllegalStateException("open file table is full");
        }

        writeAll(fileSystem, records);
        return new Handle(fileSystem, handleId);
    }

    public static List<OpenFileRecord> list(FileSystem fileSystem) throws IOException {
        validateTableLayout(fileSystem);
        List<OpenFileRecord> cleaned = cleanupDeadProcesses(readAll(fileSystem));
        writeAll(fileSystem, cleaned);

        List<OpenFileRecord> used = new ArrayList<>();
        for (OpenFileRecord record : cleaned) {
            if (record.used()) {
                used.add(record);
            }
        }
        return used;
    }

    public static void close(FileSystem fileSystem, long handleId) throws IOException {
        validateTableLayout(fileSystem);
        List<OpenFileRecord> records = readAll(fileSystem);
        for (int i = 0; i < records.size(); i++) {
            OpenFileRecord record = records.get(i);
            if (record.used() && record.handleId() == handleId) {
                records.set(i, OpenFileRecord.unused());
                break;
            }
        }
        writeAll(fileSystem, records);
    }

    public static void clear(FileSystem fileSystem) throws IOException {
        validateTableLayout(fileSystem);
        writeAll(fileSystem, emptyRecords(fileSystem));
    }

    private static void validateTableLayout(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        if (fileSystem.openFileTableBlockCount() <= 0) {
            throw new IllegalStateException("disk does not reserve an open file table");
        }
        if (fileSystem.openFileTableBlockCount() != RESERVED_BLOCK_COUNT) {
            throw new IllegalStateException("unexpected open file table block count: "
                    + fileSystem.openFileTableBlockCount());
        }
    }

    private static List<OpenFileRecord> readAll(FileSystem fileSystem) throws IOException {
        int recordsPerBlock = fileSystem.superBlock().blockSize() / OpenFileRecord.BINARY_SIZE;
        List<OpenFileRecord> records = new ArrayList<>(capacity(fileSystem));

        try (VirtualDisk disk = VirtualDisk.openReadOnly(fileSystem.diskName(), fileSystem.superBlock().blockSize())) {
            for (int block = 0; block < fileSystem.openFileTableBlockCount(); block++) {
                byte[] blockBytes = disk.readBlock(fileSystem.openFileTableStartBlock() + block);
                for (int slot = 0; slot < recordsPerBlock; slot++) {
                    int start = slot * OpenFileRecord.BINARY_SIZE;
                    int end = start + OpenFileRecord.BINARY_SIZE;
                    records.add(OpenFileRecord.fromBytes(Arrays.copyOfRange(blockBytes, start, end)));
                }
            }
        }

        return records;
    }

    private static void writeAll(FileSystem fileSystem, List<OpenFileRecord> records) throws IOException {
        int capacity = capacity(fileSystem);
        if (records.size() != capacity) {
            throw new IllegalArgumentException("record list must contain exactly " + capacity + " records");
        }

        int blockSize = fileSystem.superBlock().blockSize();
        int recordsPerBlock = blockSize / OpenFileRecord.BINARY_SIZE;

        try (VirtualDisk disk = VirtualDisk.openReadWrite(fileSystem.diskName(), blockSize)) {
            int recordIndex = 0;
            for (int block = 0; block < fileSystem.openFileTableBlockCount(); block++) {
                byte[] blockBytes = new byte[blockSize];
                for (int slot = 0; slot < recordsPerBlock; slot++) {
                    byte[] recordBytes = records.get(recordIndex++).toBytes();
                    System.arraycopy(
                            recordBytes,
                            0,
                            blockBytes,
                            slot * OpenFileRecord.BINARY_SIZE,
                            OpenFileRecord.BINARY_SIZE
                    );
                }
                disk.writeBlock(fileSystem.openFileTableStartBlock() + block, blockBytes);
            }
        }
    }

    private static List<OpenFileRecord> cleanupDeadProcesses(List<OpenFileRecord> records) {
        List<OpenFileRecord> cleaned = new ArrayList<>(records.size());
        for (OpenFileRecord record : records) {
            if (!record.used() || processIsAlive(record.processId())) {
                cleaned.add(record);
            } else {
                cleaned.add(OpenFileRecord.unused());
            }
        }
        return cleaned;
    }

    private static boolean processIsAlive(long processId) {
        if (processId == ProcessHandle.current().pid()) {
            return true;
        }
        return ProcessHandle.of(processId)
                .map(ProcessHandle::isAlive)
                .orElse(false);
    }

    private static List<OpenFileRecord> emptyRecords(FileSystem fileSystem) {
        int capacity = capacity(fileSystem);
        List<OpenFileRecord> records = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            records.add(OpenFileRecord.unused());
        }
        return records;
    }

    private static int capacity(FileSystem fileSystem) {
        return fileSystem.openFileTableBlockCount()
                * (fileSystem.superBlock().blockSize() / OpenFileRecord.BINARY_SIZE);
    }

    private static long newHandleId() {
        long value = System.nanoTime() ^ System.currentTimeMillis() ^ ProcessHandle.current().pid();
        if (value == Long.MIN_VALUE) {
            return 1;
        }
        value = Math.abs(value);
        return value == 0 ? 1 : value;
    }
}
