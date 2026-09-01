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


import org.fractalmicro.core.Log;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.win.AppBar;

import javax.swing.*;
import java.awt.*;

/**
 * A strip along an edge of the screen: the menu bar at the top, the Dock at the bottom.
 *
 * The strip is a window of its own, always above other windows, and it asks the shell to
 * reserve its edge. That reservation is the difference between a menu bar and a window
 * that happens to be at the top: with it, something maximised stops underneath rather
 * than covering it.
 *
 * If the shell will not reserve the edge, which happens when Explorer is not running the
 * desktop, the strip still works and simply stays on top. That is logged rather
 * than hidden, because the difference is visible the first time a window is maximised.
 */
public class ScreenBar extends JDialog {

    private final int edge;
    private final int thickness;
    private AppBar reserved;

    public ScreenBar(Window owner, JComponent content, int edge, int thickness, String name) {
        this(owner, content, edge, thickness, name, name);
    }

    /**
     * @param name  the window title, which is how the shell is told which window to
     *              reserve an edge for, so it has to be unlike any other
     * @param spoken what this is called, which should be what it is
     */
    public ScreenBar(Window owner, JComponent content, int edge, int thickness, String name,
                     String spoken) {
        // A dialog rather than a plain window, because it can carry a title: the shell
        // is told which window to reserve an edge for by name, and an owned dialog also
        // stays out of the host system's window list, which a strip should.
        super(owner, name);
        this.edge = edge;
        this.thickness = thickness;

        setUndecorated(true);
        setAlwaysOnTop(true);
        setFocusableWindowState(true);
        setBackground(new Color(0, 0, 0, 0));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(content, BorderLayout.CENTER);
        setContentPane(body);
        getAccessibleContext().setAccessibleName(spoken);
        setBounds(placeOnScreen());
    }

    /** Where the strip goes: the full width of the screen, at its edge. */
    private Rectangle placeOnScreen() {
        Rectangle screen = screenBounds();
        return edge == AppBar.ABE_TOP
            ? new Rectangle(screen.x, screen.y, screen.width, thickness)
            : new Rectangle(screen.x, screen.y + screen.height - thickness,
                            screen.width, thickness);
    }

    public static Rectangle screenBounds() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    /**
     * Shows the strip and claims its edge. The window has to exist before the shell can
     * be told about it, so this happens here rather than in the constructor.
     */
    public void show(boolean reserve) {
        setVisible(true);
        if (!reserve) return;
        long handle = org.fractalmicro.win.User32.handleOf(this);
        if (handle == 0) {
            Log.info("no window handle for the " + getAccessibleContext().getAccessibleName()
                     + "; the edge is not reserved");
            return;
        }
        reserved = AppBar.claim(handle, edge, placeOnScreen());
        if (reserved != null) {
            Rectangle given = reserved.setPosition(placeOnScreen());
            setBounds(given);
            Log.info("reserved " + given + " for the "
                     + getAccessibleContext().getAccessibleName());
        }
    }

    /** Gives the edge back. Left registered, it would shrink the desktop for good. */
    public void release() {
        if (reserved != null) {
            reserved.release();
            reserved = null;
        }
    }

    public boolean reservedEdge() { return reserved != null && reserved.isRegistered(); }

    /** The part of the screen left over once the strips have taken theirs. */
    public static Rectangle workArea() {
        Rectangle screen = screenBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration());
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                             screen.width - insets.left - insets.right,
                             screen.height - insets.top - insets.bottom);
    }

    @Override public void paint(Graphics g) {
        super.paint(g);
    }

    /** The height a menu bar wants, which is the one measurement Aqua is firm about. */
    public static int menuBarHeight() { return Aqua.MENU_BAR_HEIGHT; }
}
