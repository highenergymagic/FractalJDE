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
package org.fractalmicro.windowserver;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.bundle.LaunchServices;


import org.fractalmicro.fs.FS;
import org.fractalmicro.win.HotKeys;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * The shortcuts that belong to the desktop rather than to any one menu.
 *
 * There are two kinds. A few have to work wherever the keyboard is, even inside another
 * program, and those are claimed from Windows itself with RegisterHotKey. The rest are
 * ordinary key bindings that fire while this desktop is in front; they also cover the
 * Windows key, which AWT does not report as a modifier, so its state is tracked by hand.
 */
public final class Shortcuts {
    private Shortcuts() {}

    private static boolean windowsKeyDown;
    private static long lastMenuBarJump;

    /** The bindings that only apply while this desktop is in front. */
    public static void install(Desktop desktop) {
        installLocal(desktop);
    }

    /**
     * Claims the system wide shortcuts. Kept separate from the rest because it takes
     * combinations away from every other program, which a checking run has no business
     * doing for longer than it takes to prove it works.
     */
    public static void installGlobalShortcuts(Desktop desktop) {
        installGlobal(desktop);
    }

    /**
     * The four that have to work from inside other programs. Windows claims them for
     * this program, so they never reach whatever is in front. Alt Space is the Windows
     * system menu ordinarily; while this desktop runs it opens Spotlight instead.
     */
    private static void installGlobal(Desktop desktop) {
        int alt = HotKeys.MOD_ALT;
        int win = HotKeys.MOD_WIN;

        int ctrl = HotKeys.MOD_CONTROL;

        // The system wide ones are not menu items, so they are registered by hand.
        announceGlobal(alt, KeyEvent.VK_SPACE, word(FMString.of("extra.spotlight")));
        announceGlobal(ctrl, KeyEvent.VK_F2, word(FMString.of("desktop.menuBar")));
        announceGlobal(ctrl, KeyEvent.VK_F3, word(FMString.of("desktop.dock")));
        announceGlobal(ctrl, KeyEvent.VK_F5, word(FMString.of("finder.toolbar")));

        register(desktop, alt, KeyEvent.VK_SPACE, word(FMString.of("extra.spotlight")), Spotlight::open);
        registerWithFallback(desktop, alt | win, KeyEvent.VK_M, ctrl, KeyEvent.VK_F2,
                             word(FMString.of("desktop.menuBar")), () -> focusMenuBar(desktop));
        registerWithFallback(desktop, alt | win, KeyEvent.VK_D, ctrl, KeyEvent.VK_F3,
                             word(FMString.of("desktop.dock")), () -> focusDock(desktop));
        // The toolbar of the front window, as Control F5 does on the system
        // this imitates when full keyboard access is on.
        registerWithFallback(desktop, alt | win, KeyEvent.VK_T, ctrl, KeyEvent.VK_F5,
                             word(FMString.of("finder.toolbar")), () -> focusToolbar(desktop));
        register(desktop, alt | win, KeyEvent.VK_ESCAPE, word(FMString.of("forceQuit.button")),
                 ForceQuitWindow::open);

        Runtime.getRuntime().addShutdownHook(
            new Thread(HotKeys::releaseAll, "fractal-hotkeys-release"));
    }

    private static void register(Desktop desktop, int modifiers, int keyCode,
                                 String name, Runnable action) {
        HotKeys.register(modifiers, keyCode, name, forward(desktop, action));
    }

    private static void registerWithFallback(Desktop desktop, int modifiers, int keyCode,
                                             int fallbackModifiers, int fallbackKeyCode,
                                             String name, Runnable action) {
        HotKeys.registerWithFallback(modifiers, keyCode, fallbackModifiers, fallbackKeyCode,
                                     name, forward(desktop, action));
    }

    private static Runnable forward(Desktop desktop, Runnable action) {
        return () -> {
            // The keyboard may be inside another program; come forward first.
            desktop.bringToFront();
            action.run();
        };
    }

    /** The same shortcuts, and a few more, for when this desktop already has the keyboard. */
    private static void installLocal(Desktop desktop) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_WINDOWS) {
                windowsKeyDown = true;
                return false;
            }
            if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_WINDOWS) {
                windowsKeyDown = false;
                return false;
            }
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            boolean alt = e.isAltDown();
            boolean option = windowsKeyDown || e.isMetaDown();

            if (alt && e.getKeyCode() == KeyEvent.VK_SPACE) {
                Spotlight.open();
                return true;
            }
            if ((alt && option && e.getKeyCode() == KeyEvent.VK_M)
                || (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F2)) {
                focusMenuBar(desktop);
                return true;
            }
            if ((alt && option && e.getKeyCode() == KeyEvent.VK_D)
                || (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F3)) {
                focusDock(desktop);
                return true;
            }
            if (alt && option && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                ForceQuitWindow.open();
                return true;
            }
            if (alt && option && e.getKeyCode() == KeyEvent.VK_L) {
                LaunchServices.openFolder(FS.downloads());
                return true;
            }
            if (e.getKeyCode() == KeyEvent.VK_F5) {
                LaunchServices.tellFileBrowser(LaunchServices.REFRESH);
                return true;
            }
            return false;
        });
    }

    /**
     * First press opens the leftmost menu; a second press within a few seconds, or a
     * press while the menu bar already has the keyboard, moves to the status menus.
     */
    private static void focusMenuBar(Desktop desktop) {
        MainMenu bar = desktop.mainMenu();
        MenuSelectionManager msm = MenuSelectionManager.defaultManager();
        boolean alreadyThere = msm.getSelectedPath().length > 0;
        long now = System.currentTimeMillis();
        boolean second = alreadyThere || now - lastMenuBarJump < 3000;
        lastMenuBarJump = now;

        int index = second ? bar.firstStatusIndex() : 0;
        JMenu menu = firstMenuFrom(bar, index);
        if (menu == null) menu = firstMenuFrom(bar, 0);
        if (menu == null) return;

        // The bar may be in a window of its own, in which case it has to come forward
        // and take the keyboard before a menu in it can be opened.
        desktop.focusMenuBarWindow();
        JMenu target = menu;
        SwingUtilities.invokeLater(() -> {
            msm.setSelectedPath(new MenuElement[]{bar, target, target.getPopupMenu()});
            desktop.setStatus(target.getText());
        });
    }

    private static JMenu firstMenuFrom(MainMenu bar, int index) {
        for (int i = index; i < bar.getMenuCount(); i++) {
            JMenu m = bar.getMenu(i);
            if (m != null) return m;
        }
        return null;
    }

    /** Teaches the announcer one shortcut that is not in any menu. */
    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    private static void announceGlobal(int modifiers, int keyCode, String phrase) {
        int swing = 0;
        if ((modifiers & HotKeys.MOD_ALT) != 0) swing |= java.awt.event.InputEvent.ALT_DOWN_MASK;
        if ((modifiers & HotKeys.MOD_CONTROL) != 0) {
            swing |= java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
        if ((modifiers & HotKeys.MOD_SHIFT) != 0) {
            swing |= java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        }
        org.fractalmicro.a11y.Announcer.register(KeyStroke.getKeyStroke(keyCode, swing), phrase);
    }

    /**
     * Puts the keyboard on the toolbar of the front window. Tab reaches it too, but this is
     * the way it is reached on the system this imitates, and it goes straight there.
     */
    private static void focusToolbar(Desktop desktop) {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        // Whichever window is in front, if it is one that says where the keyboard goes.
        // Which program's window it is does not come into it: a toolbar is a toolbar.
        javax.swing.JInternalFrame front = desktop.activeWindow();
        if (front == null) {
            Desktop.beep();
            return;
        }
        boolean landed = front instanceof org.fractalmicro.appkit.KeyWindow window
                      && window.focusToolbar();
        if (!landed) Desktop.beep();
    }

    private static void focusDock(Desktop desktop) {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        // The Dock may be a window of its own, which has to come forward before the
        // keyboard can be put into it.
        desktop.focusDockWindow();
    }
}
