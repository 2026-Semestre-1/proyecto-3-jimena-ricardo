package pso.filesystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileSystem {

    private final String diskName;
    private final BootBlock bootBlock;
    private final SuperBlock superBlock;

    private FileSystem(String diskName, BootBlock bootBlock, SuperBlock superBlock) {
        this.diskName = diskName;
        this.bootBlock = bootBlock;
        this.superBlock = superBlock;
    }

    public static FileSystem mount(String diskName) throws IOException {
        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, BootBlock.BINARY_SIZE)) {
            BootBlock bootBlock = BootBlock.fromBytes(disk.readBlock(0));
            SuperBlock superBlock = SuperBlock.fromBytes(disk.readBlock(bootBlock.superBlockIndex()));
            return new FileSystem(diskName, bootBlock, superBlock);
        }
    }

    public String diskName() {
        return diskName;
    }

    public BootBlock bootBlock() {
        return bootBlock;
    }

    public SuperBlock superBlock() {
        return superBlock;
    }

    public UserRecord readUserRecord(int userId) throws IOException {
        BinaryFormatValidator.requireNonNegative("userId", userId);

        int recordsPerBlock = superBlock.blockSize() / UserRecord.BINARY_SIZE;
        int blockOffset = userId / recordsPerBlock;
        int slotInBlock = userId % recordsPerBlock;

        if (blockOffset >= superBlock.userTableBlockCount()) {
            throw new IllegalArgumentException("user id out of range: " + userId);
        }

        int blockNumber = superBlock.userTableStartBlock() + blockOffset;

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            int start = slotInBlock * UserRecord.BINARY_SIZE;
            int end = start + UserRecord.BINARY_SIZE;
            UserRecord user = UserRecord.fromBytes(Arrays.copyOfRange(block, start, end));

            if (!user.used()) {
                throw new IllegalArgumentException("user does not exist: " + userId);
            }
            if (user.userId() != userId) {
                throw new IllegalArgumentException("user table is inconsistent for user id: " + userId);
            }

            return user;
        }
    }

    public Inode readInode(int inodeId) throws IOException {
        BinaryFormatValidator.requirePositive("inodeId", inodeId);

        int inodesPerBlock = superBlock.blockSize() / Inode.BINARY_SIZE;
        int blockOffset = (inodeId - 1) / inodesPerBlock;
        int slotInBlock = (inodeId - 1) % inodesPerBlock;

        if (blockOffset >= superBlock.inodeTableBlockCount()) {
            throw new IllegalArgumentException("inode id out of range: " + inodeId);
        }

        int blockNumber = superBlock.inodeTableStartBlock() + blockOffset;

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            int start = slotInBlock * Inode.BINARY_SIZE;
            int end = start + Inode.BINARY_SIZE;
            Inode inode = Inode.fromBytes(Arrays.copyOfRange(block, start, end));

            if (inode.type() == InodeType.UNUSED) {
                throw new IllegalArgumentException("inode does not exist: " + inodeId);
            }
            if (inode.inodeId() != inodeId) {
                throw new IllegalArgumentException("inode table is inconsistent for inode id: " + inodeId);
            }

            return inode;
        }
    }

    public IndexBlock readIndexBlock(int blockId) throws IOException {
        BinaryFormatValidator.requireBlockIndex("blockId", blockId, superBlock.totalBlocks());

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
            return IndexBlock.fromBytes(disk.readBlock(blockId));
        }
    }

    public List<DirectoryEntry> readDirectoryEntries(Inode directoryInode) throws IOException {
        if (directoryInode == null) {
            throw new IllegalArgumentException("directoryInode cannot be null");
        }
        if (directoryInode.type() != InodeType.DIRECTORY) {
            throw new IllegalArgumentException("inode is not a directory: " + directoryInode.inodeId());
        }
        if (directoryInode.sizeBytes() % DirectoryEntry.BINARY_SIZE != 0) {
            throw new IllegalArgumentException("directory size is not aligned to directory entry size");
        }
        if (directoryInode.sizeBytes() == 0) {
            return List.of();
        }
        if (directoryInode.indexBlockId() == Inode.NO_INDEX_BLOCK) {
            throw new IllegalArgumentException("directory has entries but no index block: " + directoryInode.inodeId());
        }

        int entriesRemaining = Math.toIntExact(directoryInode.sizeBytes() / DirectoryEntry.BINARY_SIZE);
        List<Integer> dataBlockIds = readIndexBlock(directoryInode.indexBlockId()).blockPointers();
        List<DirectoryEntry> entries = new ArrayList<>();

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
            for (int dataBlockId : dataBlockIds) {
                if (entriesRemaining == 0) {
                    break;
                }

                byte[] block = disk.readBlock(dataBlockId);
                int entriesInThisBlock = Math.min(entriesRemaining, superBlock.blockSize() / DirectoryEntry.BINARY_SIZE);
                for (int i = 0; i < entriesInThisBlock; i++) {
                    int start = i * DirectoryEntry.BINARY_SIZE;
                    int end = start + DirectoryEntry.BINARY_SIZE;
                    entries.add(DirectoryEntry.fromBytes(Arrays.copyOfRange(block, start, end)));
                }
                entriesRemaining -= entriesInThisBlock;
            }
        }

        if (entriesRemaining != 0) {
            throw new IllegalArgumentException("directory index block does not point to enough data blocks");
        }

        return entries;
    }

    public DirectoryEntry findDirectoryEntry(int directoryInodeId, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("directory entry name cannot be blank");
        }

        Inode directoryInode = readInode(directoryInodeId);
        for (DirectoryEntry entry : readDirectoryEntries(directoryInode)) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }

        throw new IllegalArgumentException("directory entry not found: " + name);
    }
}
