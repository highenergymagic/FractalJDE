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
# Builds the graphical launcher, which is the thing that starts the system when there is
# no terminal to start it from: a boot screen, and something to find a runtime with.
#
#   sh tools/launcher.sh
#
# Out of it comes build/Fractal.exe. It is written in Rust and has no dependencies at all,
# so this needs a Rust compiler and nothing else. Without one it says so and stops, and
# the release is built without it: a launcher is a convenience over `java -jar`, and a
# release that could not be made because a second toolchain was missing is not.
set -e
cd "$(dirname "$0")/.."

if ! command -v cargo >/dev/null 2>&1; then
    echo "no cargo on this machine, so there is no launcher to build"
    echo "install Rust from https://rustup.rs to build build/Fractal.exe"
    exit 1
fi

# The company mark, as coverage, so the launcher can draw it before there is a volume with
# a copy of it on. Made again from the artwork on every build rather than trusted, since
# the answer is kept in the tree and a kept answer is one that can go stale.
if command -v java >/dev/null 2>&1; then
    java tools/Logo.java resources/FractalLogo.png tools/launcher/src/mark.mask
else
    echo "no java here, so the mark in the tree is the one that will be used"
fi

( cd tools/launcher && cargo build --release )
( cd tools/launcher && cargo test --quiet )

mkdir -p build
cp tools/launcher/target/release/Fractal.exe build/Fractal.exe
echo "built build/Fractal.exe: $(($(wc -c < build/Fractal.exe) / 1024))K"
