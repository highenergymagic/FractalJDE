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


import org.fractalmicro.theme.Aqua;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The desktop picture.
 *
 * The Snow Leopard original is a plume: a magenta and violet aurora rising from the
 * bottom centre of the screen and fanning outwards over near black blue, with fine rays
 * fraying off the edges and stars behind. An earlier version of this file drew
 * horizontal bands across the screen, which is a different picture entirely and the
 * kind of mistake that gives the whole imitation away.
 *
 * Painted rather than shipped, so there is no copyrighted image here.
 */
public final class Wallpaper {
    private Wallpaper() {}

    private static BufferedImage cached;
    private static int cachedW, cachedH;
    private static boolean cachedContrast;

    public static void paint(Graphics2D g, int w, int h) {
        if (cached == null || cachedW != w || cachedH != h || cachedContrast != Aqua.highContrast()) {
            cached = render(w, h);
            cachedW = w;
            cachedH = h;
            cachedContrast = Aqua.highContrast();
        }
        g.drawImage(cached, 0, 0, null);
    }

    private static BufferedImage render(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Aqua.antialias(g);

        if (Aqua.highContrast()) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.dispose();
            return img;
        }

        // Night: nearly black, a little blue, deepest at the top corners.
        g.setPaint(new GradientPaint(0, 0, new Color(0x04010B), 0, h, new Color(0x150420)));
        g.fillRect(0, 0, w, h);
        stars(g, w, h);

        // The plume rises from below the bottom edge, so its root is off screen.
        double originX = w * 0.5;
        double originY = h * 1.06;

        double scale = Math.min(w, h);
        glow(g, originX, originY, scale * 0.62, new Color(0x5E1652), 0.42f);
        glow(g, originX, h * 0.99, scale * 0.40, new Color(0xB8419A), 0.40f);

        // Rays, fanning out and leaning to the sides the way the original does.
        Random rnd = new Random(20090828);            // the day Snow Leopard shipped
        for (int i = 0; i < 30; i++) {
            double spread = (i / 29.0) * 2 - 1;                  // -1 at the left, 1 at the right
            double angle = spread * 1.18 + (rnd.nextDouble() - 0.5) * 0.10;
            double length = h * (0.44 + rnd.nextDouble() * 0.34) * (1 - Math.abs(spread) * 0.30);
            float alpha = (float) (0.16 - Math.abs(spread) * 0.07 + rnd.nextDouble() * 0.05);
            ray(g, originX, originY, angle, length, w * 0.020f, colourFor(spread), Math.max(0.03f, alpha));
        }

        // A brighter core where the rays gather, close to the bottom edge.
        glow(g, originX, h * 1.0, scale * 0.14, new Color(0xFF9BE8), 0.34f);

        // The night has to stay night: hold the top of the screen down.
        g.setPaint(new GradientPaint(0, 0, new Color(0x05010E),
                                     0, (float) (h * 0.62), new Color(5, 1, 14, 0)));
        g.fillRect(0, 0, w, (int) (h * 0.62));
        g.dispose();
        return img;
    }

    /** Violet at the edges of the fan, magenta and pink towards the middle. */
    private static Color colourFor(double spread) {
        double t = Math.abs(spread);
        int r = (int) (0xFF - t * 0x60);
        int gg = (int) (0x6A - t * 0x40);
        int b = (int) (0xE0 + t * 0x1F);
        return new Color(clamp(r), clamp(gg), clamp(b));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** A soft round wash of light. */
    private static void glow(Graphics2D g, double x, double y, double radius,
                             Color colour, float alpha) {
        if (radius <= 1) return;
        Paint paint = new RadialGradientPaint(
            new java.awt.geom.Point2D.Double(x, y), (float) radius,
            new float[]{0f, 0.45f, 1f},
            new Color[]{
                new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                          Math.round(alpha * 255)),
                new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                          Math.round(alpha * 90)),
                new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)});
        g.setPaint(paint);
        g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
    }

    /**
     * One ray: a tapered sliver from the plume's root to a point, filled with a
     * gradient that fades out along its length. Filled rather than stroked, because a
     * round stroke cap leaves a scalloped edge that looks like soap bubbles.
     */
    private static void ray(Graphics2D g, double x, double y, double angle,
                            double length, float width, Color colour, float alpha) {
        double tipX = x + Math.sin(angle) * length * 1.1;
        double tipY = y - Math.cos(angle) * length;

        // Perpendicular to the ray, for the width at the base.
        double px = Math.cos(angle) * width;
        double py = Math.sin(angle) * width;

        Path2D sliver = new Path2D.Double();
        sliver.moveTo(x - px, y - py);
        sliver.quadTo(x - px * 2.2 + (tipX - x) * 0.5, y + (tipY - y) * 0.5, tipX, tipY);
        sliver.quadTo(x + px * 2.2 + (tipX - x) * 0.5, y + (tipY - y) * 0.5, x + px, y + py);
        sliver.closePath();

        Paint paint = new GradientPaint(
            (float) x, (float) y,
            new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                      Math.max(1, Math.round(alpha * 255))),
            (float) tipX, (float) tipY,
            new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0));
        g.setPaint(paint);
        g.fill(sliver);
    }

    private static void stars(Graphics2D g, int w, int h) {
        Random rnd = new Random(1006);
        for (int i = 0; i < 320; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(Math.max(1, (int) (h * 0.75)));
            int alpha = 30 + rnd.nextInt(140);
            g.setColor(new Color(255, 255, 255, alpha));
            int size = rnd.nextInt(10) == 0 ? 2 : 1;
            g.fillOval(x, y, size, size);
        }
    }

    public static void invalidate() { cached = null; }
}
