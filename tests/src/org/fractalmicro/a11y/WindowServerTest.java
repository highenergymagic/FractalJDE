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
import org.fractalmicro.nib.Nib.ControlClass;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The window server, and the reason it is built the way it is.
 *
 * A window described by a program somewhere else has to end up as real controls with real
 * names, or the split has cost the thing the split was supposed to preserve. So the checks
 * here are mostly about the accessible tree: a window opened over a message is walked
 * afterwards, and every control in it must have a name, exactly as if it had been built by
 * hand in this process.
 *
 * A description that leaves a control unnamed is refused rather than drawn, which is the
 * only way to keep that true for programs nobody here has written yet.
 */
public final class WindowServerTest {
    private WindowServerTest() {}

    public static int count() { return 17; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the window server:");

        /* ------------------------------------------------------ a description */
        Nib nib = new Nib.Builder()
            .title(FMString.of("Checking")).size(320, 200).resizable(false)
            .add(ControlClass.FMLabel, FMString.of("caption"), FMString.of("Count"), FMString.of("Count:"),
                 20, 20, 60, 20)
            .add(ControlClass.FMTextField, FMString.of("total"), FMString.of("Total"), FMString.of("0"),
                 90, 18, 200, 24)
            .button(FMString.of("more"), FMString.of("More"), FMString.of("increase"), 190, 60, 100, 24, true)
            .add(ControlClass.FMProgressIndicator, FMString.of("bar"), FMString.of("How far"), FMString.EMPTY,
                 20, 110, 270, 16)
            .build();

        try {
            Nib again = Nib.parse(nib.toBytes());
            failures += check(out, "a description survives being written and read",
                again.controls().count() == nib.controls().count()
                && again.title().sameAs(FMString.of("Checking"))
                && again.control(FMString.of("total")).kind() == ControlClass.FMTextField);
        } catch (Exception e) {
            out.println("FAIL  a description survives being written and read: " + e);
            failures++;
        }

        failures += check(out, "a description naming a control with no name is refused",
            refused(unnamedControl()));
        failures += check(out, "a description naming a class this system has not got is refused",
            refused(unknownClass()));

        /* -------------------------------------------------------- the server */
        WindowServer server = WindowServer.sharedServer();
        boolean serving = server.start();
        failures += check(out, "the window server serves its name",
            serving && server.isRunning() && FMApplication.serverAvailable());

        int openBefore = server.windowCount();
        try (FMApplication app = FMApplication.named(FMString.of("Checking"))) {
            boolean opened = app.showWindow(nib);
            drain();
            failures += check(out, "a window opens from a description sent as a message",
                opened && server.windowCount() == openBefore + 1);

            /* ------------------------------- the whole reason for doing it this way */
            JInternalFrame frame = frameTitled(desktop, "Checking");
            failures += check(out, "the window is a real window in this process", frame != null);

            List<String> unnamed = new ArrayList<>();
            int named = frame == null ? 0 : countNamed(frame, unnamed);
            for (String one : unnamed) out.println("      unnamed: " + one);
            failures += check(out, "every control in it has a name a screen reader can read",
                frame != null && unnamed.isEmpty() && named >= 4);
            out.println("      the window holds " + named + " named controls");

            failures += check(out, "the window itself is named",
                frame != null
                && "Checking".equals(frame.getAccessibleContext().getAccessibleName()));

            /* ------------------------------------------------------ values */
            app.setValue(FMString.of("total"), FMString.of("42"));
            drain();
            failures += check(out, "a value put into a control from outside arrives",
                app.valueOf(FMString.of("total")).sameAs(FMString.of("42")));

            app.setValue(FMString.of("bar"), org.fractalmicro.foundation.FMNumber.of(60L));
            drain();
            failures += check(out, "a value of another kind arrives too",
                app.valueOf(FMString.of("bar")).sameAs(FMString.of("60")));

            app.setEnabled(FMString.of("more"), false);
            drain();
            JComponent button = componentNamed(frame, "More");
            failures += check(out, "a control can be turned off from outside",
                button != null && !button.isEnabled());
            app.setEnabled(FMString.of("more"), true);
            drain();

            /* ------------------------------------------------------ events */
            JButton more = (JButton) componentNamed(frame, "More");
            if (more != null) {
                SwingUtilities.invokeLater(more::doClick);
                drain();
            }
            FMApplication.Event event = app.nextEvent(2000);
            failures += check(out, "using a control sends an event to the program",
                event != null && event.action().sameAs(FMString.of("increase"))
                && event.control().sameAs(FMString.of("more")));

            app.setTitle(FMString.of("Checking, changed"));
            drain();
            failures += check(out, "the title can be changed, and the name changes with it",
                frame != null && "Checking, changed".equals(frame.getTitle())
                && "Checking, changed".equals(
                    frame.getAccessibleContext().getAccessibleName()));

            app.hideWindow();
            drain();
            failures += check(out, "closing the window from outside closes it here",
                server.windowCount() == openBefore);

            FMApplication.Event closed = app.nextEvent(2000);
            failures += check(out, "and the program is told that it closed",
                closed != null && closed.isClosed());
        } catch (Exception e) {
            out.println("FAIL  the window checks ran to the end: " + e);
            failures++;
        }

        /* --------------------------------------- everything a program can ask for */

        // A verb the server names and does not answer is a command a program can send and
        // wait on forever. Save in TextEdit had the other half of this: it needed to ask a
        // question, there was no verb for it, so it drew its own dialog in a process with
        // no screen and the command looked like it did nothing.
        java.util.List<String> unanswered = verbsWithNoHandler();
        for (String one : unanswered) out.println("      nothing answers " + one);
        failures += check(out, "every message the server names is one it answers",
            unanswered.isEmpty());

        // A program in its own process has no screen. Anything it draws itself appears
        // outside the desktop, behind whatever is in front, where nobody is looking.
        java.util.List<String> drawing = programsThatDrawTheirOwn();
        for (String one : drawing) out.println("      " + one);
        failures += check(out, "and no program in its own process draws its own dialogs",
            drawing.isEmpty());

        out.println("      " + (failures == 0 ? "a window from elsewhere is a window like any other"
                                              : failures + " failed"));
        return failures;
    }

    /**
     * Verbs the server declares but does not act on.
     *
     * Read out of the source, because what is being checked is that two lists agree: the
     * names a program may send, and the names the answering switch mentions. Sending each
     * one instead would mean putting a modal dialog on somebody's screen to find out.
     */
    private static java.util.List<String> verbsWithNoHandler() {
        String source = sourceOf("system/AppKit/src/org/fractalmicro/windowserver/WindowServer.java");
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (source == null) return missing;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "public static final String (\\w+) = \"(\\w+)\";").matcher(source);
        while (m.find()) {
            String name = m.group(1);
            // The service name and the event kinds are not things a program sends.
            if (name.equals("SERVICE") || name.startsWith("EVENT")) continue;
            if (!source.contains("case " + name + " ->")) missing.add(name);
        }
        return missing;
    }

    /** Programs that reach for the alert panel instead of asking the server. */
    private static java.util.List<String> programsThatDrawTheirOwn() {
        java.util.List<String> found = new java.util.ArrayList<>();
        java.nio.file.Path apps = java.nio.file.Path.of("apps");
        if (!java.nio.file.Files.isDirectory(apps)) {
            apps = java.nio.file.Path.of("../apps");
            if (!java.nio.file.Files.isDirectory(apps)) return found;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(apps)) {
            for (java.nio.file.Path file : walk.toList()) {
                if (!file.toString().endsWith(".java")) continue;
                String source = sourceOf(file.toString());
                if (source == null) continue;
                for (String line : source.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("*") || t.startsWith("//")) continue;
                    if (t.contains("FMAlert.ask(") || t.contains("FMAlert.tell(")
                            || t.contains("FMAlert.confirm")) {
                        found.add(file.getFileName() + " draws its own: " + t);
                    }
                }
            }
        } catch (java.io.IOException unreadable) {
            // A tree that went away while it was being read.
        }
        return found;
    }

    private static String sourceOf(String path) {
        for (String from : new String[]{"", "../"}) {
            java.nio.file.Path at = java.nio.file.Path.of(from + path);
            if (java.nio.file.Files.isReadable(at)) {
                try {
                    return java.nio.file.Files.readString(at);
                } catch (java.io.IOException unreadable) {
                    return null;
                }
            }
        }
        return null;
    }

    /* ------------------------------------------------------------- helpers */

    private static boolean refused(org.fractalmicro.foundation.FMDictionary description) {
        try {
            Nib.from(description);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static org.fractalmicro.foundation.FMDictionary unnamedControl() {
        org.fractalmicro.foundation.FMMutableDictionary control = org.fractalmicro.foundation.FMMutableDictionary.empty();
        control.set(Nib.CLASS, FMString.of("FMButton"));
        control.set(Nib.IDENTIFIER, FMString.of("nameless"));
        control.set(Nib.NAME, FMString.EMPTY);
        return describe(control);
    }

    private static org.fractalmicro.foundation.FMDictionary unknownClass() {
        org.fractalmicro.foundation.FMMutableDictionary control = org.fractalmicro.foundation.FMMutableDictionary.empty();
        control.set(Nib.CLASS, FMString.of("FMSomethingElse"));
        control.set(Nib.IDENTIFIER, FMString.of("odd"));
        control.set(Nib.NAME, FMString.of("Odd"));
        return describe(control);
    }

    private static org.fractalmicro.foundation.FMDictionary describe(org.fractalmicro.foundation.FMMutableDictionary control) {
        org.fractalmicro.foundation.FMMutableDictionary window = org.fractalmicro.foundation.FMMutableDictionary.empty();
        window.set(Nib.TITLE, FMString.of("Bad"));
        org.fractalmicro.foundation.FMMutableDictionary root = org.fractalmicro.foundation.FMMutableDictionary.empty();
        root.set(Nib.WINDOW, window.asDictionary());
        root.set(Nib.CONTROLS,
                 org.fractalmicro.foundation.FMArray.of((Object) control.asDictionary()));
        return root.asDictionary();
    }

    private static JInternalFrame frameTitled(Desktop desktop, String title) {
        for (JInternalFrame frame : desktop.windows()) {
            if (title.equals(frame.getTitle())) return frame;
        }
        return null;
    }

    /** Counts the named controls, and collects anything that has no name. */
    private static int countNamed(Component c, List<String> unnamed) {
        int named = 0;
        if (isControl(c) && c instanceof Accessible accessible) {
            AccessibleContext context = accessible.getAccessibleContext();
            String name = context == null ? null : context.getAccessibleName();
            if (name == null || name.isBlank()) unnamed.add(c.getClass().getSimpleName());
            else named++;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) named += countNamed(child, unnamed);
        }
        return named;
    }

    /** The things a person actually uses, rather than the panels holding them. */
    private static boolean isControl(Component c) {
        return c instanceof AbstractButton || c instanceof JLabel
            || c instanceof javax.swing.text.JTextComponent || c instanceof JComboBox
            || c instanceof JSlider || c instanceof JProgressBar || c instanceof JList;
    }

    private static JComponent componentNamed(Container root, String name) {
        if (root == null) return null;
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component && child instanceof Accessible accessible) {
                AccessibleContext context = accessible.getAccessibleContext();
                if (context != null && name.equals(context.getAccessibleName())) return component;
            }
            if (child instanceof Container container) {
                JComponent found = componentNamed(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void drain() {
        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(() -> { });
            }
            Thread.sleep(120);
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
