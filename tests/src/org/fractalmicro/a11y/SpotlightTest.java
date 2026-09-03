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
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.mds.Server;
import org.fractalmicro.spotlight.FMImporters;
import org.fractalmicro.spotlight.FMMetadataAttributes;
import org.fractalmicro.xpc.Message;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether the index knows anything but names.
 *
 * It held a path and a lowercased file name, so a search found a file called Invoice and
 * never a file that said invoice. The importers read what is inside, the way Spotlight
 * does, and the server keeps what they said.
 */
public final class SpotlightTest {
    private SpotlightTest() {}

    /** A word that is in no file on any volume until this check writes one. */
    private static final String RARE = "quinquagenarian";

    public static int count() { return 11; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the Spotlight importers:");

        /* ------------------------------------------------ what is installed */

        List<String> found = new ArrayList<>();
        File[] kids = FMImporters.importersFolder().listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory() && f.getName().endsWith(FMImporters.EXTENSION)) {
                    found.add(f.getName());
                }
            }
        }
        java.util.Collections.sort(found);
        out.println("      in Spotlight: " + String.join(", ", found));
        failures += check(out, "what reads a file is a bundle on disk, not code in the server",
            found.size() >= 3 && found.contains("Text.mdimporter"));

        Bundle text = Bundle.read(new File(FMImporters.importersFolder(),
                                           "Text.mdimporter"));
        failures += check(out, "an importer is a bundle with an executable",
            text != null && text.machOExecutable() != null
            && text.machOExecutable().isFile());
        failures += check(out, "and names the class that reads it",
            text != null && text.principalClass().beginsWith(
                FMString.of("org.fractalmicro.mdimporters.")));

        List<String> declared = declaredTypes(text);
        out.println("      Text.mdimporter reads " + String.join(", ", declared));
        failures += check(out, "it says what it reads as a type, not as a list of suffixes",
            declared.equals(List.of("public.text")));

        Path dir;
        try {
            dir = Files.createTempDirectory("fractal-spotlight-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return count();
        }

        try {
            /* ------------------------------------------------ what they read */

            File notes = dir.resolve("Notes.java").toFile();
            Files.writeString(notes.toPath(), "class Notes { String word = \"" + RARE + "\"; }");
            failures += check(out, "a language nobody wrote an importer for is still text",
                FMImporters.importerFor(notes).sameAs(
                    FMString.of("org.fractalmicro.spotlight.text")));

            FMDictionary said = FMImporters.attributesFor(notes);
            failures += check(out, "and the words in it come back as its text content",
                said.string(FMMetadataAttributes.TEXT_CONTENT).toString().contains(RARE));

            File picture = dir.resolve("Sample.png").toFile();
            ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB),
                          "png", picture);
            FMDictionary shape = FMImporters.attributesFor(picture);
            out.println("      Sample.png is "
                        + shape.string(FMMetadataAttributes.PIXEL_WIDTH) + " by "
                        + shape.string(FMMetadataAttributes.PIXEL_HEIGHT));
            failures += check(out, "a picture says how big it is without being drawn",
                "64".equals(shape.string(FMMetadataAttributes.PIXEL_WIDTH).toString())
                && "48".equals(shape.string(FMMetadataAttributes.PIXEL_HEIGHT).toString()));

            Bundle finder = org.fractalmicro.bundle.Bundles.byIdentifier(
                "org.fractalmicro.finder");
            FMDictionary program = finder == null ? null
                : FMImporters.attributesFor(finder.root());
            failures += check(out, "and a program says what it calls itself",
                program != null && program.string(FMMetadataAttributes.BUNDLE_IDENTIFIER)
                    .sameAs(FMString.of("org.fractalmicro.finder")));

            File nothing = dir.resolve("Sample.unheardof").toFile();
            Files.writeString(nothing.toPath(), RARE);
            failures += check(out, "a kind nothing declared has no importer at all",
                FMImporters.importerFor(nothing).isEmpty()
                && FMImporters.attributesFor(nothing).count() == 0);

            /* ------------------------------------------- and what that is for */

            // The whole point of importing anything. The name has nothing in common with
            // the word, so a hit can only have come from what the file says.
            Server server = new Server();
            int held = server.index(List.of(dir.toFile()));
            Message answer = server.query(RARE, 5);
            out.println("      " + held + " indexed, "
                        + answer.strings("paths").size() + " found by a word inside one");
            failures += check(out, "and the index finds a file by something it says",
                answer.strings("paths").contains(notes.getAbsolutePath()));

            failures += check(out, "while a word in nothing finds nothing",
                server.query("unsearchablenonsenseword", 5).strings("paths").isEmpty());

            // An importer declares public.text as a program that opened text would.
            List<String> names = new ArrayList<>();
            for (Bundle one : LaunchServices.applicationsFor(notes)) {
                names.add(one.displayName().toString());
            }
            out.println("      Notes.java can be opened by " + String.join(", ", names));
            failures += check(out, "and an importer is not offered as a way to open a file",
                !names.isEmpty() && names.contains("TextEdit"));
        } catch (Exception e) {
            out.println("FAIL  the check could not be made: " + e);
            failures++;
        } finally {
            deleteTree(dir.toFile());
        }

        out.println("      " + (failures == 0
            ? "the index holds what a file says, not only what it is called"
            : failures + " failed"));
        return failures;
    }

    /** The types one importer says it reads. */
    private static List<String> declaredTypes(Bundle bundle) {
        List<String> said = new ArrayList<>();
        if (bundle == null) return said;
        for (Object entry : bundle.info().array(Bundle.DOCUMENT_TYPES)) {
            if (!(entry instanceof FMDictionary one)) continue;
            for (Object type : one.array(Bundle.CONTENT_TYPES)) {
                said.add(String.valueOf(type));
            }
        }
        return said;
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
