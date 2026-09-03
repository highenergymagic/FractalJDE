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

import javax.swing.*;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saying what a key just did.
 *
 * A screen reader describes whatever has focus. That breaks down for shortcuts: Command W
 * closes a window, and all the reader can then describe is wherever focus landed next, as
 * though the user had gone there on purpose. Nothing says the window closed.
 *
 * So each shortcut announces what it did: "Close Window", "Quit TextEdit", "Empty Trash".
 * The wording is taken from the menu item rather than written separately here, so a command
 * and its shortcut cannot drift apart.
 *
 * This holds keystroke-to-phrase pairs. The menu bar refills them whenever it is rebuilt,
 * which is whenever the front program changes, so Command Q names whatever is actually in
 * front.
 */
public final class Announcer {
    private Announcer() {}

    /** What a keystroke says, and the item it came from when it came from one. */
    private record Phrase(String said, JMenuItem item) {
        /** A shortcut whose menu item is switched off did nothing, so it says nothing. */
        boolean applies() { return item == null || item.isEnabled(); }
    }

    /** The ones read out of the menu bar, replaced whenever the bar changes. */
    private static final Map<KeyStroke, Phrase> MENU = new ConcurrentHashMap<>();
    /** The ones registered by hand, which no rebuild of the bar takes away. */
    private static final Map<KeyStroke, Phrase> EXTRA = new ConcurrentHashMap<>();
    private static volatile boolean listening;
    private static volatile boolean enabled = true;
    private static volatile String lastSaid = "";

    /** Whether shortcuts say what they did. */
    public static boolean enabled() { return enabled; }

    public static void setEnabled(boolean on) { enabled = on; }

    /** The last thing said, so a check can see that the right thing was. */
    public static String lastSaid() { return lastSaid; }

    /* ---------------------------------------------------------- registering */

    /**
     * Registers a phrase for a keystroke by hand. These survive menu bar rebuilds.
     */
    public static void register(KeyStroke stroke, String phrase) {
        if (stroke == null || phrase == null || phrase.isBlank()) return;
        EXTRA.put(stroke, new Phrase(clean(phrase), null));
    }

    /**
     * Reads every shortcut out of a menu bar, submenus included. Called on each rebuild,
     * so what Command Q says follows the program actually in front.
     */
    public static void learn(JMenuBar bar) {
        if (bar == null) return;
        // Drop only what came from the bar; hand-registered phrases survive a rebuild.
        MENU.clear();
        for (int i = 0; i < bar.getMenuCount(); i++) learn(bar.getMenu(i));
        listen();
    }

    private static void learn(JMenu menu) {
        if (menu == null) return;
        for (java.awt.Component child : menu.getMenuComponents()) {
            if (child instanceof JMenu submenu) {
                learn(submenu);
            } else if (child instanceof JMenuItem item) {
                KeyStroke stroke = item.getAccelerator();
                if (stroke != null && item.getText() != null && !item.getText().isBlank()) {
                    MENU.put(stroke, new Phrase(clean(item.getText()), item));
                }
            }
        }
    }

    /**
     * Every shortcut it knows, sorted by what it does.
     *
     * Read out of the menu bar rather than written down, so the list is the bar and cannot
     * go stale. What used to be beside this was forty rows of a keystroke and a sentence,
     * kept true by remembering to.
     */
    public static java.util.List<KeyStroke> known() {
        java.util.List<KeyStroke> out = new java.util.ArrayList<>(MENU.keySet());
        out.addAll(EXTRA.keySet());
        out.sort((a, b) -> {
            String said = phraseFor(a), other = phraseFor(b);
            return said == null || other == null ? 0 : said.compareToIgnoreCase(other);
        });
        return out;
    }

    /** What this keystroke would say, or nothing if it says nothing. */
    public static String phraseFor(KeyStroke stroke) {
        Phrase found = lookup(stroke);
        return found == null ? null : found.said();
    }

    private static Phrase lookup(KeyStroke stroke) {
        if (stroke == null) return null;
        Phrase found = MENU.get(stroke);
        return found != null ? found : EXTRA.get(stroke);
    }

    /** How many shortcuts are known about, for anything that wants to say so. */
    public static int size() { return MENU.size() + EXTRA.size(); }

    public static Map<KeyStroke, String> all() {
        Map<KeyStroke, String> out = new LinkedHashMap<>();
        EXTRA.forEach((stroke, phrase) -> out.put(stroke, phrase.said()));
        MENU.forEach((stroke, phrase) -> out.put(stroke, phrase.said()));
        return out;
    }

    /* ------------------------------------------------------------ listening */

    /**
     * Watches the keyboard and announces a shortcut as it is pressed. It only observes and
     * never consumes the key, so the command still runs normally.
     */
    public static synchronized void listen() {
        if (listening) return;
        listening = true;
        KeyEventDispatcher watcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || !enabled) return false;
            KeyStroke pressed = KeyStroke.getKeyStrokeForEvent(event);
            Phrase phrase = lookup(pressed);
            // A disabled command does nothing, so announcing it would be a lie.
            if (phrase != null && phrase.applies()) announce(phrase.said());
            return false;                 // never consumed: this is a listener, not a binding
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(watcher);
    }

    /** Says one thing, and remembers that it did. */
    public static void announce(String phrase) {
        if (phrase == null || phrase.isBlank()) return;
        lastSaid = clean(phrase);
        Speech.announce(lastSaid);
    }

    /**
     * The wording as it should be heard rather than drawn. A trailing ellipsis means a
     * dialog follows and is worth nothing aloud; everything else already reads well.
     */
    private static String clean(String text) {
        String out = text.replace("…", "").replace("...", "").trim();
        return out.endsWith(":") ? out.substring(0, out.length() - 1) : out;
    }
}
