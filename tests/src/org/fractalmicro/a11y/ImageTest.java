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

import org.fractalmicro.BaseImage;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The one file a system arrives as.
 *
 * A release is a kernel and an image, and the image is the entire system. Everything here
 * is about what happens to it in between: it is built on one machine, downloaded over
 * something, and unpacked on another, and each of those can go wrong quietly.
 *
 * So the checks are for what a volume looks like afterwards rather than what the code did.
 * Does everything come back the way it went in, including the links that make a framework
 * resolve at all. Is a file that changed on the way refused, rather than written and found
 * out later by whatever fails to load. Is a name that says ../ refused, since an archive
 * is a stranger's file and the names in it are a stranger's strings. And does a build
 * twice over the same tree make the same image, without which two people cannot compare
 * what they downloaded.
 */
public final class ImageTest {
    private ImageTest() {}

    public static int count() { return 8; }

    public static int run(PrintStream out) {
        out.println();
        out.println("the system image:");
        Path work;
        try {
            work = Files.createTempDirectory("fractal-image");
        } catch (IOException noTemp) {
            out.println("FAIL  somewhere to work: " + noTemp);
            return count();
        }
        try {
            return checks(out, work);
        } catch (Exception broken) {
            out.println("FAIL  the image round trip: " + broken);
            return count();
        } finally {
            remove(work);
        }
    }

    private static int checks(PrintStream out, Path work) throws IOException {
        int failures = 0;

        /* ------------------------------------------------------- a volume goes in */

        Path volume = work.resolve("volume");
        Files.createDirectories(volume.resolve("System/Library/Frameworks/Test.framework/Versions/A"));
        Files.createDirectories(volume.resolve("usr/lib"));
        Files.writeString(volume.resolve("usr/lib/dyld"), "the loader");
        Files.writeString(volume.resolve("System/Library/Frameworks/Test.framework/Versions/A/Test"),
                          "a framework");
        // A name with a space in it, because half the applications have one and a manifest
        // that splits on the wrong space would drop them without saying so.
        Files.writeString(volume.resolve("System/Library/System Preferences.plist"), "a name");

        Path link = volume.resolve("System/Library/Frameworks/Test.framework/Versions/Current");
        boolean linked = true;
        try {
            Files.createSymbolicLink(link, Path.of("A"));
        } catch (IOException | UnsupportedOperationException notAllowed) {
            // Windows only hands out that privilege to an administrator or an account with
            // developer mode on. Where it does not, the system writes the target into a
            // file instead, and so does the image; there is just nothing to check here.
            linked = false;
            Files.writeString(link, "A");
        }

        Path image = work.resolve("BaseSystem.dmg");
        int packed = BaseImage.create(volume, image, "9.9.9", "TESTBUILD", "then");
        failures += check(out, "a volume packs into one file",
            packed == 4 && Files.size(image) > 0);

        Map<String, String> says = BaseImage.fields(BaseImage.manifestIn(image));
        failures += check(out, "which says which build it holds",
            "9.9.9".equals(says.get("Version")) && "TESTBUILD".equals(says.get("Build"))
            && "4".equals(says.get("Files")));

        /* -------------------------------------------------------- and comes back out */

        Path onto = work.resolve("onto");
        int written = BaseImage.unpack(image, onto);
        failures += check(out, "and unpacks into a volume that is the same volume",
            written == 4
            && "the loader".equals(Files.readString(onto.resolve("usr/lib/dyld")))
            && "a name".equals(Files.readString(
                   onto.resolve("System/Library/System Preferences.plist"))));

        Path back = onto.resolve("System/Library/Frameworks/Test.framework/Versions/Current");
        boolean pointsAtA = linked && Files.isSymbolicLink(back)
                            ? Files.readSymbolicLink(back).toString().equals("A")
                            : "A".equals(Files.readString(back));
        failures += check(out, "with the links a framework is held together by", pointsAtA);
        out.println("      links came back as "
                    + (Files.isSymbolicLink(back) ? "links" : "files naming their target"));

        failures += check(out, "and a note saying what was installed",
            "TESTBUILD".equals(BaseImage.buildOn(onto)));

        /* ---------------------------------------------------- what must not come out */

        // A file that changed on the way. Everything before the damage is already written
        // when it is found, which is why a volume says nothing about its build until the
        // unpack finished: a half-written system must not look like an installed one.
        Path damaged = work.resolve("damaged.dmg");
        rewrite(image, damaged, "usr/lib/dyld", "not the loader");
        Path spoiled = work.resolve("spoiled");
        String refusal = refuses(damaged, spoiled);
        failures += check(out, "a file that changed on the way is refused",
            refusal != null && refusal.contains("usr/lib/dyld")
            && BaseImage.buildOn(spoiled).isEmpty());

        // A name that walks out of the volume. It is in the manifest with the right digest,
        // so nothing but the name itself says no.
        Path hostile = work.resolve("hostile.dmg");
        escape(image, hostile);
        String refused = refuses(hostile, work.resolve("target"));
        failures += check(out, "and so is a name pointing outside the volume",
            refused != null && refused.contains("outside")
            && !Files.exists(work.resolve("taken")));

        /* ----------------------------------------------------------- twice the same */

        // Two builds of one tree have to make one image, or nobody can compare the file
        // they downloaded against the file somebody else built from the same source.
        Path again = work.resolve("again.dmg");
        BaseImage.create(volume, again, "9.9.9", "TESTBUILD", "then");
        failures += check(out, "and building the same volume twice makes the same image",
            java.util.Arrays.equals(Files.readAllBytes(image), Files.readAllBytes(again)));

        out.println("      " + (failures == 0
            ? "what was built is what arrives, or nothing does"
            : failures + " failed"));
        return failures;
    }

    /* ------------------------------------------------------------------ the tools */

    /** Unpacks an image that should not unpack, and answers why it did not. */
    private static String refuses(Path image, Path onto) {
        try {
            BaseImage.unpack(image, onto);
            return null;
        } catch (IOException refused) {
            return refused.getMessage();
        }
    }

    /** Copies an image, changing one file inside it and leaving the manifest alone. */
    private static void rewrite(Path from, Path to, String name, String content)
            throws IOException {
        copy(from, to, name, content.getBytes(StandardCharsets.UTF_8), null);
    }

    /**
     * Copies an image, adding a file whose name climbs out of the volume.
     *
     * Its digest is right, so this is the case where every check but the name passes.
     */
    private static void escape(Path from, Path to) throws IOException {
        byte[] payload = "taken".getBytes(StandardCharsets.UTF_8);
        copy(from, to, null, null, payload);
    }

    private static void copy(Path from, Path to, String replace, byte[] with, byte[] escaping)
            throws IOException {
        String manifest = BaseImage.manifestIn(from);
        if (escaping != null) {
            manifest = manifest.trim() + "\n  " + BaseImage.digestOf(escaping)
                       + " ../taken\n";
        }
        try (java.util.zip.ZipInputStream in =
                 new java.util.zip.ZipInputStream(Files.newInputStream(from));
             ZipOutputStream outZip = new ZipOutputStream(Files.newOutputStream(to))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] bytes = in.readAllBytes();
                if (BaseImage.MANIFEST.equals(entry.getName())) {
                    bytes = manifest.getBytes(StandardCharsets.UTF_8);
                } else if (entry.getName().equals(replace)) {
                    bytes = with;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(0L);
                outZip.putNextEntry(copy);
                outZip.write(bytes);
                outZip.closeEntry();
            }
            if (escaping != null) {
                ZipEntry out = new ZipEntry("../taken");
                out.setTime(0L);
                outZip.putNextEntry(out);
                outZip.write(escaping);
                outZip.closeEntry();
            }
        }
    }

    private static void remove(Path at) {
        try (java.util.stream.Stream<Path> walk = Files.walk(at)) {
            for (Path each : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(each);
            }
        } catch (IOException leftBehind) {
            // A temporary directory nothing will look at again.
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
