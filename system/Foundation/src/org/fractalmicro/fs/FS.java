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
package org.fractalmicro.fs;

import org.fractalmicro.core.Shell;
import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.os.OSPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

/** Directory listings, name formatting, and the standard folders. */
public final class FS {
    private FS() {}

    public static final File HOME = OSPaths.USER_HOME.toFile();

    public static File home() { return HOME; }
    public static File desktopFolder() { return OSPaths.desktopFolder(); }
    public static File documents() { return new File(HOME, "Documents"); }
    public static File downloads() { return new File(HOME, "Downloads"); }
    public static File music()     { return new File(HOME, "Music"); }
    public static File pictures()  { return new File(HOME, "Pictures"); }
    public static File movies()    { return new File(HOME, "Videos"); }

    public static List<Node> list(File dir) {
        List<Node> out = new ArrayList<>();
        if (dir == null) return out;
        File[] kids = dir.listFiles();
        if (kids == null) return out;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (File f : kids) {
            if (isHidden(f)) continue;
            names.add(f.getName());
            out.add(node(f));
        }
        for (File f : systemApplicationsShownIn(dir)) {
            if (names.add(f.getName())) out.add(node(f));
        }
        sort(out, "name");
        return out;
    }

    /**
     * The applications that ship with the system, when the folder being listed is the one
     * they belong in.
     *
     * Under System/Library/Applications, where an install can replace them without
     * touching what a person put in Applications. Shown as one folder, since the difference
     * is who owns the file. A program in Applications hides one of the same name that
     * shipped, which is what makes it possible to replace one.
     */
    private static List<File> systemApplicationsShownIn(File dir) {
        try {
            java.nio.file.Path shown = dir.getCanonicalFile().toPath();
            java.nio.file.Path user = org.fractalmicro.os.OSPaths.applications().toRealPath();
            java.nio.file.Path system = org.fractalmicro.os.OSPaths.systemApplications();
            if (!shown.equals(user)) return List.of();
            File[] kids = system.toFile().listFiles();
            if (kids == null) return List.of();
            List<File> out = new ArrayList<>();
            for (File f : kids) if (!isHidden(f)) out.add(f);
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Whether a directory is a bundle: named .app and holding a Contents.
     *
     * A question about a directory, so it is answered here rather than by asking the layer
     * that reads what is inside one.
     */
    public static boolean looksLikeBundle(File f) {
        return f != null && f.isDirectory()
            && f.getName().toLowerCase(Locale.ROOT).endsWith(".app")
            && new File(f, "Contents").isDirectory();
    }

    /** How an application directory gives up its display name, when something can read one. */
    public interface BundleNaming {
        String displayName(File bundle);
    }

    private static volatile BundleNaming naming;

    public static void setBundleNaming(BundleNaming how) { naming = how; }

    public static boolean isHidden(File f) {
        String n = f.getName();
        if (n.startsWith(".")) return true;
        if (n.equalsIgnoreCase("desktop.ini") || n.equalsIgnoreCase("thumbs.db")) return true;
        if (n.startsWith("$Recycle.Bin") || n.equalsIgnoreCase("System Volume Information")) return true;
        try {
            return Files.isHidden(f.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    public static Node.Kind kindOf(File f) {
        // A bundle is one thing, not a folder full of things.
        if (looksLikeBundle(f)) return Node.Kind.APPLICATION;
        if (f.isDirectory()) return Node.Kind.FOLDER;
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".lnk")) return Node.Kind.ALIAS;
        // An alias has nothing in its data fork, so only small files are asked; reading
        // a fork for every file in a large folder would be felt.
        if (f.length() <= 512 && org.fractalmicro.alias.Alias.isAlias(f)) return Node.Kind.ALIAS;
        if (isApplication(f)) return Node.Kind.APPLICATION;
        return Node.Kind.FILE;
    }

    public static Node node(File f) {
        Node n = new Node(kindOf(f), displayName(f), f);
        if (n.kind == Node.Kind.APPLICATION && f.isDirectory()) {
            BundleNaming how = naming;
            String identifier = how == null ? null : how.displayName(f);
            if (identifier != null) n.detail = identifier;
        }
        try {
            BasicFileAttributes a = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            n.modified = a.lastModifiedTime().toMillis();
            if (!a.isDirectory()) n.size = a.size();
        } catch (Exception ignored) { }
        n.locked = f.exists() && !f.canWrite();
        n.label = Labels.of(f);
        return n;
    }

    public static boolean isApplication(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".exe") || n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".msi");
    }

    /** Extensions stay hidden unless the global domain says to show them all. */
    public static String displayName(File f) {
        String n = f.getName();
        if (n.isEmpty()) return f.getAbsolutePath();
        // A bundle shows its name without the .app, unless extensions are all showing.
        if (looksLikeBundle(f)) {
            return FinderSettings.showAllExtensions() ? n : n.substring(0, n.length() - 4);
        }
        if (f.isDirectory()) return n;
        if (FinderSettings.showAllExtensions()) return n;
        int dot = n.lastIndexOf('.');
        if (dot <= 0) return n;
        // Only a real extension is hidden. "Budget.txt alias" ends in an alias, not in
        // an extension, and the Finder shows that name whole.
        String tail = n.substring(dot + 1);
        boolean extension = !tail.isEmpty() && tail.length() <= 8
                         && tail.chars().allMatch(Character::isLetterOrDigit);
        return extension ? n.substring(0, dot) : n;
    }

    /**
     * The exact number, with the separators of this place: 151,372,126,512 bytes.
     *
     * Get Info shows both this and the rounded figure, because the rounded one is what a
     * person reads and the exact one is what they came to Get Info for.
     */
    public static String formatExactBytes(long bytes) {
        if (bytes < 0) return "--";
        return String.format("%,d", bytes) + (bytes == 1 ? " byte" : " bytes");
    }

    /**
     * The size of a file, as Get Info writes it: exactly how much data is in it, then how
     * much of the disk it takes up.
     *
     * Two numbers: the second is the first rounded up to whole blocks, or smaller for a
     * sparse file. Printing one twice in different units would answer a question nobody
     * asked.
     */
    public static String formatSize(long bytes, long onDisk) {
        if (bytes < 0) return "--";
        if (onDisk < 0) return formatExactBytes(bytes);
        return formatExactBytes(bytes) + " (" + formatBytes(onDisk) + " on disk)";
    }

    /** Sizes in the decimal units Finder uses. */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < 1000) return bytes + (bytes == 1 ? " byte" : " bytes");
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int i = -1;
        while (v >= 1000 && i < units.length - 1) { v /= 1000; i++; }

        // One decimal place for small units, two for large, as Get Info shows: 965.7 MB,
        // 999.86 GB. Rounding a disk to whole gigabytes drops the digits people opened Get
        // Info to read.
        int places = i >= 2 ? 2 : 1;

        // Rounding can carry the number back over the threshold the unit was chosen by:
        // 999,960 bytes is 999.96 KB, which prints as "1000.0 KB" unless the unit is picked
        // again afterwards. Round first, then decide what to call it.
        double rounded = round(v, places);
        if (rounded >= 1000 && i < units.length - 1) {
            rounded = round(rounded / 1000, i + 1 >= 2 ? 2 : 1);
            i++;
            places = i >= 2 ? 2 : 1;
        }
        return String.format("%." + places + "f", rounded) + " " + units[i];
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "--";
        return new SimpleDateFormat("d MMMM yyyy HH:mm").format(new Date(millis));
    }

    /** Sorts by one of Finder's arrangement keys. */
    public static void sort(List<Node> nodes, String arrangeKey) {
        Comparator<Node> c;
        switch (arrangeKey == null ? "name" : arrangeKey) {
            case "dateModified":
                c = Comparator.comparingLong((Node n) -> n.modified).reversed();
                break;
            case "size":
                c = Comparator.comparingLong((Node n) -> n.size).reversed();
                break;
            case "kind":
                c = Comparator.comparing(Node::kindLabel)
                              .thenComparing(n -> n.name, String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                c = Comparator.comparing(n -> n.name, String.CASE_INSENSITIVE_ORDER);
        }
        if (FinderSettings.sortFoldersFirst()) {
            c = Comparator.comparing((Node n) -> !n.isContainer()).thenComparing(c);
        }
        nodes.sort(c);
    }

    /** Creates "untitled folder", then "untitled folder 2", as Finder names them. */
    public static File newFolder(File parent) {
        File f = new File(parent, "untitled folder");
        int i = 2;
        while (f.exists()) f = new File(parent, "untitled folder " + (i++));
        f.mkdirs();
        return f;
    }

    /**
     * Where a file would go inside a folder without treading on anything.
     *
     * The name it already has, or the one a Mac gives a second: "Report copy", then
     * "Report copy 2". The extension stays on the end, so a copy is still a document of
     * that kind and a folder with a dot in its name keeps all of it.
     */
    public static File freeNameIn(File src, File folder) {
        File plain = new File(folder, src.getName());
        if (!plain.exists()) return plain;

        String base = src.getName();
        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0 && !src.isDirectory()) {
            ext = base.substring(dot);
            base = base.substring(0, dot);
        }
        File dest = new File(folder, base + " copy" + ext);
        int i = 2;
        while (dest.exists()) dest = new File(folder, base + " copy " + (i++) + ext);
        return dest;
    }

    /** Copies a file or a whole folder to an exact place, which must not be there yet. */
    public static void copyTo(File src, File dest) throws IOException {
        if (src.isDirectory()) copyTree(src.toPath(), dest.toPath());
        else Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * Moves a file or a whole folder to an exact place.
     *
     * A rename on one disk, which is instant and cannot half happen. Across two there is no
     * such thing as moving: it is a copy and then a delete, and the delete only once the
     * copy is done, so an interrupted move leaves the file where it started.
     */
    public static void moveTo(File src, File dest) throws IOException {
        try {
            Files.move(src.toPath(), dest.toPath());
            return;
        } catch (IOException notARename) {
            // Across two disks, and for a folder the runtime will not move whole.
        }
        copyTo(src, dest);
        if (src.isDirectory()) deleteTree(src); else Files.delete(src.toPath());
    }

    private static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) {
            throw new IOException("could not remove " + file.getName());
        }
    }

    public static File duplicate(File src) throws IOException {
        String base = src.getName();
        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0 && !src.isDirectory()) {
            ext = base.substring(dot);
            base = base.substring(0, dot);
        }
        File dest = new File(src.getParentFile(), base + " copy" + ext);
        int i = 2;
        while (dest.exists()) dest = new File(src.getParentFile(), base + " copy " + (i++) + ext);
        if (src.isDirectory()) copyTree(src.toPath(), dest.toPath());
        else Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return dest;
    }

    public static void copyTree(Path src, Path dest) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(src)) {
            walk.forEach(p -> {
                try {
                    Path target = dest.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    public static void reveal(File f) { Shell.revealInExplorer(f); }
}
