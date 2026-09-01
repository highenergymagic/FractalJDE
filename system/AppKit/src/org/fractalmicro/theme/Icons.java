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


import org.fractalmicro.fs.Node;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Every icon is drawn with Java2D. Nothing here is copied from Apple; these are
 * lookalikes built out of gradients and rounded rectangles.
 */
public final class Icons {
    private Icons() {}

    private static final Map<String, Image> CACHE = new HashMap<>();

    /**
     * The arrow an alias carries in the corner of its icon. Drawn here rather than kept
     * as a picture, so it is the right size whatever the icon size is.
     */
    public static Image withAliasBadge(Image icon, int size) {
        if (icon == null) return null;
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        org.fractalmicro.theme.Aqua.antialias(g);
        g.drawImage(icon, 0, 0, size, size, null);

        int badge = Math.max(8, size / 3);
        int x = 1;
        int y = size - badge - 1;
        g.setColor(new java.awt.Color(255, 255, 255, 235));
        g.fillOval(x, y, badge, badge);
        g.setColor(new java.awt.Color(0x50, 0x50, 0x50));
        g.drawOval(x, y, badge, badge);

        // A curved arrow pointing up and to the right, as the badge has always been.
        java.awt.geom.GeneralPath arrow = new java.awt.geom.GeneralPath();
        float left = x + badge * 0.28f;
        float bottom = y + badge * 0.74f;
        float right = x + badge * 0.74f;
        float top = y + badge * 0.30f;
        arrow.moveTo(left, bottom);
        arrow.quadTo(left, top + badge * 0.10f, right, top);
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, badge / 8f)));
        g.setColor(new java.awt.Color(0x20, 0x20, 0x20));
        g.draw(arrow);
        g.fillPolygon(
            new int[]{(int) right + 1, (int) (right - badge * 0.22f), (int) (right - badge * 0.05f)},
            new int[]{(int) top - 1, (int) top, (int) (top + badge * 0.26f)}, 3);
        g.dispose();
        return out;
    }

    public static Image forNode(Node n, int size) {
        // A bundle carries its own icon; prefer it over anything generic.
        if (n.file != null && org.fractalmicro.bundle.Bundle.looksLikeBundle(n.file)) {
            Image own = bundleIcon(n.file, size);
            if (own != null) return own;
        }
        // Applications keep the icon Windows gives them unless an icon set is installed:
        // a Dock of identical blue tiles is no use to anyone.
        if ((n.kind == Node.Kind.APPLICATION || n.kind == Node.Kind.ALIAS)
                && n.file != null && org.fractalmicro.os.FinderSettings.systemIconsForApplications()) {
            Image sys = systemIcon(n.file, size);
            if (sys != null) {
                return n.kind == Node.Kind.ALIAS ? withAliasBadge(sys, size) : sys;
            }
        }
        if (n.kind == Node.Kind.ALIAS) {
            return withAliasBadge(forKind(Node.Kind.FILE, size), size);
        }
        return forKind(n.kind, size);
    }

    /** The icon inside an application bundle, from its Resources folder. */
    private static Image bundleIcon(java.io.File folder, int size) {
        String key = "bundle:" + folder.getAbsolutePath() + "/" + size;
        Image cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            org.fractalmicro.bundle.Bundle bundle = org.fractalmicro.bundle.Bundle.read(folder);
            if (bundle == null) return null;
            java.io.File icon = bundle.iconFile();
            if (icon == null) return null;
            java.awt.image.BufferedImage image =
                icon.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".icns")
                    ? org.fractalmicro.icns.IcnsFile.read(icon.toPath()).image(size)
                    : javax.imageio.ImageIO.read(icon);
            if (image == null) return null;
            Image scaled = image.getWidth() == size
                ? image : image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            CACHE.put(key, scaled);
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    /** The icon Windows itself would draw, scaled to the size we want. */
    private static Image systemIcon(java.io.File file, int size) {
        String key = "system:" + file.getAbsolutePath() + "/" + size;
        Image cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            javax.swing.Icon icon = javax.swing.filechooser.FileSystemView
                    .getFileSystemView().getSystemIcon(file, size, size);
            if (icon == null) return null;
            java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Aqua.antialias(g);
            icon.paintIcon(null, g, (size - icon.getIconWidth()) / 2, (size - icon.getIconHeight()) / 2);
            g.dispose();
            CACHE.put(key, img);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Image forKind(Node.Kind kind, int size) {
        // An installed icon set wins; otherwise these are drawn from scratch.
        Image themed = IconProvider.lookup(kind, size, !org.fractalmicro.fs.Trash.isEmpty());
        if (themed != null) return themed;
        String key = kind + "/" + size + "/" + (kind == Node.Kind.TRASH ? trashState() : "");
        return CACHE.computeIfAbsent(key, k -> render(kind, size));
    }

    private static String trashState() {
        return org.fractalmicro.fs.Trash.isEmpty() ? "empty" : "full";
    }

    public static void invalidateTrash() {
        CACHE.keySet().removeIf(k -> k.startsWith(Node.Kind.TRASH.toString()));
    }

    private static Image render(Node.Kind kind, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Aqua.antialias(g);
        double s = size / 128.0;
        g.scale(s, s);
        switch (kind) {
            case FOLDER:          folder(g); break;
            case APPLICATION:     application(g); break;
            case ALIAS:           document(g); aliasBadge(g); break;
            case HARD_DISK:       drive(g, new Color(0xD8DDE4), new Color(0x8E97A3)); break;
            case EXTERNAL_DISK:   drive(g, new Color(0xF6D79A), new Color(0xB98A34)); break;
            case REMOVABLE_MEDIA: disc(g); break;
            case SERVER:          server(g); break;
            case NETWORK:         globe(g); break;
            case COMPUTER:        computer(g); break;
            case TRASH:           trash(g, !org.fractalmicro.fs.Trash.isEmpty()); break;
            case SEARCH:          search(g); break;
            default:              document(g); break;
        }
        g.dispose();
        return img;
    }

    /* ---------------------------------------------------------------- shapes */

    private static void folder(Graphics2D g) {
        Color back1 = new Color(0x9FC3E8), back2 = new Color(0x6E9CD2);
        Color front1 = new Color(0xC7DDF4), front2 = new Color(0x86AEDC);
        // back panel with the tab
        Path2D tab = new Path2D.Float();
        tab.moveTo(10, 34);
        tab.lineTo(46, 34);
        tab.lineTo(54, 44);
        tab.lineTo(118, 44);
        tab.quadTo(122, 44, 122, 48);
        tab.lineTo(122, 100);
        tab.lineTo(10, 100);
        tab.closePath();
        g.setPaint(new GradientPaint(0, 34, back1, 0, 100, back2));
        g.fill(tab);
        // front panel
        Shape front = new RoundRectangle2D.Float(8, 52, 112, 54, 8, 8);
        g.setPaint(new GradientPaint(0, 52, front1, 0, 106, front2));
        g.fill(front);
        g.setColor(new Color(0x5C87BC));
        g.draw(front);
        g.setColor(new Color(255, 255, 255, 130));
        g.draw(new RoundRectangle2D.Float(10, 54, 108, 50, 6, 6));
    }

    private static void document(Graphics2D g) {
        Path2D page = new Path2D.Float();
        page.moveTo(26, 12);
        page.lineTo(84, 12);
        page.lineTo(104, 32);
        page.lineTo(104, 116);
        page.lineTo(26, 116);
        page.closePath();
        g.setPaint(new GradientPaint(0, 12, Color.WHITE, 0, 116, new Color(0xE2E2E2)));
        g.fill(page);
        g.setColor(new Color(0x9A9A9A));
        g.draw(page);
        Path2D fold = new Path2D.Float();
        fold.moveTo(84, 12);
        fold.lineTo(84, 32);
        fold.lineTo(104, 32);
        fold.closePath();
        g.setColor(new Color(0xCBCBCB));
        g.fill(fold);
        g.setColor(new Color(0x9A9A9A));
        g.draw(fold);
        g.setColor(new Color(0xB4B4B4));
        for (int y = 48; y < 104; y += 12) g.drawLine(38, y, 92, y);
    }

    private static void application(Graphics2D g) {
        Shape tile = new RoundRectangle2D.Float(14, 14, 100, 100, 24, 24);
        g.setPaint(new GradientPaint(0, 14, new Color(0x7FB2F0), 0, 114, new Color(0x2E62B8)));
        g.fill(tile);
        g.setColor(new Color(0x1B3E77));
        g.draw(tile);
        // glassy top half
        g.setPaint(new GradientPaint(0, 14, new Color(255, 255, 255, 150), 0, 64, new Color(255, 255, 255, 20)));
        g.fill(new RoundRectangle2D.Float(18, 18, 92, 46, 20, 20));
        fractalMark(g, 64, 74, 34, new Color(255, 255, 255, 230));
    }

    private static void aliasBadge(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillOval(10, 84, 34, 34);
        g.setColor(new Color(0x6A6A6A));
        g.drawOval(10, 84, 34, 34);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0x3A3A3A));
        g.drawLine(20, 108, 34, 94);
        g.drawLine(34, 94, 26, 94);
        g.drawLine(34, 94, 34, 102);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drive(Graphics2D g, Color light, Color dark) {
        Shape body = new RoundRectangle2D.Float(12, 30, 104, 68, 12, 12);
        g.setPaint(new GradientPaint(0, 30, light, 0, 98, dark));
        g.fill(body);
        g.setColor(dark.darker());
        g.draw(body);
        g.setPaint(new GradientPaint(0, 32, new Color(255, 255, 255, 170), 0, 62, new Color(255, 255, 255, 20)));
        g.fill(new RoundRectangle2D.Float(16, 34, 96, 28, 10, 10));
        fractalMark(g, 64, 74, 26, new Color(60, 70, 85, 200));
    }

    private static void disc(Graphics2D g) {
        Shape outer = new Ellipse2D.Float(14, 14, 100, 100);
        g.setPaint(new GradientPaint(14, 14, new Color(0xEDEDF5), 114, 114, new Color(0x9AA6C8)));
        g.fill(outer);
        // rainbow sheen
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(Color.HSBtoRGB(i / 6f, 0.45f, 1f) & 0xFFFFFF | 0x40000000, true));
            g.fillArc(18, 18, 92, 92, i * 60, 60);
        }
        g.setColor(new Color(0x77809B));
        g.draw(outer);
        g.setColor(new Color(0xF4F4F8));
        g.fillOval(52, 52, 24, 24);
        g.setColor(new Color(0x77809B));
        g.drawOval(52, 52, 24, 24);
    }

    private static void server(Graphics2D g) {
        Shape body = new RoundRectangle2D.Float(20, 20, 88, 88, 10, 10);
        g.setPaint(new GradientPaint(0, 20, new Color(0xD5DAE1), 0, 108, new Color(0x7E8895)));
        g.fill(body);
        g.setColor(new Color(0x5A626D));
        g.draw(body);
        for (int y = 32; y < 100; y += 22) {
            g.setColor(new Color(0x4A5361));
            g.fillRoundRect(28, y, 72, 14, 4, 4);
            g.setColor(new Color(0x8CE08C));
            g.fillOval(90, y + 4, 6, 6);
        }
    }

    private static void globe(Graphics2D g) {
        Shape ball = new Ellipse2D.Float(16, 16, 96, 96);
        g.setPaint(new GradientPaint(16, 16, new Color(0x9BD2F7), 112, 112, new Color(0x1F5FA8)));
        g.fill(ball);
        g.setColor(new Color(255, 255, 255, 140));
        for (int i = 1; i < 4; i++) g.drawOval(16 + i * 12, 16, 96 - i * 24, 96);
        g.drawLine(16, 64, 112, 64);
        g.drawArc(16, 34, 96, 60, 0, 180);
        g.drawArc(16, 34, 96, 60, 180, 180);
        g.setColor(new Color(0x184C86));
        g.draw(ball);
    }

    private static void computer(Graphics2D g) {
        Shape screen = new RoundRectangle2D.Float(14, 24, 100, 66, 8, 8);
        g.setPaint(new GradientPaint(0, 24, new Color(0xE6E6E6), 0, 90, new Color(0xB0B0B0)));
        g.fill(screen);
        g.setColor(new Color(0x6E6E6E));
        g.draw(screen);
        g.setPaint(new GradientPaint(0, 28, new Color(0x2A4E86), 0, 84, new Color(0x0C2549)));
        g.fill(new RoundRectangle2D.Float(20, 30, 88, 54, 4, 4));
        g.setColor(new Color(0xC8C8C8));
        g.fillRoundRect(50, 92, 28, 12, 3, 3);
        g.fillRoundRect(34, 102, 60, 8, 4, 4);
    }

    private static void trash(Graphics2D g, boolean full) {
        Shape can = new Path2D.Float();
        Path2D p = new Path2D.Float();
        p.moveTo(34, 34);
        p.lineTo(94, 34);
        p.lineTo(86, 116);
        p.lineTo(42, 116);
        p.closePath();
        g.setPaint(new GradientPaint(34, 34, new Color(0xE9EBEE), 94, 116, new Color(0x9BA2AC)));
        g.fill(p);
        g.setColor(new Color(0x707880));
        g.draw(p);
        // mesh
        g.setColor(new Color(255, 255, 255, 120));
        for (int x = 40; x < 90; x += 8) g.drawLine(x, 38, x - 2, 112);
        // rim
        g.setPaint(new GradientPaint(0, 26, new Color(0xF2F4F6), 0, 36, new Color(0xA8AEB8)));
        g.fill(new RoundRectangle2D.Float(28, 24, 72, 12, 6, 6));
        g.setColor(new Color(0x707880));
        g.draw(new RoundRectangle2D.Float(28, 24, 72, 12, 6, 6));
        if (full) {
            g.setColor(new Color(0xF2F0E4));
            g.fillOval(44, 12, 26, 22);
            g.fillOval(62, 8, 24, 20);
            g.setColor(new Color(0xC8C4B0));
            g.drawOval(44, 12, 26, 22);
            g.drawOval(62, 8, 24, 20);
        }
    }

    private static void search(Graphics2D g) {
        g.setStroke(new BasicStroke(10f));
        g.setColor(new Color(0x8A9099));
        g.drawOval(22, 18, 68, 68);
        g.drawLine(80, 80, 108, 108);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval(30, 26, 52, 52);
    }

    /**
     * The Fractal Microsystems mark: a Sierpinski triangle. Stands in for the
     * menu-bar logo and the badge on drives and applications.
     */
    public static void fractalMark(Graphics2D g, double cx, double cy, double size, Color color) {
        g.setColor(color);
        double h = size * Math.sqrt(3) / 2;
        sierpinski(g, cx - size / 2, cy + h / 2, cx + size / 2, cy + h / 2, cx, cy - h / 2, 3);
    }

    private static void sierpinski(Graphics2D g, double x1, double y1, double x2, double y2,
                                   double x3, double y3, int depth) {
        if (depth == 0) {
            Path2D t = new Path2D.Double();
            t.moveTo(x1, y1);
            t.lineTo(x2, y2);
            t.lineTo(x3, y3);
            t.closePath();
            g.fill(t);
            return;
        }
        double m12x = (x1 + x2) / 2, m12y = (y1 + y2) / 2;
        double m23x = (x2 + x3) / 2, m23y = (y2 + y3) / 2;
        double m13x = (x1 + x3) / 2, m13y = (y1 + y3) / 2;
        sierpinski(g, x1, y1, m12x, m12y, m13x, m13y, depth - 1);
        sierpinski(g, m12x, m12y, x2, y2, m23x, m23y, depth - 1);
        sierpinski(g, m13x, m13y, m23x, m23y, x3, y3, depth - 1);
    }

    /** Menu-bar sized logo, drawn directly into a component. */
    public static void paintLogo(Graphics2D g, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        Aqua.antialias(g2);
        fractalMark(g2, x + size / 2.0, y + size / 2.0, size * 0.9, color);
        g2.dispose();
    }
}
