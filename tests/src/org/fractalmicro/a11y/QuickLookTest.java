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
package org.fractalmicro.a11y;

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.LaunchServices;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.quicklook.FMQuickLook;
import org.fractalmicro.uti.UTTypes;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Who decides what is inside a file.
 *
 * The Finder used to, from the end of the name. They are generators now, bundles that say
 * which types they show, and the check is that a PNG reaches the image one without either
 * of them naming PNG: the tree says a PNG is an image.
 */
public final class QuickLookTest {
    private QuickLookTest() {}

    public static int count() { return 11; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the Quick Look generators:");

        /* ------------------------------------------------ what is installed */

        List<String> found = new ArrayList<>();
        File[] kids = FMQuickLook.generatorsFolder().listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory() && f.getName().endsWith(FMQuickLook.EXTENSION)) {
                    found.add(f.getName());
                }
            }
        }
        java.util.Collections.sort(found);
        out.println("      in QuickLook: " + String.join(", ", found));
        failures += check(out, "what shows a file is a bundle on disk, not code in the panel",
            found.size() >= 3 && found.contains("Image.qlgenerator"));

        Bundle image = Bundle.read(new File(FMQuickLook.generatorsFolder(),
                                            "Image.qlgenerator"));
        failures += check(out, "a generator is a bundle with an executable",
            image != null && image.machOExecutable() != null
            && image.machOExecutable().isFile());
        failures += check(out, "and names the class that draws it",
            image != null && image.principalClass().beginsWith(
                FMString.of("org.fractalmicro.qlgenerators.")));

        /* ------------------------------------------- and what it says it shows */

        List<String> declared = declaredTypes(image);
        out.println("      Image.qlgenerator shows " + String.join(", ", declared));
        failures += check(out, "it says what it shows as a type, not as a list of suffixes",
            declared.equals(List.of("public.image")));

        Path dir;
        try {
            dir = Files.createTempDirectory("fractal-quicklook-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return count();
        }

        try {
            /* ------------------------------------------------- found by the tree */

            File png = dir.resolve("Sample.png").toFile();
            ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB), "png", png);

            FMString kind = LaunchServices.typeOf(png);
            out.println("      Sample.png is " + kind + ", which is a "
                        + String.join(", a ", asStrings(UTTypes.conformance(kind))));
            failures += check(out, "a PNG reaches the generator that asked for images",
                FMQuickLook.generatorFor(png).sameAs(
                    FMString.of("org.fractalmicro.quicklook.image")));

            // The whole point. It would pass just as well if the generator had listed PNG,
            // so what makes it worth checking is the line under it.
            failures += check(out, "and neither of them named PNG to do it",
                !declared.contains(kind.toString())
                && UTTypes.conforms(kind, FMString.of("public.image")));

            File text = dir.resolve("Sample.java").toFile();
            Files.writeString(text.toPath(), "class Sample {}\n");
            failures += check(out, "a language nobody wrote a generator for is still text",
                FMQuickLook.generatorFor(text).sameAs(
                    FMString.of("org.fractalmicro.quicklook.text")));

            File plist = dir.resolve("Sample.plist").toFile();
            Files.writeString(plist.toPath(), SAMPLE_PLIST);
            failures += check(out, "and a property list goes to the one that reads them",
                FMQuickLook.generatorFor(plist).sameAs(
                    FMString.of("org.fractalmicro.quicklook.propertylist")));

            File nothing = dir.resolve("Sample.unheardof").toFile();
            Files.writeString(nothing.toPath(), "not a kind of anything");
            failures += check(out, "a kind nothing declared has no generator at all",
                FMQuickLook.generatorFor(nothing).isEmpty()
                && FMQuickLook.previewOf(nothing) == null);

            JComponent view = FMQuickLook.previewOf(png);
            failures += check(out, "and asking for the picture gives back a view of it",
                view != null);

            /* ---------------------------------------- and who no longer decides */

            failures += check(out, "the file browser no longer decides what a file is",
                !panelMentions(".png") && !panelMentions(".txt"));

            // A generator declares public.image the way a program that opens images would,
            // and Launch Services has to know that is not an offer to open one.
            List<String> names = new ArrayList<>();
            for (Bundle one : LaunchServices.applicationsFor(png)) {
                names.add(one.displayName().toString());
            }
            failures += check(out, "and a generator is not offered as a way to open a file",
                !names.contains("Image"));
        } catch (Exception e) {
            out.println("FAIL  the check could not be made: " + e);
            failures++;
        } finally {
            deleteTree(dir.toFile());
        }

        out.println("      " + (failures == 0
            ? "what shows a file is declared, not written into the panel"
            : failures + " failed"));
        return failures;
    }

    /** Small enough to read, and enough of one to be one. */
    private static final String SAMPLE_PLIST =
        "<plist version=\"1.0\"><dict><key>Who</key><string>Fractal</string></dict></plist>";

    /** The types one generator says it shows. */
    private static List<String> declaredTypes(Bundle bundle) {
        List<String> said = new ArrayList<>();
        if (bundle == null) return said;
        for (Object entry : bundle.info().array(Bundle.DOCUMENT_TYPES)) {
            if (!(entry instanceof org.fractalmicro.foundation.FMDictionary one)) continue;
            for (Object type : one.array(Bundle.CONTENT_TYPES)) {
                said.add(String.valueOf(type));
            }
        }
        return said;
    }

    private static List<String> asStrings(Iterable<FMString> them) {
        List<String> said = new ArrayList<>();
        for (FMString one : them) said.add(one.toString());
        return said;
    }

    /** Whether the panel's own source still works a kind out from the end of a name. */
    private static boolean panelMentions(String suffix) {
        Path p = Path.of("system/Finder/src/org/fractalmicro/ui/QuickLook.java");
        try {
            return Files.isReadable(p) && Files.readString(p).contains("\"" + suffix + "\"");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void deleteTree(File at) {
        File[] kids = at.listFiles();
        if (kids != null) for (File one : kids) deleteTree(one);
        at.delete();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
