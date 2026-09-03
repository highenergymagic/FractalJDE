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
package org.fractalmicro.app;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.scripting.FMScriptObjectSpecifier;
import org.fractalmicro.scripting.FMScriptable;
import org.fractalmicro.ui.FinderWindow;
import org.fractalmicro.windowserver.Desktop;

import javax.swing.JInternalFrame;
import java.io.File;
import java.util.List;

/**
 * What a script can see when it looks at the Finder: windows, disks and what is in them.
 *
 * Nothing here is held on to. Each question walks the same objects the screen is drawn
 * from, so a script asking what the front window is showing gets the answer as it is now
 * rather than as it was when something registered.
 */
public final class FinderScripting implements FMScriptable {

    /** The Finder itself, which is what a specifier with no container resolves to. */
    static final FinderScripting APPLICATION = new FinderScripting();

    private FinderScripting() {}

    @Override public FMString scriptClass() { return FMScriptObjectSpecifier.APPLICATION; }

    @Override public FMString scriptName() { return FMString.of("Finder"); }

    @Override public Object property(FMString code) {
        if (FMScriptObjectSpecifier.VERSION.sameAs(code)) {
            return FMString.of(org.fractalmicro.os.SystemProfile.version());
        }
        return FMScriptable.super.property(code);
    }

    @Override public FMArray<FMScriptable> elements(FMString wantClass) {
        FMMutableArray<FMScriptable> out = FMMutableArray.empty();
        if (isKind(FMScriptObjectSpecifier.FILE_VIEWER, wantClass)) {
            Desktop desktop = Desktop.sharedDesktop();
            if (desktop == null) return out.asArray();
            for (JInternalFrame frame : desktop.windows()) {
                if (frame instanceof FinderWindow window) out.add(new OneWindow(window));
            }
            return out.asArray();
        }
        if (FMScriptObjectSpecifier.DISK.sameAs(wantClass)) {
            for (Node volume : Volumes.all()) out.add(new OneItem(volume));
            return out.asArray();
        }
        return out.asArray();
    }

    /* ------------------------------------------------------------------ a window */

    /** One Finder window: what it is called, where it sits, and what it is showing. */
    private static final class OneWindow implements FMScriptable {
        private final FinderWindow window;

        OneWindow(FinderWindow window) { this.window = window; }

        // A window on a folder is a file viewer, which is a window and something more.
        // Asking for windows finds one; asking for Finder windows finds only these.
        @Override public FMString scriptClass() {
            return FMScriptObjectSpecifier.FILE_VIEWER;
        }

        @Override public FMString scriptName() {
            return FMString.describing(window.getTitle());
        }

        @Override public Object property(FMString code) {
            if (FMScriptObjectSpecifier.TARGET.sameAs(code)) {
                File folder = window.currentFolder();
                return folder == null ? FMString.EMPTY
                                      : FMString.of(folder.getAbsolutePath());
            }
            if (FMScriptObjectSpecifier.INDEX.sameAs(code)) {
                return FMNumber.of(positionOf(window));
            }
            return FMScriptable.super.property(code);
        }

        @Override public boolean setProperty(FMString code, Object value) {
            if (!FMScriptObjectSpecifier.TARGET.sameAs(code)) return false;
            File wanted = new File(FMString.describing(value).toString());
            if (!wanted.isDirectory()) return false;
            onTheScreen(() -> window.navigateTo(wanted));
            return true;
        }

        @Override public FMArray<FMScriptable> elements(FMString wantClass) {
            FMMutableArray<FMScriptable> out = FMMutableArray.empty();
            File folder = window.currentFolder();
            if (folder == null || !wanted(wantClass)) return out.asArray();
            for (Node one : FS.list(folder)) {
                if (matches(one, wantClass)) out.add(new OneItem(one));
            }
            return out.asArray();
        }

        @Override public boolean delete() {
            onTheScreen(window::doDefaultCloseAction);
            return true;
        }
    }

    /* -------------------------------------------------------------------- an item */

    /** One thing on a disk, which is a file, a folder or a disk itself. */
    private static final class OneItem implements FMScriptable {
        private final Node node;

        OneItem(Node node) { this.node = node; }

        @Override public FMString scriptClass() {
            if (node.isVolume()) return FMScriptObjectSpecifier.DISK;
            return node.file != null && node.file.isDirectory()
                ? FMScriptObjectSpecifier.FOLDER : FMScriptObjectSpecifier.FILE;
        }

        @Override public FMString scriptName() { return FMString.describing(node.name); }

        @Override public Object property(FMString code) {
            if (FMScriptObjectSpecifier.URL.sameAs(code)) {
                return node.file == null ? FMString.EMPTY
                    : org.fractalmicro.foundation.FMURL.of(node.file).absoluteString();
            }
            if (FMScriptObjectSpecifier.SIZE.sameAs(code)) {
                return FMNumber.of(Math.max(0, node.size));
            }
            if (FMScriptObjectSpecifier.KIND.sameAs(code)) {
                return FMString.describing(org.fractalmicro.fs.Kinds.display(node));
            }
            return FMScriptable.super.property(code);
        }

        @Override public FMArray<FMScriptable> elements(FMString wantClass) {
            FMMutableArray<FMScriptable> out = FMMutableArray.empty();
            if (node.file == null || !node.file.isDirectory() || !wanted(wantClass)) {
                return out.asArray();
            }
            for (Node one : FS.list(node.file)) {
                if (matches(one, wantClass)) out.add(new OneItem(one));
            }
            return out.asArray();
        }

        @Override public boolean delete() {
            if (node.file == null) return false;
            onTheScreen(() -> org.fractalmicro.ui.Finder.moveToTrash(List.of(node)));
            return true;
        }
    }

    /* ------------------------------------------------------------------- pieces */

    /**
     * Whether one thing answers to the class being asked for.
     *
     * A file viewer is a window, so asking for windows finds it. That is what inherits
     * means in a terminology, and it has to hold here or "window 1" finds nothing.
     */
    private static boolean isKind(FMString scriptClass, FMString wantClass) {
        if (scriptClass.sameAs(wantClass)) return true;
        return FMScriptObjectSpecifier.FILE_VIEWER.sameAs(scriptClass)
            && FMScriptObjectSpecifier.WINDOW.sameAs(wantClass);
    }

    /** Whether that class is one of the kinds a folder holds. */
    private static boolean wanted(FMString wantClass) {
        return FMScriptObjectSpecifier.ITEM.sameAs(wantClass)
            || FMScriptObjectSpecifier.FILE.sameAs(wantClass)
            || FMScriptObjectSpecifier.FOLDER.sameAs(wantClass);
    }

    /**
     * Whether one thing is of the kind being asked for.
     *
     * Asking for items is asking for everything, which is what item means: a file and a
     * folder are both one, and a script that wanted only folders said folders.
     */
    private static boolean matches(Node node, FMString wantClass) {
        if (FMScriptObjectSpecifier.ITEM.sameAs(wantClass)) return true;
        boolean folder = node.file != null && node.file.isDirectory();
        return FMScriptObjectSpecifier.FOLDER.sameAs(wantClass) == folder;
    }

    private static int positionOf(FinderWindow window) {
        Desktop desktop = Desktop.sharedDesktop();
        if (desktop == null) return 0;
        int at = 0;
        for (JInternalFrame frame : desktop.windows()) {
            if (frame instanceof FinderWindow) {
                at++;
                if (frame == window) return at;
            }
        }
        return 0;
    }

    /** Anything that changes the screen happens where the screen is drawn, and waits. */
    private static void onTheScreen(Runnable what) {
        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) what.run();
            else javax.swing.SwingUtilities.invokeAndWait(what);
        } catch (Exception wentWrong) {
            throw new org.fractalmicro.scripting.FMScriptError(
                FMString.describing(wentWrong.getMessage()));
        }
    }
}
