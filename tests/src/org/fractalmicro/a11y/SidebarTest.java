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
import org.fractalmicro.ui.Finder;
import org.fractalmicro.ui.FinderWindow;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;

/**
 * The sidebar, which is a table.
 *
 * A tree announces itself as a tree: levels, expanding, collapsing. None of that is true
 * of a source list, which has nothing to expand. It has headings and places in one flat
 * column, and a table is the control that says so.
 *
 * A heading is not somewhere to go, so it cannot be selected and the arrow keys step over
 * it, and it says out loud that it is a heading rather than sounding like a place.
 */
public final class SidebarTest {
    private SidebarTest() {}

    public static int count() { return 6; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the sidebar:");

        FinderWindow window = Finder.frontWindow();
        if (window == null) window = Finder.newWindow(org.fractalmicro.fs.FS.home());
        JTable table = find(window);

        failures += check(out, "the sidebar is a table", table != null);
        if (table == null) {
            out.println("      (nothing else can be checked without it)");
            return failures + count() - 1;
        }

        AccessibleContext context = table.getAccessibleContext();
        failures += check(out, "and says it is a table, not a tree",
            AccessibleRole.TABLE.equals(context.getAccessibleRole())
            && "Sidebar".equals(context.getAccessibleName()));

        int headings = 0;
        int places = 0;
        int firstPlace = -1;
        for (int row = 0; row < table.getRowCount(); row++) {
            String spoken = spokenAt(table, row);
            if (spoken.endsWith(", heading")) headings++;
            else {
                places++;
                if (firstPlace < 0) firstPlace = row;
            }
        }
        out.println("      " + table.getRowCount() + " rows: " + headings
                    + " headings and " + places + " places");
        failures += check(out, "it has headings and places, and says which is which",
            headings >= 2 && places >= 2);

        failures += check(out, "a heading names itself as one",
            spokenAt(table, 0).endsWith(", heading"));

        // Moving down from the last place of a group must land on the next place, not on
        // the heading in between.
        boolean skipped = true;
        if (firstPlace >= 0) {
            table.setRowSelectionInterval(firstPlace, firstPlace);
            Action down = table.getActionMap().get("nextPlace");
            if (down == null) {
                skipped = false;
            } else {
                down.actionPerformed(new java.awt.event.ActionEvent(table, 0, "down"));
                int landed = table.getSelectedRow();
                skipped = landed > firstPlace && !spokenAt(table, landed).endsWith(", heading");
                out.println("      down from row " + firstPlace + " landed on row " + landed);
            }
        }
        failures += check(out, "the arrow keys step over the headings", skipped);

        // Selecting a heading must not open anything. Checked by where the window is
        // before and after, because that is what going somewhere means.
        java.io.File before = window.currentFolder();
        boolean wentNowhere = true;
        for (int row = 0; row < table.getRowCount(); row++) {
            if (!spokenAt(table, row).endsWith(", heading")) continue;
            table.clearSelection();
            table.setRowSelectionInterval(row, row);
            drain();
            java.io.File after = window.currentFolder();
            wentNowhere = before == null ? after == null : before.equals(after);
            out.println("      selecting the heading on row " + row + " left the window at "
                        + (after == null ? "nowhere" : after.getName()));
            break;
        }
        failures += check(out, "selecting a heading does not open anything", wentNowhere);

        out.println("      " + (failures == 0 ? "the sidebar reads as what it is"
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

    /** What a screen reader would say for one row. */
    private static String spokenAt(JTable table, int row) {
        Component rendered = table.getCellRenderer(row, 0)
            .getTableCellRendererComponent(table, table.getValueAt(row, 0), false, false, row, 0);
        if (!(rendered instanceof Accessible accessible)) return "";
        AccessibleContext context = accessible.getAccessibleContext();
        String name = context == null ? null : context.getAccessibleName();
        return name == null ? "" : name;
    }

    private static JTable find(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTable table && child instanceof Accessible accessible
                && "Sidebar".equals(accessible.getAccessibleContext().getAccessibleName())) {
                return table;
            }
            if (child instanceof Container inner) {
                JTable found = find(inner);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
