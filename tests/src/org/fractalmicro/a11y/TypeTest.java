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

import org.fractalmicro.appkit.FMWorkspace;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.uti.UTType;
import org.fractalmicro.uti.UTTypes;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a file is, which is a name in a tree and not a filename extension.
 *
 * public.png conforms to public.image conforms to public.data conforms to public.item, and
 * asking whether something is an image is asking about that. The answer stays right for a
 * kind of image nobody had heard of when the question was written, which is the whole
 * reason for types rather than a list of extensions.
 *
 * The declarations are read out of the frameworks installed on the volume, so the first
 * check here is that anything was read at all. A database that came up empty would answer
 * every question with a shrug and the Kind column would quietly fall back to whatever
 * Windows says, which is a thing to fail on rather than to live with.
 */
public final class TypeTest {
    private TypeTest() {}

    public static int count() { return 15; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("what a file is:");

        out.println("      " + UTTypes.count() + " types declared");
        failures += check(out, "the declarations were read off the volume",
            UTTypes.count() > 80);

        /* -------------------------------------------------------- the tree */

        failures += check(out, "a type is itself",
            UTTypes.conforms(UTTypes.PLAIN_TEXT, UTTypes.PLAIN_TEXT));

        failures += check(out, "and is what it was declared to be",
            UTTypes.conforms(FMString.of("public.png"), UTTypes.IMAGE)
            && UTTypes.conforms(UTTypes.IMAGE, UTTypes.DATA)
            && UTTypes.conforms(FMString.of("public.png"), UTTypes.DATA)
            && UTTypes.conforms(FMString.of("public.png"), UTTypes.ITEM));

        failures += check(out, "and is not what it was not",
            !UTTypes.conforms(FMString.of("public.png"), UTTypes.TEXT)
            && !UTTypes.conforms(UTTypes.DATA, FMString.of("public.png")));

        // Two parents, which a type is allowed to have and this one has: an SVG is a
        // picture and is also XML, and both answers are true at once.
        failures += check(out, "a type can be two things at once",
            UTTypes.conforms(FMString.of("public.svg-image"), UTTypes.IMAGE)
            && UTTypes.conforms(FMString.of("public.svg-image"), FMString.of("public.xml"))
            && UTTypes.conforms(FMString.of("public.svg-image"), UTTypes.TEXT));

        FMArray<FMString> chain = UTTypes.conformance(FMString.of("public.mp3"));
        failures += check(out, "and says everything it is, from itself to the root",
            chain.contains(FMString.of("public.mp3"))
            && chain.contains(UTTypes.AUDIO)
            && chain.contains(UTTypes.DATA)
            && chain.contains(UTTypes.ITEM));

        // A declaration is data, and data can say a type is its own ancestor. The walk has
        // to end whatever it is given, or one bad declaration hangs the Kind column.
        UTTypes.declare(new UTType(FMString.of("test.circular.a"), FMString.EMPTY,
            FMArray.of(FMString.of("test.circular.b")), FMArray.empty(), FMString.EMPTY));
        UTTypes.declare(new UTType(FMString.of("test.circular.b"), FMString.EMPTY,
            FMArray.of(FMString.of("test.circular.a")), FMArray.empty(), FMString.EMPTY));
        failures += check(out, "a declaration that loops does not hang the question",
            !UTTypes.conforms(FMString.of("test.circular.a"), UTTypes.ITEM));

        /* ------------------------------------------------- what a file resolves to */

        failures += check(out, "an extension names one type",
            UTTypes.PLAIN_TEXT.sameAs(UTTypes.preferredType(FMString.of("txt")))
            && FMString.of("public.png").sameAs(UTTypes.preferredType(FMString.of("png")))
            && FMString.of("com.adobe.pdf").sameAs(UTTypes.preferredType(FMString.of("pdf"))));

        failures += check(out, "and it does not matter how it was written",
            UTTypes.PLAIN_TEXT.sameAs(UTTypes.preferredType(FMString.of("TXT"))));

        Path folder;
        try {
            folder = Files.createTempDirectory("fractal-type-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return failures + 3;
        }
        try {
            FMWorkspace workspace = FMWorkspace.sharedWorkspace();
            File picture = new File(folder.toFile(), "Photo.PNG");
            Files.writeString(picture.toPath(), "not really a picture");

            failures += check(out, "the workspace says what a file is",
                FMString.of("public.png").sameAs(workspace.typeOfFile(FMURL.of(picture))));

            failures += check(out, "and a folder is a folder",
                UTTypes.FOLDER.sameAs(workspace.typeOfFile(FMURL.of(folder.toFile()))));

            File nothing = new File(folder.toFile(), "Whatever.qqqq");
            Files.writeString(nothing.toPath(), "no declaration claims this");
            failures += check(out, "and something nothing declared is still data",
                UTTypes.DATA.sameAs(workspace.typeOfFile(FMURL.of(nothing)))
                && workspace.type(workspace.typeOfFile(FMURL.of(nothing)), UTTypes.ITEM));

            /* ------------------------------------------------- and who can open it */

            // The point of the tree, from the other end. TextEdit declared public.text and
            // nothing about Java, so a .java file it has never heard of is offered to it
            // because that is what the type says a .java is.
            File source = new File(folder.toFile(), "Thing.java");
            Files.writeString(source.toPath(), "class Thing { }");
            failures += check(out, "a program is offered a kind of file it never named",
                named(org.fractalmicro.bundle.LaunchServices.applicationsFor(source))
                    .contains("TextEdit"));

            File plain = new File(folder.toFile(), "Notes.txt");
            Files.writeString(plain.toPath(), "a note");
            failures += check(out, "and is the one that would open it",
                org.fractalmicro.bundle.LaunchServices.defaultApplicationFor(plain) != null
                && "TextEdit".equals(org.fractalmicro.bundle.LaunchServices
                    .defaultApplicationFor(plain).displayName().toString()));

            failures += check(out, "while nothing is offered for a kind nobody handles",
                named(org.fractalmicro.bundle.LaunchServices.applicationsFor(picture))
                    .isEmpty());
        } catch (Exception e) {
            out.println("FAIL  what a file resolves to: " + e);
            failures++;
        } finally {
            deleteTree(folder.toFile());
        }

        out.println("      " + (failures == 0 ? "a file knows what it is"
                                              : failures + " failed"));
        return failures;
    }

    /** The names of the programs a list of bundles is. */
    private static java.util.List<String> named(
            java.util.List<org.fractalmicro.bundle.Bundle> bundles) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (org.fractalmicro.bundle.Bundle one : bundles) out.add(one.displayName().toString());
        return out;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
