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
package org.fractalmicro.appkit;

import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.bundle.LaunchServices;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Trash;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.kernel.TaskServer;

import java.io.File;

/**
 * What the system does with a file, for a program that does not want to know how.
 *
 * Open this. Show me where it is. What kind of thing is it. What is mounted. What is
 * running. Put this in the Trash. Every one of those is a question about the system rather
 * than about the program asking, and NSWorkspace is where a Cocoa program asks them.
 *
 * There was no such door here. Opening a file meant reaching into LaunchServices; asking
 * what a file was meant reaching into the file layer; the mounted volumes were somewhere
 * else again and what was running somewhere else after that. So the applications shipped
 * with this system named six or seven packages between them, most of which were the
 * plumbing under the platform rather than the platform. A platform is what its programs
 * can see, and what they could see was the inside.
 *
 * This is in AppKit for the reason NSWorkspace is: it is about what a person can see
 * happening. The answers still come from the layers below, which have not moved.
 */
public final class FMWorkspace {

    private static final FMWorkspace SHARED = new FMWorkspace();

    private FMWorkspace() {}

    /** The one for this program, as NSWorkspace has always been reached. */
    public static FMWorkspace sharedWorkspace() { return SHARED; }

    /* ---------------------------------------------------------------- opening */

    /**
     * Opens something the way double-clicking it would: a program starts, a folder gets a
     * window, anything else goes to whatever the host says opens it.
     */
    public boolean openURL(FMURL url) {
        return url != null && LaunchServices.open(url.asFile());
    }

    /** The same, said the way a program with a file rather than an address says it. */
    public boolean openFile(FMURL file) { return openURL(file); }

    /**
     * Opens a web address in whatever the machine browses with.
     *
     * Its own method rather than the one above, because FMURL here is a file and nothing
     * else. NSURL is any address and NSWorkspace -openURL: therefore covers both; making
     * that true here means teaching a URL about schemes, hosts and queries, which is a
     * larger thing than this and is not pretended at by taking a string that says http.
     */
    public boolean browse(FMString address) {
        if (address == null || address.isEmpty()) return false;
        org.fractalmicro.core.Shell.browse(address.toString());
        return true;
    }

    /**
     * Opens the host's command line on a folder.
     *
     * NSWorkspace has no such thing, because on a Mac the Terminal is an application and
     * this would be opening it on a URL. Here the command line belongs to the host system
     * underneath and there is no bundle to open, so it is a thing the workspace does.
     */
    public boolean openTerminal(FMURL folder) {
        if (folder == null) return false;
        org.fractalmicro.core.Shell.openTerminal(folder.asFile());
        return true;
    }

    /** Starts an installed program by its bundle identifier, whether or not it is running. */
    public boolean launchApplication(FMString bundleIdentifier) {
        return bundleIdentifier != null
            && Bundles.openIdentifier(bundleIdentifier.toString());
    }

    /** Opens a program on some files, the way dropping them on its icon would. */
    public boolean openWithApplication(FMString bundleIdentifier, FMArray<FMURL> files) {
        java.util.List<File> named = new java.util.ArrayList<>();
        if (files != null) for (FMURL one : files) named.add(one.asFile());
        return Bundles.openFiles(bundleIdentifier.toString(), named);
    }

    /**
     * Shows something where it lives, with itself picked out.
     *
     * NSWorkspace calls this activating the file viewer selecting a URL, which is a long
     * way of saying reveal it in the Finder, and is what it does.
     */
    public boolean selectFile(FMURL file) {
        if (file == null) return false;
        File where = file.asFile();
        File folder = where.isDirectory() ? where : where.getParentFile();
        return LaunchServices.openFolder(folder);
    }

    /* --------------------------------------------------------------- asking */

    /** What a person should see this called, which is not always what it is called. */
    public FMString displayNameForFile(FMURL file) {
        return file == null ? FMString.EMPTY : FMString.of(FS.displayName(file.asFile()));
    }

    /** What kind of thing it is, in words: a folder, a program, a plain text document. */
    public FMString localizedDescriptionForFile(FMURL file) {
        if (file == null) return FMString.EMPTY;
        return FMString.of(org.fractalmicro.fs.Kinds.ofFile(file.asFile()));
    }

    /** Whether it is a folder the system shows as one thing, which a bundle is. */
    public boolean isFilePackage(FMURL file) {
        return file != null && FS.looksLikeBundle(file.asFile());
    }

    /** Whether opening it would start a program. */
    public boolean isApplication(FMURL file) {
        return file != null && FS.isApplication(file.asFile());
    }

    /* -------------------------------------------------------------- the disks */

    /** Every volume mounted right now, startup disk included. */
    public FMArray<FMVolume> mountedVolumes() {
        FMMutableArray<FMVolume> out = FMMutableArray.empty();
        for (Node one : Volumes.all()) {
            out.add(new FMVolume(FMString.of(one.name),
                                 one.file == null ? null : FMURL.of(one.file),
                                 FMString.of(one.fileSystem),
                                 one.size, one.free));
        }
        return out.asArray();
    }

    /** The one the system started from. */
    public FMURL startupVolume() {
        Node disk = Volumes.startupDisk();
        return disk == null || disk.file == null ? null : FMURL.of(disk.file);
    }

    /* -------------------------------------------------------------- the Trash */

    /** Puts something in the Trash, which is not the same as deleting it. */
    public boolean recycle(FMURL file) {
        return file != null && Trash.moveToTrash(java.util.List.of(file.asFile())) > 0;
    }

    /** How many things are in it, for anything that draws it full or empty. */
    public int trashCount() { return Trash.count(); }

    /* ----------------------------------------------------------- what is running */

    /**
     * Everything running anywhere in this system, not only in this process.
     *
     * One line each, in the shape a listing wants: the number, what started it, the name,
     * what kind of thing it is, where it actually runs, and whether it still is. The task
     * table is the authority and it is in another process; this asks it.
     */
    public FMArray<FMRunningApplication> runningApplications() {
        FMMutableArray<FMRunningApplication> out = FMMutableArray.empty();
        for (TaskServer.Row row : TaskServer.everything()) {
            out.add(new FMRunningApplication(row.pid(), row.parent(), row.name(),
                                             row.kind(), row.where(), row.state()));
        }
        return out.asArray();
    }

    /** Asks something to stop, wherever it is running. */
    public boolean terminateApplication(int taskIdentifier) {
        return TaskServer.kill(taskIdentifier);
    }
}
