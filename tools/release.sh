#!/bin/sh
#CDDL HEADER START
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
# You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
# or http://illumos.org/license/CDDL.
# See the License for the specific language governing permissions
# and limitations under the License.
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information:
#
# CDDL HEADER END
# Copyright (C) 2026 by Fractal Microsystems, Inc.
# Use is subject to license terms.
#
# Builds a release.
#
#   sh tools/release.sh
#
# Out of it comes build/FractalJDE-<version>.zip, holding two files that matter:
#
#   FractalJDE.jar    the kernel. The launcher, the code that finds a volume and the
#                     image format, and nothing else. No framework is in it.
#   BaseSystem.dmg    a whole system volume in one file: the frameworks, the loader,
#                     launchd, every application, at the paths they will sit at.
#
# Started, the kernel looks for ~/.fractaldt. If the volume is not there, or holds a
# different build from the image, it unpacks the image onto it. Then it reads
# /usr/lib/dyld off the volume and starts /sbin/launchd through it. From there the volume
# is the system: nothing further is read out of the jar.
#
# The volume in the image is laid out by running the system's own installer against an
# empty directory, so what ships is what installing produces rather than an arrangement
# this script believes in separately. It is laid out somewhere other than ~/.fractaldt,
# because the volume being built is not the volume this machine runs.
set -e
cd "$(dirname "$0")/.."
# Windows' own find.exe has never heard of -name, and whichever is first on the path runs.
# The same reason build.sh chooses one rather than assuming.
find=
for candidate in /usr/bin/find /bin/find find; do
    if command -v "$candidate" >/dev/null 2>&1        && "$candidate" tools -name release.sh >/dev/null 2>&1; then
        find=$candidate
        break
    fi
done
[ -n "$find" ] || { echo "no find here understands -name; run this from Git Bash." >&2; exit 1; }


sh tools/build.sh --no-tests

version=$(sed -n 's/^version=//p' version.properties)
build=$(sed -n 's/^build=//p' build/system/org/fractalmicro/resources/version.properties)
built=$(sed -n 's/^built=//p' build/system/org/fractalmicro/resources/version.properties)

volume="build/volume"
release="build/FractalJDE-$version"
archive="FractalJDE-$version.zip"

rm -rf "$volume" "$release" "build/$archive" "build/BaseSystem.dmg"
mkdir -p "$volume" "$release"

# ------------------------------------------------------------------- the volume
# -Dorg.fractalmicro.volume puts it somewhere other than this machine's own. Without
# -Dorg.fractalmicro.appcode the framework has no compiled applications to put in the
# bundles, and every .app in the image would be a plist with nothing behind it.
java --enable-preview --enable-native-access=ALL-UNNAMED \
     -Dorg.fractalmicro.volume="$PWD/$volume" \
     -Dorg.fractalmicro.appcode="$PWD/build/apps" \
     -Dorg.fractalmicro.images="$PWD/build/frameworks" \
     -jar build/Fractal.jar --install > "$volume.log" 2>&1 \
  || { echo "laying out the volume failed:"; cat "$volume.log"; exit 1; }

# A volume with no loader is a volume nothing can start, and it is worth finding that out
# here rather than on somebody's machine after they downloaded it.
for needed in usr/lib/dyld sbin/launchd; do
    [ -f "$volume/$needed" ] || { echo "the volume has no $needed"; exit 1; }
done

# Nothing on the volume may name the machine it was built on, in either spelling. The
# scripts on the volume are written with backslashes and this shell works in forward ones,
# and a check that knew only one of them passed for a while over launchers naming the
# directory a release was staged in, which start nothing on any other machine.
posix="$PWD"
windows=$(cygpath -w "$PWD" 2>/dev/null || printf '%s' "$PWD")
named=$(grep -rIl -e "$posix" -e "$windows" -e 'build.volume' "$volume" 2>/dev/null || true)
if [ -n "$named" ]; then
    echo "these files name the machine the release was built on:"
    echo "$named" | sed 's/^/  /'
    exit 1
fi

# -------------------------------------------------------------------- the image
java -cp build/kernel org.fractalmicro.BaseImage \
     "$volume" build/BaseSystem.dmg "$version" "$build" "$built"

# ------------------------------------------------------------------ the release
cp build/Kernel.jar "$release/FractalJDE.jar"
cp build/BaseSystem.dmg "$release/BaseSystem.dmg"
cp LICENSE "$release/LICENSE" 2>/dev/null || true

# The graphical launcher, when there is a Rust compiler to build it with. It is the nicer
# way in and not the only one, so a machine without one still produces a release; what it
# produces is a release you start from a terminal.
launcher="not built: no Rust compiler on this machine"
if sh tools/launcher.sh > "$volume-launcher.log" 2>&1; then
    cp build/Fractal.exe "$release/Fractal.exe"
    launcher="$(($(wc -c < build/Fractal.exe) / 1024))K"
else
    echo "the launcher was not built; the release will not have one:"
    sed 's/^/  /' "$volume-launcher.log"
fi

cat > "$release/FractalJDE.cmd" <<CMD
@echo off
rem Starts FractalJDE and watches it come up.
rem
rem java rather than javaw, and in this window, because this is where the system says how
rem far it has got. Closing the window stops it. For a boot screen instead, run Fractal.exe.
setlocal
title Fractal console
cd /d "%~dp0"
java --enable-preview --enable-native-access=ALL-UNNAMED -jar FractalJDE.jar %*
if errorlevel 1 pause
endlocal
CMD

cat > "$release/README.txt" <<TXT
FractalJDE $version ($build)
A Mac OS X 10.6 desktop and Finder, by Fractal Microsystems.

Two ways in, and they start the same system.

    Fractal.exe        a boot screen, and the log written to
                       .fractaldt\private\var\log\boot.log
    FractalJDE.cmd     a console, with the system saying where it has got to in it

or by hand:

    java --enable-preview --enable-native-access=ALL-UNNAMED -jar FractalJDE.jar

The first run unpacks BaseSystem.dmg into .fractaldt in your home directory and starts
the system from there. Later runs find it already installed and skip straight to
starting it. A release with a different build number replaces the system files and
leaves everything else on the volume alone.

Fractal.exe, FractalJDE.jar and BaseSystem.dmg have to stay in the same directory: the
kernel looks for the image beside itself, and the launcher for the jar beside itself.

Fractal.exe finds a Java runtime by looking at FRACTAL_JAVA, then a runtime directory
beside itself, then JAVA_HOME, then the path, then the registry. Set FRACTAL_JAVA to a
javaw.exe to settle it. "Fractal.exe --where report.txt" writes down what it found.

Needs a Java 21 runtime or newer. The layer that talks to Windows uses the foreign
function interface, a preview feature on 21, which is what the flags are for.
TXT

# One directory at the top of the archive, named for the version, whichever tool made it.
if command -v zip >/dev/null 2>&1; then
    ( cd build && zip -qr "$archive" "FractalJDE-$version" )
else
    ( cd build && jar --create --file "$archive" "FractalJDE-$version" )
fi

kernel=$(wc -c < build/Kernel.jar | tr -d ' ')
image=$(wc -c < build/BaseSystem.dmg | tr -d ' ')
echo "released build/$archive"
echo "  kernel        $((kernel / 1024))K"
echo "  launcher      $launcher"
echo "  base image    $((image / 1024))K, $("$find" "$volume" -type f | wc -l | tr -d ' ') files"
echo "  applications  $("$find" "$volume" -maxdepth 5 -name '*.app' | wc -l | tr -d ' ')"
