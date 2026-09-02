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
            case LaunchServices.DESKTOP ->
                org.fractalmicro.ui.FinderMenus.install(
                    org.fractalmicro.windowserver.Desktop.get());
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

    /** Text handed over by a service: show the folder it names, if it names one. */
    @Override public void openText(FMString text) {
        File named = new File(text.trimmed().toString());
        if (named.isDirectory()) Finder.newWindow(named);
        else if (named.exists()) Finder.goTo(named.getParentFile());
        else Finder.goTo(FS.home());
    }
}
