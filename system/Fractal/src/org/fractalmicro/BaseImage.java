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
package org.fractalmicro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A whole system volume in one file.
 *
 * This is what ships. Inside are the frameworks, the loader, launchd, the applications and
 * everything else that belongs on a volume, at the paths they will sit at once installed,
 * plus a manifest saying which build they are and what each of them should hash to.
 *
 * The format is a zip. Mac OS X would use a disk image here, and the extension kept is the
 * one it uses, but a disk image is a filesystem in a file and there is no filesystem here
 * for Windows to mount. What is wanted from a disk image is that a system arrives as one
 * file, that the file says what it is, and that a damaged one is caught before any of it
 * is believed. A zip with a manifest is all three, and every machine can already open it.
 *
 * Both ends of the format are here, which is why this is one class rather than a writer in
 * the build and a reader in the kernel. A format described twice is a format that will
 * eventually be described two ways, and the half that finds out is the half running on
 * somebody else's machine.
 */
public final class BaseImage {
    private BaseImage() {}

    /** What a released image is called, and what it holds at its root. */
    public static final String FILE_NAME = "BaseSystem.dmg";
    public static final String MANIFEST = ".VolumeInfo";

    /** What stands where a digest would, on the line saying a name is a link. */
    private static final String LINK = "link";

    /** Directories that belong to whoever is using the machine, and so are never shipped. */
    private static final List<String> NOT_THE_SYSTEMS =
        List.of("Users/", "Library/Logs/", "Library/Caches/", ".Trash/");

    /* ------------------------------------------------------------------ writing */

    /**
     * Packs a volume into an image, and answers how many files went in.
     *
     * The volume handed in is one the build laid out from nothing, so what goes into the
     * image is only what the build put there. Files a person would own are skipped anyway:
     * a build machine has an account on it too, and none of what collects in it belongs to
     * anybody who installs the result.
     */
    public static int create(Path volume, Path image, String version, String build,
                             String built) throws IOException {
        List<Path> files = filesIn(volume);
        Map<String, String> digests = new LinkedHashMap<>();
        List<String> links = new ArrayList<>();

        Path where = image.toAbsolutePath();
        Files.createDirectories(where.getParent());
        Path part = where.resolveSibling(where.getFileName() + ".part");

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(part))) {
            for (Path file : files) {
                String name = relative(volume, file);
                byte[] bytes;
                if (Files.isSymbolicLink(file)) {
                    bytes = Files.readSymbolicLink(file).toString()
                                 .replace('\\', '/').getBytes(StandardCharsets.UTF_8);
                    links.add(name);
                } else {
                    bytes = Files.readAllBytes(file);
                }
                digests.put(name, digestOf(bytes));

                ZipEntry entry = new ZipEntry(name);
                // A fixed time, so building one tree twice makes one image twice. An image
                // whose bytes move on their own cannot be compared against the one
                // somebody else built from the same source.
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
            }

            ZipEntry note = new ZipEntry(MANIFEST);
            note.setTime(0L);
            zip.putNextEntry(note);
            zip.write(manifest(version, build, built, digests, links)
                      .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Files.move(part, where, StandardCopyOption.REPLACE_EXISTING);
        return files.size();
    }

    /** Every file on the volume that belongs to the system, in a settled order. */
    private static List<Path> filesIn(Path volume) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(volume, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = relative(volume, file);
                if (!MANIFEST.equals(name) && !somebodyElses(name)) found.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        // Sorted, for the reason the times are fixed: two builds of one tree should not
        // differ over the order a directory happened to be read in.
        found.sort((a, b) -> relative(volume, a).compareTo(relative(volume, b)));
        return found;
    }

    private static boolean somebodyElses(String name) {
        for (String theirs : NOT_THE_SYSTEMS) {
            if (name.startsWith(theirs) || name.contains("/" + theirs)) return true;
        }
        return name.endsWith(".part");
    }

    private static String relative(Path volume, Path file) {
        return volume.relativize(file).toString().replace('\\', '/');
    }

    /**
     * What the image says about itself.
     *
     * Fields first, one to a line, then every file indented by two spaces: what it hashes
     * to, a space, and its name. Names have spaces in them, so only the first space
     * separates the two and the rest of the line is the name.
     *
     * The links are listed again afterwards, saying which of those files is not one. A
     * digest of what a link points at is still a digest, so both lists can be checked the
     * same way and neither has to be believed on its own.
     */
    private static String manifest(String version, String build, String built,
                                   Map<String, String> digests, List<String> links) {
        StringBuilder out = new StringBuilder();
        out.append("# FractalJDE base system image\n");
        out.append("Version: ").append(version).append('\n');
        out.append("Build: ").append(build).append('\n');
        out.append("Built: ").append(built).append('\n');
        out.append("Files: ").append(digests.size()).append('\n');
        out.append("Links: ").append(links.size()).append('\n');
        for (Map.Entry<String, String> file : digests.entrySet()) {
            out.append("  ").append(file.getValue()).append(' ')
               .append(file.getKey()).append('\n');
        }
        for (String link : links) {
            out.append("  ").append(LINK).append(' ').append(link).append('\n');
        }
        return out.toString();
    }

    /* ------------------------------------------------------------------ reading */

    /** The manifest inside an image. */
    public static String manifestIn(Path image) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(image))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (MANIFEST.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException(image + " has no " + MANIFEST + ", so it is not a system image");
    }

    /** The fields at the top of a manifest: what the volume is. */
    public static Map<String, String> fields(String manifest) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : manifest.split("\n")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("  ")) continue;
            int colon = line.indexOf(':');
            if (colon > 0) {
                out.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return out;
    }

    /** The file list in a manifest, by name. */
    public static Map<String, String> digests(String manifest) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : listedIn(manifest)) {
            int space = line.indexOf(' ');
            if (space > 0 && !LINK.equals(line.substring(0, space))) {
                out.put(line.substring(space + 1).trim(), line.substring(0, space));
            }
        }
        return out;
    }

    /** Which of those files are pointers at another name rather than a file of their own. */
    public static java.util.Set<String> links(String manifest) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String line : listedIn(manifest)) {
            int space = line.indexOf(' ');
            if (space > 0 && LINK.equals(line.substring(0, space))) {
                out.add(line.substring(space + 1).trim());
            }
        }
        return out;
    }

    private static List<String> listedIn(String manifest) {
        List<String> out = new ArrayList<>();
        for (String line : manifest.split("\n")) {
            if (line.startsWith("  ")) out.add(line.substring(2).trim());
        }
        return out;
    }

    /** Which build an installed volume holds, or nothing if it does not say. */
    public static String buildOn(Path volume) {
        try {
            Path says = volume.resolve(MANIFEST);
            if (!Files.isReadable(says)) return "";
            return fields(Files.readString(says)).getOrDefault("Build", "");
        } catch (IOException unreadable) {
            return "";
        }
    }

    /* ---------------------------------------------------------------- unpacking */

    /**
     * Unpacks an image onto a volume, and answers how many files it wrote.
     *
     * Each file is checked against the manifest as it comes out. An image is a file that
     * travelled: a program that unpacks whatever it is handed and then runs it cannot tell
     * a half-finished download from a working system, and the first sign of the difference
     * would be something failing to load much later, somewhere unrelated to the cause.
     *
     * What is on the volume already and not in the image stays. Somebody's documents, their
     * preferences, anything they installed themselves: none of it came from an image, and
     * none of it is an image's to remove.
     */
    public static int unpack(Path image, Path root) throws IOException {
        String manifest = manifestIn(image);
        Map<String, String> expected = digests(manifest);
        java.util.Set<String> pointers = links(manifest);
        Path top = root.toAbsolutePath().normalize();
        Files.createDirectories(top);

        List<String> refused = new ArrayList<>();
        int written = 0;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(image))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (MANIFEST.equals(name)) continue;

                Path into = within(top, name);
                if (into == null) {
                    refused.add(name + ": points outside the volume");
                    continue;
                }
                byte[] bytes = zip.readAllBytes();
                String want = expected.get(name);
                if (want == null) {
                    refused.add(name + ": not in the manifest");
                    continue;
                }
                if (!want.equals(digestOf(bytes))) {
                    refused.add(name + ": not what the manifest says it is");
                    continue;
                }

                Files.createDirectories(into.getParent());
                clear(into);
                Files.write(into, bytes);
                if (pointers.contains(name)) {
                    point(into, new String(bytes, StandardCharsets.UTF_8));
                } else if (runnable(name)) {
                    into.toFile().setExecutable(true);
                }
                written++;
            }
        }

        if (!refused.isEmpty()) {
            StringBuilder why = new StringBuilder(refused.size() + " files in "
                                                  + image.getFileName() + " were refused:");
            for (String one : refused) why.append("\n  ").append(one);
            throw new IOException(why.toString());
        }

        // Last, so a volume claiming to hold a build is a volume that finished unpacking
        // one. Stopped halfway it claims nothing, and the next start unpacks again rather
        // than running half a system.
        Files.write(top.resolve(MANIFEST), manifest.getBytes(StandardCharsets.UTF_8));
        return written;
    }

    /**
     * Turns a file holding a path into a real link, where the file system allows one.
     *
     * A framework is a directory of versions with names pointing at the current one, so
     * these are what make Resources and the rest resolve at all. Windows hands out the
     * privilege to make them only to an administrator or an account with developer mode
     * on, and a desktop is not worth either.
     *
     * So the file written first is the fallback rather than a step towards the link: a
     * small file holding the path it stands for, which the system reads as a pointer
     * wherever it finds one. If the link can be made it replaces the file, and if it
     * cannot, what is already there is the answer. The image carries the same bytes
     * either way, and so hashes the same on a machine that can and one that cannot.
     */
    private static void point(Path at, String target) {
        try {
            Files.delete(at);
            Files.createSymbolicLink(at, Path.of(target));
        } catch (IOException | UnsupportedOperationException notAllowed) {
            try {
                if (!Files.exists(at)) {
                    Files.write(at, target.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException lost) {
                throw new IllegalStateException("could not point " + at + " at " + target, lost);
            }
        }
    }

    /**
     * Empties a name so something can be written there.
     *
     * What is in the way is normally the same file from the last install, but it can be a
     * directory: a link that could not be made last time and was copied instead leaves
     * one, and writing a file over a directory fails rather than replacing it.
     */
    private static void clear(Path at) throws IOException {
        if (Files.isDirectory(at) && !Files.isSymbolicLink(at)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(at)) {
                for (Path each : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(each);
                }
            }
        } else {
            Files.deleteIfExists(at);
        }
    }

    /**
     * Refuses a name that would write outside the volume.
     *
     * An entry name is a string out of a file, and a string out of a file can say ../ as
     * often as it likes. Resolving it and looking at where it landed is the only answer
     * that holds for every way it can be spelled.
     */
    private static Path within(Path top, String name) {
        Path resolved = top.resolve(name).normalize();
        return resolved.startsWith(top) ? resolved : null;
    }

    /** Programs, so a volume unpacked where there is a mode bit has one set. */
    private static boolean runnable(String name) {
        return name.startsWith("usr/lib/") || name.startsWith("sbin/")
               || name.startsWith("bin/") || name.contains("/Contents/Fractal/")
               || name.contains("/CoreServices/");
    }

    /* ------------------------------------------------------------------ digests */

    /** SHA-256, written out the way the manifest carries it. */
    public static String digestOf(byte[] bytes) {
        try {
            StringBuilder out = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException noDigest) {
            throw new IllegalStateException("this runtime has no SHA-256", noDigest);
        }
    }

    /* -------------------------------------------------------------- the command */

    /**
     * Makes an image out of a volume, for the build to call.
     *
     * The build lays the volume out by installing onto it; this is the step that turns
     * what it laid out into the one file that ships.
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 5) {
            System.err.println("usage: BaseImage <volume> <image> <version> <build> <built>");
            System.exit(64);
            return;
        }
        Path image = Path.of(arguments[1]);
        int files = create(Path.of(arguments[0]), image, arguments[2], arguments[3],
                           arguments[4]);
        System.out.println("wrote " + image + ": " + files + " files, "
                           + (Files.size(image) / 1024) + "K");
    }
}
