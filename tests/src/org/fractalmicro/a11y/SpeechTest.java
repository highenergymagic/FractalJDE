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

import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.MainMenu;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.PrintStream;

/**
 * What a shortcut says when it is pressed.
 *
 * A screen reader reads what has focus, and a shortcut usually changes something that has
 * no focus at all: a window closes, a program quits, the Trash empties. Without something
 * said out loud, a person who cannot see the screen has to work out what happened from
 * whatever the keyboard landed on afterwards.
 *
 * These checks do not make a sound. What they check is that the right words are attached to
 * the right keys, that the words follow the program in front, and that a shortcut which
 * would do nothing says nothing.
 */
public final class SpeechTest {
    private SpeechTest() {}

    public static int count() { return 9; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("what the keys say:");
        out.println("      " + Speech.describe());

        failures += check(out, "the screen reader library is bundled, not looked for",
            Speech.loaded() && Speech.libraryPath() != null
            && Speech.libraryPath().toString().contains("nvdaControllerClient"));

        MainMenu bar = desktop.mainMenu();
        Announcer.learn(bar);
        out.println("      " + Announcer.size() + " shortcuts know what they do");
        failures += check(out, "the shortcuts are read out of the menu bar itself",
            Announcer.size() > 20);

        KeyStroke closeWindow = KeyStroke.getKeyStroke(KeyEvent.VK_W, MainMenu.CMD);
        failures += check(out, "Command W says that it closes a window",
            "Close Window".equals(Announcer.phraseFor(closeWindow)));

        KeyStroke getInfo = KeyStroke.getKeyStroke(KeyEvent.VK_I, MainMenu.CMD);
        failures += check(out, "and the words are the menu's own words",
            "Get Info".equals(Announcer.phraseFor(getInfo)));

        /* ------------------------------------- the words follow what is in front */
        KeyStroke quit = KeyStroke.getKeyStroke(KeyEvent.VK_Q, MainMenu.CMD);
        String inFinder = Announcer.phraseFor(quit);
        // A program in front, which is now always a program in another process. What is
        // being checked is what the words say, so any program with a window will do.
        org.fractalmicro.windowserver.WindowServer.sharedServer().start();
        String inTextEdit;
        try (org.fractalmicro.appkit.FMApplication app = org.fractalmicro.appkit.FMApplication.named(
                 org.fractalmicro.foundation.FMString.of("TextEdit"))) {
            app.showWindow(new org.fractalmicro.nib.Nib.Builder()
                .title(org.fractalmicro.foundation.FMString.of("A Document"))
                .size(240, 160).resizable(true)
                .add(org.fractalmicro.nib.Nib.ControlClass.FMLabel,
                     org.fractalmicro.foundation.FMString.of("body"),
                     org.fractalmicro.foundation.FMString.of("Document"),
                     org.fractalmicro.foundation.FMString.EMPTY, 8, 8, 200, 22)
                .build());
            drain();
            inTextEdit = Announcer.phraseFor(quit);
            app.hideWindow();
            drain();
        }
        out.println("      Command Q in Finder says " + inFinder
                    + ", and with TextEdit in front says " + inTextEdit);
        failures += check(out, "Command Q names whichever program is in front",
            "Quit TextEdit".equals(inTextEdit));
        failures += check(out, "and says nothing in Finder, which has nothing to quit",
            inFinder == null);

        /* ------------------------------------------ what a rebuild must not lose */
        KeyStroke ours = KeyStroke.getKeyStroke(KeyEvent.VK_F13, 0);
        Announcer.register(ours, "Something Of Our Own");
        Announcer.learn(bar);
        failures += check(out, "a shortcut registered by hand survives the bar being rebuilt",
            "Something Of Our Own".equals(Announcer.phraseFor(ours)));

        /* ----------------------------------------------------------- the wording */
        JMenuItem item = new JMenuItem("Connect to Server…");
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F14, 0));
        JMenu menu = new JMenu("Checking");
        menu.add(item);
        JMenuBar small = new JMenuBar();
        small.add(menu);
        Announcer.learn(small);
        failures += check(out, "the ellipsis of a menu item is not read out",
            "Connect to Server".equals(
                Announcer.phraseFor(KeyStroke.getKeyStroke(KeyEvent.VK_F14, 0))));

        // Put the real bar back, since the checks after this one use it.
        Announcer.learn(bar);
        failures += check(out, "and the real menu bar is back afterwards",
            "Close Window".equals(Announcer.phraseFor(closeWindow)));

        out.println("      " + (failures == 0 ? "the keys say what they do"
                                              : failures + " failed"));
        return failures;
    }

    private static void drain() {
        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(() -> { });
            }
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
