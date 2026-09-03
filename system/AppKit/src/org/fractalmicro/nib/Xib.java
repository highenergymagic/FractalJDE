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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An interface, written down where a person can edit it.
 *
 * XML in the shape Interface Builder writes it:
 *
 *     <button id="digit 7">
 *       <rect key="frame" x="12" y="52" width="46" height="40"/>
 *       <buttonCell key="cell" title="7"/>
 *       <accessibility key="accessibilityLabel" label="Seven"/>
 *       <connections><action selector="digit 7"/></connections>
 *     </button>
 *
 * A subset: classes, names, frames, connections and menus, and none of what Interface
 * Builder records about how it was drawing at the time. Identifiers are words rather than
 * numbers, a file written by hand doing better with the names the program uses.
 *
 * A compiled interface would be a nib. There is no compiler here, so what ships is what
 * was written, as the format was before ibtool.
 */
public final class Xib {
    private Xib() {}

    /** What the file is called on disk, and what Interface Builder calls the format. */
    public static final FMString EXTENSION = FMString.of("xib");

    /** The document type, written so that a real Interface Builder would know it. */
    public static final FMString DOCUMENT_TYPE =
        FMString.of("com.apple.InterfaceBuilder3.Cocoa.XIB");

    /** The element each kind of control is written as. */
    private static final String[][] ELEMENTS = {
        {"FMButton", "button"},
        {"FMLabel", "textField"},
        {"FMTextField", "textField"},
        {"FMTextView", "textView"},
        {"FMRichText", "textView"},
        {"FMCheckBox", "button"},
        {"FMPopUpButton", "popUpButton"},
        {"FMSlider", "slider"},
        {"FMProgressIndicator", "progressIndicator"},
        {"FMTableView", "tableView"},
        // The element NSBrowser has always been written as, so a file browser in an
        // interface file is spelled the way Interface Builder spells one.
        {"FMBrowser", "browser"},
        {"FMSplitView", "splitView"},
        {"FMToolbar", "toolbar"},
        {"FMSeparator", "box"},
    };

    private static String elementFor(Nib.ControlClass kind) {
        for (String[] one : ELEMENTS) {
            if (one[0].equals(kind.name())) return one[1];
        }
        return "view";
    }

    /* ---------------------------------------------------------------- reading */

    public static Nib read(FMURL file) throws IOException {
        FMData held = FMData.withContentsOf(file);
        if (held == null) throw new IOException("no interface at " + file.path());
        return parse(held);
    }

    /**
     * Reads one.
     *
     * External entities are turned off. A description is a file, files come from places
     * the program did not choose, and an XML parser that follows a reference in one will
     * read whatever the reference names.
     */
    public static Nib parse(FMData bytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Element document = builder
                .parse(new ByteArrayInputStream(bytes.asBytes()))
                .getDocumentElement();

            Element objects = firstChild(document, "objects");
            Element window = objects == null ? null : firstChild(objects, "window");
            if (window == null) throw new IOException("this interface has no window");

            Element frame = childWithKey(window, "rect", "contentRect");
            Element content = childWithKey(window, "view", "contentView");
            Element subviews = content == null ? null : firstChild(content, "subviews");

            FMMutableArray<Nib.Control> controls = FMMutableArray.empty();
            if (subviews != null) collect(subviews, FMString.EMPTY, controls);

            FMMutableArray<Nib.Menu> menus = FMMutableArray.empty();
            Element bar = firstChild(objects, "mainMenu");
            if (bar != null) {
                for (Element one : childrenNamed(bar, "menu")) menus.add(menuIn(one));
            }

            return new Nib(FMString.of(text(window, "title", "Window")),
                           number(frame, "width", 400), number(frame, "height", 300),
                           !"NO".equals(attribute(window, "resizable")),
                           controls.asArray(), menus.asArray());
        } catch (IOException already) {
            throw already;
        } catch (Exception notReadable) {
            throw new IOException("this interface could not be read: "
                                  + notReadable.getMessage());
        }
    }

    /**
     * Every control under a list of subviews, and every control under those.
     *
     * An interface file nests, as Interface Builder writes it. A description is flat, each
     * control naming the one it sits inside. This is where one becomes the other.
     */
    private static void collect(Element subviews, FMString parent,
                                FMMutableArray<Nib.Control> into) throws IOException {
        for (Element one : children(subviews)) {
            Nib.Control made = controlIn(one);
            into.add(parent.isEmpty() ? made : made.within(parent));
            Element inside = firstChild(one, "subviews");
            if (inside != null) collect(inside, made.identifier(), into);
        }
    }

    private static Nib.Control controlIn(Element e) throws IOException {
        Nib.ControlClass kind = kindOf(e);
        if (kind == null) throw new IOException("no control class for <" + e.getTagName() + ">");
        Element frame = childWithKey(e, "rect", "frame");
        Element cell = firstChild(e, "buttonCell");
        if (cell == null) cell = firstChild(e, "textFieldCell");
        Element access = firstChild(e, "accessibility");
        Element connections = firstChild(e, "connections");
        Element action = connections == null ? null : firstChild(connections, "action");
        // A binding sits beside the action, in the same connections element, because the
        // two are the same kind of thing: something the control is joined to that nobody
        // had to write code for.
        Element binding = connections == null ? null : firstChild(connections, "binding");
        FMString boundTo = binding == null
            ? FMString.EMPTY : FMString.of(attribute(binding, "keyPath"));

        FMMutableArray<FMString> choices = FMMutableArray.empty();
        Element items = firstChild(e, "items");
        if (items != null) {
            for (Element one : childrenNamed(items, "item")) {
                choices.add(FMString.of(text(one, "title", "")));
            }
        }

        String title = cell != null ? text(cell, "title", null) : text(e, "title", null);
        boolean isDefault = "\r".equals(attribute(e, "keyEquivalent"));

        return new Nib.Control(kind,
            FMString.of(attribute(e, "id")),
            FMString.of(access == null ? "" : text(access, "label", "")),
            FMString.of(access == null ? "" : text(access, "help", "")),
            title == null ? null : FMString.of(title),
            FMString.of(action == null ? "" : text(action, "selector", "")),
            number(frame, "x", 0), number(frame, "y", 0),
            number(frame, "width", 100), number(frame, "height", 22),
            // A slider says where its two ends are, the way a nib always has. Anything
            // else has no use for them and gets the pair everything used to have.
            null, number(e, "minValue", 0), number(e, "maxValue", 100),
            choices.asArray(), isDefault, FMString.EMPTY, boundTo);
    }

    private static Nib.ControlClass kindOf(Element e) {
        String said = attribute(e, "customClass");
        if (!said.isEmpty()) return Nib.ControlClass.of(FMString.of(said));
        // Two classes share an element, and what tells them apart is what Cocoa uses:
        // whether the thing can be typed in, and whether a button holds a state.
        String tag = e.getTagName();
        if ("textField".equals(tag)) {
            return "NO".equals(attribute(e, "editable"))
                ? Nib.ControlClass.FMLabel : Nib.ControlClass.FMTextField;
        }
        if ("button".equals(tag)) {
            return "check".equals(attribute(e, "type"))
                ? Nib.ControlClass.FMCheckBox : Nib.ControlClass.FMButton;
        }
        if ("textView".equals(tag)) {
            return "YES".equals(attribute(e, "richText"))
                ? Nib.ControlClass.FMRichText : Nib.ControlClass.FMTextView;
        }
        for (String[] one : ELEMENTS) {
            if (one[1].equals(tag)) return Nib.ControlClass.of(FMString.of(one[0]));
        }
        return null;
    }

    /**
     * The commands of one menu in the language asked for, and the ones under them.
     *
     * A submenu's items are keyed under the item that opens them, so a translator sees
     * "menu File.openWith.chooseApplication" and knows where in the bar it appears without
     * having to be told.
     */
    private static FMArray<Nib.MenuItem> translatedItems(FMArray<Nib.MenuItem> items,
                                                         FMString under, FMDictionary table) {
        FMMutableArray<Nib.MenuItem> out = FMMutableArray.empty();
        for (int i = 0; i < items.count(); i++) {
            Nib.MenuItem item = items.at(i);
            FMString id = under.appending(FMString.of(".")).appending(
                item.action().isEmpty() ? item.title() : item.action());
            out.add(new Nib.MenuItem(
                said(table, id, "title", item.title()),
                item.action(), item.key(), item.modifiers(),
                item.separator(), item.enabled(), item.checkable(), item.checked(),
                item.hasSubmenu() ? translatedItems(item.submenu(), id, table)
                                  : FMArray.empty()));
        }
        return out.asArray();
    }

    private static Nib.Menu menuIn(Element e) {
        return new Nib.Menu(FMString.of(text(e, "title", "Menu")), itemsIn(e));
    }

    /**
     * The commands under a menu, and under any of them that open onto more.
     *
     * Written the way Interface Builder writes it: an item that has more under it holds a
     * whole menu of its own, so reading one is reading this again. A state attribute marks
     * an item that shows a tick, and its value is whether the tick is there now.
     */
    private static FMArray<Nib.MenuItem> itemsIn(Element menu) {
        FMMutableArray<Nib.MenuItem> items = FMMutableArray.empty();
        Element list = firstChild(menu, "items");
        if (list == null) return items.asArray();
        for (Element one : childrenNamed(list, "menuItem")) {
            boolean separator = "YES".equals(attribute(one, "isSeparatorItem"));
            FMMutableArray<FMString> modifiers = FMMutableArray.empty();
            Element mask = firstChild(one, "keyEquivalentModifierMask");
            if (mask != null) {
                for (String named : new String[]{"command", "shift", "option", "control"}) {
                    if ("YES".equals(attribute(mask, named))) modifiers.add(FMString.of(named));
                }
            }
            Element connections = firstChild(one, "connections");
            Element action = connections == null ? null : firstChild(connections, "action");
            Element under = firstChild(one, "menu");
            String state = attribute(one, "state");
            items.add(new Nib.MenuItem(
                FMString.of(text(one, "title", "")),
                FMString.of(action == null ? "" : text(action, "selector", "")),
                FMString.of(text(one, "keyEquivalent", "")),
                modifiers.asArray(), separator,
                !"NO".equals(attribute(one, "enabled")),
                state != null && !state.isEmpty(), "on".equals(state),
                under == null ? FMArray.empty() : itemsIn(under)));
        }
        return items.asArray();
    }

    /* ---------------------------------------------------------------- writing */

    /** Writes an interface out, in the shape it is read back from. */
    public static FMData write(Nib nib) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<document type=\"").append(DOCUMENT_TYPE)
          .append("\" version=\"3.0\" targetRuntime=\"MacOSX.Cocoa\">\n");
        sb.append("    <objects>\n");
        sb.append("        <window title=\"").append(quote(nib.title().toString()))
          .append("\" resizable=\"").append(nib.resizable() ? "YES" : "NO")
          .append("\" id=\"window\">\n");
        sb.append("            <rect key=\"contentRect\" x=\"0\" y=\"0\" width=\"")
          .append(nib.width()).append("\" height=\"").append(nib.height()).append("\"/>\n");
        sb.append("            <view key=\"contentView\" id=\"content\">\n");
        sb.append("                <subviews>\n");
        for (Nib.Control control : nib.controls()) {
            if (control.isLoose()) writeControl(sb, control, nib);
        }
        sb.append("                </subviews>\n");
        sb.append("            </view>\n");
        sb.append("        </window>\n");
        if (nib.menus().count() > 0) {
            sb.append("        <mainMenu id=\"menu bar\">\n");
            for (Nib.Menu menu : nib.menus()) writeMenu(sb, menu);
            sb.append("        </mainMenu>\n");
        }
        sb.append("    </objects>\n");
        sb.append("</document>\n");
        return FMData.of(FMString.of(sb.toString()));
    }

    private static void writeControl(StringBuilder sb, Nib.Control c, Nib nib) {
        String tag = elementFor(c.kind());
        sb.append("                    <").append(tag)
          .append(" id=\"").append(quote(c.identifier().toString())).append("\"");
        if (c.kind() == Nib.ControlClass.FMLabel) sb.append(" editable=\"NO\"");
        if (c.kind() == Nib.ControlClass.FMCheckBox) sb.append(" type=\"check\"");
        if (c.kind() == Nib.ControlClass.FMRichText) sb.append(" richText=\"YES\"");
        if (c.defaultButton()) sb.append(" keyEquivalent=\"&#13;\"");
        sb.append(">\n");

        sb.append("                        <rect key=\"frame\" x=\"").append(c.x())
          .append("\" y=\"").append(c.y()).append("\" width=\"").append(c.width())
          .append("\" height=\"").append(c.height()).append("\"/>\n");

        if (c.text() != null) {
            String cell = "button".equals(tag) ? "buttonCell" : "textFieldCell";
            sb.append("                        <").append(cell)
              .append(" key=\"cell\" title=\"").append(quote(c.text().toString()))
              .append("\"/>\n");
        }
        if (!c.name().isEmpty() || !c.description().isEmpty()) {
            sb.append("                        <accessibility key=\"accessibilityLabel\" label=\"")
              .append(quote(c.name().toString())).append("\"");
            if (!c.description().isEmpty()) {
                sb.append(" help=\"").append(quote(c.description().toString())).append("\"");
            }
            sb.append("/>\n");
        }
        if (c.choices().count() > 0) {
            sb.append("                        <items>\n");
            for (FMString one : c.choices()) {
                sb.append("                            <item title=\"")
                  .append(quote(one.toString())).append("\"/>\n");
            }
            sb.append("                        </items>\n");
        }
        if (!c.action().isEmpty() || !c.boundTo().isEmpty()) {
            sb.append("                        <connections>");
            if (!c.action().isEmpty()) {
                sb.append("<action selector=\"")
                  .append(quote(c.action().toString())).append("\"/>");
            }
            if (!c.boundTo().isEmpty()) {
                sb.append("<binding name=\"value\" keyPath=\"")
                  .append(quote(c.boundTo().toString())).append("\"/>");
            }
            sb.append("</connections>\n");
        }
        // What is inside it, nested, because that is how a view holding views is written.
        // The description this came from is flat and each child names its parent; here the
        // shape goes back the way an interface file has it.
        boolean any = false;
        for (Nib.Control child : nib.controls()) {
            if (!child.in().sameAs(c.identifier())) continue;
            if (!any) {
                sb.append("                        <subviews>\n");
                any = true;
            }
            writeControl(sb, child, nib);
        }
        if (any) sb.append("                        </subviews>\n");
        sb.append("                    </").append(tag).append(">\n");
    }

    private static void writeMenu(StringBuilder sb, Nib.Menu menu) {
        writeMenu(sb, menu.title(), menu.items(), 12);
    }

    /**
     * One menu and everything under it, indented to say where it sits.
     *
     * An item that opens onto more holds a menu of its own, so writing one is writing this
     * again. That is Interface Builder's arrangement rather than a choice made here: a
     * submenu is a menu, and the only thing that makes it a submenu is what it hangs from.
     */
    private static void writeMenu(StringBuilder sb, FMString title,
                                  FMArray<Nib.MenuItem> items, int indent) {
        String pad = " ".repeat(indent);
        sb.append(pad).append("<menu title=\"").append(quote(title.toString()))
          .append("\" id=\"menu ").append(quote(title.toString())).append("\">\n");
        sb.append(pad).append("    <items>\n");
        for (Nib.MenuItem item : items) {
            String at = pad + "        ";
            if (item.separator()) {
                sb.append(at).append("<menuItem isSeparatorItem=\"YES\"/>\n");
                continue;
            }
            sb.append(at).append("<menuItem title=\"")
              .append(quote(item.title().toString())).append("\"");
            if (!item.key().isEmpty()) {
                sb.append(" keyEquivalent=\"").append(quote(item.key().toString())).append("\"");
            }
            if (!item.enabled()) sb.append(" enabled=\"NO\"");
            // Having a state at all is what makes an item one that shows a tick; the value
            // of it is whether the tick is there as the program is written down.
            if (item.checkable()) {
                sb.append(" state=\"").append(item.checked() ? "on" : "off").append("\"");
            }
            sb.append(">\n");
            if (item.modifiers().count() > 0) {
                sb.append(at).append("    <keyEquivalentModifierMask key=\"keyEquivalentModifierMask\"");
                for (FMString one : item.modifiers()) {
                    sb.append(" ").append(one).append("=\"YES\"");
                }
                sb.append("/>\n");
            }
            if (!item.action().isEmpty()) {
                sb.append(at).append("    <connections><action selector=\"")
                  .append(quote(item.action().toString())).append("\"/></connections>\n");
            }
            if (item.hasSubmenu()) writeMenu(sb, item.title(), item.submenu(), indent + 12);
            sb.append(at).append("</menuItem>\n");
        }
        sb.append(pad).append("    </items>\n");
        sb.append(pad).append("</menu>\n");
    }

    /* -------------------------------------------------------------- translating */

    /**
     * The same interface with its words replaced.
     *
     * The table is keyed the way Interface Builder keys one: the object's identifier, a
     * dot, and the property being translated. So an entry
     *
     *     "digit 7.accessibilityLabel" = "Sieben";
     *
     * says what one control is called in one language, and nothing about the program.
     * Anything with no entry keeps the words it was written with, so a partly translated
     * interface is partly translated rather than partly blank.
     */
    public static Nib localized(Nib nib, FMDictionary table) {
        if (table.isEmpty()) return nib;

        FMMutableArray<Nib.Control> controls = FMMutableArray.empty();
        for (Nib.Control c : nib.controls()) {
            controls.add(new Nib.Control(c.kind(), c.identifier(),
                said(table, c.identifier(), "accessibilityLabel", c.name()),
                said(table, c.identifier(), "accessibilityHelp", c.description()),
                c.text() == null ? null : said(table, c.identifier(), "title", c.text()),
                c.action(), c.x(), c.y(), c.width(), c.height(), c.value(),
                c.from(), c.to(), translatedChoices(c, table), c.defaultButton(),
                c.in(), c.boundTo()));
        }

        FMMutableArray<Nib.Menu> menus = FMMutableArray.empty();
        for (Nib.Menu menu : nib.menus()) {
            FMString id = FMString.of("menu ").appending(menu.title());
            menus.add(new Nib.Menu(said(table, id, "title", menu.title()),
                                   translatedItems(menu.items(), id, table)));
        }

        return new Nib(said(table, FMString.of("window"), "title", nib.title()),
                       nib.width(), nib.height(), nib.resizable(),
                       controls.asArray(), menus.asArray());
    }

    /**
     * The rows of a list, which are words like any others.
     *
     * Keyed by where each one sits, "sections.item0", because a row has no identifier of
     * its own. A program is sent the position as well as the title for the same reason.
     */
    private static FMArray<FMString> translatedChoices(Nib.Control c, FMDictionary table) {
        if (c.choices().isEmpty()) return c.choices();
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (int i = 0; i < c.choices().count(); i++) {
            out.add(said(table, c.identifier(), "item" + i, c.choices().at(i)));
        }
        return out.asArray();
    }

    private static FMString said(FMDictionary table, FMString id, String property,
                                 FMString written) {
        FMString key = id.appending(FMString.of("." + property));
        return table.has(key) ? table.string(key) : written;
    }

    /**
     * The keys a translator would be given for this interface, and what they say now.
     *
     * This is what ibtool does when asked for a strings file: it walks the interface and
     * writes out everything in it that is words, so the file a translator receives is
     * complete without anybody having to remember what was in the window.
     */
    public static FMDictionary stringsFor(Nib nib) {
        org.fractalmicro.foundation.FMMutableDictionary out =
            org.fractalmicro.foundation.FMMutableDictionary.empty();
        out.set(FMString.of("window.title"), nib.title());
        for (Nib.Control c : nib.controls()) {
            if (!c.name().isEmpty()) {
                out.set(c.identifier().appending(FMString.of(".accessibilityLabel")), c.name());
            }
            if (!c.description().isEmpty()) {
                out.set(c.identifier().appending(FMString.of(".accessibilityHelp")),
                        c.description());
            }
            if (c.text() != null && !c.text().isEmpty()) {
                out.set(c.identifier().appending(FMString.of(".title")), c.text());
            }
            for (int i = 0; i < c.choices().count(); i++) {
                out.set(c.identifier().appending(FMString.of(".item" + i)), c.choices().at(i));
            }
        }
        for (Nib.Menu menu : nib.menus()) {
            FMString id = FMString.of("menu ").appending(menu.title());
            out.set(id.appending(FMString.of(".title")), menu.title());
            offerItems(out, menu.items(), id);
        }
        return out.asDictionary();
    }

    /**
     * Every command's words, and every command under those.
     *
     * The same walk the translator gets and the same walk the reader does, so a submenu
     * added to a description turns up in the table to be translated without anyone having
     * to remember to add it.
     */
    private static void offerItems(FMMutableDictionary out, FMArray<Nib.MenuItem> items,
                                   FMString under) {
        for (Nib.MenuItem item : items) {
            if (item.separator() || item.title().isEmpty()) continue;
            FMString id = under.appending(FMString.of(".")).appending(
                item.action().isEmpty() ? item.title() : item.action());
            out.set(id.appending(FMString.of(".title")), item.title());
            if (item.hasSubmenu()) offerItems(out, item.submenu(), id);
        }
    }

    /* ------------------------------------------------------------------ xml */

    private static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node one = kids.item(i);
            if (one instanceof Element e) out.add(e);
        }
        return out;
    }

    private static List<Element> childrenNamed(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Element one : children(parent)) {
            if (one.getTagName().equals(tag)) out.add(one);
        }
        return out;
    }

    private static Element firstChild(Element parent, String tag) {
        List<Element> found = childrenNamed(parent, tag);
        return found.isEmpty() ? null : found.get(0);
    }

    /** A child of that tag whose key attribute is the one wanted, as Cocoa keys them. */
    private static Element childWithKey(Element parent, String tag, String key) {
        for (Element one : childrenNamed(parent, tag)) {
            if (key.equals(one.getAttribute("key"))) return one;
        }
        return firstChild(parent, tag);
    }

    private static String attribute(Element e, String name) {
        return e == null ? "" : e.getAttribute(name);
    }

    private static String text(Element e, String name, String fallback) {
        if (e == null || !e.hasAttribute(name)) return fallback;
        return e.getAttribute(name);
    }

    private static int number(Element e, String name, int fallback) {
        String said = attribute(e, name);
        if (said.isEmpty()) return fallback;
        try {
            return (int) Double.parseDouble(said);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private static String quote(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
