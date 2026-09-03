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

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.theme.Aqua;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * One window of this system, as a window of the host system.
 *
 * The windows here are internal frames, carrying their own title bar, buttons, focus rules
 * and accessible tree. Inside one big window the host sees one application with one entry
 * in its window list, and nothing can be moved past the edge of the desktop.
 *
 * So each gets an undecorated frame of its own with the internal frame filled out inside
 * it. The title bar this program draws becomes the window's, dragging it moves the real
 * window, and the three buttons act on the real window. Nothing above here changes.
 */
public class AppFrame extends JFrame {

    private final JInternalFrame window;
    private final JDesktopPane holder = new JDesktopPane();
    private Point grabbedAt;

    public AppFrame(JInternalFrame window) {
        super(window.getTitle());
        this.window = window;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        holder.setBorder(BorderFactory.createEmptyBorder());
        holder.setDesktopManager(new DefaultDesktopManager());
        holder.setBackground(Aqua.WINDOW_BG);
        holder.add(window);
        setContentPane(holder);

        Dimension size = window.getSize();
        if (size.width <= 0 || size.height <= 0) size = window.getPreferredSize();
        setSize(size);

        window.setLocation(0, 0);
        window.setVisible(true);
        // A maximised internal frame cannot be dragged about inside its pane, which is
        // exactly right here: the thing that moves is the window around it.
        try {
            window.setMaximum(true);
        } catch (java.beans.PropertyVetoException ignored) { }

        holder.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                window.setBounds(0, 0, holder.getWidth(), holder.getHeight());
            }
        });

        installTitleBarDrag();
        installEdgeResize();

        window.addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameClosed(InternalFrameEvent e) {
                dispose();
            }
            @Override public void internalFrameIconified(InternalFrameEvent e) {
                setState(Frame.ICONIFIED);
            }
        });
        window.addPropertyChangeListener(JInternalFrame.TITLE_PROPERTY,
            e -> setTitle(window.getTitle()));

        // The frame is a container the host system knows about; the window inside it is
        // the one somebody opened, so the frame takes its name.
        getAccessibleContext().setAccessibleName(window.getTitle());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                window.doDefaultCloseAction();
            }
            @Override public void windowActivated(java.awt.event.WindowEvent e) {
                Desktop desktop = Desktop.sharedDesktop();
                if (desktop != null) desktop.frameActivated(AppFrame.this);
            }
        });
    }

    /** The window this frame is carrying. */
    public JInternalFrame window() { return window; }

    /* ----------------------------------------------------------- the mouse */

    /**
     * Dragging the title bar moves the whole window. The internal frame is maximised, so
     * its own dragging is switched off and this is the only handler that runs.
     */
    private void installTitleBarDrag() {
        Component north = window.getUI() instanceof BasicInternalFrameUI ui
            ? ui.getNorthPane() : null;
        if (north == null) return;
        north.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                grabbedAt = e.getPoint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                grabbedAt = null;
            }
            @Override public void mouseClicked(MouseEvent e) {
                // Two clicks on the title bar put a window away, as they do in Aqua.
                if (e.getClickCount() == 2) setState(Frame.ICONIFIED);
            }
        });
        north.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (grabbedAt == null) return;
                Point onScreen = e.getLocationOnScreen();
                setLocation(onScreen.x - grabbedAt.x, onScreen.y - grabbedAt.y);
            }
        });
    }

    /** An undecorated window still has to be resizable, so the edges do it. */
    private void installEdgeResize() {
        final int grip = 5;
        MouseAdapter resizer = new MouseAdapter() {
            private Point start;
            private Rectangle bounds;
            private int where;

            private int edgeAt(Point p) {
                int mask = 0;
                if (p.x <= grip) mask |= 1;
                if (p.y <= grip) mask |= 2;
                if (p.x >= getWidth() - grip) mask |= 4;
                if (p.y >= getHeight() - grip) mask |= 8;
                return mask;
            }

            @Override public void mouseMoved(MouseEvent e) {
                int mask = edgeAt(e.getPoint());
                setCursor(Cursor.getPredefinedCursor(switch (mask) {
                    case 1 -> Cursor.W_RESIZE_CURSOR;
                    case 2 -> Cursor.N_RESIZE_CURSOR;
                    case 4 -> Cursor.E_RESIZE_CURSOR;
                    case 8 -> Cursor.S_RESIZE_CURSOR;
                    case 5, 10 -> Cursor.MOVE_CURSOR;
                    case 9 -> Cursor.SW_RESIZE_CURSOR;
                    case 12 -> Cursor.SE_RESIZE_CURSOR;
                    case 3 -> Cursor.NW_RESIZE_CURSOR;
                    case 6 -> Cursor.NE_RESIZE_CURSOR;
                    default -> Cursor.DEFAULT_CURSOR;
                }));
            }

            @Override public void mousePressed(MouseEvent e) {
                where = edgeAt(e.getPoint());
                start = e.getLocationOnScreen();
                bounds = getBounds();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (where == 0 || start == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - start.x;
                int dy = now.y - start.y;
                Rectangle r = new Rectangle(bounds);
                if ((where & 1) != 0) { r.x += dx; r.width -= dx; }
                if ((where & 2) != 0) { r.y += dy; r.height -= dy; }
                if ((where & 4) != 0) r.width += dx;
                if ((where & 8) != 0) r.height += dy;
                r.width = Math.max(200, r.width);
                r.height = Math.max(120, r.height);
                setBounds(r);
                validate();
            }

            @Override public void mouseReleased(MouseEvent e) {
                where = 0;
                start = null;
            }
        };
        getLayeredPane().addMouseListener(resizer);
        getLayeredPane().addMouseMotionListener(resizer);
    }

    /* ---------------------------------------------------------- appearance */

    /** Puts the window where a new window goes, stepping down and across from the last. */
    public void placeAt(int index, Rectangle screen) {
        int offset = 22 * (index % 8);
        int x = screen.x + 60 + offset;
        int y = screen.y + 40 + offset;
        setLocation(Math.min(x, screen.x + Math.max(0, screen.width - getWidth())),
                    Math.min(y, screen.y + Math.max(0, screen.height - getHeight())));
    }
}
