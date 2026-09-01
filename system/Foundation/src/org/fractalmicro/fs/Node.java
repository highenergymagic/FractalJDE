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
package org.fractalmicro.fs;

import java.io.File;
import java.util.Objects;

/**
 * Anything that can appear in a Finder view or on the desktop.
 *
 * The accessible name of an item is its name and nothing more, matching what Finder
 * exposes: a disk on the desktop reads as its name, not as a recital of its type and
 * capacity. The rest lives in {@link #detail}, used by the status bar and the Info window,
 * where the user actually asked for it.
 */
public class Node {
    public enum Kind {
        FOLDER, FILE, APPLICATION, ALIAS,
        HARD_DISK, EXTERNAL_DISK, REMOVABLE_MEDIA, SERVER,
        TRASH, SEARCH, COMPUTER, NETWORK
    }

    public final Kind kind;
    public final String name;
    public final File file;

    public long size = -1;
    public long free = -1;
    public long modified;
    public boolean locked;
    public String fileSystem = "";
    public String mountPoint;
    /** Free text for the status bar and Get Info. Never part of the accessible name. */
    public String detail = "";
    /** The label, 0 for none through 7, as the file's own Finder flags record it. */
    public int label;

    public Node(Kind kind, String name, File file) {
        this.kind = kind;
        this.name = name;
        this.file = file;
    }

    public boolean isContainer() {
        switch (kind) {
            case FOLDER: case HARD_DISK: case EXTERNAL_DISK: case REMOVABLE_MEDIA:
            case SERVER: case TRASH: case COMPUTER: case NETWORK: case SEARCH:
                return true;
            default:
                return false;
        }
    }

    public boolean isVolume() {
        return kind == Kind.HARD_DISK || kind == Kind.EXTERNAL_DISK
            || kind == Kind.REMOVABLE_MEDIA || kind == Kind.SERVER;
    }

    public boolean isMounted() {
        return !isVolume() || size > 0;
    }

    /** What this item is called.
     *
     * The name and nothing more. Size, kind and date belong in the status bar and in Get
     * Info, where somebody has asked for them.
     */
    public String accessibleName() {
        return name;
    }

    /** The Finder "Kind" field: "Folder", "Application", "Microsoft Word document". */
    public String kindLabel() {
        return Kinds.display(this);
    }

    /** The same in lower case, for reading out: "selected property list". */
    public String kindPhrase() {
        return Kinds.of(this);
    }

    /** One line of facts, for the status bar and Get Info. */
    public String summary() {
        if (isVolume()) {
            if (size <= 0) return "no disc inserted";
            return FS.formatBytes(size) + ", " + FS.formatBytes(free) + " available";
        }
        if (file != null && file.isDirectory()) {
            String[] kids = file.list();
            int count = kids == null ? 0 : kids.length;
            return count == 1 ? "1 item" : count + " items";
        }
        if (size >= 0) return FS.formatBytes(size);
        return detail;
    }

    @Override public String toString() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node n = (Node) o;
        return kind == n.kind && Objects.equals(name, n.name) && Objects.equals(file, n.file)
            && size == n.size && free == n.free;
    }

    @Override public int hashCode() { return Objects.hash(kind, name, file, size, free); }
}
