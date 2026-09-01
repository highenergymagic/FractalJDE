# How it is put together

One process per program, images on a volume, a loader that follows the load commands,
and a table of what is running. This is the part that is a system rather than a program.

## What is real and what is pretend

Real: browsing, opening, renaming, duplicating, copy and paste, new folder, aliases
(symbolic links), Compress, Move to Trash, Empty Trash, Get Info, Quick Look, search,
the disk list, ejecting, the Trash contents, the default browser and mail lookups, the
processor and memory figures in About This Computer.

Also real, and worth saying twice: Sleep, Lock, Restart, Shut Down and Log Out act on
the machine, not on this program. The dialogs say so and offer Quit FractalJDE instead.

Pretend: Software Update has nothing to update. Burn to Disc, Customize Toolbar and
colour labels are not implemented and say so. Spotlight walks the file tree rather than
an index, so it is capped and skips deep folders.

## Programs are Mach-O

`Contents/Fractal/TextEdit` is a real 64 bit Mach-O executable.
[MachO.java](system/dyld/src/org/fractalmicro/macho/MachO.java) writes a header, load commands and segments at the
offsets the format specifies, rather than a file with the right first four bytes. This is
what is actually in the one that ships:

```
0            mach_header_64             MH_MAGIC_64, x86_64, MH_EXECUTE
             LC_SEGMENT_64 __PAGEZERO
             LC_SEGMENT_64 __TEXT       the header, the commands and the entry code
             LC_SEGMENT_64 __LINKEDIT   the symbol and string tables
             LC_SEGMENT_64 __FRACTAL    __bytecode: the program's code resources
             LC_LOAD_DYLINKER           /usr/lib/dyld
             LC_LOAD_DYLIB              @rpath/Foundation.framework/Versions/A/Foundation
             LC_LOAD_DYLIB              @rpath/AppKit.framework/Versions/A/AppKit
             LC_RPATH                   /System/Library/Frameworks
             LC_RPATH                   @loader_path/../Frameworks
             LC_UUID                    a digest of the contents, so builds repeat
             LC_MAIN                    where the entry code starts
             LC_SYMTAB                  what it defines and what it expects
             LC_DYSYMTAB                which of those are which
aligned      the entry code             twelve bytes of x86_64
             the symbol table           nlist_64 entries, sixteen bytes each
page aligned the code resources
```

`__LINKEDIT` is before `__FRACTAL` rather than after it, which is the one place this
departs from the layout Apple uses. The code resources are an archive appended to the
file, and an archive is read from its end; a segment after it would put bytes between the
end of the archive and the end of the file, and nothing would open it.

The entry code is twelve real bytes, `mov rax, 0x2000001; xor rdi, rdi; syscall`, which
is exit(0). That is all the machine code there is, and the file says so rather than
pretending otherwise: the program itself is the code resources in `__FRACTAL`.

The code resources are a zip, appended whole at a page boundary, so one file answers to
two readers at once: Mach-O from the front, an archive from the back. Both are checked
on every run of the self test.

What is in that zip is the program: a manifest naming the class to run, the bundle's own
`Info.plist`, and the class files the program calls its own: its entry class, and every
package it declares. TextEdit's executable carries twenty classes; Calculator's carries
three. What it does **not** carry is anything shared, which comes from the framework it
links, so a program is a program and not a copy of the system with a different icon.

That distinction was got wrong first time round, and quietly: the resources held the
manifest and the plist and no classes at all, and every program ran on the framework's
copy of its own code. Everything worked, which is why it survived, until the framework
was one build behind, and then a program could not find its own main class. The self test
now reads the executable and counts what is in it, because "it starts" was exactly the
evidence that missed this.

[Dyld.java](system/LaunchServices/src/org/fractalmicro/bundle/Dyld.java) is what runs it. It opens the executable, reads
`__FRACTAL,__bytecode`, unpacks it into
`~/.fractaldt/private/var/folders/<digest>/`, resolves the `LC_LOAD_DYLIB` paths to the
frameworks installed on this machine, and starts the entry class the code resources name.
Unpacking is keyed on a digest of the resources, so a program that has not changed starts
from what is already there, and a program that has cannot run from a stale copy.

## What a process is here

Three ways to answer that, and only one of them is right.

**Threads as processes** is Classic Mac OS: cheap, and one bad program takes the system
with it. Java cannot stop a thread from outside, cannot reclaim what it held, and cannot
contain its crash. Historically apt, and wrong.

**Processes of the host system only** is real isolation at about fifty megabytes and a
third of a second each. The metadata server on this machine holds 115 MB. Six programs
that way and the desktop costs more than the machine it imitates ever had.

**A numbering of its own over both** is what this does, and what the messages already
allowed: a caller asks a name, and never knew which side answered.

### The task table

`org.fractalmicro.kernel.Tasks` hands out numbers in order, wraps at 99999, and skips any
still in use. A number is freed when the task that had it is reaped, not when it ends: a
task that has ended holds its number and its exit status until whatever started it comes
and asks, or the answer would be a race. Two numbers are spoken for before anything asks:

```
0   kernel_task   the system itself
1   launchd       what starts everything else
```

Both are needed for the same reason the real ones are: something has to have been running
before the numbering started, and something has to parent everything that has no other
parent. When a task ends while it still has children, those children are handed to task 1,
because a task whose parent is gone would otherwise sit as a zombie forever holding a
number nothing could reclaim.

The table is not one process's. Task 1 holds it and serves it as
`org.fractalmicro.kernel`: every process asks it for a number and tells it what it started,
and a listing is the answer from there. A process that allocated numbers from a counter of
its own would hand out numbers another process had already used, and two tasks with the
same number is not a namespace. What the table will believe is bounded: it accepts a row
only for a number it handed out, and only for one it is not already holding itself.

A task is a process of the host system, a thread here, or something adopted: already
running when the system found it. Which it is stays the task's own business; everything
else uses the number, the name, and the services it serves.

| | |
|---|---|
| in its own process | watched by its ending, stopped by ending it |
| in this process | asked to stop, because a thread cannot be taken away |
| adopted | found by name, its host process asked for, stopped by number |

`--tasks` prints the table, and Activity Monitor shows it with the machine's own process
list one menu item away:

```
PID   Host   Kind        State     Memory   Name
0     self   system      running   11.4 MB  kernel_task
1     self   system      running   11.4 MB  launchd
2     53016  daemon      running   115 MB   metadata
```

Both numberings are shown wherever both exist. The host process is real and hiding it
would only make the table harder to trust.

## Processes

Until now everything ran in one process, which meant this was a program that looked like a
desktop. A desktop has parts that are not the desktop: things that start on their own, keep
running, and answer questions from whatever asks.

### Ports

[Pipes.java](system/LibSystem/src/org/fractalmicro/win/Pipes.java) gives a service a name that anything can look up
and send to. Windows calls it a named pipe; everything above treats it as a port with a
name. Nothing that sends a message needs to know whether the answer comes from this
process, another one, or nothing at all.

A message is a property list ([Message.java](system/Foundation/src/org/fractalmicro/xpc/Message.java)), the same
format as the settings and the bundles: one reader, one writer, one set of types.

```
Service service = new Service("org.fractalmicro.echo", m -> Message.of("echo"));
Message reply = Connection.ask("org.fractalmicro.echo", Message.of("echo").put("say", "hello"));
```

### A port anyone can reach is not a port anyone may use

"Anything can look it up" was true in both senses for a while, and the second one was a
hole. A named pipe with no security descriptor takes clients from anywhere, and a pipe name
nobody has claimed can be stood up by whoever gets there first, so another program on the
machine could have connected to the window server and drawn a window, asked the index for
the paths of everything in someone's Documents, or squatted a service's name and been
connected to in its place.

Three changes close that, all in [Pipes.java](system/LibSystem/src/org/fractalmicro/win/Pipes.java) and
[Security.java](system/LibSystem/src/org/fractalmicro/win/Security.java):

- **The pipe is fenced to the account that made it.** [Security.java](system/LibSystem/src/org/fractalmicro/win/Security.java)
  builds a security descriptor in SDDL, the string form Windows documents, granting the
  current user and the system account and naming no one else, and every port is made with
  it. A process running as someone else cannot connect.
- **Remote clients are refused** (`PIPE_REJECT_REMOTE_CLIENTS`). These ports are for
  programs on this machine; nothing off it has any business on them.
- **The first instance of a name asks to be the first** (`FILE_FLAG_FIRST_PIPE_INSTANCE`).
  If that fails, the name was already standing when the service started, which is not a
  race that clears but a sign something else holds it, so the service refuses to serve it
  rather than joining as a second instance and splitting the traffic.

### A message off the wire is not to be trusted

The property-list readers were written for files this system wrote, and then put on a port
that takes bytes from anyone. That is a different job. A message can be built to run the
binary-plist reader out of memory (a count of two billion objects in forty bytes) or out
of stack, with an array whose one element is itself. Neither is caught by
`catch (Exception)`, because both arrive as `Error`.

[BinaryPlist.java](system/Foundation/src/org/fractalmicro/plist/BinaryPlist.java) now checks every count and length
against the size of the data before it acts on it, and remembers the objects it is reading
so a reference that leads back to one is refused rather than followed. The XML reader caps
how deep a list may nest. A single message is capped at eight megabytes. And the service
loop catches `Throwable`, not `Exception`, so a message built to throw an `Error` is
answered with a "no" like any other bad message instead of taking the thread, or the
runtime, with it. [HostileMessageTest](tests/src/org/fractalmicro/a11y/HostileMessageTest.java) sends a
live service each of these and checks it is still answering afterwards, because the reason
none of this showed up for so long is that every test until it sent only messages this
system had made.

### Jobs

[Launchd.java](system/launchd/src/org/fractalmicro/launchd/Launchd.java) reads job descriptions from
`System/Library/LaunchDaemons` and `Users/<user>/Library/LaunchAgents`, under the key names
a job description has always had: `Label`, `ProgramArguments`, `RunAtLoad`, `KeepAlive`,
`ThrottleInterval`, `MachServices`, `StandardOutPath`, `WorkingDirectory`, `Disabled`.

It starts jobs as processes of the host system and watches them. A job that says
`KeepAlive` is started again when it stops, never faster than its `ThrottleInterval`.
That is the difference between supervision and a fork bomb.

The name is what matters, not the process: a job whose `MachServices` name is already being
served is not started again, because a second copy could not claim the name anyway.

A job may write `${ROOT}`, `${LOGS}` and `${JAVA}` where a path would go, and launchd fills
them in as it reads the description. Mac OS X needs nothing like this: its volume is `/` and
its runtime is at a known path. Here a job ships inside a system image, written on one
machine and unpacked on another — under a different home directory, for an account with a
different name, against a runtime installed somewhere else entirely. Everything else in a
job is the same everywhere. These are not, and the metadata job spelled all three of them
out for a while, which meant it named a directory on the build machine and a JDK nobody
else had, and so failed on every start and was started again ten seconds later, forever.

A session takes its jobs with it when it ends. `--launchctl start` does the opposite: it
hands the job over and leaves it running.

```bash
java -jar build/FractalJDE.jar --launchctl list
```

### The metadata server

[Server.java](system/Metadata/src/org/fractalmicro/mds/Server.java) is the first thing here that is a program rather
than part of the desktop. It walks the folders worth knowing about, keeps what it found in
`private/var/db/Spotlight-V100`, and answers questions over its port. On this machine it
holds 24,698 items, and the difference is the point:

| | |
|---|---|
| asking the server | 24 ms |
| walking the disk for the same answer | 964 ms |

Spotlight asks the server when it is running and walks the disk when it is not, and says
which it did rather than quietly being slow. A search that works slowly beats a search that
fails because a daemon is down.

## How it talks to Windows

Nothing shells out. Everything the system is asked for goes through
`java.lang.foreign` calls into the DLLs, in `system/LibSystem/src/org/fractalmicro/win`:

| What | Call |
|---|---|
| Which drives exist, and their type | `GetLogicalDrives`, `GetDriveTypeW` |
| Volume names, file systems, capacity | `GetVolumeInformationW`, `GetDiskFreeSpaceExW` |
| Memory and processor cores | `GlobalMemoryStatusEx`, `GetLogicalProcessorInformationEx` |
| Default browser and mail client | `RegGetValueW` on the UserChoice keys |
| Trash size and count | `SHQueryRecycleBinW` |
| Empty Trash | `SHEmptyRecycleBinW` |
| Eject | `DeviceIoControl` with `IOCTL_STORAGE_EJECT_MEDIA` |
| Move to Trash | `java.awt.Desktop.moveToTrash`, which is the same shell operation |
| Force Quit | `ProcessHandle`, no external tools |

Two file formats are parsed rather than shelled out to:

- **Recycle Bin metadata.** The contents of the Trash come from the `$I` files Windows
  writes beside each deleted item: version, original size, a FILETIME, then the
  original path as UTF-16. Version 1 uses a fixed 520 byte path field, version 2 puts a
  character count in front of it. Both are read.
- **Shortcuts.** `.lnk` files are parsed for their target through the published shell
  link format, taking the LinkInfo local base path and falling back to the relative
  path in the string data.

`--native-report` prints what the native layer sees, which is the quickest way to check
it on a new machine.
