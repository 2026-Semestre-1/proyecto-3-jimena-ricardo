package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class OpenFileRecord {

    public enum Mode {
        READ(1),
        READ_WRITE(2);

        private final int code;

        Mode(int code) {
            this.code = code;
        }

        public byte code() {
            return (byte) code;
        }

        public static Mode fromCode(byte code) {
            int unsignedCode = code & 0xFF;
            for (Mode mode : values()) {
                if (mode.code == unsignedCode) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown open file mode code: " + unsignedCode);
        }
    }

    public static final int BINARY_SIZE = 512;
    public static final int USERNAME_SIZE = 32;
    public static final int REQUESTED_PATH_SIZE = 220;
    public static final int TARGET_PATH_SIZE = 220;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final boolean used;
    private final Mode mode;
    private final long handleId;
    private final long processId;
    private final int sessionId;
    private final int userId;
    private final int inodeId;
    private final long openedAtMillis;
    private final String username;
    private final String requestedPath;
    private final String targetPath;

    public OpenFileRecord(
            boolean used,
            Mode mode,
            long handleId,
            long processId,
            int sessionId,
            int userId,
            int inodeId,
            long openedAtMillis,
            String username,
            String requestedPath,
            String targetPath
    ) {
        if (used) {
            if (mode == null) {
                throw new IllegalArgumentException("mode cannot be null");
            }
            BinaryFormatValidator.requirePositive("handleId", handleId);
            BinaryFormatValidator.requirePositive("processId", processId);
            BinaryFormatValidator.requirePositive("sessionId", sessionId);
            BinaryFormatValidator.requireNonNegative("userId", userId);
            BinaryFormatValidator.requirePositive("inodeId", inodeId);
            BinaryFormatValidator.requirePositive("openedAtMillis", openedAtMillis);
            BinaryFormatValidator.requireFixedUtf8Length("username", username, USERNAME_SIZE);
            if (username.isBlank()) {
                throw new IllegalArgumentException("username cannot be blank");
            }
            BinaryFormatValidator.requireFixedUtf8Length("requestedPath", requestedPath, REQUESTED_PATH_SIZE);
            if (requestedPath.isBlank() || !requestedPath.startsWith("/")) {
                throw new IllegalArgumentException("requestedPath must be absolute");
            }
            BinaryFormatValidator.requireFixedUtf8Length("targetPath", targetPath, TARGET_PATH_SIZE);
            if (targetPath.isBlank() || !targetPath.startsWith("/")) {
                throw new IllegalArgumentException("targetPath must be absolute");
            }
        }

        this.used = used;
        this.mode = mode == null ? Mode.READ : mode;
        this.handleId = handleId;
        this.processId = processId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.inodeId = inodeId;
        this.openedAtMillis = openedAtMillis;
        this.username = username == null ? "" : username;
        this.requestedPath = requestedPath == null ? "" : requestedPath;
        this.targetPath = targetPath == null ? "" : targetPath;
    }

    public static OpenFileRecord unused() {
        return new OpenFileRecord(false, Mode.READ, 0, 0, 0, 0, 0, 0, "", "", "");
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.put((byte) (used ? 1 : 0));
        buffer.put(mode.code());
        buffer.put(new byte[2]);
        buffer.putLong(handleId);
        buffer.putLong(processId);
        buffer.putInt(sessionId);
        buffer.putInt(userId);
        buffer.putInt(inodeId);
        buffer.putLong(openedAtMillis);
        putFixedString(buffer, username, USERNAME_SIZE);
        putFixedString(buffer, requestedPath, REQUESTED_PATH_SIZE);
        putFixedString(buffer, targetPath, TARGET_PATH_SIZE);

        return buffer.array();
    }

    public static OpenFileRecord fromBytes(byte[] bytes) {
        BinaryFormatValidator.requireBytesAtLeast("OpenFileRecord", bytes, BINARY_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        boolean used = buffer.get() != 0;
        byte modeCode = buffer.get();
        if (!used) {
            return unused();
        }
        Mode mode = Mode.fromCode(modeCode);
        buffer.position(buffer.position() + 2);
        long handleId = buffer.getLong();
        long processId = buffer.getLong();
        int sessionId = buffer.getInt();
        int userId = buffer.getInt();
        int inodeId = buffer.getInt();
        long openedAtMillis = buffer.getLong();
        String username = readFixedString(buffer, USERNAME_SIZE);
        String requestedPath = readFixedString(buffer, REQUESTED_PATH_SIZE);
        String targetPath = readFixedString(buffer, TARGET_PATH_SIZE);

        return new OpenFileRecord(
                used,
                mode,
                handleId,
                processId,
                sessionId,
                userId,
                inodeId,
                openedAtMillis,
                username,
                requestedPath,
                targetPath
        );
    }

    private static void putFixedString(ByteBuffer buffer, String value, int fieldSize) {
        BinaryFormatValidator.requireFixedUtf8Length("value", value, fieldSize);
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        buffer.put(raw);
        for (int i = raw.length; i < fieldSize; i++) {
            buffer.put((byte) 0);
        }
    }

    private static String readFixedString(ByteBuffer buffer, int fieldSize) {
        byte[] raw = new byte[fieldSize];
        buffer.get(raw);

        int length = 0;
        while (length < raw.length && raw[length] != 0) {
            length++;
        }

        return new String(raw, 0, length, StandardCharsets.UTF_8);
    }

    public boolean used() {
        return used;
    }

    public Mode mode() {
        return mode;
    }

    public long handleId() {
        return handleId;
    }

    public long processId() {
        return processId;
    }

    public int sessionId() {
        return sessionId;
    }

    public int userId() {
        return userId;
    }

    public int inodeId() {
        return inodeId;
    }

    public long openedAtMillis() {
        return openedAtMillis;
    }

    public String username() {
        return username;
    }

    public String requestedPath() {
        return requestedPath;
    }

    public String targetPath() {
        return targetPath;
    }
}
