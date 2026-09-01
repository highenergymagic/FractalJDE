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
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Shared drawing for menu items and menu titles, plus the shortcut symbols.
 *
 * Keyboard mapping note: this runs on a PC keyboard, so Command lives where Alt is.
 * Alt is drawn as the Command clover, the Windows key as Option, and Control as the
 * Control chevron. What Swing reports is still the real modifier, not the symbol.
 */
public final class AquaMenuPainter {
    private AquaMenuPainter() {}

    public static final int GUTTER = 22;
    public static final int RIGHT_PAD = 14;
    public static final int ITEM_HEIGHT = 20;

    private static final char CMD = '⌘';
    private static final char SHIFT = '⇧';
    private static final char OPT = '⌥';
    private static final char CTRL = '⌃';
    private static final char DEL = '⌫';
    private static final char RET = '↩';

    private static Boolean symbolsAvailable;

    private static boolean symbols() {
        if (symbolsAvailable == null) {
            Font f = Aqua.menuFont();
            symbolsAvailable = f.canDisplay(CMD) && f.canDisplay(SHIFT) && f.canDisplay(OPT);
        }
        return symbolsAvailable;
    }

    /** "⇧⌘A" when the font can draw it, "Shift+Alt+A" when it cannot. */
    public static String acceleratorText(KeyStroke ks) {
        if (ks == null) return "";
        int mods = ks.getModifiers();
        StringBuilder sb = new StringBuilder();
        boolean glyphs = symbols();
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0 || (mods & InputEvent.CTRL_MASK) != 0)
            sb.append(glyphs ? String.valueOf(CTRL) : "Ctrl+");
        if ((mods & InputEvent.META_DOWN_MASK) != 0 || (mods & InputEvent.META_MASK) != 0)
            sb.append(glyphs ? String.valueOf(OPT) : "Win+");
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0 || (mods & InputEvent.SHIFT_MASK) != 0)
            sb.append(glyphs ? String.valueOf(SHIFT) : "Shift+");
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0 || (mods & InputEvent.ALT_MASK) != 0)
            sb.append(glyphs ? String.valueOf(CMD) : "Alt+");
        sb.append(keyText(ks.getKeyCode(), ks.getKeyChar(), glyphs));
        return sb.toString();
    }

    private static String keyText(int code, char ch, boolean glyphs) {
        switch (code) {
            case KeyEvent.VK_BACK_SPACE: return glyphs ? String.valueOf(DEL) : "Delete";
            case KeyEvent.VK_DELETE:     return glyphs ? String.valueOf(DEL) : "Del";
            case KeyEvent.VK_ENTER:      return glyphs ? String.valueOf(RET) : "Return";
            case KeyEvent.VK_UP:         return "↑";
            case KeyEvent.VK_DOWN:       return "↓";
            case KeyEvent.VK_LEFT:       return "←";
            case KeyEvent.VK_RIGHT:      return "→";
            case KeyEvent.VK_SPACE:      return "Space";
            case KeyEvent.VK_ESCAPE:     return "Esc";
            case KeyEvent.VK_OPEN_BRACKET:  return "[";
            case KeyEvent.VK_CLOSE_BRACKET: return "]";
            case KeyEvent.VK_COMMA:      return ",";
            case KeyEvent.VK_PERIOD:     return ".";
            case KeyEvent.VK_SLASH:      return "/";
            case KeyEvent.VK_BACK_QUOTE: return "`";
            case 0: return String.valueOf(Character.toUpperCase(ch));
            default: return KeyEvent.getKeyText(code);
        }
    }

    /** Paints one item in a drop-down menu. */
    public static void paintItem(Graphics g0, JMenuItem mi, boolean armed, boolean hasSubmenu) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = mi.getWidth(), h = mi.getHeight();
        boolean enabled = mi.isEnabled();

        g.setColor(Aqua.MENU_BG);
        g.fillRect(0, 0, w, h);
        if (armed && enabled) Aqua.paintHighlight(g, 0, 0, w, h);

        Color fg = !enabled ? Aqua.MENU_DISABLED
                 : armed ? Aqua.HILITE_TEXT
                 : (Aqua.highContrast() ? Color.WHITE : Aqua.MENU_TEXT);
        g.setFont(mi.getFont() == null ? Aqua.menuFont() : mi.getFont());
        FontMetrics fm = g.getFontMetrics();
        int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;

        if (mi.isSelected() && (mi instanceof JCheckBoxMenuItem || mi instanceof JRadioButtonMenuItem)) {
            g.setColor(fg);
            drawCheck(g, 8, h / 2 - 4);
        }

        Icon icon = mi.getIcon();
        int textX = GUTTER;
        if (icon != null) {
            icon.paintIcon(mi, g, 4, (h - icon.getIconHeight()) / 2);
            textX = Math.max(GUTTER, 8 + icon.getIconWidth());
        }

        String accel = acceleratorText(mi.getAccelerator());
        int accelWidth = accel.isEmpty() ? 0 : fm.stringWidth(accel) + 16;
        int arrowWidth = hasSubmenu ? 14 : 0;

        g.setColor(fg);
        String text = Aqua.clipEnd(fm, mi.getText() == null ? "" : mi.getText(),
                                   Math.max(10, w - textX - RIGHT_PAD - accelWidth - arrowWidth));
        g.drawString(text, textX, baseline);

        if (!accel.isEmpty()) {
            g.setColor(!enabled ? Aqua.MENU_DISABLED : armed ? Aqua.HILITE_TEXT : new Color(0x555555));
            g.drawString(accel, w - RIGHT_PAD - fm.stringWidth(accel) - arrowWidth, baseline);
        }
        if (hasSubmenu) {
            g.setColor(fg);
            int cx = w - 12, cy = h / 2;
            Polygon p = new Polygon(new int[]{cx - 2, cx + 3, cx - 2}, new int[]{cy - 4, cy, cy + 4}, 3);
            g.fill(p);
        }
        g.dispose();
    }

    private static void drawCheck(Graphics2D g, int x, int y) {
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine(x, y + 4, x + 3, y + 7);
        g.drawLine(x + 3, y + 7, x + 8, y - 1);
        g.setStroke(new BasicStroke(1f));
    }

    /** Paints a top-level title in the menu bar. */
    public static void paintTitle(Graphics g0, JMenu menu, boolean selected) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = menu.getWidth(), h = menu.getHeight();
        if (selected) {
            Aqua.paintHighlight(g, 0, 0, w, h);
        }
        Color fg = selected ? (Aqua.highContrast() ? Color.BLACK : Color.WHITE)
                 : !menu.isEnabled() ? Aqua.MENU_DISABLED
                 : (Aqua.highContrast() ? Color.WHITE : Aqua.MENU_TEXT);
        g.setFont(menu.getFont() == null ? Aqua.menuFont() : menu.getFont());
        FontMetrics fm = g.getFontMetrics();
        String text = menu.getText() == null ? "" : menu.getText();
        Icon icon = menu.getIcon();
        int x = (w - fm.stringWidth(text)) / 2;
        if (icon != null) {
            icon.paintIcon(menu, g, (w - icon.getIconWidth()) / 2, (h - icon.getIconHeight()) / 2);
        } else {
            g.setColor(fg);
            g.drawString(text, x, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
        g.dispose();
    }

    public static Dimension itemSize(JMenuItem mi, boolean hasSubmenu) {
        FontMetrics fm = mi.getFontMetrics(mi.getFont() == null ? Aqua.menuFont() : mi.getFont());
        String accel = acceleratorText(mi.getAccelerator());
        int w = GUTTER + fm.stringWidth(mi.getText() == null ? "" : mi.getText()) + RIGHT_PAD;
        if (!accel.isEmpty()) w += fm.stringWidth(accel) + 24;
        if (hasSubmenu) w += 16;
        if (mi.getIcon() != null) w += mi.getIcon().getIconWidth();
        return new Dimension(w, Math.max(ITEM_HEIGHT, fm.getHeight() + 4));
    }
}
