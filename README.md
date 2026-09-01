# FractalJDE

A desktop environment for Windows that looks and works like Mac OS X 10.6. The taskbar is
replaced by a menu bar and a Dock, and Explorer by the Finder. It runs alongside Explorer,
and it can take over from it.

![The desktop, with System Profiler open](docs/desktop.png)

It is written in Java, and it is built like the system it imitates rather than painted to
resemble it. Programs are Mach-O bundles installed on a volume of their own. A dynamic
linker maps them and binds their symbols. An init process starts everything else and keeps
the table of what is running. Each program runs in a process of its own and draws nothing
at all: it describes the window it wants, and the window server puts up real controls.

That last part matters most. What is on screen is a window and not a picture of a window,
so anything can move through it, describe it, or act on it, and a program falling over
takes nothing with it.

## Try it

You need a JDK 21 or newer. Then:

```bash
sh tools/build.sh
java -jar build/FractalJDE.jar
```

The jar starts a small launcher first, which restarts the virtual machine with the flags
the rest of it needs. Started any other way it used to half work, and absorbing that is
what a launcher is for.

`sh tools/release.sh` builds what other people get: a 14K kernel jar and `BaseSystem.dmg`,
a whole system volume in one file. Started, the kernel unpacks the image into `~/.fractaldt`
if there is nothing there yet, then reads the loader off that volume and boots it. Nothing
is compiled or assembled on the machine it is installed on, so what shipped is what runs.

## What comes with it

Finder, Dock and the menu bar are the desktop. On top of those: TextEdit, Terminal,
Calculator, Activity Monitor, System Profiler and System Preferences. Each is its own
process, started by LaunchServices out of its own bundle.

## Reading further

The rest is in `docs/`, roughly in the order it makes sense to read.

| | |
|---|---|
| [architecture.md](docs/architecture.md) | images, the loader, processes, the task table |
| [volume.md](docs/volume.md) | the volume, bundles, installing, icons, packaging |
| [windows.md](docs/windows.md) | the window server, descriptions, the menu bar, the desktop |
| [interfaces.md](docs/interfaces.md) | interface files, and the words in them |
| [panels.md](docs/panels.md) | the save and open panels, and the file browser |
| [programs.md](docs/programs.md) | TextEdit, Calculator, the rest |
| [finder.md](docs/finder.md) | aliases, labels, renaming, shortcuts |
| [text.md](docs/text.md) | the text system, spelling, services |
| [keyboard.md](docs/keyboard.md) | the keys, and what announces itself |
| [aqua.md](docs/aqua.md) | the look, and where it came from |
| [shell.md](docs/shell.md) | taking over from Explorer |

## Source

```
system/     the frameworks and the desktop, one directory per image
apps/       one directory per application, each compiled on its own
tests/      the checks, which are allowed to see everything
tools/      build and packaging
```

An application is compiled against the frameworks and never into them, enforced by the
build rather than by good intentions: `apps/Calculator` is given `build/system` on its
class path and nothing else, so it cannot reach into another application, and the
frameworks are compiled before any application exists and so cannot reach into one either.
Where a framework does need a program, it opens it by bundle identifier and talks to it
through `org.fractalmicro.appkit.FMApplicationDelegate`. Nothing in `system/` names a class
in `apps/`.

The images under `system/` are built in the order they depend on each other, so a library
cannot use something above it even by accident. Packages are `org.fractalmicro.*`
throughout, matching the bundle identifiers.

```
system/LibSystem       win     the Windows calls, the shortcut parser
                       core    logging, the shell, startup items
system/Foundation      foundation  FMString, FMArray, FMDictionary, FMNumber, FMDate,
                                   FMURL, FMData, FMError, FMTask, FMDecimal
                       kernel  the process table, and the table as a service
                       plist   XML and binary property lists
                       fs      files, volumes, Trash, applications, search
                       os      ~/.fractaldt, the preference domains, the system profile
                       xpc     messages, connections, services
                       icns    the icon file reader
                       alias   Finder aliases and Windows shortcuts
system/dyld            dyld    the loader: images, two level binding, runpaths
                       macho   reading and writing Mach-O, the linker, symbol tables
system/launchd         launchd jobs, and Init, which is task 1
system/LaunchServices  bundle  bundles, the images on the volume, opening a program
system/Metadata        mds     the search index and its server
system/AppKit          appkit      FMApplication, alerts, sheets, text, services
                       nib         interface descriptions
                       windowserver  the screen, the Dock, the menu bar, the windows
                       theme       colours, fonts, drawn icons, the Swing UI delegates
                       a11y        the dumps, the keyboard test, offscreen rendering
                       menuextras  the clock, volume, network and user indicators
system/Finder          ui, app the file manager
system/Fractal                 Main and Boot: the session, installed as loginwindow
```

## Checking

Everything below renders offscreen. No window appears, and nothing is sent to the
keyboard or the mouse.

| Flag | What it does |
|---|---|
| `--selftest` | Opens every window and view, then runs the keyboard and accessibility checks |
| `--dump-accessibility` | Prints the accessibility tree: role, name, states |
| `--native-report` | Prints what the native layer reads from Windows |
| `--screenshot FILE` | Renders the desktop to a PNG |
| `--open PATH` | Opens a Finder window on that folder at start-up |

Anything that goes wrong is written to `~/.fractaldt/Users/<user>/Library/Logs/Fractal.log`,
including how many icons the desktop ended up with and what the drive list returned.
Started from `javaw` there is nowhere else for a message to go.

The keyboard check reads focus traversal policies, input maps and action maps: that the
Dock, the window buttons and the toolbar are each a single tab stop, that the arrow
keys and Escape are bound inside them, that the desktop has its own bindings, and that
no two menu items claim the same shortcut. It checks the wiring, not the pressing;
whether Escape actually lands back on the previous control is the one part still worth
trying by hand.

## Version

FractalJDE follows semantic versioning. The number lives in `version.properties` and is
bumped per change: patch for a fix, minor for a feature, major for a break. The build
script can do it:

```bash
sh build.sh --bump
sh build.sh --bump minor
sh build.sh --bump major
```

The build number is the moment of the build, written as `yymmddhhmmss` and then in
hexadecimal, so it always rises and stays short: a build at 21:16:49 on 30 August 2026
is 260830211649, which is `3CBAB12E41`. About This Computer shows both, and
`Version.decodeBuild` turns one back into the time it was made. Running straight from
the class files rather than the jar, where nothing has been stamped, the build number is
worked out at start-up.

## Known gaps

- The painting is a lookalike, drawn from the published metrics and from screenshots.
  OpenJDK's own Aqua look and feel could be vendored instead, but it is GPL with the
  classpath exception, which would set the licence of this program; that is a decision
  to make deliberately rather than by accident.
- No drag and drop; use Copy and Paste.
- Desktop icon positions are not remembered, so icons always sit in a grid.
- The sidebar's Devices and Places switches are this program's own keys, not the
  structure the sidebar list format really uses.
- Windows live inside one full screen frame unless the window style setting says
  otherwise, so Windows' Alt Tab usually sees a single application.
- A program in another process can describe a window and menus, but not yet styled text,
  panels or sheets. Every application is built and shipped separately, but only Calculator
  runs in a process of its own; the rest are loaded into the desktop's process out of their
  own bundles. Each one moves out as the description protocol grows to carry what it draws.
- Burn folders, burning to disc and the clipboard viewer are named but do nothing.

## The licence

This is under the Common Development and Distribution License, Version 1.0. The full text
is in [LICENSE](LICENSE), and the header the licence asks for is at the top of every source
file. 213 of them, which the self test counts and checks on every run, because a file
without the header is a licensing problem rather than an untidy one.

CDDL is a file-level copyleft licence: those files stay under it, and modifications to them
are returned under it. It is worth being plain about one thing it does not do, since it is
a common hope. **CDDL cannot take in LGPL or GPL code.** Putting this header on someone
else's LGPL source would be relicensing it without permission, and combining the two in one
work is the incompatibility that keeps ZFS out of the Linux kernel. Code can be absorbed
from permissive licences (BSD, MIT, Apache) and not from copyleft ones that are not this
one.

One thing here is not under it. `resources/nvda/` holds the NVDA controller client, three
DLLs and its own LGPL 2.1 text, exactly as NVDA publishes them. They are not modified and
nothing here is derived from them: this program looks the library up at run time and calls
it, which is the use LGPL is written to allow. The licence travels with the files, and the
CDDL header is not applied to them.
