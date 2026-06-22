package pso.filesystem;

import java.nio.charset.StandardCharsets;

public final class BinaryFormatValidator {

    private BinaryFormatValidator() {
    }

    public static void requirePositive(String fieldName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public static void requirePositive(String fieldName, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public static void requireNonNegative(String fieldName, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    public static void requireNonNegative(String fieldName, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    public static void requireBytesAtLeast(String structureName, byte[] bytes, int minLength) {
        if (bytes == null || bytes.length < minLength) {
            throw new IllegalArgumentException(structureName + " requires at least " + minLength + " bytes");
        }
    }

    public static void requireBytesExactly(String structureName, byte[] bytes, int expectedLength) {
        if (bytes == null || bytes.length != expectedLength) {
            throw new IllegalArgumentException(structureName + " requires exactly " + expectedLength + " bytes");
        }
    }

    public static void requireBlockIndex(String fieldName, int blockIndex, int totalBlocks) {
        requirePositive("totalBlocks", totalBlocks);

        if (blockIndex < 0 || blockIndex >= totalBlocks) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and " + (totalBlocks - 1));
        }
    }

    public static void requireBlockRange(String fieldName, int startBlock, int blockCount, int totalBlocks) {
        requireBlockIndex(fieldName + " startBlock", startBlock, totalBlocks);
        requirePositive(fieldName + " blockCount", blockCount);

        long endExclusive = (long) startBlock + blockCount;
        if (endExclusive > totalBlocks) {
            throw new IllegalArgumentException(fieldName + " block range exceeds total block count");
        }
    }

    public static void requireFixedUtf8Length(String fieldName, String value, int maxBytes) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        requirePositive("maxBytes", maxBytes);

        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > maxBytes) {
            throw new IllegalArgumentException(fieldName + " exceeds fixed field size of " + maxBytes + " bytes");
        }
    }
}
