package pso.filesystem;

import java.io.IOException;
import pso.filesystem.shell.Session;

public final class PermissionManager {

    public static final int READ = 4;
    public static final int WRITE = 2;
    public static final int EXECUTE = 1;

    private static final int IMPLICIT_FILE_PERMISSIONS = READ;
    private static final int IMPLICIT_DIRECTORY_PERMISSIONS = READ | EXECUTE;
    private static final int IMPLICIT_SYMLINK_PERMISSIONS = READ | EXECUTE;

    private final FileSystem fileSystem;

    public PermissionManager(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        this.fileSystem = fileSystem;
    }

    public boolean canRead(Session session, Inode inode) {
        return hasPermission(session, inode, READ);
    }

    public boolean canWrite(Session session, Inode inode) {
        return hasPermission(session, inode, WRITE);
    }

    public boolean canExecute(Session session, Inode inode) {
        return hasPermission(session, inode, EXECUTE);
    }

    public void requireRead(Session session, Inode inode) {
        require(canRead(session, inode));
    }

    public void requireWrite(Session session, Inode inode) {
        require(canWrite(session, inode));
    }

    public void requireExecute(Session session, Inode inode) {
        require(canExecute(session, inode));
    }

    public void requireReadAndWrite(Session session, Inode inode) {
        require(canRead(session, inode) && canWrite(session, inode));
    }

    public void requireWriteAndExecute(Session session, Inode inode) {
        require(canWrite(session, inode) && canExecute(session, inode));
    }

    private boolean hasPermission(Session session, Inode inode, int permissionBit) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (inode == null) {
            throw new IllegalArgumentException("inode cannot be null");
        }
        if (session.isRoot()) {
            return true;
        }

        int effectivePermissions;
        if (session.currentUserId() == inode.ownerUserId()) {
            effectivePermissions = ownerPermissions(inode.permissions());
        } else if (isCurrentUserInInodeGroup(session, inode)) {
            effectivePermissions = groupPermissions(inode.permissions());
        } else {
            effectivePermissions = implicitPermissions(inode.type());
        }

        return (effectivePermissions & permissionBit) == permissionBit;
    }

    private boolean isCurrentUserInInodeGroup(Session session, Inode inode) {
        try {
            return fileSystem.isUserInGroup(session.currentUserId(), inode.groupId());
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
    }

    private int ownerPermissions(int permissions) {
        return (permissions >> 4) & 0xF;
    }

    private int groupPermissions(int permissions) {
        return permissions & 0xF;
    }

    private int implicitPermissions(InodeType type) {
        return switch (type) {
            case FILE -> IMPLICIT_FILE_PERMISSIONS;
            case DIRECTORY -> IMPLICIT_DIRECTORY_PERMISSIONS;
            case SYMLINK -> IMPLICIT_SYMLINK_PERMISSIONS;
            case UNUSED -> 0;
        };
    }

    private void require(boolean allowed) {
        if (!allowed) {
            throw new IllegalArgumentException("permission denied");
        }
    }
}
