package pso.filesystem.shell;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;
import pso.filesystem.BootBlock;
import pso.filesystem.SuperBlock;
import pso.filesystem.VirtualDisk;

public class Shell {

    private static final int SUPER_BLOCK_INDEX = 1;
    private static final int BITMAP_START_BLOCK = 2;
    private static final int BITMAP_BLOCK_COUNT = 1;
    private static final int INODE_TABLE_START_BLOCK = 3;
    private static final int INODE_TABLE_BLOCK_COUNT = 8;
    private static final int USER_TABLE_BLOCK = 11;
    private static final int GROUP_TABLE_BLOCK = 12;
    private static final int DATA_REGION_START_BLOCK = 13;
    private static final int ROOT_INODE_ID = 1;
    private static final int NEXT_INODE_ID = 2;

    public void start() {
        Scanner sc = new Scanner(System.in);
        String command;
        do {
            System.out.print("ricardo@fs> ");
            command = sc.nextLine();
            ParsedCommand parsedCommand = parse(command);
            dispatch(parsedCommand);
        } while (!command.equals("exit"));
    }

    private ParsedCommand parse(String input) {
        String trimmed = input == null ? "" : input.trim();

        if (trimmed.isEmpty()) {
            return new ParsedCommand("", new String[0], new String[0]);
        }

        String[] parts = trimmed.split("\\s+");
        String name = parts[0];
        int lastOptionIndex = -1;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("-")) {
                lastOptionIndex = i;
            }
        }
        String[] options = lastOptionIndex == -1 ? new String[0] : Arrays.copyOfRange(parts, 1, lastOptionIndex + 1);
        String[] operands = lastOptionIndex == -1 ? Arrays.copyOfRange(parts, 1, parts.length) : Arrays.copyOfRange(parts, lastOptionIndex + 1, parts.length);
        return new ParsedCommand(name, options, operands);
    }

    private void dispatch(ParsedCommand parsedCommand) {
        switch (parsedCommand.name()) {
            case "":
                break;
            case "format":
                format(parsedCommand);
                break;
            case "exit":
                System.out.println("bye");
                break;
            case "useradd":
                break;
            case "groupadd":
                break;
            case "passwd":
                break;
            case "su":
                break;
            case "whoami":
                break;
            case "pwd":
                break;
            case "mkdir":
                break;
            case "rm":
                break;
            case "mv":
                break;
            case "ls":
                break;
            case "clear":
                break;
            case "cd":
                break;
            case "whereis":
                break;
            case "ln":
                break;
            case "touch":
                break;
            case "cat":
                break;
            case "less":
                break;
            case "chown":
                break;
            case "chgrp":
                break;
            case "chmod":
                break;
            case "viewFilesOpen":
                break;
            case "viewFCB":
                break;
            case "infoFS":
                break;
            case "hexdump":
                hexdump(parsedCommand);
                break;
            default:
                System.out.println("Unknown command: " + parsedCommand.name());
        }
    }

    private void format(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 3) {
            System.out.println("usage: format <diskName> <sizeMb> <rootPassword>");
            return;
        }

        String diskName = operands[0];
        String sizeText = operands[1];
        String rootPassword = operands[2];

        if (!isSimpleFileName(diskName)) {
            System.out.println("disk name must be a simple filename, not a path");
            return;
        }
        if (rootPassword.isBlank()) {
            System.out.println("root password cannot be blank");
            return;
        }

        int sizeMb;
        try {
            sizeMb = Integer.parseInt(sizeText);
        } catch (NumberFormatException ex) {
            System.out.println("disk size must be a positive integer in MB");
            return;
        }

        if (sizeMb <= 0) {
            System.out.println("disk size must be a positive integer in MB");
            return;
        }

        long diskSizeBytes = (long) sizeMb * 1024 * 1024;
        int blockSize = 1024;
        int totalBlocks = Math.toIntExact(diskSizeBytes / blockSize);

        if (totalBlocks <= DATA_REGION_START_BLOCK) {
            System.out.println("disk is too small for the initial filesystem layout");
            return;
        }

        int usedBlocks = 2;
        BootBlock bootBlock = new BootBlock(
                1,
                diskSizeBytes,
                blockSize,
                totalBlocks,
                SUPER_BLOCK_INDEX,
                System.currentTimeMillis(),
                diskName
        );
        SuperBlock superBlock = new SuperBlock(
                totalBlocks,
                blockSize,
                usedBlocks,
                totalBlocks - usedBlocks,
                ROOT_INODE_ID,
                NEXT_INODE_ID,
                BITMAP_START_BLOCK,
                BITMAP_BLOCK_COUNT,
                INODE_TABLE_START_BLOCK,
                INODE_TABLE_BLOCK_COUNT,
                USER_TABLE_BLOCK,
                GROUP_TABLE_BLOCK,
                DATA_REGION_START_BLOCK
        );

        try (VirtualDisk disk = VirtualDisk.createOrOverwrite(diskName, diskSizeBytes, blockSize)) {
            disk.writeBlock(0, bootBlock.toBytes());
            disk.writeBlock(SUPER_BLOCK_INDEX, superBlock.toBytes());
            System.out.println("formatted disk '" + diskName + "' with " + sizeMb + " MB");
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("format failed: " + ex.getMessage());
        }
    }

    private void hexdump(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1 && operands.length != 3) {
            System.out.println("usage: hexdump <diskName> [offset length]");
            return;
        }

        String diskName = operands[0];
        if (!isSimpleFileName(diskName)) {
            System.out.println("disk name must be a simple filename, not a path");
            return;
        }

        long startOffset = 0;
        long length = -1;
        if (operands.length == 3) {
            try {
                startOffset = Long.parseLong(operands[1]);
                length = Long.parseLong(operands[2]);
            } catch (NumberFormatException ex) {
                System.out.println("offset and length must be non-negative integers");
                return;
            }

            if (startOffset < 0 || length < 0) {
                System.out.println("offset and length must be non-negative integers");
                return;
            }
        }

        try (FileInputStream input = new FileInputStream(diskName)) {
            long skipped = skipFully(input, startOffset);
            if (skipped < startOffset) {
                System.out.println("offset is beyond end of file");
                return;
            }

            byte[] row = new byte[16];
            long currentOffset = startOffset;
            long remaining = length;

            System.out.println("ROW      HEX BYTES                                            ASCII");

            while (remaining != 0) {
                int maxBytesToRead = row.length;
                if (remaining > 0 && remaining < maxBytesToRead) {
                    maxBytesToRead = (int) remaining;
                }

                int bytesRead = input.read(row, 0, maxBytesToRead);
                if (bytesRead == -1) {
                    break;
                }

                printHexdumpRow(currentOffset, row, bytesRead);
                currentOffset += bytesRead;

                if (remaining > 0) {
                    remaining -= bytesRead;
                }
            }
        } catch (IOException ex) {
            System.out.println("hexdump failed: " + ex.getMessage());
        }
    }

    private long skipFully(InputStream input, long bytesToSkip) throws IOException {
        long totalSkipped = 0;
        while (totalSkipped < bytesToSkip) {
            long skipped = input.skip(bytesToSkip - totalSkipped);
            if (skipped <= 0) {
                if (input.read() == -1) {
                    break;
                }
                skipped = 1;
            }
            totalSkipped += skipped;
        }
        return totalSkipped;
    }

    private void printHexdumpRow(long offset, byte[] row, int bytesRead) {
        System.out.printf("%08X ", offset);

        for (int i = 0; i < 16; i++) {
            if (i < bytesRead) {
                System.out.printf("%02X ", row[i]);
            } else {
                System.out.print("   ");
            }
        }

        System.out.print("   ");

        for (int i = 0; i < bytesRead; i++) {
            int value = row[i] & 0xFF;
            if (value >= 32 && value <= 126) {
                System.out.print((char) value);
            } else {
                System.out.print(".");
            }
        }

        System.out.println();
    }

    private boolean isSimpleFileName(String fileName) {
        return fileName != null
                && !fileName.isBlank()
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.equals(".")
                && !fileName.equals("..");
    }
}
