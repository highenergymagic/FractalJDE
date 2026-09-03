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
# Builds FractalJDE.
#
#   sh tools/build.sh                 build at the current version
#   sh tools/build.sh --bump          bump the patch version first
#   sh tools/build.sh --bump minor    bump the minor version, zeroing the patch
#   sh tools/build.sh --bump major    bump the major version, zeroing the rest
#   sh tools/build.sh --no-tests      the system and the applications only
#
# The system is built one framework at a time, lowest first, and each is compiled against
# only the frameworks below it:
#
#   LibSystem        the boundary to the host: its calls, its windows, its registry, the log
#   Foundation       files, property lists, preferences, tasks, ports
#   dyld             the loader, which is not a framework: /usr/lib/dyld
#   launchd          the first process: /sbin/launchd
#   LaunchServices   bundles, opening a program, installing
#   Metadata         the index behind Spotlight, and the daemon that keeps it
#   AppKit           drawing, controls, the window server, the screen
#   Finder           the file browser
#   Fractal          the executable that starts the lot
#
# LaunchServices and Metadata are frameworks inside the CoreServices umbrella when they
# are installed, the way Mac OS X keeps them. dyld and launchd are not frameworks at all:
# one is a Mach-O of its own kind and the other is the process the kernel starts first.
#
# That order is the whole point. A framework is handed the class path of what is beneath
# it and nothing else, so a reference upwards is not a rule somebody has to remember: it
# does not compile. Applications come after, each against the frameworks and never against
# each other; the checks come last and see everything, because that is their job.
set -e
cd "$(dirname "$0")/.."
# Which find. Windows ships a find.exe of its own that searches inside files for a string
# and has never heard of -name, and whichever comes first on the path is the one that runs.
# On a machine where that is Windows' the only output of this whole script was
# "File not found - *.java". So the one that walks directories is chosen by trying them
# rather than assumed, and a machine with none says so.
find=
for candidate in /usr/bin/find /bin/find find; do
    if command -v "$candidate" >/dev/null 2>&1 \
       && "$candidate" tools -name build.sh >/dev/null 2>&1; then
        find=$candidate
        break
    fi
done
if [ -z "$find" ]; then
    echo "no find here understands -name." >&2
    echo "Windows has one of its own that does not; this needs the one Git Bash," >&2
    echo "MSYS or Cygwin installs. Run this from a Git Bash prompt." >&2
    exit 1
fi


tests=yes
part=patch
bump=no
for arg in "$@"; do
    case "$arg" in
        --no-tests) tests=no ;;
        --bump)     bump=yes ;;
        major|minor|patch) part="$arg" ;;
    esac
done

if [ "$bump" = yes ]; then
    old=$(sed -n 's/^version=//p' version.properties)
    major=$(echo "$old" | cut -d. -f1)
    minor=$(echo "$old" | cut -d. -f2)
    patch=$(echo "$old" | cut -d. -f3)
    case "$part" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        *)     patch=$((patch + 1)) ;;
    esac
    new="$major.$minor.$patch"
    sed -i "s/^version=.*/version=$new/" version.properties
    echo "version $old -> $new"
fi

version=$(sed -n 's/^version=//p' version.properties)
stamp=$(date +%y%m%d%H%M%S)
build=$(printf '%X' "$stamp")
built=$(date '+%Y-%m-%d %H:%M:%S')
tmp="${TMPDIR:-/tmp}"

# Path separator: everything here runs under a shell, but javac is the host's.
sep=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=";" ;; esac

rm -rf build
mkdir -p build/frameworks build/apps

# One stage per framework, in the order they depend on each other, and none of them is ever
# handed the class path of anything above it. That is what makes the stack a fact rather
# than an intention: a library that reached upward would not compile, here, in this loop.
#
# AppKit and the Finder were one stage for a long time, because the screen furniture named
# the file browser: the Dock opened the Trash by calling it, the desktop icons were an
# AppKit class that called it back, and every double-click asked it what a file was. They
# are separate now, and this line is the check. A stage that still needs the one after it
# fails here rather than in a list of known exceptions.
FRAMEWORKS="LibSystem Foundation dyld launchd LaunchServices Metadata AppKit Finder"

# ----------------------------------------------------------------- the frameworks
beneath=""
for name in $FRAMEWORKS; do
    case "$name" in
        *+*) roots="" ; for one in $(echo "$name" | tr '+' ' '); do
                 roots="$roots system/$one/src"
             done ;;
        *)   roots="system/$name/src" ;;
    esac
    "$find" $roots -name '*.java' > "$tmp/fractal-fw.txt"
    [ -s "$tmp/fractal-fw.txt" ] || continue
    out="build/frameworks/$(echo "$name" | tr -d "+")"
    mkdir -p "$out"
    if [ -z "$beneath" ]; then
        javac --release 21 --enable-preview -d "$out" \
              -encoding UTF-8 @"$tmp/fractal-fw.txt"
    else
        javac --release 21 --enable-preview -d "$out" \
              -encoding UTF-8 -cp "$beneath" @"$tmp/fractal-fw.txt"
    fi
    # A framework's words go beside its binary once it is installed, so the build puts
    # them where the installer looks. AppKit says "Cancel" in every program on the volume
    # and the translation of it belongs to AppKit, not to each program that shows it.
    # Named for the framework rather than for the stage it was compiled in: AppKit and the
    # Finder are built together, and their words are not the same words. AppKit's go into
    # AppKit.framework, and the Finder's into Finder.app with the rest of that program.
    for one in $(echo "$name" | tr '+' ' '); do
        [ -d "system/$one/resources" ] || continue
        mkdir -p "build/frameworks/$one.resources"
        cp -r "system/$one/resources/." "build/frameworks/$one.resources/"
    done
    beneath="${beneath:+$beneath$sep}$out"
done

system="$beneath"

# ------------------------------------------------------------------ the executable
mkdir -p build/system
"$find" system/Fractal/src -name '*.java' \
     ! -name 'Boot.java' ! -name 'Kernel.java' ! -name 'BaseImage.java' \
     > "$tmp/fractal-main.txt"
javac --release 21 --enable-preview -d build/system -encoding UTF-8 \
      -cp "$system" @"$tmp/fractal-main.txt"

# The launcher, the kernel and the image format load in a plain virtual machine: the
# launcher because it exists to restart itself with the preview flags and so must run
# before they are on, and the other two because they run before there is a system and have
# no business needing anything the system needs. None of them are compiled with preview,
# which is also what makes the kernel jar something a build can call directly.
javac --release 21 -d build/system -encoding UTF-8 \
      system/Fractal/src/org/fractalmicro/Boot.java \
      system/Fractal/src/org/fractalmicro/Kernel.java \
      system/Fractal/src/org/fractalmicro/BaseImage.java

# The session: what launchd starts to bring up a screen. It is an image like any other,
# so that the loader can map it and there is nothing above the frameworks that is not.
mkdir -p build/frameworks/loginwindow
cp -r build/system/org build/frameworks/loginwindow/ 2>/dev/null || true
"$find" build/frameworks/loginwindow -mindepth 3 -maxdepth 3 -type d \
     ! -name 'resources' -exec rm -rf {} + 2>/dev/null || true

cp -r build/frameworks/*/* build/system/ 2>/dev/null || true
mkdir -p build/system/org/fractalmicro/resources
cp resources/* build/system/org/fractalmicro/resources/ 2>/dev/null || true

# The screen reader client is a library, not a picture: it goes in at the top of the jar
# where it can be found by name, with its licence beside it.
if [ -d resources/nvda ]; then
    mkdir -p build/system/nvda
    cp resources/nvda/* build/system/nvda/ 2>/dev/null || true
fi
{
    echo "version=$version"
    echo "build=$build"
    echo "built=$built"
} > build/system/org/fractalmicro/resources/version.properties

# --------------------------------------------------------------- the applications
apps=""
for dir in apps/*/; do
    name=$(basename "$dir")
    [ -d "$dir/src" ] || continue
    "$find" "$dir/src" -name '*.java' > "$tmp/fractal-app.txt"
    [ -s "$tmp/fractal-app.txt" ] || continue
    mkdir -p "build/apps/$name"
    javac --release 21 --enable-preview -d "build/apps/$name" -encoding UTF-8 \
          -cp "$system" @"$tmp/fractal-app.txt"
    [ -d "$dir/resources" ] && cp -r "$dir/resources" "build/apps/$name.resources"
    apps="$apps $name"
done

# The Finder is a program in a bundle like any other: its classes go into its bundle and
# its interface files and words go in beside them. It is built under system/ rather than
# under apps/, because the session and the checks are compiled against it, so it is staged
# by hand here and the installer finds it under the same name as everything else.
#
# Its code used to be inside AppKit, which meant every program that linked a window library
# was handed the file manager as well, and the bundle it shipped in carried nothing at all.
mkdir -p build/apps/Finder
cp -r build/frameworks/Finder/* build/apps/Finder/ 2>/dev/null || true
[ -d system/Finder/resources ] && cp -r system/Finder/resources build/apps/Finder.resources

# ------------------------------------------------------------------- the checks
if [ "$tests" = yes ] && [ -d tests/src ]; then
    classpath="$system${sep}build/system"
    for name in $apps; do classpath="$classpath${sep}build/apps/$name"; done
    mkdir -p build/tests
    "$find" tests/src -name '*.java' > "$tmp/fractal-tests.txt"
    javac --release 21 --enable-preview -d build/tests -encoding UTF-8 \
          -cp "$classpath" @"$tmp/fractal-tests.txt"
fi

# --------------------------------------------------------------------- the jars
# One jar to run from a checkout: everything together, which is convenient rather than
# how it ships. What ships is built by tools/release.sh: a kernel jar and a base image.
mkdir -p build/jar
cp -r build/system/* build/jar/
for name in $apps; do cp -r "build/apps/$name"/* build/jar/; done
[ -d build/tests ] && cp -r build/tests/* build/jar/

{
    echo "Main-Class: org.fractalmicro.Boot"
    echo "Fractal-Entry: org.fractalmicro.Main"
} > "$tmp/fractal-manifest.txt"
jar --create --file build/FractalJDE.jar --manifest "$tmp/fractal-manifest.txt" -C build/jar .

# The system without the applications or the checks in it, which is what lays out a volume.
jar --create --file build/Fractal.jar --manifest "$tmp/fractal-manifest.txt" -C build/system .

# ------------------------------------------------------------------- the kernel
# What a release actually ships as a jar: the launcher, the kernel and the image format,
# and nothing else at all. It finds a volume, unpacks one from the image beside it if it
# has to, and reads /usr/lib/dyld off that volume to start launchd. No framework is in
# here, so replacing the system is replacing the image and this file stays as it is.
#
# The canary goes in because the kernel has to know whether preview is on before it starts
# anything, and the only way to ask is to try loading something stamped with it.
mkdir -p build/kernel/org/fractalmicro
for name in Boot Kernel BaseImage Preview; do
    # The glob takes the inner classes with it; a file visitor is one of them, and a
    # kernel missing it would unpack nothing and say nothing about why.
    cp build/system/org/fractalmicro/"$name".class \
       build/system/org/fractalmicro/"$name"\$*.class \
       build/kernel/org/fractalmicro/ 2>/dev/null || \
    cp build/system/org/fractalmicro/"$name".class build/kernel/org/fractalmicro/
done
mkdir -p build/kernel/org/fractalmicro/resources
cp build/system/org/fractalmicro/resources/version.properties \
   build/kernel/org/fractalmicro/resources/
{
    echo "Main-Class: org.fractalmicro.Boot"
    echo "Fractal-Entry: org.fractalmicro.Kernel"
    echo "Implementation-Version: $version"
} > "$tmp/fractal-kernel-manifest.txt"
jar --create --file build/Kernel.jar --manifest "$tmp/fractal-kernel-manifest.txt" \
    -C build/kernel .

# And each image on its own, which is what an application actually links, plus the
# session, which is a program rather than a framework but is installed the same way.
for name in $FRAMEWORKS loginwindow; do
    out="build/frameworks/$(echo "$name" | tr -d '+')"
    [ -d "$out" ] || continue
    jar --create --file "$out.jar" -C "$out" .
done

echo "built FractalJDE $version ($build) at $built"
for name in $FRAMEWORKS loginwindow; do
    out="build/frameworks/$(echo "$name" | tr -d '+')"
    [ -d "$out" ] || continue
    echo "  $name $("$find" "$out" -name '*.class' | wc -l | tr -d ' ') classes"
done
for name in $apps; do
    echo "  $name $("$find" "build/apps/$name" -name '*.class' | wc -l | tr -d ' ') classes"
done
echo "run it with: java -Dorg.fractalmicro.images=build/frameworks -jar build/FractalJDE.jar"
