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
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Xib;
import org.fractalmicro.os.FMUserDefaultsController;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A control joined to a setting, with no program in between.
 *
 * The interface file says which setting a control shows. From then on the control reads it,
 * writes it and hears it change, and the program that described the window is not told about
 * any of it and has no code that could get it wrong. That is a binding, and it is what took
 * fourteen pairs of a getter and a setter out of the settings program.
 *
 * The last check is the one that catches the mistake this makes easy. A control bound to a
 * setting nobody registered a default for comes up showing nothing, because an unset key
 * reads as nothing, while the rest of the system goes on using the fallback written in its
 * code. The switch then says one thing and the machine does another, and there is no error
 * anywhere. It happened to Show Labels the first time this was tried.
 */
public final class BindingTest {
    private BindingTest() {}

    public static int count() { return 7; }

    private static final FMString PATH = FMString.of("values.finder.WarnOnEmptyTrash");

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("controls joined to settings:");

        /* ------------------------------------------------------------ the path */

        failures += check(out, "a key path names a domain and a setting",
            FMUserDefaultsController.isSetting(PATH)
            && FMUserDefaultsController.isSetting(FMString.of("values.dock.tilesize"))
            && FMUserDefaultsController.isSetting(FMString.of("values.global.AppleShowAllExtensions")));

        failures += check(out, "and anything else is not one",
            !FMUserDefaultsController.isSetting(FMString.of("WarnOnEmptyTrash"))
            && !FMUserDefaultsController.isSetting(FMString.of("values.nowhere.Thing"))
            && !FMUserDefaultsController.isSetting(FMString.of("values.finder"))
            && !FMUserDefaultsController.isSetting(null));

        /* ------------------------------------------------- a control that is bound */

        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running");
            return failures + 5;
        }

        boolean was = org.fractalmicro.os.FinderSettings.warnOnEmptyTrash();
        try (FMApplication app = FMApplication.named(FMString.of("Binding"))) {
            FMUserDefaultsController.setValue(PATH, Boolean.TRUE);

            app.showWindow(new Nib.Builder()
                .title(FMString.of("Binding")).size(320, 120)
                .add(Nib.Control.of(Nib.ControlClass.FMCheckBox, FMString.of("warn"))
                        .named(FMString.of("Warn")).showing(FMString.of("Warn"))
                        .boundTo(PATH)
                        .at(12, 12, 280, 22))
                .build());
            drain();

            JInternalFrame frame = frameTitled(desktop, "Binding");
            JCheckBox box = frame == null ? null : (JCheckBox) named(frame, "Warn");
            failures += check(out, "the control comes up showing what the setting says",
                box != null && box.isSelected());

            // The setting changed by something else entirely, which is what happens when
            // one program writes a preference another has a window open on.
            FMUserDefaultsController.setValue(PATH, Boolean.FALSE);
            drain();
            failures += check(out, "and follows it when something else writes it",
                box != null && !box.isSelected());

            // And the other way. Nothing in the program is listening; the control writes.
            if (box != null) {
                box.setSelected(true);
                for (java.awt.event.ActionListener listener : box.getActionListeners()) {
                    listener.actionPerformed(
                        new java.awt.event.ActionEvent(box, 0, "test"));
                }
            }
            drain();
            failures += check(out, "and using it writes the setting",
                Boolean.TRUE.equals(FMUserDefaultsController.value(PATH)));

            app.close(app.mainWindow());
        } catch (Exception e) {
            out.println("FAIL  a control that is bound: " + e);
            failures++;
        } finally {
            FMUserDefaultsController.setValue(PATH, was);
            drain();
        }

        /* ------------------------------- and every bound setting has somewhere to start */

        List<String> unregistered = new ArrayList<>();
        for (FMString path : boundPaths()) {
            if (FMUserDefaultsController.value(path) == null) unregistered.add(path.toString());
        }
        for (String one : unregistered) out.println("      nothing registered for " + one);
        failures += check(out, "every setting a control is bound to has a value to start from",
            unregistered.isEmpty());

        out.println("      " + (failures == 0
            ? "a control and its setting are the same thing"
            : failures + " failed"));
        return failures;
    }

    /**
     * Every setting the interface files bind a control to.
     *
     * Read off the files rather than listed here, so a binding added later is checked
     * without this being told about it, which is the same rule the words follow.
     */
    private static List<FMString> boundPaths() {
        List<FMString> found = new ArrayList<>();
        for (String where : new String[]{"apps", "../apps", "system", "../system"}) {
            File at = new File(where);
            if (at.isDirectory()) gather(at, found);
        }
        return found;
    }

    private static void gather(File directory, List<FMString> into) {
        File[] kids = directory.listFiles();
        if (kids == null) return;
        for (File each : kids) {
            if (each.isDirectory()) {
                gather(each, into);
            } else if (each.getName().endsWith(".xib")) {
                try {
                    for (Nib.Control control : Xib.read(FMURL.of(each)).controls()) {
                        if (!control.boundTo().isBlank()) into.add(control.boundTo());
                    }
                } catch (Exception unreadable) {
                    // A file that will not parse is a different check's business.
                }
            }
        }
    }

    private static JInternalFrame frameTitled(Desktop desktop, String title) {
        for (JInternalFrame frame : desktop.windows()) {
            if (title.equals(frame.getTitle())) return frame;
        }
        return null;
    }

    private static JComponent named(java.awt.Container in, String name) {
        for (java.awt.Component child : in.getComponents()) {
            if (child instanceof JComponent one
                    && name.equals(one.getAccessibleContext().getAccessibleName())) {
                return one;
            }
            if (child instanceof java.awt.Container inside) {
                JComponent found = named(inside, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void drain() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) { }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
