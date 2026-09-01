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
package org.fractalmicro.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Scroll bars.
 *
 * A 15 pixel trough with a blue capsule running in it. Snow Leopard puts both arrows
 * together at the far end rather than one at each end, which was the "Together" setting
 * in Appearance preferences and the one it shipped with. Both buttons keep their own
 * behaviour; only the laying out is changed, so clicking the upper arrow still scrolls
 * up and the keyboard and accessibility sides are untouched.
 */
public class AquaScrollBarUI extends BasicScrollBarUI {

    private static final int WIDTH = 15;
    private static final int ARROW = 15;

    public static ComponentUI createUI(JComponent c) {
        return new AquaScrollBarUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        scrollbar.setOpaque(true);
        scrollBarWidth = WIDTH;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return scrollbar.getOrientation() == JScrollBar.VERTICAL
            ? new Dimension(WIDTH, WIDTH * 4)
            : new Dimension(WIDTH * 4, WIDTH);
    }

    @Override
    protected void paintTrack(Graphics g0, JComponent c, Rectangle bounds) {
        Graphics2D g = (Graphics2D) g0.create();
        AquaPainter.paintScrollTrack(g, bounds.x, bounds.y, bounds.width, bounds.height,
                                     scrollbar.getOrientation() == JScrollBar.VERTICAL);
        g.dispose();
    }

    @Override
    protected void paintThumb(Graphics g0, JComponent c, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g = (Graphics2D) g0.create();
        boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
        int inset = 2;
        AquaPainter.paintScrollThumb(g,
            bounds.x + (vertical ? inset : 1),
            bounds.y + (vertical ? 1 : inset),
            bounds.width - (vertical ? inset * 2 : 2),
            bounds.height - (vertical ? 2 : inset * 2),
            vertical);
        g.dispose();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return new ArrowButton(orientation, "Scroll up");
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return new ArrowButton(orientation, "Scroll down");
    }

    /* -------------------------------------------------------------- layout */

    @Override
    protected void layoutVScrollbar(JScrollBar sb) {
        Insets insets = sb.getInsets();
        int width = sb.getWidth() - insets.left - insets.right;
        int height = sb.getHeight() - insets.top - insets.bottom;
        int x = insets.left;
        int top = insets.top;

        int arrows = Math.min(ARROW * 2, Math.max(0, height));
        int trackHeight = Math.max(0, height - arrows);

        decrButton.setBounds(x, top + trackHeight, width, arrows / 2);
        incrButton.setBounds(x, top + trackHeight + arrows / 2, width, arrows - arrows / 2);
        trackRect.setBounds(x, top, width, trackHeight);
        layoutThumb(sb, trackRect, true);
    }

    @Override
    protected void layoutHScrollbar(JScrollBar sb) {
        Insets insets = sb.getInsets();
        int width = sb.getWidth() - insets.left - insets.right;
        int height = sb.getHeight() - insets.top - insets.bottom;
        int y = insets.top;
        int left = insets.left;

        int arrows = Math.min(ARROW * 2, Math.max(0, width));
        int trackWidth = Math.max(0, width - arrows);

        decrButton.setBounds(left + trackWidth, y, arrows / 2, height);
        incrButton.setBounds(left + trackWidth + arrows / 2, y, arrows - arrows / 2, height);
        trackRect.setBounds(left, y, trackWidth, height);
        layoutThumb(sb, trackRect, false);
    }

    /** Places the thumb in the track, in proportion to the value. */
    private void layoutThumb(JScrollBar sb, Rectangle track, boolean vertical) {
        int min = sb.getMinimum();
        int max = sb.getMaximum();
        int extent = sb.getVisibleAmount();
        int range = max - min;
        if (range <= 0 || extent >= range) {
            thumbRect.setBounds(0, 0, 0, 0);
            return;
        }
        int length = vertical ? track.height : track.width;
        int thumbLength = Math.max(WIDTH * 2, (int) ((float) extent / range * length));
        int available = length - thumbLength;
        int position = (int) ((float) (sb.getValue() - min) / (range - extent) * available);
        if (vertical) {
            thumbRect.setBounds(track.x, track.y + position, track.width, thumbLength);
        } else {
            thumbRect.setBounds(track.x + position, track.y, thumbLength, track.height);
        }
    }

    /** One arrow, drawn in the Aqua way and named for what it does. */
    private static class ArrowButton extends JButton {
        private final int orientation;

        ArrowButton(int orientation, String name) {
            this.orientation = orientation;
            setBorder(BorderFactory.createEmptyBorder());
            setFocusable(false);
            setOpaque(false);
            setRequestFocusEnabled(false);
            getAccessibleContext().setAccessibleName(name);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int direction;
            switch (orientation) {
                case SwingConstants.NORTH: direction = AquaPainter.SwingConstantsNorth; break;
                case SwingConstants.SOUTH: direction = AquaPainter.SwingConstantsSouth; break;
                case SwingConstants.WEST: direction = AquaPainter.SwingConstantsWest; break;
                default: direction = AquaPainter.SwingConstantsEast;
            }
            AquaPainter.paintScrollArrow(g, 0, 0, getWidth(), getHeight(),
                                         direction, getModel().isPressed());
            g.dispose();
        }

        @Override public Dimension getPreferredSize() {
            return orientation == SwingConstants.NORTH || orientation == SwingConstants.SOUTH
                ? new Dimension(WIDTH, ARROW) : new Dimension(ARROW, WIDTH);
        }
    }
}
