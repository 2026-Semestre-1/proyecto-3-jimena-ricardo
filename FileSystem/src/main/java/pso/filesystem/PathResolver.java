package pso.filesystem;

import java.io.IOException;

public final class PathResolver {

    private final FileSystem fileSystem;

    public PathResolver(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        this.fileSystem = fileSystem;
    }

    public ResolvedPath resolve(String path, int currentDirectoryInodeId) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        BinaryFormatValidator.requirePositive("currentDirectoryInodeId", currentDirectoryInodeId);

        int currentInodeId = path.startsWith("/")
                ? fileSystem.superBlock().rootInodeId()
                : currentDirectoryInodeId;

        if (path.equals("/")) {
            return new ResolvedPath(currentInodeId, fileSystem.readInode(currentInodeId));
        }

        String[] components = path.split("/");
        for (String component : components) {
            if (component.isEmpty() || component.equals(".")) {
                continue;
            }

            Inode currentInode = fileSystem.readInode(currentInodeId);
            if (currentInode.type() != InodeType.DIRECTORY) {
                throw new IllegalArgumentException("not a directory: " + component);
            }

            DirectoryEntry entry = fileSystem.findDirectoryEntry(currentInodeId, component);
            currentInodeId = entry.inodeId();
        }

        return new ResolvedPath(currentInodeId, fileSystem.readInode(currentInodeId));
    }
}
