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
package org.fractalmicro.appkit;

import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;

import org.fractalmicro.ui.Finder;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Services: what other programs can do with the text you have selected.
 *
 * A service is offered by one program and used from any other. Select an address in a
 * dialog and a map can open; select a word anywhere and it can be looked up; select some
 * text and a new document can be made out of it. The program you are in does not have to
 * know about any of it; it only has to have text.
 *
 * Each service says when it applies, so the menu shows what can actually be done with
 * what is selected rather than a list of things that will not work.
 */
public final class Services {
    private Services() {}

    /** One service: its name, when it applies, and what it does with the text. */
    public record Service(FMString name, Predicate<FMString> applies,
                          Function<FMString, FMString> run,
                          boolean replacesSelection) {
        public boolean appliesTo(FMString selection) {
            return selection != null && !selection.isBlank() && applies.test(selection);
        }
    }

    private static final List<Service> SERVICES = new ArrayList<>();

    /** The program a service hands text to. Named, not linked: it ships as its own app. */
    private static final FMString TEXT_EDITOR = FMString.of("org.fractalmicro.textedit");

    /** The program that shows a file where it lives. Named, not linked. */
    private static final FMString FILE_BROWSER = FMString.of("org.fractalmicro.finder");

    /** The first thing of a kind found in a selection, or nothing. */
    private static DataDetectors.Detection firstOf(FMString text, DataDetectors.Kind kind) {
        FMArray<DataDetectors.Detection> found = DataDetectors.find(text);
        for (int i = 0; i < found.count(); i++) {
            if (found.at(i).kind() == kind) return found.at(i);
        }
        return null;
    }

    static {
        add(new Service(FMString.of("New TextEdit Document Containing Selection"),
            text -> true,
            text -> {
                org.fractalmicro.bundle.Bundles.openText(TEXT_EDITOR.toString(), text.toString());
                return null;
            }, false));

        add(new Service(FMString.of("Search With Spotlight"),
            text -> text.length() < 200,
            text -> {
                org.fractalmicro.windowserver.Spotlight.openSearching(text.trimmed().toString());
                return null;
            }, false));

        add(new Service(FMString.of("Open URL"),
            text -> firstOf(text, DataDetectors.Kind.LINK) != null,
            text -> {
                DataDetectors.Detection link = firstOf(text, DataDetectors.Kind.LINK);
                if (link != null) {
                    org.fractalmicro.core.Shell.browse(DataDetectors.actionTarget(link).toString());
                }
                return null;
            }, false));

        add(new Service(FMString.of("Show Map"),
            text -> firstOf(text, DataDetectors.Kind.ADDRESS) != null,
            text -> {
                DataDetectors.Detection address = firstOf(text, DataDetectors.Kind.ADDRESS);
                if (address != null) {
                    org.fractalmicro.core.Shell.browse(DataDetectors.actionTarget(address).toString());
                }
                return null;
            }, false));

        add(new Service(FMString.of("Reveal in Finder"),
            text -> new java.io.File(text.trimmed().toString()).exists(),
            text -> {
                org.fractalmicro.bundle.Bundles.openFiles(FILE_BROWSER.toString(),
                    java.util.List.of(new java.io.File(text.trimmed().toString())));
                return null;
            }, false));

        // The three that change the text itself, which a service is allowed to do.
        add(new Service(FMString.of("Make Upper Case"), text -> true,
            FMString::uppercase, true));
        add(new Service(FMString.of("Make Lower Case"), text -> true,
            FMString::lowercase, true));
        add(new Service(FMString.of("Capitalize"), text -> true,
            text -> FMString.of(capitalise(text.toString())), true));

        add(new Service(FMString.of("Copy"), text -> true,
            text -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text.toString()), null);
                return null;
            }, false));
    }

    public static void add(Service service) { SERVICES.add(service); }

    public static FMArray<Service> all() {
        FMMutableArray<Service> out = FMMutableArray.empty();
        for (Service one : SERVICES) out.add(one);
        return out.asArray();
    }

    /** The services that can do something with this selection. */
    public static FMArray<Service> forSelection(FMString selection) {
        FMMutableArray<Service> out = FMMutableArray.empty();
        for (Service service : SERVICES) {
            if (service.appliesTo(selection)) out.add(service);
        }
        return out.asArray();
    }

    /**
     * The Services menu for one text control: what applies to what is selected in it,
     * with the ones that change the text writing their answer back into it.
     */
    public static JMenu menuFor(javax.swing.text.JTextComponent text) {
        JMenu menu = new JMenu("Services");
        menu.getAccessibleContext().setAccessibleName("Services");
        menu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                menu.removeAll();
                FMString selection = FMString.describing(text.getSelectedText());
                FMArray<Service> available = forSelection(selection);
                if (available.isEmpty()) {
                    JMenuItem none = new JMenuItem(selection.isBlank()
                        ? "No text is selected" : "No services apply");
                    none.setEnabled(false);
                    menu.add(none);
                    return;
                }
                for (Service service : available) {
                    JMenuItem item = new JMenuItem(service.name().toString());
                    item.addActionListener(e2 -> run(service, text));
                    menu.add(item);
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        return menu;
    }

    /** Runs one service on what is selected, putting the answer back if it gives one. */
    public static void run(Service service, javax.swing.text.JTextComponent text) {
        FMString selection = FMString.describing(text.getSelectedText());
        if (selection.isBlank()) return;
        FMString answer = service.run().apply(selection);
        if (service.replacesSelection() && answer != null && text.isEditable()) {
            text.replaceSelection(answer.toString());
        }
    }

    private static String capitalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for (char c : text.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = !Character.isLetterOrDigit(c) && c != '\'';
        }
        return out.toString();
    }
}
