# The volume, bundles and installing

Where everything goes on disk, what an application is, and what happens when one is
installed.

## The system volume

There is a system volume at `~/.fractaldt`, laid out the way a Mac is:

```
~/.fractaldt/usr/lib/dyld                           the loader
~/.fractaldt/usr/lib/libSystem.B.dylib
~/.fractaldt/sbin/launchd                           the first process
~/.fractaldt/System/Library/Frameworks/             Foundation, AppKit, CoreServices
~/.fractaldt/System/Library/Applications/           the applications that ship with it
~/.fractaldt/System/Library/CoreServices/           Finder.app, loginwindow, CoreTypes.bundle
~/.fractaldt/System/Library/LaunchDaemons/
~/.fractaldt/Applications/         and Applications/Utilities/    a person's own
~/.fractaldt/Users/<user>/Library/Preferences/
~/.fractaldt/Volumes/
```

`CoreServices.framework` is an umbrella: LaunchServices and Metadata are whole frameworks
again inside its `Versions/A/Frameworks`, and the metadata server is at
`Metadata.framework/Versions/A/Support/mds`.

The whole volume is one directory so that it can be packed into one file, copied to another
machine and unpacked there. That file is what `tools/release.sh` produces, and unpacking it
is the whole of installing.

### Two Applications folders, one Applications folder

Applications that ship with the system live in `System/Library/Applications`, and an
install replaces them. Applications a person installs live in `Applications`, and an
install never touches those. The Finder shows the two as one folder, with a program in
`Applications` hiding one of the same name that shipped, which is what makes it possible
to replace one.

The volume used to be called `.fractalos`, from when this was a program that looked like a
desktop. A volume found under the old name is moved to the new one at start-up. On Windows
a directory cannot be renamed while something is running out of it, so the daemons are
asked to stop first, and if the move still cannot be made it is said plainly rather than
leaving an empty volume beside a full one.

Preferences are property lists under this system's own domain names, with the key names
Mac OS X uses, so a settings file written there can be read here as it stands.
`org.fractalmicro.finder.plist` holds `ShowHardDrivesOnDesktop`,
`ShowExternalHardDrivesOnDesktop`, `ShowRemovableMediaOnDesktop`,
`ShowMountedServersOnDesktop`, `NewWindowTarget` (`PfHm`, `PfDe`, `PfDo`, `PfCm`),
`FXPreferredViewStyle` (`icnv`, `Nlsv`, `clmv`, `Flwv`), `WarnOnEmptyTrash`,
`FXDefaultSearchScope`, `ShowPathbar`, `ShowStatusBar`, `SidebarWidth`, and the icon
size nested in `DesktopViewSettings` → `IconViewSettings` → `iconSize`.
`org.fractalmicro.dock.plist` holds `tilesize`, `magnification`, `largesize`,
`orientation` and `persistent-apps` as file tiles with a `file-label` and a
`_CFURLString`. `AppleShowAllExtensions` is in the global domain and `whiteOnBlack` in
`org.fractalmicro.universalaccess`. TextEdit keeps `RichText`, `WidthInChars` and
`HeightInChars` in `org.fractalmicro.textedit`.

Files are written as XML plists and read as either XML or binary, so a plist written
elsewhere in the same format can be dropped straight in.

The domains were renamed from `com.apple.*` and `com.fractalmicro.finder`; anything
written under the old names is moved, or folded in where both files exist, once at
start-up, so a rename costs nobody their settings.

## Applications are bundles

A program here is a folder that is treated as one thing, the way NeXTSTEP started and
Mac OS X continued:

```
TextEdit.app/
  Contents/
    Info.plist              identifier, name, what to run, which icon
    PkgInfo                 the eight bytes APPLFMI_ (FMI, the creator code here)
    Fractal/TextEdit        the program: a Mach-O executable
    Fractal/TextEdit.sh     the script the format calls for
    Fractal/TextEdit.cmd    the same thing Windows can actually run
    Resources/TextEdit.png  its icon
```

The executable folder is `Contents/Fractal`, not `Contents/MacOS`: there is no Mac in
this system, so there is none in its bundles either. Bundles written before the rename
are still read from the old folder.

## Installing

A release is two files, and installing is unpacking one of them:

```
FractalJDE.jar     the kernel: the launcher, the code that finds a volume, the image format
BaseSystem.dmg     a whole volume, at the paths it will sit at once installed
```

The kernel looks for `~/.fractaldt`. If it is not there, or holds a different build from
the image beside the jar, it unpacks the image onto it, then reads `/usr/lib/dyld` off that
volume and starts `/sbin/launchd` through it. Nothing is assembled on the machine it is
installed on: what shipped is what runs, and a release is replaced by replacing one file.

The image is a zip with a manifest at its root saying which build it is and what every file
in it should hash to. Each file is checked as it comes out, because an image is a file that
travelled and an unpacker that trusts whatever it is handed cannot tell a truncated download
from a working system. Names are checked too — an entry name is a stranger's string, and one
that says `../` often enough writes wherever it likes. The manifest is written onto the
volume last, so a volume claiming to hold a build is one that finished unpacking it.

The extension is Apple's; the format is not. A disk image is a filesystem in a file, and
there is no filesystem here for Windows to mount. What is actually wanted from one is that
a system arrives as a single file, that the file says what it is, and that a damaged one is
caught before any of it is believed.

Symbolic links are how a framework holds together, and the image carries them as files
naming their target. On unpacking, each becomes a real link where the filesystem allows one
and stays a file where it does not — Windows hands that privilege only to an administrator
or an account with developer mode on, and both forms are read the same way.

`java -jar build/FractalJDE.jar --install` is the other path: it lays out a volume from a
checkout, which is what the release build itself runs against an empty directory.

### Launchers name nothing

A bundle's `.cmd` and `.sh` hand the loader the executable beside them, which is what the
system itself does when it opens a program. Neither is allowed to name a location:

```
set ROOT=
for %%d in ("%~dp0.." "%~dp0..\.." ...) do if not defined ROOT if exist "%%~fd\usr\lib\dyld" set ROOT=%%~fd
if not defined ROOT ( echo Calculator.app is not on a FractalJDE volume. & exit /b 70 )
set JAVAW=javaw
if exist "%JAVA_HOME%\bin\javaw.exe" set JAVAW=%JAVA_HOME%\bin\javaw.exe
start "" "%JAVAW%" ... "-Dorg.fractalmicro.root=%ROOT%" -cp "%ROOT%\usr\lib\dyld" ^
      org.fractalmicro.dyld.Start "%~dp0Calculator"
```

Each walks up from where it is until it finds `usr/lib/dyld`, so a bundle runs against the
volume it is actually on. Only the runtime comes from the environment, since it is the one
thing not on the volume.

Three versions of this were wrong the same way. The first wrote out the full path to the JDK
that built it and the full path through one person's home folder. The second read the home
folder from the environment and worked out the rest, which held until a release was staged
somewhere that was not a home folder — at which point every launcher in the image pointed
into a directory on the build machine. Naming a location at all was the mistake. The shell
walk stops at the filesystem root; the batch one is bounded instead, because a batch file
climbing past the drive root goes on finding the drive root forever.

The self test refuses a launcher containing the home folder or the runtime path, and the
release build refuses a volume where anything names the machine it was built on.

`java -jar build/FractalJDE.jar --program-info <bundle>` prints what a program is: its
segments, the size of its code resources, what it links against and the class path the
loader would give it.

`Info.plist` carries the keys Apple's does: `CFBundleIdentifier`, `CFBundleName`,
`CFBundleExecutable`, `CFBundleIconFile`, `CFBundlePackageType`,
`CFBundleShortVersionString` and `LSMinimumSystemVersion`, plus `NSPrincipalClass`, which
names the class that runs when the bundle is opened. That is what NSPrincipalClass
means where the format comes from, and it means the same here. The four-byte creator
code is `FMI `, for Fractal Microsystems, so `PkgInfo` reads `APPLFMI `.

There is one more, `FMRunsInOwnProcess`, and it is this system's own. Cocoa has no such
key because on a Mac every application is a process; here most are hosted by the desktop
and a few are not, and the bundle is the honest place to say which. The day they have all
moved out it goes with them.

### Where a program starts

Not in the program. The loader maps the image and calls its entry point, and the entry
point named in an application's image is `org.fractalmicro.appkit.FMApplicationMain`,
which is in the framework it links rather than in the program at all.

That is what a Mac does. The entry point of a Cocoa application is `main`, and what every
application writes in it is one line handing over to `NSApplicationMain`, which reads
`NSPrincipalClass` out of the bundle, makes one, and runs the loop. Nobody writes anything
else there, because at that moment the program knows nothing its bundle has not said.

So the programs here have no `main`. Each is a class answering `open`, which is what a
delegate is. What went with the `main` was the same fifteen lines in every one of them:
check that there is a window server, make itself, show its window, register its handlers,
read events until told to stop, close. Two of those are the program's business and the
rest are the framework's.

It also collapsed two keys into one. There used to be a second class key naming a class
with a Java `main` in it, which existed only because the entry point was a Java `main`
rather than the thing Cocoa puts there. Two answers to one question, and the question was
already answered by `NSPrincipalClass`.

Six ship, installed on first run and identified under `org.fractalmicro`:

| Bundle | Identifier | Where |
|---|---|---|
| Finder | `org.fractalmicro.finder` | System/Library/CoreServices |
| System Preferences | `org.fractalmicro.systempreferences` | Applications |
| TextEdit | `org.fractalmicro.textedit` | Applications |
| System Profiler | `org.fractalmicro.systemprofiler` | Applications/Utilities |
| Activity Monitor | `org.fractalmicro.activitymonitor` | Applications/Utilities |
| Terminal | `org.fractalmicro.terminal` | Applications/Utilities |

Finder treats a bundle as an application rather than a folder: one icon, the name
without the `.app`, opened by double-clicking, with **Show Package Contents** to go
inside. The icon comes from the bundle's own Resources. Opening one from outside works
too, because the `.cmd` launcher starts this program with `--open-app <identifier>`.

## Icons

Icons are looked for in the CoreTypes and Dock resource folders above, under Apple's
own file names: `GenericFolderIcon.icns`, `GenericApplicationIcon.icns`,
`GenericHardDiskIcon.icns`, `TrashEmpty.icns`, `TrashFull.icns`, and so on. Both
`.icns` and `.png` are read; the ICNS reader handles the PNG element types and the
older run-length encoded 24 bit ones with their masks.

Nothing is shipped. Fetch a set yourself and drop it in; two starting points, both of
which you should check the licensing on:

- <https://archive.org/details/macosx10.6-iconfiles> — Apple's own artwork.
- <https://github.com/B00merang-Project/OS-X-Leopard> — redrawn, easier to reuse.

The company mark in the menu bar and in About This Computer comes from
`~/.fractaldt/System/Library/CoreServices/FractalLogo.png`, installed from the artwork
in `resources/` on first run. It is turned into a mask, where how dark a pixel was becomes
how opaque it is, so the one file draws dark on the grey menu bar and white on the blue
highlight. Replace that file to change the mark.

Until an icon set is installed the item icons are drawn in code by `org.fractalmicro.theme.Icons`.
Applications keep the icon Windows gives them, which can be turned off in
Finder → Preferences → Advanced.

## Releasing

```bash
sh tools/release.sh
```

This produces `build/FractalJDE-<version>.zip`, holding the kernel, the base image, a
launcher and a note. The volume inside the image is laid out by running the system's own
installer against an empty directory, so what ships is what installing produces rather than
an arrangement the release script believes in separately.

Two things are checked before an image is written. The volume has to have a loader and a
first process, since one without them is a system nothing can start. And nothing on it may
name the machine it was built on, in either spelling — the scripts on a volume are written
with backslashes and the build shell works in forward ones, and a check that knew only one
of them passed for a while over launchers that named the directory a release was staged in.

The build produces four jars, and they are not the same thing:

| | |
|---|---|
| `build/FractalJDE.jar` | framework, applications and checks together, for running from a checkout |
| `build/Fractal.jar` | the system alone, which is what lays out a volume |
| `build/Kernel.jar` | the launcher, the kernel and the image format, which is what ships |
| `build/frameworks/*.jar` | one per image, which is what each image's Mach-O carries |

`Kernel.jar` holds no part of the system at all: four classes and a version. Everything
else is on the volume, which is what makes a release replaceable by swapping one file.
`Fractal.jar` holds no application code — each application's classes ship inside that
application's own executable, and the self test counts them to keep it that way.
