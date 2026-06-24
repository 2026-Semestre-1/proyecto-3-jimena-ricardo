package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class GroupRecord {

    public static final int BINARY_SIZE = 128;
    public static final int GROUP_NAME_SIZE = 16;
    public static final int MAX_MEMBER_COUNT = 25;
    public static final int UNUSED_MEMBER_ID = -1;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final boolean used;
    private final int groupId;
    private final String groupName;
    private final int[] memberUserIds;

    public GroupRecord(boolean used, int groupId, String groupName, int[] memberUserIds) {
        if (used) {
            BinaryFormatValidator.requireNonNegative("groupId", groupId);
            BinaryFormatValidator.requireFixedUtf8Length("groupName", groupName, GROUP_NAME_SIZE);
            if (groupName.isBlank()) {
                throw new IllegalArgumentException("groupName cannot be blank");
            }
            if (memberUserIds == null) {
                throw new IllegalArgumentException("memberUserIds cannot be null");
            }
            if (memberUserIds.length > MAX_MEMBER_COUNT) {
                throw new IllegalArgumentException("group cannot have more than " + MAX_MEMBER_COUNT + " members");
            }
            for (int memberUserId : memberUserIds) {
                BinaryFormatValidator.requireNonNegative("memberUserId", memberUserId);
            }
        }

        this.used = used;
        this.groupId = groupId;
        this.groupName = groupName == null ? "" : groupName;
        this.memberUserIds = memberUserIds == null ? new int[0] : Arrays.copyOf(memberUserIds, memberUserIds.length);
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.put((byte) (used ? 1 : 0));
        buffer.put(new byte[3]);
        buffer.putInt(groupId);
        putFixedString(buffer, groupName, GROUP_NAME_SIZE);
        buffer.putInt(memberUserIds.length);

        for (int memberUserId : memberUserIds) {
            buffer.putInt(memberUserId);
        }
        for (int i = memberUserIds.length; i < MAX_MEMBER_COUNT; i++) {
            buffer.putInt(UNUSED_MEMBER_ID);
        }

        return buffer.array();
    }

    public static GroupRecord fromBytes(byte[] bytes) {
        BinaryFormatValidator.requireBytesAtLeast("GroupRecord", bytes, BINARY_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        boolean used = buffer.get() != 0;
        buffer.position(buffer.position() + 3);
        int groupId = buffer.getInt();
        String groupName = readFixedString(buffer, GROUP_NAME_SIZE);
        int memberCount = buffer.getInt();
        if (memberCount < 0 || memberCount > MAX_MEMBER_COUNT) {
            throw new IllegalArgumentException("invalid group member count: " + memberCount);
        }

        int[] memberUserIds = new int[memberCount];
        for (int i = 0; i < MAX_MEMBER_COUNT; i++) {
            int memberUserId = buffer.getInt();
            if (i < memberCount) {
                memberUserIds[i] = memberUserId;
            }
        }

        return new GroupRecord(used, groupId, groupName, memberUserIds);
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

    public int groupId() {
        return groupId;
    }

    public String groupName() {
        return groupName;
    }

    public int[] memberUserIds() {
        return Arrays.copyOf(memberUserIds, memberUserIds.length);
    }
}
