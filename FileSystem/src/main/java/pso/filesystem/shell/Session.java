package pso.filesystem.shell;

import pso.filesystem.FileSystem;

public final class Session {

    private final int sessionId;
    private final FileSystem fileSystem;
    private int currentUserId;
    private int currentDirectoryInodeId;

    Session(int sessionId, FileSystem fileSystem, int currentUserId, int currentDirectoryInodeId) {
        if (sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }
        if (fileSystem == null) {
            throw new IllegalArgumentException("fileSystem cannot be null");
        }
        if (currentUserId < 0) {
            throw new IllegalArgumentException("currentUserId cannot be negative");
        }
        if (currentDirectoryInodeId <= 0) {
            throw new IllegalArgumentException("currentDirectoryInodeId must be positive");
        }

        this.sessionId = sessionId;
        this.fileSystem = fileSystem;
        this.currentUserId = currentUserId;
        this.currentDirectoryInodeId = currentDirectoryInodeId;
    }

    public int sessionId() {
        return sessionId;
    }

    public FileSystem fileSystem() {
        return fileSystem;
    }

    public int currentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(int currentUserId) {
        if (currentUserId < 0) {
            throw new IllegalArgumentException("currentUserId cannot be negative");
        }
        this.currentUserId = currentUserId;
    }

    public int currentDirectoryInodeId() {
        return currentDirectoryInodeId;
    }

    public void setCurrentDirectoryInodeId(int currentDirectoryInodeId) {
        if (currentDirectoryInodeId <= 0) {
            throw new IllegalArgumentException("currentDirectoryInodeId must be positive");
        }
        this.currentDirectoryInodeId = currentDirectoryInodeId;
    }

    public boolean isRoot() {
        return currentUserId == 0;
    }
}
