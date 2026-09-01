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


import org.fractalmicro.os.FinderSettings;

import java.awt.*;
import java.awt.geom.*;

/**
 * The look: colours, fonts and the handful of gradients that Mac OS X 10.6 leans on.
 * Everything here is drawn rather than loaded, so there are no image assets to ship.
 */
public final class Aqua {
    private Aqua() {}

    public static final int MENU_BAR_HEIGHT = 22;

    // Menu bar and menus
    public static final Color MENUBAR_TOP     = new Color(0xF7F7F7);
    public static final Color MENUBAR_BOTTOM  = new Color(0xD8D8D8);
    public static final Color MENUBAR_EDGE    = new Color(0x9A9A9A);
    public static final Color MENU_BG         = new Color(0xFFFFFF);
    public static final Color MENU_BORDER     = new Color(0xB4B4B4);
    public static final Color MENU_TEXT       = new Color(0x000000);
    public static final Color MENU_DISABLED   = new Color(0x9C9C9C);
    public static final Color SEPARATOR       = new Color(0xDCDCDC);

    // Highlight (System Preferences > Appearance > Blue)
    public static final Color HILITE_TOP      = new Color(0x5C9DEF);
    public static final Color HILITE_BOTTOM   = new Color(0x1E62D0);
    public static final Color HILITE_TEXT     = Color.WHITE;
    public static final Color SELECTION       = new Color(0x3875D7);
    public static final Color SELECTION_INACTIVE = new Color(0xC8C8C8);

    // Windows
    public static final Color TITLE_ACTIVE_TOP    = new Color(0xE8E8E8);
    public static final Color TITLE_ACTIVE_BOTTOM = new Color(0xC2C2C2);
    public static final Color TITLE_INACTIVE_TOP  = new Color(0xF6F6F6);
    public static final Color TITLE_INACTIVE_BOTTOM = new Color(0xE4E4E4);
    public static final Color TITLE_TEXT      = new Color(0x2B2B2B);
    public static final Color TITLE_TEXT_OFF  = new Color(0x8C8C8C);
    public static final Color WINDOW_BG       = new Color(0xF2F2F2);
    public static final Color WINDOW_EDGE     = new Color(0x7A7A7A);
    public static final Color LIST_BG         = Color.WHITE;
    public static final Color LIST_STRIPE     = new Color(0xEDF3FE);
    public static final Color SIDEBAR_BG      = new Color(0xD8DEE6);
    public static final Color SIDEBAR_HEADER  = new Color(0x74808F);
    public static final Color SIDEBAR_TEXT    = new Color(0x2C2C2C);
    public static final Color TOOLBAR_TOP     = new Color(0xE4E4E4);
    public static final Color TOOLBAR_BOTTOM  = new Color(0xC9C9C9);
    public static final Color STATUSBAR_BG    = new Color(0xEDEDED);

    // Traffic lights
    public static final Color CLOSE_RED    = new Color(0xFF5F57);
    public static final Color MIN_YELLOW   = new Color(0xFFBD2E);
    public static final Color ZOOM_GREEN   = new Color(0x28C940);

    // Dock
    public static final Color DOCK_GLASS_TOP    = new Color(255, 255, 255, 40);
    public static final Color DOCK_GLASS_BOTTOM = new Color(160, 165, 175, 150);
    public static final Color DOCK_EDGE         = new Color(255, 255, 255, 110);
    public static final Color DOCK_SHELF_LINE   = new Color(255, 255, 255, 200);

    // Desktop icon labels
    public static final Color DESKTOP_LABEL      = Color.WHITE;
    public static final Color DESKTOP_LABEL_SHADOW = new Color(0, 0, 0, 160);

    private static Font base(int style, float size) {
        String[] candidates = {"Lucida Grande", "Lucida Sans Unicode", "Segoe UI", Font.SANS_SERIF};
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String c : candidates) {
            for (String f : families) {
                if (f.equalsIgnoreCase(c)) return new Font(f, style, Math.round(size));
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    private static Font menuFont, systemFont, emphasizedSystemFont, smallFont,
                        emphasizedSmallFont, viewFont, labelFont, miniFont, titleFont;

    // The sizes are Apple's: system 13, small 11, views 12, labels 10, mini 9.
    public static Font menuFont() { if (menuFont == null) menuFont = base(Font.PLAIN, 13); return menuFont; }
    public static Font systemFont() { if (systemFont == null) systemFont = base(Font.PLAIN, 13); return systemFont; }
    public static Font emphasizedSystemFont() {
        if (emphasizedSystemFont == null) emphasizedSystemFont = base(Font.BOLD, 13);
        return emphasizedSystemFont;
    }
    public static Font smallFont() { if (smallFont == null) smallFont = base(Font.PLAIN, 11); return smallFont; }
    public static Font emphasizedSmallFont() {
        if (emphasizedSmallFont == null) emphasizedSmallFont = base(Font.BOLD, 11);
        return emphasizedSmallFont;
    }
    /** Text in lists and tables, and icon labels: 12 point, not 11. */
    public static Font viewFont() { if (viewFont == null) viewFont = base(Font.PLAIN, 12); return viewFont; }
    public static Font labelFont() { if (labelFont == null) labelFont = base(Font.PLAIN, 10); return labelFont; }
    public static Font miniFont() { if (miniFont == null) miniFont = base(Font.PLAIN, 9); return miniFont; }
    public static Font titleFont() { if (titleFont == null) titleFont = base(Font.BOLD, 13); return titleFont; }

    /** Apple's stated spacing, in pixels. */
    public static final int WINDOW_MARGIN = 20;
    public static final int ALERT_SIDE_MARGIN = 24;
    public static final int ALERT_BOTTOM_MARGIN = 20;
    public static final int CONTROL_SPACING = 8;
    public static final int GROUP_SPACING = 12;
    public static final int TITLE_BAR_TO_CONTENT = 14;
    public static final int LIST_ROW_HEIGHT = 17;

    public static boolean highContrast() { return FinderSettings.highContrast(); }

    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static void vgradient(Graphics2D g, int x, int y, int w, int h, Color top, Color bottom) {
        if (w <= 0 || h <= 0) return;
        g.setPaint(new GradientPaint(x, y, top, x, y + h, bottom));
        g.fillRect(x, y, w, h);
    }

    public static void paintMenuBar(Graphics2D g, int w, int h) {
        if (highContrast()) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE);
            g.drawLine(0, h - 1, w, h - 1);
            return;
        }
        vgradient(g, 0, 0, w, h - 1, MENUBAR_TOP, MENUBAR_BOTTOM);
        g.setColor(MENUBAR_EDGE);
        g.drawLine(0, h - 1, w, h - 1);
    }

    public static void paintHighlight(Graphics2D g, int x, int y, int w, int h) {
        if (highContrast()) {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h);
            return;
        }
        vgradient(g, x, y, w, h, HILITE_TOP, HILITE_BOTTOM);
    }

    /** Rounded rectangle with the soft 10.6 window shadow underneath. */
    public static void paintWindowFrame(Graphics2D g, int w, int h, boolean active) {
        antialias(g);
        Shape r = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(WINDOW_BG);
        g.fill(r);
        g.setColor(active ? WINDOW_EDGE : new Color(0xA9A9A9));
        g.draw(r);
    }

    public static void paintTitleBar(Graphics2D g, int w, int h, boolean active) {
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Float(0, 0, w, h * 2f, 10, 10));
        if (highContrast()) {
            g.setColor(active ? Color.BLACK : new Color(0x202020));
            g.fillRect(0, 0, w, h);
        } else {
            vgradient(g, 0, 0, w, h,
                active ? TITLE_ACTIVE_TOP : TITLE_INACTIVE_TOP,
                active ? TITLE_ACTIVE_BOTTOM : TITLE_INACTIVE_BOTTOM);
        }
        g.setClip(clip);
        g.setColor(new Color(0x9B9B9B));
        g.drawLine(0, h - 1, w, h - 1);
    }

    /** One traffic light. Grey when the window is not in front, as in the real thing. */
    public static void paintTrafficLight(Graphics2D g, int x, int y, int d, Color base,
                                         boolean active, boolean hover, String glyph) {
        antialias(g);
        Color fill = active ? base : new Color(0xD4D4D4);
        g.setPaint(new GradientPaint(x, y, fill.brighter(), x, y + d, fill.darker()));
        g.fillOval(x, y, d, d);
        g.setColor(new Color(0, 0, 0, 60));
        g.drawOval(x, y, d, d);
        g.setColor(new Color(255, 255, 255, 130));
        g.drawArc(x + 1, y + 1, d - 2, d - 2, 40, 100);
        if (hover && glyph != null) {
            g.setColor(new Color(0, 0, 0, 150));
            g.setFont(smallFont().deriveFont(Font.BOLD, 8f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(glyph, x + (d - fm.stringWidth(glyph)) / 2f,
                         y + (d + fm.getAscent()) / 2f - 1);
        }
    }

    /** Focus ring, drawn wherever a real Mac would draw one. */
    public static void paintFocusRing(Graphics2D g, int x, int y, int w, int h, int arc) {
        antialias(g);
        Composite old = g.getComposite();
        g.setColor(highContrast() ? Color.YELLOW : new Color(0x4A90E2));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 3, h - 3, arc, arc));
        g.setComposite(old);
        g.setStroke(new BasicStroke(1f));
    }

    public static void drawShadowedString(Graphics2D g, String s, float x, float y) {
        g.setColor(DESKTOP_LABEL_SHADOW);
        g.drawString(s, x + 1, y + 1);
        g.setColor(DESKTOP_LABEL);
        g.drawString(s, x, y);
    }

    /** Trims a label with an ellipsis in the middle, the way Finder does. */
    public static String clipMiddle(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String ell = "…";
        int keep = text.length();
        while (keep > 4) {
            keep--;
            int half = keep / 2;
            String candidate = text.substring(0, half) + ell + text.substring(text.length() - (keep - half));
            if (fm.stringWidth(candidate) <= width) return candidate;
        }
        return ell;
    }

    public static String clipEnd(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return text;
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 1 && fm.stringWidth(sb + "…") > width) sb.setLength(sb.length() - 1);
        return sb + "…";
    }
}
