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
import org.fractalmicro.foundation.FMData;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.plist.Plist;

import java.io.IOException;

/**
 * An interface description: a window and what is in it, written down rather than built.
 *
 * A program that draws its own window has to be where the drawing is. A program that
 * describes its window can be anywhere, and something else can do the drawing, which is
 * the whole reason this exists. The description goes across in one message; after that only
 * values and events do.
 *
 * The file is a property list, like everything else here, and says only what a window is:
 *
 *   Window        title, size, and whether it can be resized
 *   Controls      an ordered list, each with a class, a name, a place and a size
 *
 * Every control carries an identifier the program uses to refer to it afterwards, and a
 * name. A control with no name is not a control anyone can use: it cannot be described,
 * found, or told apart from the one next to it. A description that leaves one out is
 * refused.
 *
 * Everything in it is this system's own: the names are FMString, the lists are FMArray, and
 * a description read from a file is an FMDictionary. A program describing a window never
 * has to reach for a type belonging to the runtime underneath.
 */
public final class Nib {

    public static final FMString WINDOW = FMString.of("Window");
    public static final FMString CONTROLS = FMString.of("Controls");
    public static final FMString TITLE = FMString.of("Title");
    public static final FMString WIDTH = FMString.of("Width");
    public static final FMString HEIGHT = FMString.of("Height");
    public static final FMString RESIZABLE = FMString.of("Resizable");

    public static final FMString CLASS = FMString.of("Class");
    public static final FMString IDENTIFIER = FMString.of("Identifier");
    public static final FMString NAME = FMString.of("AccessibleName");
    public static final FMString DESCRIPTION = FMString.of("AccessibleDescription");
    public static final FMString TEXT = FMString.of("Text");
    public static final FMString ACTION = FMString.of("Action");
    public static final FMString X = FMString.of("X");
    public static final FMString Y = FMString.of("Y");
    public static final FMString VALUE = FMString.of("Value");
    public static final FMString CHOICES = FMString.of("Choices");
    public static final FMString DEFAULT_BUTTON = FMString.of("DefaultButton");

    /**
     * The two ends of a control that holds a number between them.
     *
     * A slider is the only control here that has them, and without them every slider in
     * the system ran from nothing to a hundred whatever it was for. What it is set to only
     * means something against what it can be set to, so the ends belong in the description
     * beside the value and not in the head of whoever wrote the program.
     */
    public static final FMString FROM = FMString.of("From");
    public static final FMString TO = FMString.of("To");
    /** Which control this one sits inside, when it is not the window itself. */
    public static final FMString IN = FMString.of("In");

    public static final FMString MENUS = FMString.of("Menus");
    public static final FMString ITEMS = FMString.of("Items");
    public static final FMString KEY = FMString.of("Key");
    public static final FMString MODIFIERS = FMString.of("Modifiers");
    public static final FMString SEPARATOR = FMString.of("Separator");
    public static final FMString ENABLED = FMString.of("Enabled");
    public static final FMString CHECKABLE = FMString.of("Checkable");
    public static final FMString CHECKED = FMString.of("Checked");

    public enum ControlClass {
        FMButton("a button"),
        FMLabel("a piece of text that is not edited"),
        FMTextField("a line of text to type in"),
        FMTextView("a box of text to type in"),
        FMRichText("a box of text with fonts and styles in it"),
        FMCheckBox("a switch"),
        FMPopUpButton("a choice from a list"),
        FMSlider("a value between two ends"),
        FMProgressIndicator("how far along something is"),
        FMTableView("rows of things"),
        /**
         * A folder, shown as icons, as a list, or as columns.
         *
         * The first control here that is a view of something the program does not send.
         * A description says which folder and how to show it; the folder itself is read on
         * this side, where the icons, the kinds and the dates already are. Sending a
         * listing across instead would mean sending a picture for every file in it, and a
         * program would be maintaining a copy of the disk to draw a window with.
         */
        FMBrowser("a folder, shown as icons, a list or columns"),
        /**
         * Two things side by side, with a divider somebody can move.
         *
         * The first control here that holds others. What is in it is said by the controls
         * themselves: each names the one it is inside, which keeps the description a list
         * rather than a tree and means nothing that reads one has to walk it.
         *
         * Its first child's width is where the divider starts, because a description that
         * says how wide the sidebar is has already said where the divider goes and saying
         * it twice is two chances to disagree.
         */
        FMSplitView("two things side by side, with a divider between them"),
        /**
         * The row of controls along the top of a window.
         *
         * A separator inside one is flexible space, which is what NSToolbarFlexibleSpaceItem
         * is: everything before it is pushed left and everything after it right. That is how
         * a search field ends up at the far end of a toolbar without anybody measuring.
         */
        FMToolbar("the row of controls along the top of a window"),
        FMSeparator("a line between groups");

        private final FMString what;

        ControlClass(String what) { this.what = FMString.of(what); }

        /** What this kind of control is, in words a person could be read. */
        public FMString what() { return what; }

        public FMString className() { return FMString.of(name()); }

        public static ControlClass of(FMString name) {
            for (ControlClass one : values()) {
                if (name.sameAs(FMString.of(one.name()))) return one;
            }
            return null;
        }
    }

    /**
     * One command in a menu: what it is called, what it sends back, and the keys that do it
     * without opening the menu at all.
     *
     * A separator is an item with nothing in it but a line, which is how a menu is written
     * down rather than a different kind of thing.
     */
    public record MenuItem(FMString title, FMString action, FMString key,
                           FMArray<FMString> modifiers, boolean separator, boolean enabled,
                           boolean checkable, boolean checked, FMArray<MenuItem> submenu) {

        /** The line between two groups of commands. */
        public static MenuItem line() {
            return new MenuItem(FMString.EMPTY, FMString.EMPTY, FMString.EMPTY,
                                FMArray.empty(), true, true, false, false, FMArray.empty());
        }

        public static MenuItem of(FMString title, FMString action, FMString key,
                                  FMString... modifiers) {
            FMMutableArray<FMString> keys = FMMutableArray.empty();
            for (FMString one : modifiers) keys.add(one);
            return new MenuItem(title, action, key, keys.asArray(), false, true,
                                false, false, FMArray.empty());
        }

        /**
         * A command that is either on or off.
         *
         * A menu is where most of a program's settings are turned on, and an item that
         * shows a tick is a different thing from one that does something. Written down as
         * a property of the item rather than as a second kind, because everything else
         * about it is the same.
         */
        public static MenuItem toggle(FMString title, FMString action, boolean on,
                                      FMString key, FMString... modifiers) {
            FMMutableArray<FMString> keys = FMMutableArray.empty();
            for (FMString one : modifiers) keys.add(one);
            return new MenuItem(title, action, key, keys.asArray(), false, true,
                                true, on, FMArray.empty());
        }

        /**
         * A command that opens onto more commands.
         *
         * Open With, Arrange By and Services are all this shape. An item holding items is
         * how a menu describes it, so nothing here needs a separate idea of a submenu: it
         * is an item whose action is the list under it.
         */
        public static MenuItem holding(FMString title, FMArray<MenuItem> items) {
            return new MenuItem(title, FMString.EMPTY, FMString.EMPTY, FMArray.empty(),
                                false, true, false, false, items);
        }

        public boolean hasSubmenu() { return submenu != null && submenu.count() > 0; }

        public FMDictionary toPlist() {
            FMMutableDictionary out = FMMutableDictionary.empty();
            if (separator) {
                out.set(SEPARATOR, true);
                return out.asDictionary();
            }
            out.set(TITLE, title);
            if (action != null && !action.isBlank()) out.set(ACTION, action);
            if (key != null && !key.isBlank()) out.set(KEY, key);
            if (modifiers != null && modifiers.count() > 0) out.set(MODIFIERS, modifiers);
            if (!enabled) out.set(ENABLED, false);
            if (checkable) {
                out.set(CHECKABLE, true);
                if (checked) out.set(CHECKED, true);
            }
            if (hasSubmenu()) {
                FMMutableArray<Object> list = FMMutableArray.empty();
                for (int i = 0; i < submenu.count(); i++) list.add(submenu.at(i).toPlist());
                out.set(ITEMS, list.asArray());
            }
            return out.asDictionary();
        }

        public static MenuItem from(FMDictionary values) {
            if (values.truth(SEPARATOR, false)) return line();
            FMMutableArray<MenuItem> under = FMMutableArray.empty();
            FMArray<Object> list = values.array(ITEMS);
            for (int i = 0; i < list.count(); i++) {
                FMDictionary one = asDictionary(list.at(i));
                if (one != null) under.add(MenuItem.from(one));
            }
            return new MenuItem(values.string(TITLE), values.string(ACTION),
                                values.string(KEY), textList(values, MODIFIERS), false,
                                values.truth(ENABLED, true),
                                values.truth(CHECKABLE, false), values.truth(CHECKED, false),
                                under.asArray());
        }
    }

    /** One menu in the bar: a name, and the commands under it. */
    public record Menu(FMString title, FMArray<MenuItem> items) {

        public FMDictionary toPlist() {
            FMMutableDictionary out = FMMutableDictionary.empty();
            out.set(TITLE, title);
            FMMutableArray<Object> list = FMMutableArray.empty();
            for (int i = 0; i < items.count(); i++) list.add(items.at(i).toPlist());
            out.set(ITEMS, list.asArray());
            return out.asDictionary();
        }

        public static Menu from(FMDictionary values) {
            FMMutableArray<MenuItem> items = FMMutableArray.empty();
            FMArray<Object> list = values.array(ITEMS);
            for (int i = 0; i < list.count(); i++) {
                FMDictionary one = asDictionary(list.at(i));
                if (one != null) items.add(MenuItem.from(one));
            }
            return new Menu(values.string(TITLE, FMString.of("Menu")), items.asArray());
        }
    }

    /**
     * One control in a description.
     *
     * The value is left as whatever a property list may hold, because a control's value is
     * text for one kind, a number for another and a list for a third, and naming one of
     * those here would be deciding for all of them.
     */
    public record Control(ControlClass kind, FMString identifier, FMString name,
                          FMString description, FMString text, FMString action,
                          int x, int y, int width, int height,
                          Object value, double from, double to,
                          FMArray<FMString> choices, boolean defaultButton,
                          FMString in) {

        /**
         * The same, in the window rather than inside something else.
         *
         * Most controls are, so this is the shape most descriptions are written in and the
         * one everything written before there were containers still uses.
         */
        public Control(ControlClass kind, FMString identifier, FMString name,
                       FMString description, FMString text, FMString action,
                       int x, int y, int width, int height,
                       Object value, FMArray<FMString> choices, boolean defaultButton) {
            this(kind, identifier, name, description, text, action, x, y, width, height,
                 value, 0, 100, choices, defaultButton, FMString.EMPTY);
        }

        /**
         * A control being described a piece at a time.
         *
         * Fourteen things make up a control and most of them are nothing most of the time,
         * so writing one out in full is fourteen arguments in an order nobody remembers
         * with six of them holding a blank. Objective-C does not have that problem, because
         * every argument of a message is labelled where it is passed; Java has no such
         * thing and this is the nearest it gets. Each piece is named where it is given, and
         * the ones not mentioned are the ones that are nothing.
         *
         * <pre>
         *   Control.of(FMBrowser, FILES).named("Files").showing(folder)
         *          .at(8, 8, 540, 300).within(SPLIT)
         * </pre>
         */
        public static Control of(ControlClass kind, FMString identifier) {
            return new Control(kind, identifier, FMString.EMPTY, FMString.EMPTY,
                               FMString.EMPTY, FMString.EMPTY, 0, 0, 100, 22,
                               null, 0, 100, FMArray.empty(), false, FMString.EMPTY);
        }

        /** What a screen reader says it is, which every control has to have. */
        public Control named(FMString name) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** And the longer sentence, for a control whose name is not the whole story. */
        public Control describedAs(FMString description) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** What is written on it, or in it: a button's label, a field's contents. */
        public Control showing(FMString text) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** What it sends back when somebody uses it. */
        public Control sending(FMString action) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

    /** The two ends of a control that holds a number between them. */
        public Control between(double from, double to) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** Where it goes and how big it is. */
        public Control at(int x, int y, int width, int height) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** What it holds, for the controls that hold something other than words. */
        public Control holding(Object value) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** What is in it, for the ones that are a list of things. */
        public Control choosingFrom(FMArray<FMString> choices) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, in);
        }

        /** The one Return presses. A window has at most one. */
        public Control asDefault() {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, true, in);
        }

        /** The same control, inside the one named. */
        public Control within(FMString parent) {
            return new Control(kind, identifier, name, description, text, action,
                               x, y, width, height, value, from, to, choices, defaultButton, parent);
        }

        /** Whether it goes in the window itself rather than inside another control. */
        public boolean isLoose() { return in == null || in.isEmpty(); }

        public FMDictionary toPlist() {
            FMMutableDictionary out = FMMutableDictionary.empty();
            out.set(CLASS, kind.className());
            out.set(IDENTIFIER, identifier);
            out.set(NAME, name);
            if (kind == ControlClass.FMSlider) {
                out.set(FROM, from);
                out.set(TO, to);
            }
            if (description != null && !description.isBlank()) out.set(DESCRIPTION, description);
            if (text != null) out.set(TEXT, text);
            if (action != null && !action.isBlank()) out.set(ACTION, action);
            out.set(X, (long) x);
            out.set(Y, (long) y);
            out.set(WIDTH, (long) width);
            out.set(HEIGHT, (long) height);
            if (value != null) out.set(VALUE, value);
            if (choices != null && choices.count() > 0) out.set(CHOICES, choices);
            if (defaultButton) out.set(DEFAULT_BUTTON, true);
            if (!isLoose()) out.set(IN, in);
            return out.asDictionary();
        }

        public static Control from(FMDictionary values) {
            ControlClass kind = ControlClass.of(values.string(CLASS));
            if (kind == null) return null;
            return new Control(kind,
                values.string(IDENTIFIER), values.string(NAME), values.string(DESCRIPTION),
                values.has(TEXT) ? values.string(TEXT) : null,
                values.string(ACTION),
                (int) values.whole(X, 0), (int) values.whole(Y, 0),
                (int) values.whole(WIDTH, 100), (int) values.whole(HEIGHT, 22),
                values.value(VALUE), values.real(FROM, 0), values.real(TO, 100),
                textList(values, CHOICES),
                values.truth(DEFAULT_BUTTON, false),
                values.string(IN));
        }
    }

    private final FMString title;
    private final int width;
    private final int height;
    private final boolean resizable;
    private final FMArray<Control> controls;
    private final FMArray<Menu> menus;

    public Nib(FMString title, int width, int height, boolean resizable,
               FMArray<Control> controls) {
        this(title, width, height, resizable, controls, FMArray.empty());
    }

    public Nib(FMString title, int width, int height, boolean resizable,
               FMArray<Control> controls, FMArray<Menu> menus) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.resizable = resizable;
        this.controls = controls;
        this.menus = menus;
    }

    public FMString title() { return title; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean resizable() { return resizable; }
    public FMArray<Control> controls() { return controls; }

    /** The menus this window's program puts in the bar while it is in front. */
    public FMArray<Menu> menus() { return menus; }

    public Control control(FMString identifier) {
        for (int i = 0; i < controls.count(); i++) {
            if (controls.at(i).identifier().sameAs(identifier)) return controls.at(i);
        }
        return null;
    }

    /* --------------------------------------------------------------- reading */

    public static Nib read(FMURL file) throws IOException {
        return from(Plist.dictionary(file.asFile().toPath()));
    }

    public static Nib parse(FMData bytes) throws IOException {
        return from(FMDictionary.fromMap(Plist.readDictionary(bytes.asBytes())));
    }

    /**
     * Reads a description, refusing one that would produce a window nobody could use.
     *
     * A control with no identifier cannot be referred to afterwards, and a control with no
     * name cannot be described. Both are refused here rather than drawn and
     * discovered later, because the program that wrote the description is the only thing
     * that can still fix them.
     */
    public static Nib from(FMDictionary values) throws IOException {
        FMDictionary window = values.dictionary(WINDOW);
        if (window.isEmpty()) throw new IOException("this description has no window");

        FMMutableArray<Control> controls = FMMutableArray.empty();
        FMArray<Object> list = values.array(CONTROLS);
        for (int i = 0; i < list.count(); i++) {
            FMDictionary one = asDictionary(list.at(i));
            if (one == null) continue;
            Control control = Control.from(one);
            if (control == null) {
                throw new IOException("a control names a class this system does not have: "
                                      + one.string(CLASS));
            }
            if (control.identifier().isBlank()) {
                throw new IOException("a control has no identifier to refer to it by");
            }
            if (control.name().isBlank() && control.kind() != ControlClass.FMSeparator) {
                throw new IOException("the control " + control.identifier()
                                      + " has no accessible name");
            }
            controls.add(control);
        }

        FMMutableArray<Menu> menus = FMMutableArray.empty();
        FMArray<Object> bar = values.array(MENUS);
        for (int i = 0; i < bar.count(); i++) {
            FMDictionary one = asDictionary(bar.at(i));
            if (one == null) continue;
            Menu menu = Menu.from(one);
            if (menu.title().isBlank()) throw new IOException("a menu has no name");
            for (int j = 0; j < menu.items().count(); j++) {
                MenuItem item = menu.items().at(j);
                if (!item.separator() && item.title().isBlank()) {
                    throw new IOException("a command in " + menu.title() + " has no name");
                }
            }
            menus.add(menu);
        }

        return new Nib(window.string(TITLE, FMString.of("Window")),
                       (int) window.whole(WIDTH, 400), (int) window.whole(HEIGHT, 300),
                       window.truth(RESIZABLE, true),
                       controls.asArray(), menus.asArray());
    }

    /* --------------------------------------------------------------- writing */

    public FMDictionary toPlist() {
        FMMutableDictionary window = FMMutableDictionary.empty();
        window.set(TITLE, title);
        window.set(WIDTH, (long) width);
        window.set(HEIGHT, (long) height);
        window.set(RESIZABLE, resizable);

        FMMutableArray<Object> list = FMMutableArray.empty();
        for (int i = 0; i < controls.count(); i++) list.add(controls.at(i).toPlist());

        FMMutableDictionary out = FMMutableDictionary.empty();
        out.set(WINDOW, window.asDictionary());
        out.set(CONTROLS, list.asArray());
        if (menus.count() > 0) {
            FMMutableArray<Object> bar = FMMutableArray.empty();
            for (int i = 0; i < menus.count(); i++) bar.add(menus.at(i).toPlist());
            out.set(MENUS, bar.asArray());
        }
        return out.asDictionary();
    }

    public FMData toBytes() { return FMData.of(Plist.toBytes(toPlist().asMap())); }

    public void write(FMURL file) throws IOException {
        Plist.write(file.asFile().toPath(), toPlist());
    }

    /* ---------------------------------------------------------------- making */

    /** Builds a description a piece at a time, for a program with no file to read. */
    public static final class Builder {
        private FMString title = FMString.of("Window");
        private int width = 400;
        private int height = 300;
        private boolean resizable = true;
        private final FMMutableArray<Control> controls = FMMutableArray.empty();
        private final FMMutableArray<Menu> menus = FMMutableArray.empty();

        public Builder title(FMString value) { this.title = value; return this; }
        public Builder size(int w, int h) { this.width = w; this.height = h; return this; }
        public Builder resizable(boolean value) { this.resizable = value; return this; }

        public Builder add(ControlClass kind, FMString identifier, FMString name,
                           FMString text, int x, int y, int w, int h) {
            controls.add(new Control(kind, identifier, name, FMString.EMPTY, text,
                                     FMString.EMPTY, x, y, w, h, null, FMArray.empty(),
                                     false));
            return this;
        }

        public Builder add(Control control) {
            controls.add(control);
            return this;
        }

        public Builder button(FMString identifier, FMString title, FMString action,
                              int x, int y, int w, int h, boolean isDefault) {
            controls.add(new Control(ControlClass.FMButton, identifier, title,
                                     FMString.EMPTY, title, action, x, y, w, h, null,
                                     FMArray.empty(), isDefault));
            return this;
        }

        /**
         * A button whose spoken name is not the label drawn on it.
         *
         * A key marked × is a multiply key. The symbol is what fits on a key that size;
         * the name is what the key does. Taken literally the symbol is a multiplication
         * sign, which describes the glyph and not the button.
         */
        public Builder button(FMString identifier, FMString label, FMString action,
                              FMString name, int x, int y, int w, int h, boolean isDefault) {
            controls.add(new Control(ControlClass.FMButton, identifier, name,
                                     FMString.EMPTY, label, action, x, y, w, h, null,
                                     FMArray.empty(), isDefault));
            return this;
        }

        /** Adds a menu, with its commands in the order they appear. */
        public Builder menu(FMString title, MenuItem... items) {
            FMMutableArray<MenuItem> under = FMMutableArray.empty();
            for (MenuItem one : items) under.add(one);
            menus.add(new Menu(title, under.asArray()));
            return this;
        }

        public Nib build() {
            return new Nib(title, width, height, resizable, controls.asArray(),
                           menus.asArray());
        }
    }

    /* --------------------------------------------------------------- helpers */

    /** A property list value that is itself a dictionary, or nothing. */
    static FMDictionary asDictionary(Object value) {
        if (value instanceof FMDictionary already) return already;
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> one : map.entrySet()) {
                out.put(String.valueOf(one.getKey()), one.getValue());
            }
            return FMDictionary.fromMap(out);
        }
        return null;
    }

    /**
     * A list of text under a name.
     *
     * A property list read from a file holds the runtime's own strings, because that is
     * what came out of the parser. A description built in memory holds this system's. Both
     * arrive here and both leave as the same thing.
     */
    static FMArray<FMString> textList(FMDictionary values, FMString key) {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        Object held = values.value(key);
        if (held instanceof FMArray<?> already) {
            for (int i = 0; i < already.count(); i++) {
                out.add(FMString.describing(already.at(i)));
            }
        } else if (held instanceof java.util.List<?> list) {
            for (Object one : list) out.add(FMString.describing(one));
        }
        return out.asArray();
    }

    @Override public String toString() {
        return "description of " + title + " with " + controls.count() + " controls";
    }
}
