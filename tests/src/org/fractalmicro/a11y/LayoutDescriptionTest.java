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

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Nib.Control;
import org.fractalmicro.nib.Nib.ControlClass;
import org.fractalmicro.nib.Xib;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Windows that hold other things, and questions asked of one window.
 *
 * A description was a flat list of controls at fixed places, which is enough for a
 * calculator and not enough for anything with a shape. Three things were missing and this
 * checks all three: a control that holds two others with a divider between them, the row
 * along the top of a window, and a description run as a sheet.
 *
 * Nesting is written two ways on purpose. A description stays a flat list where a control
 * names the one it sits inside, because everything that reads a description then reads a
 * list. An interface file nests, because that is what a view holding views looks like and
 * what Interface Builder writes. The two have to say the same thing, so the check writes
 * one out as the other and reads it back.
 */
public final class LayoutDescriptionTest {
    private LayoutDescriptionTest() {}

    public static int count() { return 12; }

    private static final FMString SPLIT = FMString.of("split");
    private static final FMString SIDEBAR = FMString.of("sidebar");
    private static final FMString FILES = FMString.of("files");
    private static final FMString BAR = FMString.of("bar");
    private static final FMString BACK = FMString.of("back");
    private static final FMString GAP = FMString.of("gap");
    private static final FMString SEARCH = FMString.of("search");

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("windows that hold things:");

        failures += checkDescription(out);
        failures += checkWindow(desktop, out);
        failures += checkSheet(desktop, out);

        out.println("      " + (failures == 0
            ? "a window with a shape can be described in one message"
            : failures + " failed"));
        return failures;
    }

    /* ----------------------------------------------------------- written down */

    /** A window shaped like a file browser: a toolbar, and a sidebar beside a folder. */
    private static Nib shaped() {
        return new Nib.Builder()
            .title(FMString.of("Shaped")).size(600, 400)
            .add(Control.of(ControlClass.FMToolbar, BAR).named(FMString.of("Toolbar"))
                        .at(0, 0, 600, 34))
            .add(Control.of(ControlClass.FMButton, BACK).named(FMString.of("Back"))
                        .showing(FMString.of("Back")).sending(FMString.of("goBack"))
                        .at(0, 0, 60, 24).within(BAR))
            .add(Control.of(ControlClass.FMSeparator, GAP).named(FMString.of("Space"))
                        .at(0, 0, 1, 1).within(BAR))
            .add(Control.of(ControlClass.FMTextField, SEARCH).named(FMString.of("Search"))
                        .at(0, 0, 140, 24).within(BAR))
            .add(Control.of(ControlClass.FMSplitView, SPLIT).named(FMString.of("Panes"))
                        .holding(FMString.of("horizontal")).at(0, 40, 600, 350))
            .add(Control.of(ControlClass.FMTableView, SIDEBAR).named(FMString.of("Places"))
                        .at(0, 0, 170, 350).within(SPLIT))
            .add(Control.of(ControlClass.FMBrowser, FILES).named(FMString.of("Files"))
                        .at(0, 0, 420, 350).within(SPLIT))
            .build();
    }

    private static int checkDescription(PrintStream out) {
        int failures = 0;
        Nib nib = shaped();

        try {
            Nib again = Nib.parse(nib.toBytes());
            failures += check(out, "a control says which one it sits inside, and it survives",
                again.control(SIDEBAR).in().sameAs(SPLIT)
                && again.control(FILES).in().sameAs(SPLIT)
                && again.control(BACK).in().sameAs(BAR)
                && again.control(SPLIT).isLoose() && again.control(BAR).isLoose());
        } catch (Exception e) {
            out.println("FAIL  a control says which one it sits inside, and it survives: " + e);
            failures++;
        }

        try {
            Nib again = Xib.parse(Xib.write(nib));
            failures += check(out, "and the same thing nested, as an interface file writes it",
                again.controls().count() == nib.controls().count()
                && again.control(SIDEBAR) != null && again.control(SIDEBAR).in().sameAs(SPLIT)
                && again.control(SEARCH) != null && again.control(SEARCH).in().sameAs(BAR)
                && again.control(SPLIT).kind() == ControlClass.FMSplitView
                && again.control(BAR).kind() == ControlClass.FMToolbar);
        } catch (Exception e) {
            out.println("FAIL  and the same thing nested, as an interface file writes it: " + e);
            failures++;
        }
        return failures;
    }

    /* ------------------------------------------------------------ on the screen */

    private static int checkWindow(Desktop desktop, PrintStream out) {
        int failures = 0;
        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running, so nothing can be shown");
            return 6;
        }

        try (FMApplication app = FMApplication.named(FMString.of("Shaping"))) {
            boolean opened = app.showWindow(shaped());
            drain();
            JInternalFrame frame = frameTitled(desktop, "Shaped");
            failures += check(out, "a window holding other things opens from one message",
                opened && frame != null);
            if (frame == null) return failures + 5;

            JSplitPane split = firstOfKind(frame, JSplitPane.class);
            failures += check(out, "the two panes are the two controls that named it",
                split != null
                && named(split.getLeftComponent(), "Places")
                && named(split.getRightComponent(), "Files"));

            failures += check(out, "the divider starts where the first pane's width put it",
                split != null && split.getDividerLocation() == 170);

            /* ---------------------------------------------------- the toolbar */
            AbstractButton back = firstNamed(frame, "Back", AbstractButton.class);
            JTextField search = firstNamed(frame, "Search", JTextField.class);
            failures += check(out, "the toolbar holds what was put in it",
                back != null && search != null);

            // The separator between them is flexible space, so what came before it is at
            // the left of the bar and what came after it is at the right.
            boolean apart = back != null && search != null
                && locationInWindow(search) - locationInWindow(back) > 200;
            failures += check(out, "and a separator in it pushes the rest to the far end",
                apart);

            /* ----------------------------------------- and it is all still reachable */
            app.setValue(SEARCH, FMString.of("report"));
            drain();
            failures += check(out, "something inside a container is still addressed by name",
                app.valueOf(SEARCH).sameAs(FMString.of("report")));

            app.hideWindow();
            drain();
        } catch (Exception e) {
            out.println("FAIL  the window holding other things could be driven: " + e);
            failures++;
        }
        return failures;
    }

    /* ---------------------------------------------------------------- the sheet */

    /** A question: a line to type in, and two buttons. */
    private static Nib asking() {
        return new Nib.Builder()
            .title(FMString.of("Name")).size(400, 130)
            .add(ControlClass.FMLabel, FMString.of("prompt"), FMString.of("Prompt"),
                 FMString.of("Name of the new folder:"), 20, 16, 360, 20)
            .add(ControlClass.FMTextField, FMString.of("name"), FMString.of("Name"),
                 FMString.of("untitled folder"), 20, 44, 360, 24)
            .button(FMString.of("cancel"), FMString.of("Cancel"), FMString.of("cancel"),
                    200, 88, 84, 24, false)
            .button(FMString.of("make"), FMString.of("Create"), FMString.of("create"),
                    296, 88, 84, 24, true)
            .build();
    }

    /**
     * The sheet is built and answered without being shown.
     *
     * A sheet needs a window that is on the screen, and a checking run has none: the
     * desktop is laid out and painted into an image and never shown, which is what lets
     * these run on somebody's machine while they are using it. So what is checked is what
     * can be: the panel a description makes, and what pressing a button in it answers.
     */
    private static int checkSheet(Desktop desktop, PrintStream out) {
        int failures = 0;
        WindowServer server = WindowServer.sharedServer();
        try (FMApplication app = FMApplication.named(FMString.of("Asking"))) {
            app.showWindow(new Nib.Builder().title(FMString.of("Asking")).size(300, 200)
                .add(ControlClass.FMLabel, FMString.of("nothing"), FMString.of("Nothing"),
                     FMString.of(""), 10, 10, 100, 20)
                .build());
            drain();

            Map<String, JComponent> made = new LinkedHashMap<>();
            String[] chosen = {""};
            javax.swing.JPanel panel = server.sheetPanelForChecking(asking(), made, chosen);

            failures += check(out, "a sheet is built from a description like any window",
                panel != null && made.size() == 4
                && made.get("name") instanceof JTextField
                && made.get("make") instanceof AbstractButton);

            AbstractButton create = (AbstractButton) made.get("make");
            AbstractButton cancel = (AbstractButton) made.get("cancel");
            failures += check(out, "its buttons are named for what they do",
                create != null && "Create".equals(create.getText())
                && cancel != null && "Cancel".equals(cancel.getText()));

            ((JTextField) made.get("name")).setText("Reports");
            create.doClick();
            failures += check(out, "pressing one says which was pressed",
                "create".equals(chosen[0]));

            FMApplication.Answer answer = new FMApplication.Answer(
                FMString.of(chosen[0]),
                arrayOf(made.keySet().toArray(new String[0])),
                arrayOf("Name of the new folder:", "Reports", "Cancel", "Create"));
            failures += check(out, "and the answer carries what was in it, by name",
                answer.valueOf(FMString.of("name")).sameAs(FMString.of("Reports"))
                && !answer.isNothing());

            /* -------------------------------- and a sheet with nothing to hang from */
            FMApplication.Answer nowhere = app.sheet(asking());
            failures += check(out, "a sheet on a window that is not on screen says so",
                nowhere.isNothing());

            app.hideWindow();
            drain();
        } catch (Exception e) {
            out.println("FAIL  the sheet could be built and answered: " + e);
            failures++;
        }
        return failures;
    }

    /* ---------------------------------------------------------------- looking */

    private static org.fractalmicro.foundation.FMArray<FMString> arrayOf(String... said) {
        org.fractalmicro.foundation.FMMutableArray<FMString> out =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (String one : said) out.add(FMString.of(one));
        return out.asArray();
    }

    private static boolean named(Component what, String name) {
        return what instanceof javax.swing.JComponent one
            && name.equals(one.getAccessibleContext().getAccessibleName());
    }

    /** How far along the window a control ended up, for saying which end it is at. */
    private static int locationInWindow(Component what) {
        int x = 0;
        for (Component at = what; at != null && !(at instanceof JInternalFrame);
             at = at.getParent()) {
            x += at.getX();
        }
        return x;
    }

    private static <T> T firstOfKind(Container where, Class<T> kind) {
        for (Component child : where.getComponents()) {
            if (kind.isInstance(child)) return kind.cast(child);
            if (child instanceof Container inside) {
                T found = firstOfKind(inside, kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends JComponent> T firstNamed(Container where, String name,
                                                       Class<T> kind) {
        for (Component child : where.getComponents()) {
            if (kind.isInstance(child) && named(child, name)) return kind.cast(child);
            if (child instanceof Container inside) {
                T found = firstNamed(inside, name, kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JInternalFrame frameTitled(Desktop desktop, String title) {
        for (JInternalFrame frame : desktop.windows()) {
            if (title.equals(frame.getTitle())) return frame;
        }
        return null;
    }

    private static void drain() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(60);
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
