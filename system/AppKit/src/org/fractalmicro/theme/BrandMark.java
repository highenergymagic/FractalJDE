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

import org.fractalmicro.os.OSPaths;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The Fractal Microsystems mark, from
 * ~/.fractaldt/System/Library/CoreServices/FractalLogo.png.
 *
 * The artwork is black on white with the company name underneath. The menu bar wants
 * the device on its own, in whatever colour the bar is using, so the file is cropped at
 * the rule above the wordmark and turned into a mask: how dark a pixel was becomes how
 * opaque it is, and the colour comes from the caller. That way one file serves a dark
 * glyph on the light menu bar and a white one on the blue highlight.
 */
public final class BrandMark {
    private BrandMark() {}

    private static final Map<String, Image> CACHE = new HashMap<>();
    private static BufferedImage mask;
    private static boolean loaded;

    public static Path file() {
        return OSPaths.coreServices().resolve("FractalLogo.png");
    }

    /** Copies the artwork into the system folder the first time it is needed. */
    public static void install() {
        try {
            Path target = file();
            if (Files.exists(target)) return;
            Files.createDirectories(target.getParent());
            try (InputStream in = BrandMark.class.getResourceAsStream("/org/fractalmicro/resources/FractalLogo.png")) {
                if (in != null) Files.copy(in, target);
            }
        } catch (Exception e) {
            org.fractalmicro.core.Log.info("could not install the logo: " + e.getMessage());
        }
    }

    /** True when there is artwork to draw; otherwise callers fall back to the drawn mark. */
    public static boolean available() {
        return mask() != null;
    }

    private static synchronized BufferedImage mask() {
        if (loaded) return mask;
        loaded = true;
        try {
            Path f = file();
            if (!Files.isReadable(f)) return null;
            BufferedImage source = ImageIO.read(f.toFile());
            if (source == null) return null;
            mask = toMask(crop(source));
        } catch (Exception e) {
            org.fractalmicro.core.Log.info("could not read the logo: " + e.getMessage());
        }
        return mask;
    }

    /** Keeps the device above the rule, dropping the rule and the wordmark below it. */
    private static BufferedImage crop(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int rule = -1;
        for (int y = h / 4; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (luminance(source.getRGB(x, y)) < 128) dark++;
            }
            if (dark > w * 0.8) { rule = y; break; }
        }
        int bottom = rule > 0 ? rule : h;

        // Trim the white margin around what is left.
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < bottom; y++) {
            for (int x = 0; x < w; x++) {
                if (luminance(source.getRGB(x, y)) < 200) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return source;
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Dark pixels become opaque, light ones transparent. */
    private static BufferedImage toMask(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int sourceAlpha = (rgb >>> 24);
                int alpha = 255 - luminance(rgb);
                if (sourceAlpha < 255) alpha = alpha * sourceAlpha / 255;
                out.setRGB(x, y, (alpha << 24) | 0x000000);
            }
        }
        return out;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 30 + g * 59 + b * 11) / 100;
    }

    /** The mark at a size, in a colour. Returns null when there is no artwork. */
    public static Image image(int size, Color colour) {
        BufferedImage m = mask();
        if (m == null) return null;
        String key = size + "/" + colour.getRGB();
        Image cached = CACHE.get(key);
        if (cached != null) return cached;

        int height = size;
        int width = Math.max(1, Math.round(size * (float) m.getWidth() / m.getHeight()));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Aqua.antialias(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(m, 0, 0, width, height, null);
        g.setComposite(AlphaComposite.SrcIn);
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.dispose();

        CACHE.put(key, out);
        return out;
    }

    public static int widthFor(int height) {
        BufferedImage m = mask();
        if (m == null) return height;
        return Math.max(1, Math.round(height * (float) m.getWidth() / m.getHeight()));
    }

    public static synchronized void reload() {
        loaded = false;
        mask = null;
        CACHE.clear();
    }
}
