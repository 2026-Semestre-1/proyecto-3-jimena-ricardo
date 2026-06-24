package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class UserRecord {

    public static final int BINARY_SIZE = 64;
    public static final int USERNAME_SIZE = 32;
    public static final int PASSWORD_SIZE = 16;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final boolean used;
    private final int userId;
    private final String username;
    private final int primaryGroupId;
    private final int homeDirectoryInodeId;
    private final String password;

    public UserRecord(
            boolean used,
            int userId,
            String username,
            int primaryGroupId,
            int homeDirectoryInodeId,
            String password
    ) {
        if (used) {
            BinaryFormatValidator.requireNonNegative("userId", userId);
            BinaryFormatValidator.requireFixedUtf8Length("username", username, USERNAME_SIZE);
            if (username.isBlank()) {
                throw new IllegalArgumentException("username cannot be blank");
            }
            BinaryFormatValidator.requireNonNegative("primaryGroupId", primaryGroupId);
            BinaryFormatValidator.requirePositive("homeDirectoryInodeId", homeDirectoryInodeId);
            BinaryFormatValidator.requireFixedUtf8Length("password", password, PASSWORD_SIZE);
            if (password.isBlank()) {
                throw new IllegalArgumentException("password cannot be blank");
            }
        }

        this.used = used;
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.primaryGroupId = primaryGroupId;
        this.homeDirectoryInodeId = homeDirectoryInodeId;
        this.password = password == null ? "" : password;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.put((byte) (used ? 1 : 0));
        buffer.put(new byte[3]);
        buffer.putInt(userId);
        putFixedString(buffer, username, USERNAME_SIZE);
        buffer.putInt(primaryGroupId);
        buffer.putInt(homeDirectoryInodeId);
        putFixedString(buffer, password, PASSWORD_SIZE);

        return buffer.array();
    }

    public static UserRecord fromBytes(byte[] bytes) {
        BinaryFormatValidator.requireBytesAtLeast("UserRecord", bytes, BINARY_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        boolean used = buffer.get() != 0;
        buffer.position(buffer.position() + 3);
        int userId = buffer.getInt();
        String username = readFixedString(buffer, USERNAME_SIZE);
        int primaryGroupId = buffer.getInt();
        int homeDirectoryInodeId = buffer.getInt();
        String password = readFixedString(buffer, PASSWORD_SIZE);

        return new UserRecord(used, userId, username, primaryGroupId, homeDirectoryInodeId, password);
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

    public int userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public int primaryGroupId() {
        return primaryGroupId;
    }

    public int homeDirectoryInodeId() {
        return homeDirectoryInodeId;
    }

    public String password() {
        return password;
    }
}
