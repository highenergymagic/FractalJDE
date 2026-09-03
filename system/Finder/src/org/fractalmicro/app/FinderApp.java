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

import org.fractalmicro.appkit.FMApplicationDelegate;
import org.fractalmicro.bundle.LaunchServices;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.fs.FS;
import org.fractalmicro.scripting.FMAppleEvent;
import org.fractalmicro.scripting.FMAppleEventManager;
import org.fractalmicro.ui.Finder;

import java.io.File;

/**
 * The Finder, as the rest of the system sees it.
 *
 * Everything the screen furniture wants of the file manager comes through here, by bundle
 * identifier: the Dock's Trash tile, the desktop opening a folder, Control F5 asking for a
 * fresh look at what is on disk. None of them names a class in this program, which is what
 * lets the program be replaced, or eventually moved into a process of its own, without any
 * of them being rebuilt.
 *
 * Opening it with no files makes a window, the way clicking its Dock tile does.
 */
public final class FinderApp implements FMApplicationDelegate {

    @Override public void open() {
        Finder.newWindow(null);
    }

    /**
     * The parts of the Finder something else can ask for by name.
     *
     * The names are LaunchServices' rather than this file's, because both ends have to
     * agree on them and only one of them can be the place they are written down.
     */
    @Override public void openPart(FMString part) {
        String named = part == null ? "" : part.toString();
        switch (named) {
            // Asked for at login, and it does not open a window. The bar and the icons on
            // the desktop are what this program draws when nothing of its own is in front.
            case LaunchServices.DESKTOP -> {
                org.fractalmicro.ui.FinderMenus.install(
                    org.fractalmicro.windowserver.Desktop.sharedDesktop());
                installScripting();
            }
            case LaunchServices.TRASH -> Finder.openTrash();
            case LaunchServices.EMPTY_TRASH -> Finder.emptyTrash(false);
            case LaunchServices.EMPTY_TRASH_SECURELY -> Finder.emptyTrash(true);
            case LaunchServices.REFRESH -> Finder.refreshAll();
            case LaunchServices.CONNECT_TO_SERVER -> Finder.connectToServer();
            default -> open();
        }
    }

    /**
     * Opened on some files, which is what dragging them onto its icon means, and what
     * asking for a window on a folder means too.
     *
     * A folder gets a window. Anything else is shown where it lives, with itself picked
     * out, because a file manager opening a document would be a file manager deciding it
     * was a text editor.
     */
    @Override public void openURLs(FMArray<FMURL> urls) {
        for (FMURL url : urls) {
            File f = url.asFile();
            if (f == null) continue;
            if (f.isDirectory()) Finder.newWindow(f);
            else Finder.goTo(f.getParentFile());
        }
    }

    /* ------------------------------------------------------------ being told things */

    private static boolean scripting;

    /**
     * Says the Finder answers events, and which ones.
     *
     * It runs inside the window server, so an event for it never leaves this process; the
     * server is told to hand those straight over rather than look for a queue. Done when
     * the Finder takes the desktop, which is the moment it starts being the Finder.
     */
    static synchronized void installScripting() {
        if (scripting) return;
        scripting = true;
        // By both names, because both are used: one program has another's identifier,
        // and somebody writing a script has the name on the screen.
        org.fractalmicro.windowserver.WindowServer server =
            org.fractalmicro.windowserver.WindowServer.sharedServer();
        server.serveLocally(FMString.of(LaunchServices.FILE_BROWSER));
        server.serveLocally(FMString.of("Finder"));

        FMAppleEventManager manager = FMAppleEventManager.sharedManager();
        FMString me = FMString.of(LaunchServices.FILE_BROWSER);
        manager.setEventHandler(me, FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_APPLICATION, event -> onTheScreen(() -> Finder.newWindow(null)));
        manager.setEventHandler(me, FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.REOPEN, event -> onTheScreen(() -> Finder.newWindow(null)));
        manager.setEventHandler(me, FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_DOCUMENTS, event -> open(event));
        // Quitting the Finder is relaunching it, here as on the system this imitates:
        // its windows go and the desktop is drawn again, and it is still running.
        manager.setEventHandler(me, FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.QUIT, event -> onTheScreen(Finder::relaunch));

        // And the standard suite over what the Finder holds, which is not written here:
        // getting and counting are nobody's own idea, so they are written down once.
        org.fractalmicro.scripting.FMScriptCommands.install(
            manager, me, () -> FinderScripting.APPLICATION);
    }

    /** What an odoc event named, opened the way dragging it onto the icon would. */
    private static Object open(FMAppleEvent event) {
        java.util.List<File> files = new java.util.ArrayList<>();
        for (FMString path : pathsIn(event.directObject())) {
            File named = new File(path.toString());
            if (!named.exists()) {
                throw new org.fractalmicro.scripting.FMScriptError(
                    FMString.of("there is nothing at " + path));
            }
            files.add(named);
        }
        if (files.isEmpty()) {
            throw new org.fractalmicro.scripting.FMScriptError(
                FMString.of("nothing was named to open"));
        }
        return onTheScreen(() -> {
            for (File one : files) {
                if (one.isDirectory()) Finder.newWindow(one);
                else Finder.goTo(one.getParentFile());
            }
        });
    }

    /** One path or a list of them, which is what a direct object may be either of. */
    private static java.util.List<FMString> pathsIn(Object directObject) {
        java.util.List<FMString> out = new java.util.ArrayList<>();
        if (directObject instanceof FMArray<?> many) {
            for (Object one : many) out.add(FMString.describing(one));
        } else if (directObject != null) {
            out.add(FMString.describing(directObject));
        }
        return out;
    }

    /**
     * Does it where windows are made, and waits, so the answer means it happened.
     *
     * An event arrives on a service thread and Swing belongs to one thread of its own.
     * Waiting rather than posting is what lets a reply say a window opened rather than
     * that one had been asked for.
     */
    private static Object onTheScreen(Runnable what) {
        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) what.run();
            else javax.swing.SwingUtilities.invokeAndWait(what);
        } catch (Exception wentWrong) {
            throw new org.fractalmicro.scripting.FMScriptError(
                FMString.describing(wentWrong.getMessage()));
        }
        return null;
    }

    /** Text handed over by a service: show the folder it names, if it names one. */
    @Override public void openText(FMString text) {
        File named = new File(text.trimmed().toString());
        if (named.isDirectory()) Finder.newWindow(named);
        else if (named.exists()) Finder.goTo(named.getParentFile());
        else Finder.goTo(FS.home());
    }
}
