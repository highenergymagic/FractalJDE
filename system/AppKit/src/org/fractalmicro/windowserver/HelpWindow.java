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
package org.fractalmicro.windowserver;

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextArea;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Fractal Help, and the list of keyboard shortcuts. */
public class HelpWindow extends JInternalFrame {

    private HelpWindow(String title, JComponent body, int w, int h) {
        super(title, true, true, true, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.SEARCH, 16)));
        setContentPane(body);
        setSize(w, h);
        getAccessibleContext().setAccessibleName(title);
    }

    public static void openHelp() {
        FMTextArea text = new FMTextArea(org.fractalmicro.foundation.FMString.of(HELP));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Aqua.systemFont());
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        text.setCaretPosition(0);
        text.getAccessibleContext().setAccessibleName("Fractal Help");
        Desktop.get().addWindow(new HelpWindow("Fractal Help", new JScrollPane(text), 560, 460));
    }

    public static void showShortcuts() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"Shortcut", "What it does"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] row : SHORTCUTS) model.addRow(row);
        JTable table = new JTable(model);
        table.setFont(Aqua.smallFont());
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(340);
        table.getAccessibleContext().setAccessibleName("Keyboard shortcuts");
        Desktop.get().addWindow(new HelpWindow("Keyboard Shortcuts", new JScrollPane(table), 600, 500));
    }

    private static final String HELP =
        "FractalJDE\n"
      + "The Fractal Java Desktop Environment\n"
      + "-----------------------------------\n\n"
      + "A desktop and a Finder, written in Java Swing, running on Windows. "
      + "It reads and writes real files.\n\n"
      + "The desktop shows the folder Desktop-Folder in your home directory and, "
      + "depending on Finder Preferences, your disks and connected servers beside it.\n\n"
      + "Command is the Alt key and Option is the Windows key, because that is where "
      + "they sit on a PC keyboard. Command N, for a new Finder window, is Alt N here.\n\n"
      + "The Dock holds the Finder, your default web browser and mail client, anything "
      + "started from here, and the Trash. The Trash is the Windows Recycle Bin: items "
      + "moved there really are recycled, and emptying it really does empty it.\n\n"
      + "Keyboard\n"
      + "--------\n"
      + "Alt Windows M puts the keyboard on the menu bar; again for the status menus. "
      + "Alt Space opens Spotlight. Tab moves between the sidebar, the file list and "
      + "the toolbar; the toolbar, the window buttons and the Dock are each one stop, "
      + "with the arrow keys moving inside them and Escape leaving.\n\n"
      + "Screen readers\n"
      + "--------------\n"
      + "Swing talks to Windows screen readers through the Java Access Bridge. If "
      + "nothing is read, run jabswitch -enable once, then sign out and back in.\n\n"
      + "What is pretend\n"
      + "---------------\n"
      + "Sleep dims the screen until a key is pressed. Restart, Shut Down and Log Out "
      + "close this program and leave Windows alone. Software Update has nothing to "
      + "update.\n";

    private static final String[][] SHORTCUTS = {
        {"Alt Space", "Spotlight"},
        {"Alt Windows M", "Move to the menu bar; again for the status menus"},
        {"Alt Windows D", "Move to the Dock"},
        {"Alt N", "New Finder window"},
        {"Shift Alt N", "New folder"},
        {"Alt O", "Open the selection"},
        {"Alt Down", "Open the selection"},
        {"Return", "Rename the selection"},
        {"Alt W", "Close the front window"},
        {"Alt I", "Get Info"},
        {"Alt D", "Duplicate"},
        {"Alt L", "Make alias"},
        {"Alt Y", "Quick Look"},
        {"Alt Backspace", "Move to Trash"},
        {"Shift Alt Backspace", "Empty Trash"},
        {"Alt C / Alt V", "Copy and paste items"},
        {"Alt A", "Select all"},
        {"Alt 1 to Alt 4", "Icon, list, column and Cover Flow views"},
        {"Alt J", "Show view options"},
        {"Alt Up", "Enclosing folder"},
        {"Alt [ / Alt ]", "Back and forward"},
        {"Shift Alt A", "Applications"},
        {"Shift Alt U", "Utilities"},
        {"Shift Alt H", "Home"},
        {"Shift Alt D", "Desktop"},
        {"Shift Alt C", "Computer"},
        {"Shift Alt O", "Documents"},
        {"Shift Alt K", "Network"},
        {"Windows Alt L", "Downloads"},
        {"Shift Alt G", "Go to folder"},
        {"Alt K", "Connect to server"},
        {"Alt M", "Minimize the front window"},
        {"Alt backtick", "Cycle through windows"},
        {"Alt comma", "Finder preferences"},
        {"Windows Alt Escape", "Force Quit"},
        {"Alt F", "Find"},
        {"Alt /", "Show or hide the status bar"},
        {"Shift Alt /", "Fractal Help"},
    };
}
