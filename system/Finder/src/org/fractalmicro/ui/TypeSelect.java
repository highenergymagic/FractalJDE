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
package org.fractalmicro.ui;

import javax.swing.JTable;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Typing a name to get to it.
 *
 * A list gets this from the runtime and a table does not, so the icon view had it and the
 * list view did not, which is the same window behaving differently depending on a button
 * at the top of it.
 */
final class TypeSelect {

    /** How long a letter waits for the next one before it starts a new word. */
    static final long PATIENCE = 1000;

    private final StringBuilder typed = new StringBuilder();
    private long lastAt;

    /** Installs it on a table, told how to read the name in a row. */
    static void install(JTable table, IntFunction<String> nameAt) {
        TypeSelect state = new TypeSelect();
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) return;
                char c = e.getKeyChar();
                if (c < ' ' || c == 0x7F) return;
                int row = state.rowFor(c, table.getRowCount(), nameAt,
                                       table.getSelectedRow(), e.getWhen());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                e.consume();
            }
        });
    }

    /**
     * Which row that letter means, or -1 for none.
     *
     * A letter typed soon after the last one lengthens what is being looked for; typed
     * after a pause it starts again. The same letter over and over steps through the
     * things beginning with it, which is what a list does and what a person expects.
     */
    private int rowFor(char c, int rows, IntFunction<String> nameAt, int selected,
                       long when) {
        if (rows <= 0) return -1;
        if (when - lastAt > PATIENCE) typed.setLength(0);
        lastAt = when;

        boolean stepping = typed.length() == 1
            && Character.toLowerCase(typed.charAt(0)) == Character.toLowerCase(c);
        if (!stepping) typed.append(c);
        String wanted = typed.toString().toLowerCase(Locale.ROOT);

        int from = stepping || wanted.length() == 1 ? selected + 1 : Math.max(selected, 0);
        for (int step = 0; step < rows; step++) {
            int row = Math.floorMod(from + step, rows);
            String name = nameAt.apply(row);
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(wanted)) return row;
        }
        // Nothing begins with it, so what was typed was not the start of a name. Keeping
        // it would make every later letter miss too.
        typed.setLength(0);
        return -1;
    }
}
