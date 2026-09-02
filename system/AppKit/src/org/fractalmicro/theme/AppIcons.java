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


import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Icons for the programs this system ships, drawn rather than shipped, and written into
 * each bundle's Resources folder when the bundle is installed.
 *
 * Each is the same glassy tile with a different device on it, which is roughly how the
 * utilities of that era looked.
 */
public final class AppIcons {
    private AppIcons() {}

    public static BufferedImage forApplication(String name, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Aqua.antialias(g);
        double scale = size / 128.0;
        g.scale(scale, scale);

        Color top;
        Color bottom;
        switch (name) {
            case "Finder": top = new Color(0x9FD1F5); bottom = new Color(0x2E7BC4); break;
            case "System Preferences": top = new Color(0xD8D8DE); bottom = new Color(0x7C818C); break;
            case "System Profiler": top = new Color(0xB8CFE8); bottom = new Color(0x4A6E96); break;
            case "Activity Monitor": top = new Color(0xB6E3B0); bottom = new Color(0x3C7A38); break;
            case "TextEdit": top = new Color(0xFFFFFF); bottom = new Color(0xD5D5D5); break;
            case "Terminal": top = new Color(0x5A5A5A); bottom = new Color(0x141414); break;
            default: top = new Color(0x7FB2F0); bottom = new Color(0x2E62B8);
        }

        Shape tile = new RoundRectangle2D.Float(12, 12, 104, 104, 26, 26);
        g.setPaint(new GradientPaint(0, 12, top, 0, 116, bottom));
        g.fill(tile);
        g.setPaint(new GradientPaint(0, 12, new Color(255, 255, 255, 150),
                                     0, 66, new Color(255, 255, 255, 15)));
        g.fill(new RoundRectangle2D.Float(16, 16, 96, 48, 22, 22));
        g.setColor(new Color(0, 0, 0, 90));
        g.draw(tile);

        device(g, name);
        g.dispose();
        return image;
    }

    private static void device(Graphics2D g, String name) {
        switch (name) {
            case "Finder":
                finderFace(g);
                break;
            case "System Preferences":
                gear(g);
                break;
            case "System Profiler":
                report(g);
                break;
            case "Activity Monitor":
                trace(g);
                break;
            case "TextEdit":
                pen(g);
                break;
            case "Terminal":
                prompt(g);
                break;
            default:
                Icons.fractalMark(g, 64, 68, 44, new Color(255, 255, 255, 230));
        }
    }

    /** The two-tone face: one half light, one half dark. */
    private static void finderFace(Graphics2D g) {
        g.setColor(new Color(0xF2F7FC));
        g.fillArc(34, 34, 60, 60, 90, 180);
        g.setColor(new Color(0x2A5C8F));
        g.fillArc(34, 34, 60, 60, 270, 180);
        g.setColor(new Color(0x123A63));
        g.fillOval(48, 52, 6, 10);
        g.setColor(new Color(0xE8F1F8));
        g.fillOval(74, 52, 6, 10);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x123A63));
        g.drawArc(50, 66, 28, 14, 200, 140);
        g.setStroke(new BasicStroke(1f));
    }

    private static void gear(Graphics2D g) {
        g.setColor(new Color(0x3A3F49));
        g.translate(64, 64);
        for (int i = 0; i < 10; i++) {
            g.rotate(Math.PI / 5);
            g.fillRoundRect(-4, -34, 8, 12, 3, 3);
        }
        g.translate(-64, -64);
        g.fillOval(46, 46, 36, 36);
        g.setColor(new Color(0xD8D8DE));
        g.fillOval(56, 56, 16, 16);
    }

    private static void report(Graphics2D g) {
        g.setColor(new Color(0xE8F0F8));
        g.fillRoundRect(38, 40, 52, 40, 6, 6);
        g.setColor(new Color(0x24405F));
        for (int y = 48; y <= 72; y += 8) g.fillRect(44, y, 40, 3);
        g.fillRoundRect(56, 82, 16, 8, 2, 2);
    }

    private static void trace(Graphics2D g) {
        g.setColor(new Color(0xF2FBF1));
        g.fillRoundRect(34, 40, 60, 44, 6, 6);
        g.setColor(new Color(0x2E6B2A));
        Path2D line = new Path2D.Float();
        line.moveTo(38, 72);
        line.lineTo(50, 72);
        line.lineTo(56, 50);
        line.lineTo(64, 78);
        line.lineTo(72, 58);
        line.lineTo(80, 72);
        line.lineTo(90, 72);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        g.setStroke(new BasicStroke(1f));
    }

    private static void pen(Graphics2D g) {
        g.setColor(new Color(0xB4B4B4));
        for (int y = 44; y <= 84; y += 10) g.fillRect(36, y, 56, 2);
        Path2D quill = new Path2D.Float();
        quill.moveTo(80, 36);
        quill.lineTo(96, 52);
        quill.lineTo(60, 88);
        quill.lineTo(48, 92);
        quill.lineTo(52, 80);
        quill.closePath();
        g.setColor(new Color(0xE8B23A));
        g.fill(quill);
        g.setColor(new Color(0x8A6A20));
        g.draw(quill);
    }

    private static void prompt(Graphics2D g) {
        g.setColor(new Color(0xE6E6E6));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        g.drawString(">_", 40, 78);
    }
}
