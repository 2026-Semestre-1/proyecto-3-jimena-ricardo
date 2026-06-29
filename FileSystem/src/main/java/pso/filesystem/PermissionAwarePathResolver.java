package pso.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import pso.filesystem.shell.Session;

public final class PermissionAwarePathResolver {

    private static final int MAX_SYMLINK_DEPTH = 16;

    private final FileSystem fileSystem;
    private final PermissionManager permissionManager;

    public PermissionAwarePathResolver(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        this.fileSystem = fileSystem;
        this.permissionManager = new PermissionManager(fileSystem);
    }

    public ResolvedPath resolve(String path, Session session) throws IOException {
        return resolve(path, session, true);
    }

    public ResolvedPath resolve(String path, Session session, boolean followFinalSymlink) throws IOException {
        return resolveInternal(path, session, followFinalSymlink, 0);
    }

    private ResolvedPath resolveInternal(
            String path,
            Session session,
            boolean followFinalSymlink,
            int symlinkDepth
    ) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (symlinkDepth > MAX_SYMLINK_DEPTH) {
            throw new IllegalArgumentException("too many symbolic links");
        }
        BinaryFormatValidator.requirePositive("currentDirectoryInodeId", session.currentDirectoryInodeId());

        String pathToResolve = path.startsWith("/") ? path : appendPath(session.currentPath(), path);
        int currentInodeId = fileSystem.superBlock().rootInodeId();

        if (pathToResolve.equals("/")) {
            return new ResolvedPath(currentInodeId, fileSystem.readInode(currentInodeId));
        }

        String[] components = pathToResolve.split("/");
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            if (component.isEmpty() || component.equals(".")) {
                continue;
            }

            Inode currentInode = fileSystem.readInode(currentInodeId);
            if (currentInode.type() != InodeType.DIRECTORY) {
                throw new IllegalArgumentException("not a directory: " + component);
            }

            permissionManager.requireExecute(session, currentInode);
            DirectoryEntry entry = fileSystem.findDirectoryEntry(currentInodeId, component);
            Inode nextInode = fileSystem.readInode(entry.inodeId());
            boolean finalComponent = isFinalComponent(components, i);

            if (nextInode.type() == InodeType.SYMLINK && (!finalComponent || followFinalSymlink)) {
                permissionManager.requireExecute(session, nextInode);
                String target = symlinkTarget(nextInode);
                String remaining = remainingPath(components, i + 1);
                String combined = appendPath(target, remaining);
                return resolveInternal(combined, session, followFinalSymlink, symlinkDepth + 1);
            }

            currentInodeId = entry.inodeId();
        }

        return new ResolvedPath(currentInodeId, fileSystem.readInode(currentInodeId));
    }

    private String symlinkTarget(Inode symlinkInode) throws IOException {
        String target = new String(fileSystem.readFileBytes(symlinkInode), StandardCharsets.US_ASCII).trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("symbolic link target is blank");
        }
        return target;
    }

    private boolean isFinalComponent(String[] components, int currentIndex) {
        for (int i = currentIndex + 1; i < components.length; i++) {
            if (!components[i].isEmpty() && !components[i].equals(".")) {
                return false;
            }
        }
        return true;
    }

    private String remainingPath(String[] components, int startIndex) {
        StringBuilder remaining = new StringBuilder();
        for (int i = startIndex; i < components.length; i++) {
            if (components[i].isEmpty() || components[i].equals(".")) {
                continue;
            }
            if (!remaining.isEmpty()) {
                remaining.append('/');
            }
            remaining.append(components[i]);
        }
        return remaining.toString();
    }

    private String appendPath(String basePath, String remainingPath) {
        if (remainingPath.isBlank()) {
            return basePath;
        }
        if (basePath.equals("/")) {
            return "/" + remainingPath;
        }
        return basePath + "/" + remainingPath;
    }
}
