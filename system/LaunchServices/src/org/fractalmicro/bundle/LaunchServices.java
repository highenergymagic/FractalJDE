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
package org.fractalmicro.bundle;

import org.fractalmicro.core.Recent;
import org.fractalmicro.core.Running;
import org.fractalmicro.core.Shell;
import org.fractalmicro.fs.Node;

import java.io.File;
import java.util.List;

/**
 * Opening something with whatever should open it.
 *
 * This is the one question everything asks and nothing should answer for itself: given a
 * file, what happens when somebody double-clicks it. Three answers, and which one it is
 * comes from the file rather than from who is asking.
 *
 *   a program        started, out of its bundle
 *   a folder         handed to the file manager, which puts a window on it
 *   anything else    handed to whatever the host says opens that kind of file
 *
 * It lives here, below the screen, because it is not a question about windows. The Dock,
 * the desktop, Spotlight and the recent items menu all open things, and until this existed
 * each of them asked the Finder to do it, which meant the layer that draws was calling up
 * into the file manager to find out what a double-click means. The Finder is one of the
 * three answers, not the place the question goes.
 *
 * That is also what it means for the Finder to stop being special. It is reached the way
 * every other program is reached, by identifier, and if it were not installed the other
 * two answers would still work.
 */
public final class LaunchServices {
    private LaunchServices() {}

    /**
     * The file manager, named rather than linked.
     *
     * Everything that wants a window on a folder asks for this identifier. Nothing above
     * here names a class inside it, which is the whole point: it can be replaced, or moved
     * into a process of its own, without anything that opens a folder being rebuilt.
     */
    public static final String FILE_BROWSER = "org.fractalmicro.finder";

    /* ------------------------------------------------------- what a part is called */

    /**
     * Taking over the desktop: the menu bar it owns by default, and the icons on the back
     * of the screen.
     *
     * What the session asks for at login. It is not the same as opening the file manager,
     * which puts up a window; on a Mac the Finder comes up at login with no window at all
     * and the desktop is what it draws.
     */
    public static final String DESKTOP = "desktop";
    /** Its window on the Trash. */
    public static final String TRASH = "trash";
    /** Emptying it, with the warning that goes with that. */
    public static final String EMPTY_TRASH = "empty-trash";
    /** Emptying it so that what was in it cannot be read again. */
    public static final String EMPTY_TRASH_SECURELY = "empty-trash-securely";
    /** Looking at the folders again, because something changed them from outside. */
    public static final String REFRESH = "refresh";
    /** Reaching a machine on the network. */
    public static final String CONNECT_TO_SERVER = "connect-to-server";

    /* ------------------------------------------------------------------- opening */

    /**
     * Opens a file the way double-clicking it would.
     *
     * Answers whether anything took it. A false answer is a file nothing could open, which
     * is the caller's to report: the desktop says so in the status line, a program in an
     * alert, and neither of those is a decision this can make from here.
     */
    public static boolean open(File file) {
        if (file == null || !file.exists()) return false;
        if (Bundle.looksLikeBundle(file)) return openProgram(file);
        if (file.isDirectory()) return openFolder(file);
        return openDocument(file, false);
    }

    /**
     * Opens something the file manager is already holding a description of.
     *
     * The same three answers, with two more that only a listing knows about: the Trash,
     * which is a place rather than a folder, and a disk with nothing in the drive, which
     * is a name for something that is not there.
     */
    public static boolean open(Node node) {
        if (node == null) return false;
        if (node.kind == Node.Kind.TRASH) return Bundles.openPart(FILE_BROWSER, TRASH);
        if (node.isVolume() && !node.isMounted()) return false;
        if (node.file == null) return false;
        if (Bundle.looksLikeBundle(node.file)) return openProgram(node.file);
        if (node.isContainer()) return openFolder(node.file);
        return openDocument(node.file, node.kind == Node.Kind.APPLICATION);
    }

    /** Opens several, which is what a selection is. Answers whether every one was taken. */
    public static boolean openAll(List<Node> nodes) {
        boolean all = true;
        if (nodes != null) for (Node node : nodes) all &= open(node);
        return all;
    }

    /** A window on a folder, which is the file manager's to put up. */
    public static boolean openFolder(File folder) {
        if (folder == null) return false;
        return Bundles.openFiles(FILE_BROWSER, List.of(folder));
    }

    /** A named part of the file manager: its Trash window, emptying it, looking again. */
    public static boolean tellFileBrowser(String part) {
        return Bundles.openPart(FILE_BROWSER, part);
    }

    private static boolean openProgram(File bundleFolder) {
        Bundle bundle = Bundles.byFolder(bundleFolder);
        if (bundle == null) return false;
        Recent.noteItem(bundleFolder);
        return Bundles.open(bundle, null);
    }

    /**
     * Hands a file to the host, which is what opening one means for anything this system
     * does not have a program for.
     *
     * A program started this way is noted as running, because nothing else will notice: it
     * has no bundle here and no task number, and the Dock showing it is the only sign that
     * clicking the tile did anything.
     */
    private static boolean openDocument(File file, boolean isProgram) {
        Recent.noteItem(file);
        if (isProgram) Running.note(nameOf(file), file);
        Shell.open(file);
        return true;
    }

    private static String nameOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
