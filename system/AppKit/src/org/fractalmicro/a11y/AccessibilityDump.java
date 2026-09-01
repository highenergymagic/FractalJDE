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

import javax.accessibility.*;
import java.awt.Component;
import java.io.PrintStream;

/**
 * Walks the accessibility tree and prints it. Run the program with
 * --dump-accessibility to check what a screen reader will be handed: every row shows
 * the role, the name and whether the thing can take the keyboard.
 */
public final class AccessibilityDump {
    private AccessibilityDump() {}

    public static void dump(Component root, PrintStream out) {
        out.println("ROLE | NAME | STATES");
        out.println("-------------------------------------------------------------");
        walk(root instanceof Accessible ? (Accessible) root : null, out, 0);
    }

    private static void walk(Accessible a, PrintStream out, int depth) {
        if (a == null || depth > 14) return;
        AccessibleContext ctx = a.getAccessibleContext();
        if (ctx == null) return;

        AccessibleRole role = ctx.getAccessibleRole();
        String name = ctx.getAccessibleName();
        AccessibleStateSet states = ctx.getAccessibleStateSet();

        boolean interesting = name != null && !name.isBlank();
        if (interesting || role == AccessibleRole.MENU_BAR || role == AccessibleRole.PANEL) {
            out.println("  ".repeat(depth)
                + (role == null ? "?" : role.toDisplayString())
                + " | " + (name == null ? "(unnamed)" : name)
                + " | " + (states == null ? "" : states.toString()));
        }

        int count = ctx.getAccessibleChildrenCount();
        for (int i = 0; i < count && i < 400; i++) {
            walk(ctx.getAccessibleChild(i), out, depth + 1);
        }
    }

    /** Counts anything focusable that has no name; those are the accessibility bugs. */
    public static int countUnnamedFocusables(Component root, PrintStream out) {
        return countUnnamed(root instanceof Accessible ? (Accessible) root : null, out, 0);
    }

    /**
     * Containers are focusable in Swing without being controls; only things a person
     * actually operates need a name.
     */
    private static boolean isControl(AccessibleRole role) {
        return role == AccessibleRole.PUSH_BUTTON
            || role == AccessibleRole.TOGGLE_BUTTON
            || role == AccessibleRole.CHECK_BOX
            || role == AccessibleRole.RADIO_BUTTON
            || role == AccessibleRole.COMBO_BOX
            || role == AccessibleRole.TEXT
            || role == AccessibleRole.SLIDER
            || role == AccessibleRole.LIST
            || role == AccessibleRole.TABLE
            || role == AccessibleRole.TREE
            || role == AccessibleRole.MENU
            || role == AccessibleRole.MENU_ITEM
            || role == AccessibleRole.PAGE_TAB
            || role == AccessibleRole.SPLIT_PANE;
    }

    private static int countUnnamed(Accessible a, PrintStream out, int depth) {
        if (a == null || depth > 14) return 0;
        AccessibleContext ctx = a.getAccessibleContext();
        if (ctx == null) return 0;
        int total = 0;
        AccessibleStateSet states = ctx.getAccessibleStateSet();
        boolean focusable = states != null && states.contains(AccessibleState.FOCUSABLE);
        String name = ctx.getAccessibleName();
        if (focusable && isControl(ctx.getAccessibleRole()) && (name == null || name.isBlank())) {
            total++;
            out.println("unnamed focusable: " + ctx.getAccessibleRole());
        }
        int count = ctx.getAccessibleChildrenCount();
        for (int i = 0; i < count && i < 400; i++) {
            total += countUnnamed(ctx.getAccessibleChild(i), out, depth + 1);
        }
        return total;
    }
}
