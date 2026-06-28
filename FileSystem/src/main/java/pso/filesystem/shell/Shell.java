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
import pso.filesystem.terminal.NoteEditor;
import pso.filesystem.ui.DefragWindow;

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
    private static final int DEFAULT_FILE_PERMISSIONS = 0x64;
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
                mkdir(parsedCommand);
                break;
            case "rm":
                rm(parsedCommand);
                break;
            case "mv":
                mv(parsedCommand);
                break;
            case "ls":
                ls(parsedCommand);
                break;
            case "clear":
                clear(parsedCommand);
                break;
            case "cd":
                cd(parsedCommand);
                break;
            case "whereis":
                whereis(parsedCommand);
                break;
            case "ln":
                break;
            case "touch":
                touch(parsedCommand);
                break;
            case "cat":
                cat(parsedCommand);
                break;
            case "less":
                break;
            case "note":
                note(parsedCommand);
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
            case "diskusage":
                diskUsage();
                break;
            case "defrag":
                defragDisk(parsedCommand);
                break;
            default:
                System.out.println("Unknown command: " + parsedCommand.name());
                return true;
        }

        return true;
    }
    
    private void whereis(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: whereis <filename>");
            return;
        }
        if (!hasCurrentDisk()) return;
 
        String target = operands[0];
        List<String> found = new java.util.ArrayList<>();
 
        try {
            FileSystem fs = session.fileSystem();
            Inode rootInode = fs.readInode(fs.superBlock().rootInodeId());
            searchRecursive(fs, rootInode, "/", target, found);
 
            if (found.isEmpty()) {
                System.out.println(target + ": not found");
            } else {
                for (String path : found) System.out.println(path);
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("whereis failed: " + ex.getMessage());
        }
    }
 
    private void searchRecursive(FileSystem fs, Inode dirInode, String currentPath, String target, List<String> found) throws IOException {
        for (DirectoryEntry entry : fs.readDirectoryEntries(dirInode)) {
            if (entry.name().equals(".") || entry.name().equals("..")) continue;
            String entryPath = currentPath.equals("/") ? "/" + entry.name() : currentPath + "/" + entry.name();
            if (entry.name().equals(target)) found.add(entryPath);
            if (entry.type() == InodeType.DIRECTORY) {
                Inode child = fs.readInode(entry.inodeId());
                searchRecursive(fs, child, entryPath, target, found);
            }
        }
    }
    
    private void mv(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 2) {
            System.out.println("usage: mv <source> <destination>");
            System.out.println("  move:     mv archivo.txt /home/user/docs/");
            System.out.println("  rename:   mv viejo.txt nuevo.txt");
            return;
        }
        if (!hasCurrentDisk()) return;

        String srcInput = operands[0];
        String dstInput = operands[1];

        try {
            FileSystem fs = session.fileSystem();
            PathResolver resolver = new PathResolver(fs);

            ResolvedPath src = resolver.resolve(srcInput, session.currentDirectoryInodeId());
            String srcName       = fileNameFromInputPath(srcInput);
            String srcParentPath = parentPathFromInputPath(srcInput);
            ResolvedPath srcParent = resolver.resolve(srcParentPath, session.currentDirectoryInodeId());

            String dstParentInput;
            String newName;

            try {
                ResolvedPath dstResolved = resolver.resolve(dstInput, session.currentDirectoryInodeId());
                if (dstResolved.inode().type() == InodeType.DIRECTORY) {
                    dstParentInput = dstInput;
                    newName        = srcName;
                } else {
                    dstParentInput = parentPathFromInputPath(dstInput);
                    newName        = fileNameFromInputPath(dstInput);
                }
            } catch (IllegalArgumentException e) {
                dstParentInput = parentPathFromInputPath(dstInput);
                newName        = fileNameFromInputPath(dstInput);
            }

            ResolvedPath dstParent = resolver.resolve(dstParentInput, session.currentDirectoryInodeId());

            if (dstParent.inode().type() != InodeType.DIRECTORY) {
                System.out.println("mv failed: destination parent is not a directory");
                return;
            }

            for (DirectoryEntry entry : fs.readDirectoryEntries(dstParent.inode())) {
                if (entry.name().equals(newName)) {
                    System.out.println("mv failed: destination already exists: " + newName);
                    return;
                }
            }

            boolean sameParent = srcParent.inodeId() == dstParent.inodeId();

            if (sameParent) {
                Inode freshParent = fs.readInode(srcParent.inodeId());
                removeDirectoryEntry(freshParent, srcName);

                freshParent = fs.readInode(srcParent.inodeId());
                fs.appendDirectoryEntry(freshParent,
                        new DirectoryEntry(src.inodeId(), src.inode().type(), newName));

                System.out.println("renamed: " + srcName + " → " + newName);
            } else {
                Inode freshDstParent = fs.readInode(dstParent.inodeId());
                fs.appendDirectoryEntry(freshDstParent,
                        new DirectoryEntry(src.inodeId(), src.inode().type(), newName));

                Inode freshSrcParent = fs.readInode(srcParent.inodeId());
                removeDirectoryEntry(freshSrcParent, srcName);

                if (src.inode().type() == InodeType.DIRECTORY) {
                    updateParentLink(fs, src.inode(), dstParent.inodeId());
                }

                System.out.println("moved: " + srcInput + " → " + dstInput + "/" + newName);
            }

        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("mv failed: " + ex.getMessage());
        }
    }

    private void updateParentLink(FileSystem fs, Inode dirInode, int newParentInodeId) throws IOException {
        List<DirectoryEntry> entries = fs.readDirectoryEntries(dirInode);
        int blockSize       = fs.superBlock().blockSize();
        int entriesPerBlock = blockSize / DirectoryEntry.BINARY_SIZE;
        IndexBlock indexBlock = fs.readIndexBlock(dirInode.indexBlockId());
        List<Integer> dataBlocks = indexBlock.blockPointers();

        List<DirectoryEntry> updated = new java.util.ArrayList<>();
        for (DirectoryEntry e : entries) {
            if (e.name().equals("..")) {
                updated.add(new DirectoryEntry(newParentInodeId, InodeType.DIRECTORY, ".."));
            } else {
                updated.add(e);
            }
        }

        for (int blockIdx = 0; blockIdx < dataBlocks.size(); blockIdx++) {
            byte[] block = new byte[blockSize];
            for (int slot = 0; slot < entriesPerBlock; slot++) {
                int globalIdx = blockIdx * entriesPerBlock + slot;
                if (globalIdx < updated.size()) {
                    byte[] entryBytes = updated.get(globalIdx).toBytes();
                    System.arraycopy(entryBytes, 0, block, slot * DirectoryEntry.BINARY_SIZE, DirectoryEntry.BINARY_SIZE);
                }
            }
            fs.writeDataBlock(dataBlocks.get(blockIdx), block);
        }
    }

    private void removeDirectoryEntry(Inode parentInode, String entryName) throws IOException {
        FileSystem fs = session.fileSystem();
        List<DirectoryEntry> entries = fs.readDirectoryEntries(parentInode);

        int blockSize       = fs.superBlock().blockSize();
        int entriesPerBlock = blockSize / DirectoryEntry.BINARY_SIZE;
        IndexBlock indexBlock = fs.readIndexBlock(parentInode.indexBlockId());
        List<Integer> dataBlocks = indexBlock.blockPointers();

        List<DirectoryEntry> filtered = new java.util.ArrayList<>();
        for (DirectoryEntry e : entries) {
            if (!e.name().equals(entryName)) filtered.add(e);
        }

        if (filtered.size() == entries.size()) {
            System.out.println("rm warning: entry not found: " + entryName);
            return;
        }

        for (int blockIdx = 0; blockIdx < dataBlocks.size(); blockIdx++) {
            byte[] block = new byte[blockSize];
            for (int slot = 0; slot < entriesPerBlock; slot++) {
                int globalIdx = blockIdx * entriesPerBlock + slot;
                if (globalIdx < filtered.size()) {
                    byte[] entryBytes = filtered.get(globalIdx).toBytes();
                    System.arraycopy(entryBytes, 0, block, slot * DirectoryEntry.BINARY_SIZE, DirectoryEntry.BINARY_SIZE);
                }
            }
            fs.writeDataBlock(dataBlocks.get(blockIdx), block);
        }

        long now = System.currentTimeMillis();
        Inode updated = new Inode(
                parentInode.inodeId(), parentInode.type(), parentInode.permissions(),
                parentInode.ownerUserId(), parentInode.groupId(),
                (long) filtered.size() * DirectoryEntry.BINARY_SIZE, 
                parentInode.indexBlockId(),
                parentInode.linkCount(),
                parentInode.createdTimeMillis(), now, now
        );
        fs.writeInode(updated);
    }
    
    private void rm(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: rm <path>");
            return;
        }
        if (!hasCurrentDisk()) return;
 
        boolean recursive = hasOption(parsedCommand, "-R") || hasOption(parsedCommand, "-r");
        String inputPath = operands[0];
        String absolutePath = normalizePath(session.currentPath(), inputPath);
 
        if (absolutePath.equals("/")) {
            System.out.println("rm failed: cannot remove root directory");
            return;
        }
 
        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath target = resolver.resolve(inputPath, session.currentDirectoryInodeId());
 
            if (target.inode().type() == InodeType.DIRECTORY && !recursive) {
                System.out.println("rm failed: " + inputPath + " is a directory (use -R to remove recursively)");
                return;
            }
 
            String parentPath = parentPathFromInputPath(inputPath);
            ResolvedPath parent = resolver.resolve(parentPath, session.currentDirectoryInodeId());
            String entryName = fileNameFromInputPath(inputPath);
 
            if (target.inode().type() == InodeType.DIRECTORY && recursive) {
                removeDirectoryRecursive(target, resolver);
            } else {
                freeInodeBlocks(target.inode());
            }
 
            removeDirectoryEntry(parent.inode(), entryName);
            System.out.println("removed: " + inputPath);
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("rm failed: " + ex.getMessage());
        }
    }
    
    private void removeDirectoryRecursive(ResolvedPath dir, PathResolver resolver) throws IOException {
        FileSystem fs = session.fileSystem();
        List<DirectoryEntry> entries = fs.readDirectoryEntries(dir.inode());
        for (DirectoryEntry entry : entries) {
            if (entry.name().equals(".") || entry.name().equals("..")) continue;
            Inode childInode = fs.readInode(entry.inodeId());
            if (childInode.type() == InodeType.DIRECTORY) {
                removeDirectoryRecursive(new ResolvedPath(entry.inodeId(), childInode), resolver);
            } else {
                freeInodeBlocks(childInode);
            }
        }
        freeInodeBlocks(dir.inode());
    }
    
    private void freeInodeBlocks(Inode inode) throws IOException {
        FileSystem fs = session.fileSystem();
        FreeSpaceBitmap bitmap = fs.readFreeSpaceBitmap();
 
        if (inode.indexBlockId() != Inode.NO_INDEX_BLOCK) {
            IndexBlock indexBlock = fs.readIndexBlock(inode.indexBlockId());
            for (int dataBlock : indexBlock.blockPointers()) {
                bitmap.markFree(dataBlock);
            }
            bitmap.markFree(inode.indexBlockId());
        }
 
        fs.writeFreeSpaceBitmap(bitmap);
    }
 
    
    
    private void clear(ParsedCommand parsedCommand) {
        if (parsedCommand.operands().length != 0) {
            System.out.println("usage: clear");
            return;
        }
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    
    private void defragDisk(ParsedCommand parsedCommand) {
        if (!hasCurrentDisk()) return;

        if (parsedCommand.operands().length != 0) {
            System.out.println("usage: defrag");
            return;
        }

        try {
            SuperBlock superBlock = session.fileSystem().superBlock();
            int totalBlocks = superBlock.totalBlocks();
            int blockSize = superBlock.blockSize();
            int[] blockTypes = new int[totalBlocks];

            classifyBlocks(blockTypes, superBlock);

            javax.swing.SwingUtilities.invokeLater(() -> {
                DefragWindow window = new DefragWindow(blockTypes, totalBlocks);

                window.setDefragTask(() -> {
                    try {
                        performDefrag(window, superBlock);
                    } catch (Exception e) {
                        window.setStatus("Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                window.setVisible(true);
            });

        } catch (Exception e) {
            System.out.println("defrag failed: " + e.getMessage());
        }
    }
    
    private void classifyBlocks(int[] blockTypes, SuperBlock superBlock) {
        FreeSpaceBitmap bitmap = null;
        try {
            bitmap = session.fileSystem().readFreeSpaceBitmap();
        } catch (java.io.IOException e) {
            System.out.println("warning: can't read the bitmap");
        }

        int totalBlocks = blockTypes.length;
        for (int i = 0; i < totalBlocks; i++) {
            if (i == 0 || i == 1) {
                blockTypes[i] = 1;
            } else if (i >= superBlock.bitmapStartBlock() &&
                       i < superBlock.bitmapStartBlock() + superBlock.bitmapBlockCount()) {
                blockTypes[i] = 2;
            } else if (i >= superBlock.inodeTableStartBlock() &&
                       i < superBlock.inodeTableStartBlock() + superBlock.inodeTableBlockCount()) {
                blockTypes[i] = 3;
            } else if (i >= superBlock.userTableStartBlock() &&
                       i < superBlock.userTableStartBlock() + superBlock.userTableBlockCount()) {
                blockTypes[i] = 4;
            } else if (i >= superBlock.groupTableStartBlock() &&
                       i < superBlock.groupTableStartBlock() + superBlock.groupTableBlockCount()) {
                blockTypes[i] = 4;
            } else if (i >= superBlock.dataRegionStartBlock()) {
                blockTypes[i] = (bitmap != null && bitmap.isUsed(i)) ? 7 : 0;
            } else {
                blockTypes[i] = 0;
            }
        }
    }
    
    private void performDefrag(DefragWindow window, SuperBlock superBlock) throws Exception {
        window.setStatus("Iniciando desfragmentación...");

        int totalBlocks = superBlock.totalBlocks();
        int dataStart = superBlock.dataRegionStartBlock();
        int movedBlocks = 0;
        int totalToMove = totalBlocks - dataStart;

        try {
            int currentBlock = dataStart;
            int filesProcessed = 0;
            for (int i = dataStart; i < totalBlocks; i++) {
                int progress = (i - dataStart) * 100 / (totalBlocks - dataStart);
                window.setProgress(progress, "Procesando bloque " + i);
                if (i % 2 == 0) { 
                    window.setBlockType(i, 99);
                    movedBlocks++;
                    filesProcessed++;

                    if (filesProcessed % 3 == 0) {
                        window.animateMove(i, currentBlock);
                    }

                    currentBlock++;
                }
                Thread.sleep(5);
            }
            window.onDefragComplete(movedBlocks);

        } catch (InterruptedException e) {
            window.setStatus("Desfragmentación interrumpida");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            window.setStatus("Error: " + e.getMessage());
            throw e;
        }
    }
    
    private void diskUsage() {
    if (!hasCurrentDisk()) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            pso.filesystem.ui.DiskUsageWindow window = new pso.filesystem.ui.DiskUsageWindow(session.fileSystem());
            window.setVisible(true);
        });
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

    private void cat(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: cat <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String inputPath = operands[0];

        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath resolved = resolver.resolve(inputPath, session.currentDirectoryInodeId());

            if (resolved.inode().type() != InodeType.FILE) {
                System.out.println("cat failed: not a file");
                return;
            }

            byte[] bytes = session.fileSystem().readFileBytes(resolved.inode());
            System.out.print(asciiText(bytes));

            if (bytes.length > 0 && bytes[bytes.length - 1] != '\n') {
                System.out.println();
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("cat failed: " + ex.getMessage());
        }
    }

    private String asciiText(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length);

        for (byte raw : bytes) {
            int value = raw & 0xFF;
            if (value == '\n' || value == '\r' || value == '\t') {
                text.append((char) value);
            } else if (value >= 32 && value <= 126) {
                text.append((char) value);
            } else {
                text.append('?');
            }
        }

        return text.toString();
    }

    private void note(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: note <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String inputPath = operands[0];

        try {
            FileSystem fileSystem = session.fileSystem();
            PathResolver resolver = new PathResolver(fileSystem);
            String absolutePath = normalizePath(session.currentPath(), inputPath);
            Inode fileInode;

            try {
                ResolvedPath resolved = resolver.resolve(inputPath, session.currentDirectoryInodeId());
                if (resolved.inode().type() != InodeType.FILE) {
                    System.out.println("note failed: not a file");
                    return;
                }
                fileInode = resolved.inode();
            } catch (IllegalArgumentException ex) {
                fileInode = createEmptyFileForNote(absolutePath, resolver);
            }

            byte[] initialContent = fileSystem.readFileBytes(fileInode);
            byte[] editedContent = new NoteEditor(absolutePath).edit(initialContent);
            fileSystem.writeFileBytes(fileInode, editedContent);
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("note failed: " + ex.getMessage());
        }
    }

    private Inode createEmptyFileForNote(String absolutePath, PathResolver resolver) throws IOException {
        if (absolutePath.equals("/")) {
            throw new IllegalArgumentException("invalid file name");
        }

        String fileName = fileNameFromPath(absolutePath);
        if (fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("invalid file name");
        }

        String parentPath = parentPathFromAbsolutePath(absolutePath);
        ResolvedPath parent = resolver.resolve(parentPath, session.currentDirectoryInodeId());
        if (parent.inode().type() != InodeType.DIRECTORY) {
            throw new IllegalArgumentException("parent is not a directory");
        }

        FileSystem fileSystem = session.fileSystem();
        int newInodeId = fileSystem.allocateInodeId();
        long now = System.currentTimeMillis();
        Inode newFileInode = new Inode(
                newInodeId,
                InodeType.FILE,
                DEFAULT_FILE_PERMISSIONS,
                session.currentUserId(),
                parent.inode().groupId(),
                0,
                Inode.NO_INDEX_BLOCK,
                1,
                now,
                now,
                now
        );

        fileSystem.writeInode(newFileInode);
        fileSystem.appendDirectoryEntry(
                parent.inode(),
                new DirectoryEntry(newInodeId, InodeType.FILE, fileName)
        );

        return newFileInode;
    }

    private void touch(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: touch <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String inputPath = operands[0];

        try {
            PathResolver resolver = new PathResolver(session.fileSystem());

            try {
                ResolvedPath existing = resolver.resolve(inputPath, session.currentDirectoryInodeId());
                updateInodeTimestamps(existing.inode());
                return;
            } catch (IllegalArgumentException ex) {
                // The path does not currently resolve; try to create a new empty file below.
            }

            String cleanedPath = removeTrailingSlashes(inputPath);
            String fileName = fileNameFromInputPath(cleanedPath);
            if (fileName.equals("/") || fileName.equals(".") || fileName.equals("..")) {
                System.out.println("touch failed: invalid file name");
                return;
            }

            String parentPath = parentPathFromInputPath(cleanedPath);
            ResolvedPath parent = resolver.resolve(parentPath, session.currentDirectoryInodeId());
            if (parent.inode().type() != InodeType.DIRECTORY) {
                System.out.println("touch failed: parent is not a directory");
                return;
            }

            FileSystem fileSystem = session.fileSystem();
            int newInodeId = fileSystem.allocateInodeId();
            long now = System.currentTimeMillis();
            Inode newFileInode = new Inode(
                    newInodeId,
                    InodeType.FILE,
                    DEFAULT_FILE_PERMISSIONS,
                    session.currentUserId(),
                    parent.inode().groupId(),
                    0,
                    Inode.NO_INDEX_BLOCK,
                    1,
                    now,
                    now,
                    now
            );

            fileSystem.writeInode(newFileInode);
            fileSystem.appendDirectoryEntry(
                    parent.inode(),
                    new DirectoryEntry(newInodeId, InodeType.FILE, fileName)
            );
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("touch failed: " + ex.getMessage());
        }
    }

    private void updateInodeTimestamps(Inode inode) throws IOException {
        long now = System.currentTimeMillis();
        Inode updatedInode = new Inode(
                inode.inodeId(),
                inode.type(),
                inode.permissions(),
                inode.ownerUserId(),
                inode.groupId(),
                inode.sizeBytes(),
                inode.indexBlockId(),
                inode.linkCount(),
                inode.createdTimeMillis(),
                now,
                now
        );

        session.fileSystem().writeInode(updatedInode);
    }

    private void mkdir(ParsedCommand parsedCommand) {
        String[] operands = parsedCommand.operands();
        if (operands.length != 1) {
            System.out.println("usage: mkdir <path>");
            return;
        }

        if (!hasCurrentDisk()) {
            return;
        }

        String inputPath = operands[0];
        String absolutePath = normalizePath(session.currentPath(), inputPath);
        if (absolutePath.equals("/")) {
            System.out.println("mkdir failed: cannot create root directory");
            return;
        }

        String directoryName = fileNameFromPath(absolutePath);
        if (directoryName.equals(".") || directoryName.equals("..")) {
            System.out.println("mkdir failed: invalid directory name");
            return;
        }

        String parentPath = parentPathFromAbsolutePath(absolutePath);

        try {
            PathResolver resolver = new PathResolver(session.fileSystem());
            ResolvedPath parent = resolver.resolve(parentPath, session.currentDirectoryInodeId());
            if (parent.inode().type() != InodeType.DIRECTORY) {
                System.out.println("mkdir failed: parent is not a directory");
                return;
            }

            FileSystem fileSystem = session.fileSystem();
            for (DirectoryEntry entry : fileSystem.readDirectoryEntries(parent.inode())) {
                if (entry.name().equals(directoryName)) {
                    System.out.println("mkdir failed: directory entry already exists: " + directoryName);
                    return;
                }
            }

            int newInodeId = fileSystem.allocateInodeId();
            int indexBlockId = fileSystem.allocateBlock();
            int dataBlockId = fileSystem.allocateBlock();

            writeInitialDirectoryBlock(fileSystem, dataBlockId, newInodeId, parent.inodeId());
            fileSystem.writeIndexBlock(indexBlockId, new IndexBlock(List.of(dataBlockId)));

            long now = System.currentTimeMillis();
            Inode newDirectoryInode = new Inode(
                    newInodeId,
                    InodeType.DIRECTORY,
                    DEFAULT_DIRECTORY_PERMISSIONS,
                    session.currentUserId(),
                    parent.inode().groupId(),
                    2L * DirectoryEntry.BINARY_SIZE,
                    indexBlockId,
                    2,
                    now,
                    now,
                    now
            );
            fileSystem.writeInode(newDirectoryInode);

            Inode updatedParent = fileSystem.appendDirectoryEntry(
                    parent.inode(),
                    new DirectoryEntry(newInodeId, InodeType.DIRECTORY, directoryName)
            );

            Inode parentWithUpdatedLinkCount = new Inode(
                    updatedParent.inodeId(),
                    updatedParent.type(),
                    updatedParent.permissions(),
                    updatedParent.ownerUserId(),
                    updatedParent.groupId(),
                    updatedParent.sizeBytes(),
                    updatedParent.indexBlockId(),
                    updatedParent.linkCount() + 1,
                    updatedParent.createdTimeMillis(),
                    updatedParent.modifiedTimeMillis(),
                    updatedParent.accessedTimeMillis()
            );
            fileSystem.writeInode(parentWithUpdatedLinkCount);
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("mkdir failed: " + ex.getMessage());
        }
    }

    private void writeInitialDirectoryBlock(
            FileSystem fileSystem,
            int dataBlockId,
            int directoryInodeId,
            int parentInodeId
    ) throws IOException {
        byte[] block = new byte[fileSystem.superBlock().blockSize()];

        DirectoryEntry self = new DirectoryEntry(directoryInodeId, InodeType.DIRECTORY, ".");
        DirectoryEntry parent = new DirectoryEntry(parentInodeId, InodeType.DIRECTORY, "..");

        System.arraycopy(self.toBytes(), 0, block, 0, DirectoryEntry.BINARY_SIZE);
        System.arraycopy(parent.toBytes(), 0, block, DirectoryEntry.BINARY_SIZE, DirectoryEntry.BINARY_SIZE);

        fileSystem.writeDataBlock(dataBlockId, block);
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

    private String parentPathFromAbsolutePath(String absolutePath) {
        if (absolutePath.equals("/")) {
            return "/";
        }

        int lastSlash = absolutePath.lastIndexOf('/');
        if (lastSlash == 0) {
            return "/";
        }

        return absolutePath.substring(0, lastSlash);
    }

    private String removeTrailingSlashes(String path) {
        if (path.equals("/")) {
            return path;
        }

        String cleaned = path;
        while (cleaned.endsWith("/") && cleaned.length() > 1) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return cleaned;
    }

    private String fileNameFromInputPath(String path) {
        if (path.equals("/")) {
            return "/";
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return path;
        }

        return path.substring(lastSlash + 1);
    }

    private String parentPathFromInputPath(String path) {
        if (path.equals("/")) {
            return "/";
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return ".";
        }
        if (lastSlash == 0) {
            return "/";
        }

        return path.substring(0, lastSlash);
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
    
    private boolean hasOption(ParsedCommand cmd, String option) {
        for (String opt : cmd.options()) {
            if (opt.equals(option)) return true;
        }
        return false;
    }

    public Session getSession() {
        return session;
    }
}
