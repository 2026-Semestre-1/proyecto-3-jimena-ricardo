package pso.filesystem.shell;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;
import pso.filesystem.BootBlock;
import pso.filesystem.BinaryFormatValidator;
import pso.filesystem.DirectoryEntry;
import pso.filesystem.FileSystem;
import pso.filesystem.FreeSpaceBitmap;
import pso.filesystem.GroupRecord;
import pso.filesystem.IndexBlock;
import pso.filesystem.Inode;
import pso.filesystem.InodeType;
import pso.filesystem.PathResolver;
import pso.filesystem.ResolvedPath;
import pso.filesystem.SuperBlock;
import pso.filesystem.UserRecord;
import pso.filesystem.VirtualDisk;

public class Shell {

    private Session session;
    private SessionManager sessionManager;

    private static final int SUPER_BLOCK_INDEX = 1;
    private static final int BITMAP_START_BLOCK = 2;
    private static final int INODE_TABLE_BLOCK_COUNT = 8;
    private static final int USER_TABLE_BLOCK_COUNT = 16;
    private static final int GROUP_TABLE_BLOCK_COUNT = 16;
    private static final int ROOT_INODE_ID = 1;
    private static final int ROOT_HOME_INODE_ID = 2;
    private static final int HOME_INODE_ID = 3;
    private static final int NEXT_INODE_ID = 4;
    private static final int ROOT_USER_ID = 0;
    private static final int ROOT_GROUP_ID = 0;
    private static final int DEFAULT_DIRECTORY_PERMISSIONS = 0x75;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public void start() {
        Scanner sc = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.print(prompt());
            String command = sc.nextLine();
            ParsedCommand parsedCommand = parse(command);
            running = dispatch(parsedCommand);
        }
    }

    private String prompt() {
        String user = session == null ? "[no user]" : currentUserName();
        String disk = session == null ? "[no disk]" : diskNameWithoutExtension(session.fileSystem().diskName());
        return user + "@" + disk + "> ";
    }

    private String currentUserName() {
        if (session.currentUserId() == 0) {
            return "root";
        }

        return "user" + session.currentUserId();
    }

    private String diskNameWithoutExtension(String diskName) {
        if (diskName.endsWith(".fs")) {
            return diskName.substring(0, diskName.length() - 3);
        }

        return diskName;
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
        String[] operands = lastOptionIndex == -1 ? Arrays.copyOfRange(parts, 1, parts.length)
                : Arrays.copyOfRange(parts, lastOptionIndex + 1, parts.length);
        return new ParsedCommand(name, options, operands);
    }

    private boolean dispatch(ParsedCommand parsedCommand) {
        switch (parsedCommand.name()) {
            case "":
                return true;
            case "format":
                format(parsedCommand);
                return true;
            case "exit":
                System.out.println("bye");
                return false;
            case "useradd":
                break;
            case "groupadd":
                break;
            case "passwd":
                break;
            case "su":
                break;
            case "whoami":
                whoami(parsedCommand);
                break;
            case "pwd":
                pwd(parsedCommand);
                break;
            case "mkdir":
                break;
            case "rm":
                break;
            case "mv":
                break;
            case "ls":
                ls(parsedCommand);
                break;
            case "clear":
                break;
            case "cd":
                cd(parsedCommand);
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
                viewFCB(parsedCommand);
                break;
            case "infoFS":
                infoFS(parsedCommand);
                break;
            case "hexdump":
                hexdump(parsedCommand);
                break;
            default:
                System.out.println("Unknown command: " + parsedCommand.name());
                return true;
        }

        return true;
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
        int bitmapBytesNeeded = BinaryFormatValidator.ceilDiv(totalBlocks, 8);
        int bitmapBlockCount = BinaryFormatValidator.ceilDiv(bitmapBytesNeeded, blockSize);
        int inodeTableStartBlock = BITMAP_START_BLOCK + bitmapBlockCount;
        int userTableStartBlock = inodeTableStartBlock + INODE_TABLE_BLOCK_COUNT;
        int groupTableStartBlock = userTableStartBlock + USER_TABLE_BLOCK_COUNT;
        int dataRegionStartBlock = groupTableStartBlock + GROUP_TABLE_BLOCK_COUNT;
        int rootIndexBlock = dataRegionStartBlock;
        int rootDirectoryDataBlock = rootIndexBlock + 1;
        int rootHomeIndexBlock = rootDirectoryDataBlock + 1;
        int rootHomeDirectoryDataBlock = rootHomeIndexBlock + 1;
        int homeIndexBlock = rootHomeDirectoryDataBlock + 1;
        int homeDirectoryDataBlock = homeIndexBlock + 1;

        if (totalBlocks <= homeDirectoryDataBlock) {
            System.out.println("disk is too small for the initial filesystem layout");
            return;
        }

        FreeSpaceBitmap bitmap = new FreeSpaceBitmap(totalBlocks);
        for (int block = 0; block <= homeDirectoryDataBlock; block++) {
            bitmap.markUsed(block);
        }

        int usedBlocks = bitmap.usedCount();
        long now = System.currentTimeMillis();
        BootBlock bootBlock = new BootBlock(
                1,
                diskSizeBytes,
                blockSize,
                totalBlocks,
                SUPER_BLOCK_INDEX,
                now,
                diskName);
        SuperBlock superBlock = new SuperBlock(
                totalBlocks,
                blockSize,
                usedBlocks,
                totalBlocks - usedBlocks,
                ROOT_INODE_ID,
                NEXT_INODE_ID,
                BITMAP_START_BLOCK,
                bitmapBlockCount,
                inodeTableStartBlock,
                INODE_TABLE_BLOCK_COUNT,
                userTableStartBlock,
                USER_TABLE_BLOCK_COUNT,
                groupTableStartBlock,
                GROUP_TABLE_BLOCK_COUNT,
                dataRegionStartBlock);

        try (VirtualDisk disk = VirtualDisk.createOrOverwrite(diskName, diskSizeBytes, blockSize)) {
            disk.writeBlock(0, bootBlock.toBytes());
            disk.writeBlock(SUPER_BLOCK_INDEX, superBlock.toBytes());

            byte[] bitmapBytes = bitmap.toBytes(blockSize * bitmapBlockCount);
            for (int block = 0; block < bitmapBlockCount; block++) {
                byte[] blockBytes = Arrays.copyOfRange(
                        bitmapBytes,
                        block * blockSize,
                        (block + 1) * blockSize);
                disk.writeBlock(BITMAP_START_BLOCK + block, blockBytes);
            }

            byte[] inodeTableBlock = new byte[blockSize];
            writeInodeToTableBlock(inodeTableBlock, 0, new Inode(
                    ROOT_INODE_ID,
                    InodeType.DIRECTORY,
                    DEFAULT_DIRECTORY_PERMISSIONS,
                    ROOT_USER_ID,
                    ROOT_GROUP_ID,
                    4L * DirectoryEntry.BINARY_SIZE,
                    rootIndexBlock,
                    4,
                    now,
                    now,
                    now));
            writeInodeToTableBlock(inodeTableBlock, 1, new Inode(
                    ROOT_HOME_INODE_ID,
                    InodeType.DIRECTORY,
                    DEFAULT_DIRECTORY_PERMISSIONS,
                    ROOT_USER_ID,
                    ROOT_GROUP_ID,
                    2L * DirectoryEntry.BINARY_SIZE,
                    rootHomeIndexBlock,
                    2,
                    now,
                    now,
                    now));
            writeInodeToTableBlock(inodeTableBlock, 2, new Inode(
                    HOME_INODE_ID,
                    InodeType.DIRECTORY,
                    DEFAULT_DIRECTORY_PERMISSIONS,
                    ROOT_USER_ID,
                    ROOT_GROUP_ID,
                    2L * DirectoryEntry.BINARY_SIZE,
                    homeIndexBlock,
                    2,
                    now,
                    now,
                    now));
            disk.writeBlock(inodeTableStartBlock, inodeTableBlock);
            for (int block = inodeTableStartBlock + 1; block < inodeTableStartBlock
                    + INODE_TABLE_BLOCK_COUNT; block++) {
                disk.writeBlock(block, new byte[blockSize]);
            }
            byte[] firstUserTableBlock = new byte[blockSize];
            writeUserRecordToTableBlock(firstUserTableBlock, 0, new UserRecord(
                    true,
                    ROOT_USER_ID,
                    "root",
                    "Full name of the root user",
                    ROOT_GROUP_ID,
                    ROOT_HOME_INODE_ID,
                    rootPassword));
            disk.writeBlock(userTableStartBlock, firstUserTableBlock);
            for (int block = userTableStartBlock + 1; block < userTableStartBlock + USER_TABLE_BLOCK_COUNT; block++) {
                disk.writeBlock(block, new byte[blockSize]);
            }

            byte[] firstGroupTableBlock = new byte[blockSize];
            writeGroupRecordToTableBlock(firstGroupTableBlock, 0, new GroupRecord(
                    true,
                    ROOT_GROUP_ID,
                    "root",
                    new int[] { ROOT_USER_ID }));
            disk.writeBlock(groupTableStartBlock, firstGroupTableBlock);
            for (int block = groupTableStartBlock + 1; block < groupTableStartBlock
                    + GROUP_TABLE_BLOCK_COUNT; block++) {
                disk.writeBlock(block, new byte[blockSize]);
            }

            disk.writeBlock(rootIndexBlock, new IndexBlock(List.of(rootDirectoryDataBlock)).toBytes(blockSize));
            disk.writeBlock(rootDirectoryDataBlock, directoryDataBlock(
                    blockSize,
                    new DirectoryEntry(ROOT_INODE_ID, InodeType.DIRECTORY, "."),
                    new DirectoryEntry(ROOT_INODE_ID, InodeType.DIRECTORY, ".."),
                    new DirectoryEntry(ROOT_HOME_INODE_ID, InodeType.DIRECTORY, "root"),
                    new DirectoryEntry(HOME_INODE_ID, InodeType.DIRECTORY, "home")));

            disk.writeBlock(rootHomeIndexBlock, new IndexBlock(List.of(rootHomeDirectoryDataBlock)).toBytes(blockSize));
            disk.writeBlock(rootHomeDirectoryDataBlock, directoryDataBlock(
                    blockSize,
                    new DirectoryEntry(ROOT_HOME_INODE_ID, InodeType.DIRECTORY, "."),
                    new DirectoryEntry(ROOT_INODE_ID, InodeType.DIRECTORY, "..")));

            disk.writeBlock(homeIndexBlock, new IndexBlock(List.of(homeDirectoryDataBlock)).toBytes(blockSize));
            disk.writeBlock(homeDirectoryDataBlock, directoryDataBlock(
                    blockSize,
                    new DirectoryEntry(HOME_INODE_ID, InodeType.DIRECTORY, "."),
                    new DirectoryEntry(ROOT_INODE_ID, InodeType.DIRECTORY, "..")));

            FileSystem fileSystem = FileSystem.mount(diskName);
            sessionManager = new SessionManager(fileSystem);
            session = sessionManager.createRootSession();
            System.out.println("formatted disk '" + diskName + "' with " + sizeMb + " MB");
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("format failed: " + ex.getMessage());
        }
    }

    private void writeInodeToTableBlock(byte[] inodeTableBlock, int inodeSlot, Inode inode) {
        byte[] inodeBytes = inode.toBytes();
        System.arraycopy(inodeBytes, 0, inodeTableBlock, inodeSlot * Inode.BINARY_SIZE, Inode.BINARY_SIZE);
    }

    private void writeUserRecordToTableBlock(byte[] userTableBlock, int userSlot, UserRecord userRecord) {
        byte[] userBytes = userRecord.toBytes();
        System.arraycopy(userBytes, 0, userTableBlock, userSlot * UserRecord.BINARY_SIZE, UserRecord.BINARY_SIZE);
    }

    private void writeGroupRecordToTableBlock(byte[] groupTableBlock, int groupSlot, GroupRecord groupRecord) {
        byte[] groupBytes = groupRecord.toBytes();
        System.arraycopy(groupBytes, 0, groupTableBlock, groupSlot * GroupRecord.BINARY_SIZE, GroupRecord.BINARY_SIZE);
    }

    private byte[] directoryDataBlock(int blockSize, DirectoryEntry... entries) {
        byte[] block = new byte[blockSize];
        int requiredBytes = entries.length * DirectoryEntry.BINARY_SIZE;
        if (requiredBytes > blockSize) {
            throw new IllegalArgumentException("directory entries do not fit in one block");
        }

        for (int i = 0; i < entries.length; i++) {
            byte[] entryBytes = entries[i].toBytes();
            System.arraycopy(entryBytes, 0, block, i * DirectoryEntry.BINARY_SIZE, DirectoryEntry.BINARY_SIZE);
        }

        return block;
    }

    private void hexdump(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 0 && operands.length != 2) {
            System.out.println("usage: hexdump [offset length]");
            return;
        }
        if (!hasCurrentDisk()) {
            return;
        }

        long startOffset = 0;
        long length = -1;
        if (operands.length == 2) {
            try {
                startOffset = Long.parseLong(operands[0]);
                length = Long.parseLong(operands[1]);
            } catch (NumberFormatException ex) {
                System.out.println("offset and length must be non-negative integers");
                return;
            }

            if (startOffset < 0 || length < 0) {
                System.out.println("offset and length must be non-negative integers");
                return;
            }
        }

        try (FileInputStream input = new FileInputStream(session.fileSystem().diskName())) {
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

    private void viewFCB(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: viewFCB <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String inputPath = operands[0];
        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath resolved = resolver.resolve(inputPath, session.currentDirectoryInodeId());
            String absolutePath = normalizePath(session.currentPath(), inputPath);
            String fileName = fileNameFromPath(absolutePath);
            printInode(fileName, absolutePath, resolved.inode());
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("viewFCB failed: " + ex.getMessage());
        }
    }

    private void printInode(String fileName, String absolutePath, Inode inode) throws IOException {
        UserRecord owner = session.fileSystem().readUserRecord(inode.ownerUserId());
        GroupRecord group = session.fileSystem().readGroupRecord(inode.groupId());

        System.out.println("Name: " + fileName);
        System.out.println("Path: " + absolutePath);
        System.out.println("Inode ID: " + inode.inodeId());
        System.out.println("Type: " + inode.type());
        System.out.println("Permissions: " + String.format("%02X", inode.permissions()));
        System.out.println("Owner: " + owner.username());
        System.out.println("Group: " + group.groupName());
        System.out.println("Size bytes: " + inode.sizeBytes());
        System.out.println("Index block ID: " + inode.indexBlockId());
        System.out.println("Link count: " + inode.linkCount());
        System.out.println("Created: " + formatMillis(inode.createdTimeMillis()));
        System.out.println("Modified: " + formatMillis(inode.modifiedTimeMillis()));
        System.out.println("Accessed: " + formatMillis(inode.accessedTimeMillis()));
    }

    private String fileNameFromPath(String path) {
        if (path.equals("/")) {
            return "/";
        }

        int lastSlash = path.lastIndexOf('/');
        return path.substring(lastSlash + 1);
    }

    private String formatMillis(long millis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

    private void ls(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length > 1) {
            System.out.println("usage: ls [path]");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String path = operands.length == 0 ? "." : operands[0];

        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath resolved = resolver.resolve(path, session.currentDirectoryInodeId());

            if (resolved.inode().type() != InodeType.DIRECTORY) {
                System.out.println("ls failed: " + path + " is not a directory");
                return;
            }

            List<DirectoryEntry> entries = session.fileSystem().readDirectoryEntries(resolved.inode());
            boolean printedAny = false;
            for (DirectoryEntry entry : entries) {
                if (entry.name().equals(".") || entry.name().equals("..")) {
                    continue;
                }

                System.out.println(formatDirectoryEntry(entry));
                printedAny = true;
            }

            if (!printedAny) {
                System.out.println("(empty)");
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("ls failed: " + ex.getMessage());
        }
    }

    private String formatDirectoryEntry(DirectoryEntry entry) {
        return switch (entry.type()) {
            case DIRECTORY -> entry.name() + "/";
            case FILE -> entry.name();
            case SYMLINK -> entry.name() + "@";
            case UNUSED -> entry.name();
        };
    }

    private void cd(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: cd <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String path = operands[0];

        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath resolved = resolver.resolve(path, session.currentDirectoryInodeId());

            if (resolved.inode().type() != InodeType.DIRECTORY) {
                System.out.println("cd failed: not a directory");
                return;
            }

            session.setCurrentDirectoryInodeId(resolved.inodeId());
            session.setCurrentPath(normalizePath(session.currentPath(), path));
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("cd failed: " + ex.getMessage());
        }
    }

    private String normalizePath(String currentPath, String inputPath) {
        Deque<String> stack = new ArrayDeque<>();
        String combined;

        if (inputPath.startsWith("/")) {
            combined = inputPath;
        } else if (currentPath.equals("/")) {
            combined = "/" + inputPath;
        } else {
            combined = currentPath + "/" + inputPath;
        }

        for (String part : combined.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(part);
            }
        }

        if (stack.isEmpty()) {
            return "/";
        }

        return "/" + String.join("/", stack);
    }

    private void pwd(ParsedCommand parsedCommand) {
        if (parsedCommand.operands().length != 0) {
            System.out.println("usage: pwd");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        System.out.println(session.currentPath());
    }

    private void whoami(ParsedCommand parsedCommand) {
        if (parsedCommand.operands().length != 0) {
            System.out.println("usage: whoami");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        try {
            UserRecord user = session.fileSystem().readUserRecord(session.currentUserId());
            System.out.println("Username: " + user.username());
            System.out.println("Full name: " + user.fullName());
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("whoami failed: " + ex.getMessage());
        }
    }

    private void infoFS(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 0) {
            System.out.println("usage: infoFS");
            return;
        }
        if (!hasCurrentDisk()) {
            return;
        }

        BootBlock bootBlock = session.fileSystem().bootBlock();
        SuperBlock superBlock = session.fileSystem().superBlock();
        System.out.println("File system name: " + bootBlock.volumeName() +
                "\nTotal space: " + bootBlock.diskSizeBytes() / 1024 / 1024 + " MB" +
                "\nUsed space: " + superBlock.usedBlocks() / 1024 + " MB" +
                "\nFree space: " + superBlock.freeBlocks() / 1024 + " MB");
    }

    private boolean hasCurrentDisk() {
        if (session == null) {
            System.out.println("no disk is currently selected");
            return false;
        }

        return true;
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
