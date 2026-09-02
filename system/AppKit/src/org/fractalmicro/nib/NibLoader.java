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
package org.fractalmicro.nib;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.os.Languages;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Strings;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interface files, opened where the program that shows them is running.
 *
 * A program in a process of its own asks the window server to open a window and the server
 * reads the description. The desktop's own parts are not in a process of their own: the
 * Finder, the menu bar and the panels AppKit puts up are all inside the window server
 * already, and asking it over a connection to draw something it is holding in a field
 * would be a message to itself.
 *
 * So this is the other half of the same idea. The description is read from the same file,
 * in the same language, from the same place in the same bundle, and turned into the real
 * controls directly. What differs is only where a command goes when it is chosen: a
 * program in its own process is sent an event, and something in this one is called.
 *
 * A command is matched by the name it sends, not by where it sits. That is what lets the
 * file be rearranged, translated, or have an item added to it without any of the code that
 * answers those commands being touched.
 */
public final class NibLoader {

    /** What was read, and where each thing in it came out. */
    private final Nib nib;
    private final Map<String, JMenuItem> items = new LinkedHashMap<>();

    private NibLoader(Nib nib) { this.nib = nib; }

    /** The description, in the language this account reads. */
    public Nib nib() { return nib; }

    /* ------------------------------------------------------------------ reading */

    /**
     * An interface file out of a bundle, translated.
     *
     * The file is the same in every language and the words beside it are not, which is why
     * there are two of them: a description names its controls, and a table says what each
     * of those names is called here.
     */
    public static NibLoader inBundle(Path bundle, FMString name) throws IOException {
        Path resources = bundle.resolve("Contents/Resources");
        Path file = localized(resources, name + "." + Xib.EXTENSION);
        if (file == null) {
            throw new IOException("no interface file called " + name + " in " + bundle);
        }
        Nib read = Xib.read(FMURL.of(file.toFile()));
        return new NibLoader(Xib.localized(read, wordsFor(resources, name)));
    }

    /**
     * The same, out of a framework rather than a program.
     *
     * The desktop's own windows belong to AppKit, which is a framework and keeps its
     * resources where a framework keeps them.
     */
    public static NibLoader inFramework(FMString framework, FMString name)
            throws IOException {
        Path resources = OSPaths.frameworks()
            .resolve(framework + ".framework/Versions/A/Resources");
        Path file = localized(resources, name + "." + Xib.EXTENSION);
        if (file == null) {
            throw new IOException("no interface file called " + name + " in " + framework);
        }
        Nib read = Xib.read(FMURL.of(file.toFile()));
        return new NibLoader(Xib.localized(read, wordsFor(resources, name)));
    }

    /** A file in the language the account asked for, or the one outside the directories. */
    private static Path localized(Path resources, String file) {
        for (FMString language : Languages.preferred()) {
            Path in = resources.resolve(language + ".lproj").resolve(file);
            if (Files.isReadable(in)) return in;
        }
        Path beside = resources.resolve(file);
        return Files.isReadable(beside) ? beside : null;
    }

    /** The table of words for one description, which is named after it. */
    private static FMDictionary wordsFor(Path resources, FMString name) {
        Path file = localized(resources, name + "." + Strings.EXTENSION);
        if (file == null) return FMDictionary.EMPTY;
        try {
            return Strings.parse(FMString.of(Files.readString(file)));
        } catch (IOException unreadable) {
            return FMDictionary.EMPTY;
        }
    }

    /* ------------------------------------------------------------------- menus */

    /**
     * What is answered when a command is chosen.
     *
     * Given the name the item sends rather than the item, because the code that answers a
     * command has no business knowing which menu it was put in or what it is called in the
     * language somebody is reading.
     */
    public interface Commands {
        /** Does the named command. */
        void perform(FMString action);

        /** Whether a command that shows a tick has one now. False when it does not apply. */
        default boolean isOn(FMString action) { return false; }

        /**
         * Whether the command could be done right now.
         *
         * Asked as a menu opens, of every item in it, which is when NSMenuValidation asks
         * and for the same reason: what can be done depends on what is selected and where
         * the keyboard is, and both of those change constantly. Anything answering no is
         * drawn grey and does nothing when pressed.
         *
         * Yes by default, because a command with no opinion is one that always applies:
         * New Window does not care what is selected. The ones that do care say so, and
         * saying so is the whole difference between a menu and a list of everything the
         * program can ever do.
         */
        default boolean canPerform(FMString action) { return true; }
    }

    /**
     * The menus in this description, built.
     *
     * Every item is connected before it is returned, so nothing that receives these has to
     * walk them looking for the one it wanted to attach something to.
     */
    public java.util.List<JMenu> menus(Commands commands) {
        java.util.List<JMenu> out = new java.util.ArrayList<>();
        for (Nib.Menu menu : nib.menus()) {
            // No accessible name is set on a menu or an item: Swing already answers with
            // the text, and setting one freezes it. A command that renames itself as the
            // selection changes would go on being announced by the name it started with,
            // which is the one case where being told the name matters most.
            JMenu made = new JMenu(menu.title().toString());
            fill(made, menu.items(), commands);
            validateAsItOpens(made, commands);
            out.add(made);
        }
        return out;
    }

    /**
     * Asks the program about every item in a menu, as the menu opens.
     *
     * Then, rather than when the menu was built, because what can be done changes with the
     * selection and the menu was built once at start-up. This is what NSMenuValidation is
     * and when it runs; the alternative is every command in the program remembering to
     * enable and disable its own menu item from everywhere that changes anything, which is
     * how menus come to lie.
     *
     * A tick is asked for at the same moment and for the same reason.
     */
    private void validateAsItOpens(JMenu menu, Commands commands) {
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                validate(menu, commands);
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
    }

    private void validate(javax.swing.MenuElement holder, Commands commands) {
        for (java.awt.Component child : componentsOf(holder)) {
            if (child instanceof JMenu under) {
                validate(under, commands);
                // A submenu with nothing in it that can be done is one nobody should be
                // sent into. It is the only case where a menu itself is greyed out.
                under.setEnabled(anyEnabled(under));
                continue;
            }
            if (!(child instanceof JMenuItem item)) continue;
            Object named = item.getClientProperty(ACTION);
            if (!(named instanceof String action) || action.isEmpty()) continue;
            // An item the description switched off stays off. Validation says what can be
            // done now; the description says what this program offers at all, and a
            // command that is not offered cannot become available by being asked about.
            boolean described = !Boolean.FALSE.equals(item.getClientProperty(DESCRIBED));
            item.setEnabled(described && commands.canPerform(FMString.of(action)));
            if (item instanceof JCheckBoxMenuItem box) {
                box.setSelected(commands.isOn(FMString.of(action)));
            }
        }
    }

    private static java.awt.Component[] componentsOf(javax.swing.MenuElement holder) {
        if (holder instanceof JMenu menu) return menu.getMenuComponents();
        return new java.awt.Component[0];
    }

    private static boolean anyEnabled(JMenu menu) {
        for (java.awt.Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item && item.isEnabled()) return true;
        }
        return false;
    }

    /** What an item remembers about itself, so validating one is not a search for it. */
    private static final String ACTION = "org.fractalmicro.menuAction";
    private static final String DESCRIBED = "org.fractalmicro.menuEnabledInDescription";

    private void fill(JMenu into, FMArray<Nib.MenuItem> items, Commands commands) {
        for (Nib.MenuItem item : items) {
            if (item.separator()) {
                into.addSeparator();
                continue;
            }
            if (item.hasSubmenu()) {
                JMenu under = new JMenu(item.title().toString());
                fill(under, item.submenu(), commands);
                into.add(under);
                continue;
            }
            into.add(made(item, commands));
        }
    }

    private JMenuItem made(Nib.MenuItem item, Commands commands) {
        FMString action = item.action();
        ActionListener listener = e -> commands.perform(action);

        JMenuItem made = item.checkable()
            ? new JCheckBoxMenuItem(item.title().toString(), commands.isOn(action))
            : new JMenuItem(item.title().toString());
        made.addActionListener(listener);
        made.setEnabled(item.enabled());
        made.putClientProperty(ACTION, action.toString());
        made.putClientProperty(DESCRIBED, item.enabled());

        KeyStroke key = keyFor(item);
        if (key != null) made.setAccelerator(key);
        if (!action.isEmpty()) this.items.put(action.toString(), made);
        return made;
    }

    /**
     * The keys that do a command without opening the menu at all.
     *
     * The description says a character and which modifiers are held, the way a menu has
     * always said it. Turning that into what the runtime wants is this file's job and
     * nobody else's, which is the point of the description not naming a key code.
     */
    private static KeyStroke keyFor(Nib.MenuItem item) {
        String key = item.key().toString();
        if (key.isEmpty()) return null;
        // Which host key stands for Command is the menu bar's to decide, and it has
        // decided. Working it out again here would be a second answer to a question that
        // already has one, and the two would agree until one of them was changed.
        int modifiers = 0;
        for (FMString one : item.modifiers()) {
            switch (one.toString()) {
                case "command" -> modifiers |= org.fractalmicro.windowserver.MainMenu.CMD;
                case "shift" -> modifiers |= org.fractalmicro.windowserver.MainMenu.SHIFT;
                case "option" -> modifiers |= org.fractalmicro.windowserver.MainMenu.OPT;
                case "control" -> modifiers |= org.fractalmicro.windowserver.MainMenu.CTRL;
                default -> { }
            }
        }
        Integer code = codeFor(key);
        return code == null ? null : KeyStroke.getKeyStroke(code, modifiers);
    }

    /**
     * A key equivalent as the key that produces it.
     *
     * Usually one character, the way a menu has always written it. The keys that have no
     * character are written by name instead: XML cannot carry a backspace or an escape at
     * all, so a description spelling them literally would be one no parser would read.
     * Cocoa puts them in a private area of Unicode for the same reason and it comes to the
     * same thing, except that a name can be typed by whoever is editing the file.
     */
    private static Integer codeFor(String key) {
        switch (key) {
            case "delete", "backspace": return KeyEvent.VK_BACK_SPACE;
            case "forwarddelete": return KeyEvent.VK_DELETE;
            case "return", "enter": return KeyEvent.VK_ENTER;
            case "escape": return KeyEvent.VK_ESCAPE;
            case "space": return KeyEvent.VK_SPACE;
            case "tab": return KeyEvent.VK_TAB;
            case "up": return KeyEvent.VK_UP;
            case "down": return KeyEvent.VK_DOWN;
            case "left": return KeyEvent.VK_LEFT;
            case "right": return KeyEvent.VK_RIGHT;
            default: break;
        }
        if (key.length() != 1) return null;
        char c = Character.toUpperCase(key.charAt(0));
        int code = KeyEvent.getExtendedKeyCodeForChar(c);
        return code == KeyEvent.VK_UNDEFINED ? null : code;
    }

    /**
     * One item that was built, by the command it sends.
     *
     * What asks is the part of a program that has to change an item after the fact: tick
     * it, grey it out, rename it as the selection changes. Everything else should let the
     * description say how it looks and never hold on to one.
     */
    public JMenuItem item(FMString action) { return items.get(action.toString()); }

    /** Whether a command in this description exists at all, for a check to ask. */
    public boolean has(FMString action) { return items.containsKey(action.toString()); }

    /** Every command the description offers, in the order it offers them. */
    public FMArray<FMString> actions() {
        org.fractalmicro.foundation.FMMutableArray<FMString> out =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (String one : items.keySet()) out.add(FMString.of(one));
        return out.asArray();
    }
}
