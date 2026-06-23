package pso.filesystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class Inode {

    public static final int BINARY_SIZE = 64;
    public static final int UNUSED_INODE_ID = 0;
    public static final int NO_INDEX_BLOCK = -1;

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final int MAX_PERMISSION_DIGIT = 7;

    private final int inodeId;
    private final InodeType type;
    private final int permissions;
    private final int ownerUserId;
    private final int groupId;
    private final long sizeBytes;
    private final int indexBlockId;
    private final int linkCount;
    private final long createdTimeMillis;
    private final long modifiedTimeMillis;
    private final long accessedTimeMillis;

    public Inode(
            int inodeId,
            InodeType type,
            int permissions,
            int ownerUserId,
            int groupId,
            long sizeBytes,
            int indexBlockId,
            int linkCount,
            long createdTimeMillis,
            long modifiedTimeMillis,
            long accessedTimeMillis
    ) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        validateInode(
                inodeId,
                type,
                permissions,
                ownerUserId,
                groupId,
                sizeBytes,
                indexBlockId,
                linkCount,
                createdTimeMillis,
                modifiedTimeMillis,
                accessedTimeMillis
        );

        this.inodeId = inodeId;
        this.permissions = permissions;
        this.ownerUserId = ownerUserId;
        this.groupId = groupId;
        this.sizeBytes = sizeBytes;
        this.indexBlockId = indexBlockId;
        this.linkCount = linkCount;
        this.createdTimeMillis = createdTimeMillis;
        this.modifiedTimeMillis = modifiedTimeMillis;
        this.accessedTimeMillis = accessedTimeMillis;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_SIZE);
        buffer.order(BYTE_ORDER);

        buffer.putInt(inodeId);
        buffer.put(type.code());
        buffer.put((byte) permissions);
        buffer.putShort((short) 0);
        buffer.putInt(ownerUserId);
        buffer.putInt(groupId);
        buffer.putLong(sizeBytes);
        buffer.putInt(indexBlockId);
        buffer.putInt(linkCount);
        buffer.putLong(createdTimeMillis);
        buffer.putLong(modifiedTimeMillis);
        buffer.putLong(accessedTimeMillis);
        buffer.putLong(0L);

        return buffer.array();
    }

    public static Inode fromBytes(byte[] bytes) {
        BinaryFormatValidator.requireBytesAtLeast("Inode", bytes, BINARY_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(BYTE_ORDER);

        int inodeId = buffer.getInt();
        InodeType type = InodeType.fromCode(buffer.get());
        int permissions = buffer.get() & 0xFF;
        buffer.getShort();
        int ownerUserId = buffer.getInt();
        int groupId = buffer.getInt();
        long sizeBytes = buffer.getLong();
        int indexBlockId = buffer.getInt();
        int linkCount = buffer.getInt();
        long createdTimeMillis = buffer.getLong();
        long modifiedTimeMillis = buffer.getLong();
        long accessedTimeMillis = buffer.getLong();

        return new Inode(
                inodeId,
                type,
                permissions,
                ownerUserId,
                groupId,
                sizeBytes,
                indexBlockId,
                linkCount,
                createdTimeMillis,
                modifiedTimeMillis,
                accessedTimeMillis
        );
    }

    private static void validateInode(
            int inodeId,
            InodeType type,
            int permissions,
            int ownerUserId,
            int groupId,
            long sizeBytes,
            int indexBlockId,
            int linkCount,
            long createdTimeMillis,
            long modifiedTimeMillis,
            long accessedTimeMillis
    ) {
        if (type == InodeType.UNUSED) {
            if (inodeId != UNUSED_INODE_ID) {
                throw new IllegalArgumentException("unused inode id must be 0");
            }
            if (sizeBytes != 0 || linkCount != 0) {
                throw new IllegalArgumentException("unused inode size and link count must be 0");
            }
            return;
        }

        BinaryFormatValidator.requirePositive("inodeId", inodeId);
        requireValidPermissions(permissions);
        BinaryFormatValidator.requireNonNegative("ownerUserId", ownerUserId);
        BinaryFormatValidator.requireNonNegative("groupId", groupId);
        BinaryFormatValidator.requireNonNegative("sizeBytes", sizeBytes);
        if (indexBlockId < NO_INDEX_BLOCK) {
            throw new IllegalArgumentException("indexBlockId cannot be less than " + NO_INDEX_BLOCK);
        }
        BinaryFormatValidator.requirePositive("linkCount", linkCount);
        BinaryFormatValidator.requireNonNegative("createdTimeMillis", createdTimeMillis);
        BinaryFormatValidator.requireNonNegative("modifiedTimeMillis", modifiedTimeMillis);
        BinaryFormatValidator.requireNonNegative("accessedTimeMillis", accessedTimeMillis);
    }

    private static void requireValidPermissions(int permissions) {
        int ownerPermissions = (permissions >> 4) & 0xF;
        int groupPermissions = permissions & 0xF;
        if (permissions < 0 || permissions > 0x77
                || ownerPermissions > MAX_PERMISSION_DIGIT
                || groupPermissions > MAX_PERMISSION_DIGIT) {
            throw new IllegalArgumentException("permissions must be in owner/group format from 00 to 77");
        }
    }

    public int inodeId() {
        return inodeId;
    }

    public InodeType type() {
        return type;
    }

    public int permissions() {
        return permissions;
    }

    public int ownerUserId() {
        return ownerUserId;
    }

    public int groupId() {
        return groupId;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public int indexBlockId() {
        return indexBlockId;
    }

    public int linkCount() {
        return linkCount;
    }

    public long createdTimeMillis() {
        return createdTimeMillis;
    }

    public long modifiedTimeMillis() {
        return modifiedTimeMillis;
    }

    public long accessedTimeMillis() {
        return accessedTimeMillis;
    }
}
