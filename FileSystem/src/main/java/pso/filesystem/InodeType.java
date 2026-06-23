package pso.filesystem;

public enum InodeType {
    UNUSED(0),
    FILE(1),
    DIRECTORY(2),
    SYMLINK(3);

    private final int code;

    InodeType(int code) {
        this.code = code;
    }

    public byte code() {
        return (byte) code;
    }

    public static InodeType fromCode(byte code) {
        int unsignedCode = code & 0xFF;
        for (InodeType type : values()) {
            if (type.code == unsignedCode) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown inode type code: " + unsignedCode);
    }
}
