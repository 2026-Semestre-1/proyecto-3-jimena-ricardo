package pso.filesystem;

import java.io.IOException;
import java.util.Arrays;

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
}
