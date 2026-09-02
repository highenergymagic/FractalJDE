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
package org.fractalmicro.a11y;

import org.fractalmicro.os.InterfaceStyle;
import org.fractalmicro.appkit.AppFrame;
import org.fractalmicro.windowserver.ScreenBar;
import org.fractalmicro.win.AppBar;

import javax.swing.*;
import java.awt.Rectangle;
import java.io.PrintStream;

/**
 * The screen: whether a window of this system can be a window of the host system, and
 * whether the shell will reserve an edge for a strip.
 *
 * The edge is checked without taking one. Registering a bar and asking where it may go
 * reserves nothing (only setting its position does that), so this proves the call, the
 * structure and the shell's answer, and then gives the registration back. A check that
 * reserved part of the screen and then crashed would leave the screen smaller until the
 * shell was restarted, which is not a thing a check should be able to do.
 */
public final class ScreenTest {
    private ScreenTest() {}

    public static int count() { return 9; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the screen:");
        out.println("      " + InterfaceStyle.describe());

        /* ------------------------------------------------ the reserved edge */
        Rectangle screen = ScreenBar.screenBounds();
        Rectangle work = ScreenBar.workArea();
        failures += check(out, "the screen has a size and a work area",
            screen.width > 0 && screen.height > 0
            && work.width > 0 && work.height > 0 && work.height <= screen.height);

        // A window has to exist to be registered, and this makes one of its own rather than
        // borrowing whichever window happened to be in front.
        //
        // It used to use the foreground window, which on a machine somebody is using is
        // their window: their terminal, their browser. Registering an app bar against it
        // fails if it is already registered, which made this check fail on a person's own
        // desktop and pass on a build machine where nothing else is running. Worse than the
        // flapping, it was reaching into something that is not this program's to touch.
        //
        // A frame and not a plain window, because a handle is found by its title and only
        // frames and dialogs carry one. The title is unlikely enough that nothing else on
        // the desktop answers to it. The window is never shown; it exists for as long as
        // this question takes.
        java.awt.Frame own = new java.awt.Frame("Fractal edge check " + ProcessHandle.current().pid());
        own.setBounds(0, 0, 1, 1);
        own.addNotify();
        long handle = org.fractalmicro.win.User32.handleOf(own);
        Rectangle wanted = new Rectangle(screen.x, screen.y, screen.width, 22);
        AppBar bar = handle == 0 ? null : AppBar.claim(handle, AppBar.ABE_TOP, wanted);
        boolean registered = bar != null && bar.isRegistered();
        if (bar != null) bar.release();
        if (handle == 0) {
            out.println("      this display gives a window no handle, so the shell is not asked");
        }
        failures += check(out, "the shell answers about reserving an edge",
            registered || handle == 0);
        failures += check(out, "the reservation is given back",
            bar == null || !bar.isRegistered());
        own.dispose();

        /* ------------------------------------------------------ a real window */
        JInternalFrame window = new JInternalFrame("A Document", true, true, true, true);
        window.setSize(420, 300);
        window.getAccessibleContext().setAccessibleName("A Document");
        AppFrame frame = new AppFrame(window);
        frame.pack();
        frame.addNotify();

        failures += check(out, "the window is inside a window of the host system",
            frame.window() == window
            && javax.swing.SwingUtilities.isDescendingFrom(window, frame));
        failures += check(out, "the frame takes the window's title",
            "A Document".equals(frame.getTitle()));
        failures += check(out, "the frame is named for a screen reader",
            "A Document".equals(frame.getAccessibleContext().getAccessibleName()));
        failures += check(out, "the frame has no decorations of the host system's own",
            frame.isUndecorated());

        window.setTitle("A Document — Edited");
        failures += check(out, "the frame follows the window's title",
            "A Document — Edited".equals(frame.getTitle()));

        window.doDefaultCloseAction();
        failures += check(out, "closing the window closes the frame around it",
            !frame.isDisplayable());

        frame.dispose();
        out.println("      " + (failures == 0 ? "the screen holds together"
                                              : failures + " failed"));
        return failures;
    }


    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
