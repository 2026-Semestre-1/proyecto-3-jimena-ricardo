package pso.filesystem;

import java.io.IOException;

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
}
