package pso.filesystem.shell;

import pso.filesystem.FileSystem;

public final class SessionManager {

    public static final int ROOT_USER_ID = 0;
    public static final int ROOT_HOME_INODE_ID = 2;

    private final FileSystem fileSystem;
    private int nextSessionId = 1;

    public SessionManager(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        this.fileSystem = fileSystem;
    }

    public Session createRootSession() {
        return new Session(nextSessionId++, fileSystem, ROOT_USER_ID, ROOT_HOME_INODE_ID);
    }

    public Session createUserSession(int userId, int homeDirectoryInodeId) {
        return new Session(nextSessionId++, fileSystem, userId, homeDirectoryInodeId);
    }
}
