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
package org.fractalmicro.terminal;

import org.fractalmicro.foundation.FMFileManager;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

/**
 * Terminal: a command line, opened where you are.
 *
 * A process of its own, and one that puts up no window of its own at all: what it opens
 * belongs to the host system, and this asks for it and is done. That is a whole kind of
 * program, and there was never a reason for it to be living inside the desktop.
 *
 * Where it opens is given to it. It used to reach into the Finder and ask which window was
 * in front, which only worked because both were the same process. Now the folder arrives
 * as an argument, which is what opening a program on a folder has always meant, and the
 * Finder passes the one you are looking at. With nothing given, it opens at home.
 */
public final class Terminal implements org.fractalmicro.appkit.FMApplicationDelegate {
    private Terminal() {}

    public static final FMString NAME = FMString.of("Terminal");

    /** Opened with nothing, which means a command line at home. */
    @Override public void open() { openAt(FMFileManager.defaultManager().home()); }

    /** Opened on something, which means a command line where that something is. */
    @Override public void openURLs(org.fractalmicro.foundation.FMArray<FMURL> urls) {
        openAt(urls == null || urls.count() == 0
               ? FMFileManager.defaultManager().home() : urls.at(0));
    }

    /**
     * Opens a command line at a folder, or at the folder holding a file.
     *
     * Being given a file rather than a folder is the ordinary case: somebody drops a
     * document on the icon meaning "here", and here is where the document is.
     */
    public static void openAt(FMURL where) {
        FMURL folder = where.isDirectory() ? where : where.deletingLastComponent();
        if (!folder.isDirectory()) folder = FMFileManager.defaultManager().home();
        org.fractalmicro.appkit.FMWorkspace.sharedWorkspace().openTerminal(folder);
    }
}
