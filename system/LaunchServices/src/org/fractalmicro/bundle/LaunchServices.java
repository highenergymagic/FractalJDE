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
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.fs.Node;
import org.fractalmicro.uti.UTTypes;

import java.io.File;
import java.util.List;

/**
 * Opening something with whatever should open it.
 *
 * Given a file, what happens when somebody double-clicks it. Three answers, chosen by the
 * file rather than by who is asking.
 *
 *   a program        started, out of its bundle
 *   a folder         handed to the file manager, which puts a window on it
 *   anything else    handed to whatever the host says opens that kind of file
 *
 * Below the screen, because it is not a question about windows: the Dock, the desktop,
 * Spotlight and the recent items menu all open things.
 *
 * The Finder is one of the three answers, reached by identifier like any other program.
 * Without it installed the other two still work.
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

    /* ------------------------------------------------- who can open what */

    /**
     * Every installed program that says it can open this file, best claim first.
     *
     * Asked of the file's type rather than its name, so a program that declared
     * public.text is offered for a .java it never heard of. An exact claim ranks before a
     * claim on a family the type belongs to.
     */
    public static java.util.List<Bundle> applicationsFor(File file) {
        FMString type = typeOf(file);
        java.util.List<Bundle> found = new java.util.ArrayList<>();
        java.util.Map<Bundle, Integer> rank = new java.util.HashMap<>();
        for (Bundle bundle : Bundles.all()) {
            int best = claim(bundle, type);
            if (best < 0) continue;
            found.add(bundle);
            rank.put(bundle, best);
        }
        found.sort((a, b) -> {
            int byRank = Integer.compare(rank.get(a), rank.get(b));
            return byRank != 0 ? byRank
                : a.displayName().toString().compareToIgnoreCase(b.displayName().toString());
        });
        return found;
    }

    /** The one that would open it, or nothing at all when nothing installed can. */
    public static Bundle defaultApplicationFor(File file) {
        java.util.List<Bundle> able = applicationsFor(file);
        return able.isEmpty() ? null : able.get(0);
    }

    /**
     * How strong a claim a program makes on a type, lower being stronger, or -1 for none.
     *
     * An exact match beats a claim on a family the type belongs to, so an editor that names
     * public.plain-text is offered before one that only says public.text. The rank a program
     * declares breaks the tie after that.
     */
    private static int claim(Bundle bundle, FMString type) {
        int best = -1;
        for (Object entry : bundle.info().array(Bundle.DOCUMENT_TYPES)) {
            FMDictionary one = asDictionary(entry);
            if (one == null) continue;
            if (!opens(one.string(Bundle.TYPE_ROLE, FMString.EMPTY))) continue;
            int declared = rankOf(one.string(Bundle.HANDLER_RANK, FMString.EMPTY));
            for (Object named : one.array(Bundle.CONTENT_TYPES)) {
                FMString handles = FMString.describing(named);
                if (handles.sameAs(type)) {
                    best = best < 0 ? declared : Math.min(best, declared);
                } else if (UTTypes.conforms(type, handles)) {
                    int loose = declared + 10;
                    best = best < 0 ? loose : Math.min(best, loose);
                }
            }
        }
        return best;
    }

    /**
     * Whether a declaration is about opening a file at all.
     *
     * Editor, Viewer and Shell open one. A Quick Look generator names the same types and
     * shows them instead, and offering one as a way to open a file would be a program
     * that never appears. A declaration with no role is an editor, which is the old shape.
     */
    private static boolean opens(FMString role) {
        String said = role.toString();
        return said.isEmpty() || said.equalsIgnoreCase("Editor")
            || said.equalsIgnoreCase("Viewer") || said.equalsIgnoreCase("Shell");
    }

    /** Owner, Default, Alternate, None, which is the order Launch Services puts them in. */
    private static int rankOf(FMString declared) {
        String rank = declared.toString();
        if (rank.equalsIgnoreCase("Owner")) return 0;
        if (rank.equalsIgnoreCase("Alternate")) return 2;
        if (rank.equalsIgnoreCase("None")) return 8;
        return 1;
    }

    /** What kind of thing a file is, which is the question everything above asks. */
    public static FMString typeOf(File file) {
        if (file == null) return UTTypes.UNKNOWN;
        if (Bundle.looksLikeBundle(file)) return UTTypes.APPLICATION;
        if (file.isDirectory()) return UTTypes.FOLDER;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return UTTypes.UNKNOWN;
        FMString found = UTTypes.preferredType(FMString.of(name.substring(dot + 1)));
        return found == null ? UTTypes.UNKNOWN : found;
    }

    private static FMDictionary asDictionary(Object value) {
        if (value instanceof FMDictionary already) return already;
        if (!(value instanceof java.util.Map<?, ?> map)) return null;
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<?, ?> one : map.entrySet()) {
            out.put(String.valueOf(one.getKey()), one.getValue());
        }
        return FMDictionary.fromMap(out);
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
