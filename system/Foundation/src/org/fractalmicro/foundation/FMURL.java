/*CDDL HEADER START
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://illumos.org/license/CDDL.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *
 * CDDL HEADER END
 * Copyright (C) 2026 by Fractal Microsystems, Inc.
 * Use is subject to license terms.
 */
package org.fractalmicro.foundation;

/**
 * Where something is.
 *
 * A file, usually, and one day a thing on a network, which is why this is a location rather
 * than a path. A program is handed these by the file manager and hands them back, and never
 * has to spell out a separator or know which way round this machine writes them.
 *
 * It says nothing about whether the thing is there. Asking is
 * {@link FMFileManager#exists}, and the answer stops being true the moment it is given.
 */
public final class FMURL implements Comparable<FMURL> {

    private final java.io.File file;

    private FMURL(java.io.File file) { this.file = file; }

    /** A location from a path as a person would write it. */
    public static FMURL ofPath(FMString path) {
        return path == null || path.isEmpty() ? null : new FMURL(new java.io.File(path.toString()));
    }

    public static FMURL ofPath(String path) {
        return path == null || path.isEmpty() ? null : new FMURL(new java.io.File(path));
    }

    /** For the layers that still hand round the runtime's own file. */
    public static FMURL of(java.io.File file) {
        return file == null ? null : new FMURL(file);
    }

    /** Something inside this one: a file in a directory, a directory in a directory. */
    public FMURL appending(FMString component) {
        return new FMURL(new java.io.File(file, component.toString()));
    }

    /** What holds this one, or nothing when it is the top of its volume. */
    public FMURL deletingLastComponent() {
        java.io.File parent = file.getParentFile();
        return parent == null ? null : new FMURL(parent);
    }

    /** The name on its own: the last part, with its extension. */
    public FMString lastComponent() { return FMString.of(file.getName()); }

    /** The name without its extension, which is the part a person edits when renaming. */
    public FMString stem() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return FMString.of(dot <= 0 ? name : name.substring(0, dot));
    }

    /** The extension without its dot, or nothing when there is none. */
    public FMString extension() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1
            ? FMString.EMPTY : FMString.of(name.substring(dot + 1));
    }

    public FMString path() { return FMString.of(file.getPath()); }

    public FMString absolutePath() { return FMString.of(file.getAbsolutePath()); }

    /**
     * What is there, if anything.
     *
     * A location is a name for a place, and a name can be written down for something that
     * does not exist yet, so asking is a separate act from having one. These three are
     * what a program actually wants to know before it does anything with one.
     */
    public boolean exists() { return file.exists(); }

    public boolean isDirectory() { return file.isDirectory(); }

    public boolean isFile() { return file.isFile(); }

    /** The runtime's own file, for the layers beneath this one. */
    public java.io.File asFile() { return file; }

    java.nio.file.Path asPath() { return file.toPath(); }

    @Override public int compareTo(FMURL other) {
        return file.compareTo(other.file);
    }

    @Override public boolean equals(Object other) {
        return other instanceof FMURL u && file.equals(u.file);
    }

    @Override public int hashCode() { return file.hashCode(); }

    @Override public String toString() { return file.getPath(); }
}
