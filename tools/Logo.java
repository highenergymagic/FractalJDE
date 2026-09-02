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
import javax.imageio.ImageIO;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns the company mark into something the boot screen can draw.
 *
 * The boot screen is a Windows program with no picture reader in it, and it runs before
 * there is a system: on a first start there is no volume yet, so there is nowhere to read
 * a picture from either. So the mark travels inside it, as the only thing it needs, which
 * is coverage: how much of each pixel the mark covers. The colour it is drawn in belongs
 * to whoever draws it, the same way the menu bar picks its own.
 *
 * The whole mark is kept, device and company name both, unlike the menu bar's copy, which
 * takes the device on its own because a bar nineteen pixels tall has nowhere to put a name.
 * A boot screen is the one place the name belongs. What is thrown away is the white around
 * it, which is not part of the mark and would only be a box drawn around it.
 *
 * <pre>
 *   java tools/Logo.java resources/FractalLogo.png tools/launcher/src/mark.mask
 * </pre>
 *
 * Run by tools/launcher.sh, and its answer is kept in the tree, so the launcher builds
 * on a machine with a Rust compiler and nothing else.
 */
public final class Logo {
    private Logo() {}

    /** How tall the mark is kept. Twice what a boot screen draws, for a doubled display. */
    private static final int HEIGHT = 256;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            System.err.println("usage: Logo <artwork.png> <mask>");
            System.exit(2);
        }
        BufferedImage source = ImageIO.read(new File(arguments[0]));
        if (source == null) {
            System.err.println("that is not a picture: " + arguments[0]);
            System.exit(1);
            return;
        }
        BufferedImage mark = scale(mask(trim(source)), HEIGHT);
        byte[] coverage = coverage(mark);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, mark.getWidth());
        writeInt(out, mark.getHeight());
        int runs = compress(coverage, out);

        Path target = Path.of(arguments[1]);
        Files.createDirectories(target.getParent());
        Files.write(target, out.toByteArray());
        System.out.println("wrote " + target + ": " + mark.getWidth() + "x" + mark.getHeight()
                           + ", " + runs + " runs, " + out.size() + " bytes");
    }

    /* ------------------------------------------------------------- the artwork */

    /** Throws away the white around the mark, so it fills what it is given. */
    private static BufferedImage trim(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
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

    /** Dark becomes covered, light becomes nothing. */
    private static BufferedImage mask(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int was = rgb >>> 24;
                int covered = 255 - luminance(rgb);
                if (was < 255) covered = covered * was / 255;
                out.setRGB(x, y, covered << 24);
            }
        }
        return out;
    }

    private static BufferedImage scale(BufferedImage source, int height) {
        int width = Math.max(1, Math.round(height * (float) source.getWidth() / source.getHeight()));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static byte[] coverage(BufferedImage mark) {
        byte[] out = new byte[mark.getWidth() * mark.getHeight()];
        int at = 0;
        for (int y = 0; y < mark.getHeight(); y++) {
            for (int x = 0; x < mark.getWidth(); x++) {
                out[at++] = (byte) (mark.getRGB(x, y) >>> 24);
            }
        }
        return out;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 30 + g * 59 + b * 11) / 100;
    }

    /* ------------------------------------------------------------ the shortening */

    /**
     * Runs of the same value, as a count and a value.
     *
     * A mark is mostly the two extremes, covered and not, in long stretches of each, so
     * this is the whole of what a picture format would do for it and it is fifteen lines
     * at the other end rather than a library.
     */
    private static int compress(byte[] coverage, ByteArrayOutputStream out) {
        int runs = 0;
        int at = 0;
        while (at < coverage.length) {
            byte value = coverage[at];
            int run = 1;
            while (at + run < coverage.length && run < 255 && coverage[at + run] == value) run++;
            out.write(run);
            out.write(value & 0xFF);
            at += run;
            runs++;
        }
        return runs;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}
