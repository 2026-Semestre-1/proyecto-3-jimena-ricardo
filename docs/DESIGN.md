# File System Design Document

This document describes the architecture for the Java console application that simulates a minimal file system using a virtual disk file with a user-given name.

The design is based on the project requirements and on the file-system concepts from:

- *Operating System Concepts*, 10th ed., Chapters 13–14.
- *Operating Systems: Internals and Design Principles*, 9th ed., Chapter 12.

The main design direction is a UNIX-inspired, block-based virtual file system with explicit binary structures, FCB/inode metadata, indexed allocation, bitmap free-space management, directory entries, users/groups, permissions, and open-file tables.

---

## 1. Virtual Disk Representation

### Decision

The virtual disk will be represented as a sequence of fixed-size binary blocks.

Java object serialization is not used.

### Rationale

The books describe file systems as structures stored on secondary storage and organized around blocks, volumes, directories, file-control blocks, allocation metadata, and free-space structures. A fixed-size block model is closer to that design than storing Java objects directly.

This approach also makes the internal file-system structures demonstrable, which is important for commands such as:

- `infoFS`
- `viewFCB`
- `viewFilesOpen`

### Java Implementation Notes

Use `RandomAccessFile` to read and write specific blocks:

```java
RandomAccessFile disk = new RandomAccessFile("myDisk.fs", "rw");
long offset = (long) blockNumber * BLOCK_SIZE;
disk.seek(offset);
disk.write(blockBytes);
```

Structures should be manually encoded and decoded using fixed binary formats. Useful Java tools include:

- `RandomAccessFile`
- `ByteBuffer`
- `DataInputStream`
- `DataOutputStream`
- fixed-size byte arrays

Example:

```text
block size = 1024 bytes
disk size  = 10 MB
total blocks = 10240
```

---

## 2. Disk Layout

### Decision

Use an explicit logical disk layout:

```text
Block 0         BootBlock
Block 1         SuperBlock
Blocks 2..N     Free-space bitmap
Blocks N..M     FCB/Inode table
Blocks M..K     User/group metadata
Blocks K..end   Data blocks
```

The exact block ranges can be calculated during `format` depending on disk size and block size.

### Rationale

This follows the structures described in OSC and Stallings:

- boot control block / boot block
- volume control block / superblock
- FCB/inode table
- directory structures
- free-space management structure
- data blocks

---

## 3. BootBlock

### Decision

The first block of the disk is the `BootBlock`.

Suggested fields:

```text
magic number
filesystem version
disk size
block size
superblock location
creation timestamp
```

### Rationale

In the books, the boot block or boot control block identifies a bootable or mountable volume and can contain information needed to locate the file-system metadata.

For this project, the boot block is useful because it allows the program to:

- detect whether the virtual disk file is formatted;
- verify that the file belongs to this simulated file system;
- locate the superblock;
- validate the block size and disk size.

Example:

```text
magic = "MYFS"
version = 1
blockSize = 1024
superBlockStart = 1
```

---

## 4. SuperBlock

### Decision

The `SuperBlock` stores volume-level metadata.

Suggested fields:

```text
filesystem name
total blocks
free blocks
used blocks
root inode id
bitmap start block
inode table start block
data region start block
next available inode id
```

### Rationale

OSC describes the volume control block/superblock as the structure that contains details about the file system on a volume. Stallings also describes UNIX-like volume organization with a superblock and inode table.

The superblock is needed to:

- mount/open the virtual filesystem;
- locate the bitmap, inode table, and data region;
- know where the root directory is;
- support `infoFS`;
- support allocation and deallocation.

---

## 5. Free-Space Management

### Decision

Use a free-space bitmap.

Each bit represents one block:

```text
0 = free
1 = used
```

### Rationale

OSC and Stallings describe several free-space tracking techniques, including:

- bitmaps;
- linked free lists;
- grouping;
- counting;
- free block lists.

A bitmap is selected because it is simple, compact, and easy to show during evaluation. It also directly supports commands that display used and free space.

### Java Implementation Notes

The implementation can use Java's `BitSet` in memory, but it must be written to disk as raw bytes, not serialized.

Example flow:

```text
load bitmap bytes from disk
convert bytes to BitSet or boolean array
allocate/free blocks in memory
write bitmap bytes back to disk
```

---

## 6. FCB/Inode Model

### Decision

Use a UNIX-inspired FCB/inode structure.

Suggested fields:

```text
inode id
type: FILE, DIRECTORY, SYMLINK
owner id or owner name
group id or group name
permissions
size
index block id
created timestamp
modified timestamp
accessed timestamp
link count
```

Note: The open count and/or open status should be stored in a separate in-memory table, not in the inode.

### Rationale

OSC describes the FCB as the central metadata structure for files. Stallings describes UNIX inodes as storing type, permissions, owner, timestamps, size, block pointers, and link count.

This structure supports:

- `viewFCB`;
- ownership commands;
- permission commands;
- hard links;
- symbolic links;
- indexed block allocation.

### Filename Decision

The FCB/inode does **not** store the filename.

Filenames are stored only in directory entries.

Reason:

```text
/home/root/a.txt -> inode 15
/tmp/copy.txt    -> inode 15
```

Both names can point to the same inode if they are hard links. If the inode stored only one filename, it would be unclear which name is the real name. Renaming one directory entry would also incorrectly affect the other name.

Therefore:

```text
Directory entry = filename + inode id
FCB/Inode       = metadata + block references
```

---

## 7. Directory Structure

### Decision

Use a tree-structured directory model.

A directory is represented as an inode of type `DIRECTORY`, whose data blocks contain directory entries.

Each directory entry stores:

```text
filename
inode id
```

Each directory should include:

```text
.  -> current directory inode
.. -> parent directory inode
```

### Rationale

OSC describes tree-structured directories as the natural model for hierarchical file systems. Stallings emphasizes pathnames and working directories. This model supports absolute paths, relative paths, user home directories, and commands such as:

- `pwd`
- `cd`
- `mkdir`
- `ls`
- `rm`
- `mv`
- `whereis`

### Java Implementation Notes

Directory entries should be stored as fixed-size binary records inside directory data blocks.

Example record:

```text
inodeId     int
entryType   byte
name        fixed 64 bytes
```

Names shorter than the fixed size can be padded with zero bytes.

---

## 8. Path Resolution

### Decision

Use a centralized `PathResolver` component.

Responsibilities:

- resolve absolute paths from `/`;
- resolve relative paths from the session's current directory;
- handle `.`;
- handle `..`;
- handle symbolic links;
- validate permissions during traversal.

### Rationale

Path resolution is needed by many commands. Centralizing it avoids duplicated and inconsistent logic.

### Permission Note

For this project, directory traversal will require read permission, not execute permission. This is intentionally different from UNIX, where execute permission controls directory traversal.

---

## 9. File Allocation Strategy

### Decision

Use indexed allocation.

Each file inode stores an `indexBlockId`. The index block stores the block numbers of the file's data blocks.

Example:

```text
inode 20:
  indexBlockId = 100
  size = 2500 bytes

block 100:
  [201, 202, 203]

block 201: file bytes
block 202: file bytes
block 203: file bytes
```

### Rationale

OSC and Stallings describe contiguous, linked, and indexed allocation.

Indexed allocation is selected because:

- it supports file growth better than contiguous allocation;
- it avoids linked allocation's poor direct access behavior;
- it is easy to display in `viewFCB`;
- it resembles UNIX inode block-pointer designs.

### Java Implementation Notes

An index block can contain fixed-size integers:

```text
blockSize = 1024 bytes
int size  = 4 bytes
max block pointers per index block = 256
```

If more capacity is needed later, the design can add indirect index blocks, but the initial implementation should remain simple.

---

## 10. Open-File Tables

### Decision

Assume both a system-wide open-file table and per-session open-file tables.

System-wide table:

```text
inode id
open count
active modes
```

Per-session table:

```text
handle id
inode id
file pointer
open mode
```

### Rationale

OSC distinguishes between system-wide and per-process open-file tables. Stallings' Linux VFS discussion also describes open file objects associated with active use of files.

This design supports:

- `viewFilesOpen`;
- `cat`;
- `less`;
- `note`;
- multiple terminal/session environments.

### Implementation Note

The per-session table is easier for command handling because each session owns its current file handles and file pointers.

The global table is useful for visibility and open counts. A simple implementation is:

```text
on open:  add session handle; increment global open count
on close: remove session handle; decrement global open count
```

---

## 11. Session Model

### Decision

Each terminal/session stores:

```text
current user
current group
current directory inode id
per-session open file handles
```

Optional useful field:

```text
session id
```

### Rationale

The project requires more than one terminal/session environment. Each session needs its own working directory, user identity, group context, and open-file state.

---

## 12. Users and Groups

### Decision

User fields:

```text
username
full name
password hash
salt
primary group
home directory inode/path
```

Group fields:

```text
group name
members
```

The root user is identified by username:

```text
username == "root"
```

No separate `isRoot` field is required.

### Rationale

The project requires commands such as:

- `useradd`
- `groupadd`
- `passwd`
- `su`
- `whoami`

A primary group is the user's default group. When a user creates a file or directory, the new inode receives:

```text
owner = current user
group = current user's primary group
```

Root-by-username is simple, but the system must enforce that there is exactly one user named `root`.

---

## 13. Password Hashing

### Decision

Use SHA-256 for password hashing because it is easier to explain for the project.

Recommended format:

```text
hash = SHA-256(salt + password)
```

Store:

```text
username
salt
password hash
```

### Rationale

Plaintext passwords should not be stored. Salted SHA-256 is not as strong as password-specific algorithms such as PBKDF2, bcrypt, or Argon2, but it is simple to implement and explain using only standard Java APIs.

### Java Implementation Notes

Use:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
```

Use `SecureRandom` to generate the salt.

---

## 14. Permission Model

### Decision

Use two permission digits: owner and group.

Permission bits:

```text
4 = read
2 = write
1 = execute
```

Example:

```text
75
owner = 7 = read/write/execute
group = 5 = read/execute
```

### Project-Specific Interpretation

For this project:

```text
execute permission = visual/display characteristic only
```

Directory traversal uses read permission.

Directory operations:

```text
read  = list/traverse directory
write = create/delete/rename entries inside directory
```

Root bypasses permission checks.

### Rationale

OSC describes UNIX-style protection with owner, group, and others. The project only requires owner/group style permissions, so the model is intentionally simpler.

---

## 15. Links

### Decision

Assume support for both hard links and symbolic links.

### Hard Links

A hard link is another directory entry pointing to the same inode.

Example:

```text
/a/file.txt -> inode 30
/b/alias    -> inode 30
```

The inode stores:

```text
linkCount = 2
```

When one hard link is removed, decrement `linkCount`. Only when `linkCount == 0` should the inode and its data blocks be freed.

Recommended restriction:

```text
Do not allow hard links to directories.
```

Reason: hard links to directories can create cycles, making traversal, `rm -R`, and consistency checks much harder.

### Symbolic Links

A symbolic link is its own inode of type `SYMLINK`.

It stores a target path as its content.

Example:

```text
/link-to-file -> /a/file.txt
```

If the target is deleted, the symbolic link can become dangling.

Recommended behavior:

```text
cd follows symlinks to directories
cat/less/note follow symlinks to files
rm symlink removes the symlink itself, not the target
ls shows link -> target
```

### Rationale

OSC discusses links in directory structures. Stallings' UNIX discussion also supports the inode-based model where multiple directory entries may refer to the same inode.

---

## 16. Persistence Policy

### Decision

Save after every mutating command.

Examples of mutating commands:

```text
format
mkdir
rm
mv
touch
note when saved
chmod
chown
chgrp
useradd
groupadd
passwd
ln
```

Non-mutating commands do not need to write:

```text
ls
pwd
whoami
infoFS
viewFCB
viewFilesOpen
```

### Rationale

Saving after every mutating command is simpler and safer than saving only on exit. It reduces lost state if the program closes unexpectedly.

Logging and recovery mechanisms are discussed in OSC, but they are unnecessary complexity for this project.

---

## 17. Command Architecture

### Decision

Use the following structure:

```text
Shell
CommandDispatcher
Command interface
Concrete command handlers
FileSystem
UserManager
PermissionManager
PathResolver
BlockManager
OpenFileTable
```

Example flow:

```text
mkdir /docs
  -> Shell
  -> CommandDispatcher
  -> MkdirCommand
  -> FileSystem.createDirectory()
  -> PathResolver
  -> PermissionManager
  -> BlockManager
```

### Rationale

This keeps command parsing separate from filesystem logic. It also follows the layered spirit described by OSC:

- logical file system;
- file-organization module;
- basic file system;
- I/O control.

The project should avoid placing all behavior inside `main`.

---

## 18. `note` Text Editor Behavior

### Decision

The `note filename` command behaves as follows:

```text
resolve path
check permissions
open file
load content into editor buffer
edit buffer
ask whether to save on exit
if saved, write content and update blocks
close file
```

### Rationale

This demonstrates the standard file operations described by OSC:

- open;
- read;
- write;
- close;
- update metadata;
- manage open-file tables.

---

## 19. Error Handling

### Decision

Use internal domain errors and convert them to clean shell messages.

Examples:

```text
FileNotFoundFsException       -> file not found
PermissionDeniedException     -> permission denied
NoSpaceLeftException          -> no space left on device
NotDirectoryException         -> not a directory
DirectoryNotEmptyException    -> directory not empty
InvalidPathException          -> invalid path
```

### Rationale

Users should not see Java stack traces during normal shell usage. Domain-specific errors make command implementations cleaner and easier to reason about.

---

## 20. Root Behavior

### Decision

Root bypasses permission checks but cannot break essential filesystem invariants.

Important invariant:

```text
The mounted root directory / cannot be deleted.
```

### Rationale

Allowing root to bypass permissions is expected. However, deleting `/` breaks path resolution, home directories, current working directories, and the basic mounted filesystem model.

So the rule is:

```text
root can bypass permissions, but root cannot destroy the structural requirement that the filesystem has a root directory.
```

---

## 21. Initial Format Behavior

### Decision

The `format` command initializes:

```text
BootBlock
SuperBlock
free-space bitmap
inode table
root directory /
/user directory
/user/root directory
root user
root group
```

The root user's home directory is:

```text
/user/root
```

### Rationale

This satisfies the project requirement for a default root user, root password, root home directory, volume metadata, and boot/MBR-like structure.

---

## 22. Summary of Main Decisions

```text
Virtual disk:         fixed-size binary blocks
Persistence:          RandomAccessFile, no Java object serialization
Metadata:             BootBlock + SuperBlock + FCB/Inode table
Directories:          tree structure, directory entries map names to inode ids
Filename location:    directory entries only, not FCBs
Allocation:           indexed allocation
Free-space tracking:  bitmap
Open files:           global table + per-session tables
Users/groups:         root user, groups, primary group per user
Passwords:            salted SHA-256
Permissions:          owner/group bits, 4 read, 2 write, 1 execute
Directory traversal:  read permission
Links:                hard links and symbolic links
Root:                 bypasses permissions, cannot delete /
Saving:               after every mutating command
```
