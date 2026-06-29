package pso.filesystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileSystem {

    private final String diskName;
    private final BootBlock bootBlock;
    private SuperBlock superBlock;

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

    public FreeSpaceBitmap readFreeSpaceBitmap() throws IOException {
        int blockSize = superBlock.blockSize();
        byte[] bitmapBytes = new byte[blockSize * superBlock.bitmapBlockCount()];

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, blockSize)) {
            for (int i = 0; i < superBlock.bitmapBlockCount(); i++) {
                byte[] block = disk.readBlock(superBlock.bitmapStartBlock() + i);
                System.arraycopy(block, 0, bitmapBytes, i * blockSize, blockSize);
            }
        }

        return FreeSpaceBitmap.fromBytes(bitmapBytes, superBlock.totalBlocks());
    }

    public void writeFreeSpaceBitmap(FreeSpaceBitmap bitmap) throws IOException {
        if (bitmap == null) {
            throw new IllegalArgumentException("bitmap cannot be null");
        }

        int blockSize = superBlock.blockSize();
        byte[] bitmapBytes = bitmap.toBytes(blockSize * superBlock.bitmapBlockCount());

        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, blockSize)) {
            for (int i = 0; i < superBlock.bitmapBlockCount(); i++) {
                byte[] block = Arrays.copyOfRange(
                        bitmapBytes,
                        i * blockSize,
                        (i + 1) * blockSize);
                disk.writeBlock(superBlock.bitmapStartBlock() + i, block);
            }
        }
    }

    public int allocateBlock() throws IOException {
        FreeSpaceBitmap bitmap = readFreeSpaceBitmap();

        for (int block = superBlock.dataRegionStartBlock(); block < superBlock.totalBlocks(); block++) {
            if (bitmap.isFree(block)) {
                bitmap.markUsed(block);
                superBlock = superBlock.withBlockCounts(
                        superBlock.usedBlocks() + 1,
                        superBlock.freeBlocks() - 1);

                writeFreeSpaceBitmap(bitmap);
                writeSuperBlock();
                writeDataBlock(block, new byte[superBlock.blockSize()]);

                return block;
            }
        }

        throw new IllegalStateException("no free blocks available");
    }

    public int allocateInodeId() throws IOException {
        int inodeId = superBlock.nextInodeId();
        superBlock = superBlock.withNextInodeId(inodeId + 1);
        writeSuperBlock();
        return inodeId;
    }

    public void writeInode(Inode inode) throws IOException {
        if (inode == null) {
            throw new IllegalArgumentException("inode cannot be null");
        }

        int inodeId = inode.inodeId();
        int inodesPerBlock = superBlock.blockSize() / Inode.BINARY_SIZE;
        int blockOffset = (inodeId - 1) / inodesPerBlock;
        int slotInBlock = (inodeId - 1) % inodesPerBlock;

        if (inodeId <= 0 || blockOffset >= superBlock.inodeTableBlockCount()) {
            throw new IllegalArgumentException("inode id out of range: " + inodeId);
        }

        int blockNumber = superBlock.inodeTableStartBlock() + blockOffset;

        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            byte[] inodeBytes = inode.toBytes();
            System.arraycopy(
                    inodeBytes,
                    0,
                    block,
                    slotInBlock * Inode.BINARY_SIZE,
                    Inode.BINARY_SIZE);
            disk.writeBlock(blockNumber, block);
        }
    }

    public synchronized void clearInode(int inodeId) throws IOException {
        BinaryFormatValidator.requirePositive("inodeId", inodeId);

        int inodesPerBlock = superBlock.blockSize() / Inode.BINARY_SIZE;
        int blockOffset = (inodeId - 1) / inodesPerBlock;
        int slotInBlock = (inodeId - 1) % inodesPerBlock;

        if (blockOffset >= superBlock.inodeTableBlockCount()) {
            throw new IllegalArgumentException("inode id out of range: " + inodeId);
        }

        int blockNumber = superBlock.inodeTableStartBlock() + blockOffset;
        Inode unusedInode = new Inode(
                Inode.UNUSED_INODE_ID,
                InodeType.UNUSED,
                0,
                0,
                0,
                0,
                Inode.NO_INDEX_BLOCK,
                0,
                0,
                0,
                0
        );

        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            byte[] inodeBytes = unusedInode.toBytes();
            System.arraycopy(
                    inodeBytes,
                    0,
                    block,
                    slotInBlock * Inode.BINARY_SIZE,
                    Inode.BINARY_SIZE
            );
            disk.writeBlock(blockNumber, block);
        }
    }

    public void writeIndexBlock(int blockId, IndexBlock indexBlock) throws IOException {
        if (indexBlock == null) {
            throw new IllegalArgumentException("indexBlock cannot be null");
        }
        BinaryFormatValidator.requireBlockIndex("blockId", blockId, superBlock.totalBlocks());

        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            disk.writeBlock(blockId, indexBlock.toBytes(superBlock.blockSize()));
        }
    }

    public void writeDataBlock(int blockId, byte[] bytes) throws IOException {
        BinaryFormatValidator.requireBlockIndex("blockId", blockId, superBlock.totalBlocks());

        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            disk.writeBlock(blockId, bytes);
        }
    }

    private void writeSuperBlock() throws IOException {
        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, bootBlock.blockSize())) {
            disk.writeBlock(bootBlock.superBlockIndex(), superBlock.toBytes());
        }
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

    public synchronized void writeUserRecord(UserRecord userRecord) throws IOException {
        if (userRecord == null) {
            throw new IllegalArgumentException("userRecord cannot be null");
        }
        if (!userRecord.used()) {
            throw new IllegalArgumentException("cannot write unused user record with this method");
        }

        int userId = userRecord.userId();
        int recordsPerBlock = superBlock.blockSize() / UserRecord.BINARY_SIZE;
        int blockOffset = userId / recordsPerBlock;
        int slotInBlock = userId % recordsPerBlock;

        if (blockOffset >= superBlock.userTableBlockCount()) {
            throw new IllegalArgumentException("user id out of range: " + userId);
        }

        int blockNumber = superBlock.userTableStartBlock() + blockOffset;
        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            byte[] recordBytes = userRecord.toBytes();
            System.arraycopy(recordBytes, 0, block, slotInBlock * UserRecord.BINARY_SIZE, UserRecord.BINARY_SIZE);
            disk.writeBlock(blockNumber, block);
        }
    }

    public int allocateUserId() throws IOException {
        int recordsPerBlock = superBlock.blockSize() / UserRecord.BINARY_SIZE;
        int totalSlots = superBlock.userTableBlockCount() * recordsPerBlock;
        for (int userId = 0; userId < totalSlots; userId++) {
            try {
                readUserRecord(userId);
            } catch (IllegalArgumentException ex) {
                return userId;
            }
        }
        throw new IllegalStateException("no free user slots available");
    }

    public GroupRecord readGroupRecord(int groupId) throws IOException {
        BinaryFormatValidator.requireNonNegative("groupId", groupId);

        int recordsPerBlock = superBlock.blockSize() / GroupRecord.BINARY_SIZE;
        int blockOffset = groupId / recordsPerBlock;
        int slotInBlock = groupId % recordsPerBlock;

        if (blockOffset >= superBlock.groupTableBlockCount()) {
            throw new IllegalArgumentException("group id out of range: " + groupId);
        }

        int blockNumber = superBlock.groupTableStartBlock() + blockOffset;

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            int start = slotInBlock * GroupRecord.BINARY_SIZE;
            int end = start + GroupRecord.BINARY_SIZE;
            GroupRecord group = GroupRecord.fromBytes(Arrays.copyOfRange(block, start, end));

            if (!group.used()) {
                throw new IllegalArgumentException("group does not exist: " + groupId);
            }
            if (group.groupId() != groupId) {
                throw new IllegalArgumentException("group table is inconsistent for group id: " + groupId);
            }

            return group;
        }
    }

    public synchronized void writeGroupRecord(GroupRecord groupRecord) throws IOException {
        if (groupRecord == null) {
            throw new IllegalArgumentException("groupRecord cannot be null");
        }
        if (!groupRecord.used()) {
            throw new IllegalArgumentException("cannot write unused group record with this method");
        }

        int groupId = groupRecord.groupId();
        int recordsPerBlock = superBlock.blockSize() / GroupRecord.BINARY_SIZE;
        int blockOffset = groupId / recordsPerBlock;
        int slotInBlock = groupId % recordsPerBlock;

        if (blockOffset >= superBlock.groupTableBlockCount()) {
            throw new IllegalArgumentException("group id out of range: " + groupId);
        }

        int blockNumber = superBlock.groupTableStartBlock() + blockOffset;
        try (VirtualDisk disk = VirtualDisk.openReadWrite(diskName, superBlock.blockSize())) {
            byte[] block = disk.readBlock(blockNumber);
            byte[] recordBytes = groupRecord.toBytes();
            System.arraycopy(recordBytes, 0, block, slotInBlock * GroupRecord.BINARY_SIZE, GroupRecord.BINARY_SIZE);
            disk.writeBlock(blockNumber, block);
        }
    }

    public int allocateGroupId() throws IOException {
        int recordsPerBlock = superBlock.blockSize() / GroupRecord.BINARY_SIZE;
        int totalSlots = superBlock.groupTableBlockCount() * recordsPerBlock;
        for (int groupId = 0; groupId < totalSlots; groupId++) {
            try {
                readGroupRecord(groupId);
            } catch (IllegalArgumentException ex) {
                return groupId;
            }
        }
        throw new IllegalStateException("no free group slots available");
    }

    public boolean isUserInGroup(int userId, int groupId) throws IOException {
        BinaryFormatValidator.requireNonNegative("userId", userId);
        GroupRecord group = readGroupRecord(groupId);
        for (int memberUserId : group.memberUserIds()) {
            if (memberUserId == userId) {
                return true;
            }
        }
        return false;
    }

    public UserRecord findUserByUsername(String username) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }

        int recordsPerBlock = superBlock.blockSize() / UserRecord.BINARY_SIZE;
        int totalSlots = superBlock.userTableBlockCount() * recordsPerBlock;
        for (int userId = 0; userId < totalSlots; userId++) {
            try {
                UserRecord user = readUserRecord(userId);
                if (user.username().equals(username)) {
                    return user;
                }
            } catch (IllegalArgumentException ex) {
                // Empty user table slots are expected while scanning.
            }
        }

        throw new IllegalArgumentException("user not found: " + username);
    }

    public GroupRecord findGroupByName(String groupName) throws IOException {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("group name cannot be blank");
        }

        int recordsPerBlock = superBlock.blockSize() / GroupRecord.BINARY_SIZE;
        int totalSlots = superBlock.groupTableBlockCount() * recordsPerBlock;
        for (int groupId = 0; groupId < totalSlots; groupId++) {
            try {
                GroupRecord group = readGroupRecord(groupId);
                if (group.groupName().equals(groupName)) {
                    return group;
                }
            } catch (IllegalArgumentException ex) {
                // Empty group table slots are expected while scanning.
            }
        }

        throw new IllegalArgumentException("group not found: " + groupName);
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

    public byte[] readFileBytes(Inode fileInode) throws IOException {
        if (fileInode == null) {
            throw new IllegalArgumentException("fileInode cannot be null");
        }
        if (fileInode.type() != InodeType.FILE && fileInode.type() != InodeType.SYMLINK) {
            throw new IllegalArgumentException("inode is not a file or symlink: " + fileInode.inodeId());
        }
        if (fileInode.sizeBytes() == 0) {
            return new byte[0];
        }
        if (fileInode.indexBlockId() == Inode.NO_INDEX_BLOCK) {
            throw new IllegalArgumentException("file has data but no index block: " + fileInode.inodeId());
        }

        int fileSize = Math.toIntExact(fileInode.sizeBytes());
        int blockSize = superBlock.blockSize();
        int dataBlocksNeeded = BinaryFormatValidator.ceilDiv(fileSize, blockSize);
        List<Integer> dataBlockIds = readIndexBlock(fileInode.indexBlockId()).blockPointers();
        if (dataBlockIds.size() < dataBlocksNeeded) {
            throw new IllegalArgumentException("file index block does not point to enough data blocks");
        }

        byte[] result = new byte[fileSize];
        int bytesCopied = 0;

        try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, blockSize)) {
            for (int i = 0; i < dataBlocksNeeded; i++) {
                byte[] block = disk.readBlock(dataBlockIds.get(i));
                int bytesToCopy = Math.min(blockSize, fileSize - bytesCopied);
                System.arraycopy(block, 0, result, bytesCopied, bytesToCopy);
                bytesCopied += bytesToCopy;
            }
        }

        return result;
    }

    public synchronized Inode writeFileBytes(Inode fileInode, byte[] bytes) throws IOException {
        if (fileInode == null) {
            throw new IllegalArgumentException("fileInode cannot be null");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        if (fileInode.type() != InodeType.FILE && fileInode.type() != InodeType.SYMLINK) {
            throw new IllegalArgumentException("inode is not a file or symlink: " + fileInode.inodeId());
        }

        int blockSize = superBlock.blockSize();
        int maxDataBlocks = blockSize / Integer.BYTES;
        int requiredDataBlocks = bytes.length == 0 ? 0 : BinaryFormatValidator.ceilDiv(bytes.length, blockSize);
        if (requiredDataBlocks > maxDataBlocks) {
            int maxFileBytes = maxDataBlocks * blockSize;
            throw new IllegalArgumentException("file is too large; max size is " + maxFileBytes + " bytes");
        }

        List<Integer> oldDataBlockIds = List.of();
        boolean hadIndexBlock = fileInode.indexBlockId() != Inode.NO_INDEX_BLOCK;
        if (hadIndexBlock) {
            oldDataBlockIds = readIndexBlock(fileInode.indexBlockId()).blockPointers();
        }

        int oldBlockCount = oldDataBlockIds.size() + (hadIndexBlock ? 1 : 0);
        int newBlockCount = requiredDataBlocks == 0 ? 0 : requiredDataBlocks + 1;
        if (superBlock.freeBlocks() + oldBlockCount < newBlockCount) {
            throw new IllegalStateException("no free blocks available");
        }

        for (int oldDataBlockId : oldDataBlockIds) {
            freeBlock(oldDataBlockId);
        }
        if (hadIndexBlock) {
            freeBlock(fileInode.indexBlockId());
        }

        int indexBlockId = Inode.NO_INDEX_BLOCK;
        List<Integer> newDataBlockIds = new ArrayList<>();

        if (requiredDataBlocks > 0) {
            indexBlockId = allocateBlock();
            for (int i = 0; i < requiredDataBlocks; i++) {
                int dataBlockId = allocateBlock();
                newDataBlockIds.add(dataBlockId);

                int start = i * blockSize;
                int end = Math.min(bytes.length, start + blockSize);
                writeDataBlock(dataBlockId, Arrays.copyOfRange(bytes, start, end));
            }

            writeIndexBlock(indexBlockId, new IndexBlock(newDataBlockIds));
        }

        long now = System.currentTimeMillis();
        Inode updatedInode = new Inode(
                fileInode.inodeId(),
                fileInode.type(),
                fileInode.permissions(),
                fileInode.ownerUserId(),
                fileInode.groupId(),
                bytes.length,
                indexBlockId,
                fileInode.linkCount(),
                fileInode.createdTimeMillis(),
                now,
                now
        );

        writeInode(updatedInode);
        return updatedInode;
    }

    public synchronized void freeBlock(int blockId) throws IOException {
        BinaryFormatValidator.requireBlockIndex("blockId", blockId, superBlock.totalBlocks());
        if (blockId < superBlock.dataRegionStartBlock()) {
            throw new IllegalArgumentException("cannot free metadata block: " + blockId);
        }

        FreeSpaceBitmap bitmap = readFreeSpaceBitmap();
        if (bitmap.isFree(blockId)) {
            throw new IllegalArgumentException("block is already free: " + blockId);
        }

        bitmap.markFree(blockId);
        superBlock = superBlock.withBlockCounts(
                superBlock.usedBlocks() - 1,
                superBlock.freeBlocks() + 1
        );

        writeFreeSpaceBitmap(bitmap);
        writeSuperBlock();
        writeDataBlock(blockId, new byte[superBlock.blockSize()]);
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
                int entriesInThisBlock = Math.min(entriesRemaining,
                        superBlock.blockSize() / DirectoryEntry.BINARY_SIZE);
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

    public Inode appendDirectoryEntry(Inode directoryInode, DirectoryEntry newEntry) throws IOException {
        if (directoryInode == null) {
            throw new IllegalArgumentException("directoryInode cannot be null");
        }
        if (newEntry == null) {
            throw new IllegalArgumentException("newEntry cannot be null");
        }
        if (directoryInode.type() != InodeType.DIRECTORY) {
            throw new IllegalArgumentException("inode is not a directory: " + directoryInode.inodeId());
        }
        if (directoryInode.sizeBytes() % DirectoryEntry.BINARY_SIZE != 0) {
            throw new IllegalArgumentException("directory size is not aligned to directory entry size");
        }

        for (DirectoryEntry existingEntry : readDirectoryEntries(directoryInode)) {
            if (existingEntry.name().equals(newEntry.name())) {
                throw new IllegalArgumentException("directory entry already exists: " + newEntry.name());
            }
        }

        int entriesPerBlock = superBlock.blockSize() / DirectoryEntry.BINARY_SIZE;
        if (entriesPerBlock <= 0) {
            throw new IllegalStateException("block size is too small for directory entries");
        }

        int existingEntryCount = Math.toIntExact(directoryInode.sizeBytes() / DirectoryEntry.BINARY_SIZE);
        int indexBlockId = directoryInode.indexBlockId();
        List<Integer> dataBlockIds;

        if (indexBlockId == Inode.NO_INDEX_BLOCK) {
            if (existingEntryCount != 0) {
                throw new IllegalArgumentException("directory has entries but no index block: " + directoryInode.inodeId());
            }

            indexBlockId = allocateBlock();
            dataBlockIds = new ArrayList<>();
        } else {
            dataBlockIds = new ArrayList<>(readIndexBlock(indexBlockId).blockPointers());
        }

        int targetPointerIndex = existingEntryCount / entriesPerBlock;
        int entryIndexInBlock = existingEntryCount % entriesPerBlock;
        int indexBlockCapacity = superBlock.blockSize() / Integer.BYTES;
        if (targetPointerIndex >= indexBlockCapacity) {
            throw new IllegalStateException("directory index block is full");
        }
        if (targetPointerIndex > dataBlockIds.size()) {
            throw new IllegalArgumentException("directory index block does not match directory size");
        }

        int targetDataBlockId;
        byte[] dataBlock;

        if (targetPointerIndex < dataBlockIds.size()) {
            targetDataBlockId = dataBlockIds.get(targetPointerIndex);

            try (VirtualDisk disk = VirtualDisk.openReadOnly(diskName, superBlock.blockSize())) {
                dataBlock = disk.readBlock(targetDataBlockId);
            }
        } else {
            targetDataBlockId = allocateBlock();
            dataBlockIds.add(targetDataBlockId);
            dataBlock = new byte[superBlock.blockSize()];
            writeIndexBlock(indexBlockId, new IndexBlock(dataBlockIds));
        }

        int entryOffset = entryIndexInBlock * DirectoryEntry.BINARY_SIZE;
        byte[] entryBytes = newEntry.toBytes();
        System.arraycopy(entryBytes, 0, dataBlock, entryOffset, DirectoryEntry.BINARY_SIZE);
        writeDataBlock(targetDataBlockId, dataBlock);

        long now = System.currentTimeMillis();
        Inode updatedDirectoryInode = new Inode(
                directoryInode.inodeId(),
                directoryInode.type(),
                directoryInode.permissions(),
                directoryInode.ownerUserId(),
                directoryInode.groupId(),
                directoryInode.sizeBytes() + DirectoryEntry.BINARY_SIZE,
                indexBlockId,
                directoryInode.linkCount(),
                directoryInode.createdTimeMillis(),
                now,
                now
        );

        writeInode(updatedDirectoryInode);
        return updatedDirectoryInode;
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
