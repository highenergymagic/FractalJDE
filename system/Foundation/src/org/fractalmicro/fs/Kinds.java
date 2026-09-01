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

import org.fractalmicro.win.Shell32;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What an item is, in the words Finder uses for its Kind field.
 *
 * A table of the types Mac OS X names for itself comes first, so a .plist is a property
 * list and a .docx is a Microsoft Word document rather than whatever program happens to
 * be registered for it on this machine. Anything not in the table falls back to the
 * description Windows gives, and anything Windows only knows as "XYZ File" is a
 * document, the name a Mac gives an unknown type.
 */
public final class Kinds {
    private Kinds() {}

    private static final Map<String, String> BY_EXTENSION = new HashMap<>();
    private static final Map<String, String> CACHE = new HashMap<>();

    static {
        // Text and code
        put("kind.plainTextDocument", "txt", "text", "log", "nfo");
        put("kind.richTextDocument", "rtf");
        put("kind.htmlText", "html", "htm", "xhtml");
        put("kind.xmlText", "xml", "xsd", "xsl");
        put("kind.jsonText", "json");
        put("kind.yamlText", "yaml", "yml");
        put("kind.markdownText", "md", "markdown");
        put("kind.propertyList", "plist");
        put("kind.configurationSettings", "ini", "cfg", "conf", "toml", "properties");
        put("kind.commaSeparatedValues", "csv");
        put("kind.javaSource", "java");
        put("kind.cSource", "c", "h");
        put("kind.c++Source", "cpp", "cc", "cxx", "hpp");
        put("kind.pythonScript", "py");
        put("kind.rubyScript", "rb");
        put("kind.shellScript", "sh", "bash", "zsh");
        put("kind.batchFile", "bat", "cmd");
        put("kind.powerShellScript", "ps1");
        put("kind.javaScriptSource", "js", "mjs");
        put("kind.typeScriptSource", "ts", "tsx");
        put("kind.rustSource", "rs");
        put("kind.goSource", "go");
        put("kind.patchFile", "patch", "diff");

        // Documents
        put("kind.pdfDocument", "pdf");
        put("kind.microsoftWordDocument", "doc", "docx", "docm");
        put("kind.microsoftExcelWorkbook", "xls", "xlsx", "xlsm");
        put("kind.microsoftPowerPointPresentation", "ppt", "pptx");
        put("kind.openDocumentText", "odt");
        put("kind.openDocumentSpreadsheet", "ods");
        put("kind.openDocumentPresentation", "odp");
        put("kind.pagesDocument", "pages");
        put("kind.numbersSpreadsheet", "numbers");
        put("kind.keynotePresentation", "key");
        put("kind.epubPublication", "epub");

        // Images
        put("kind.portableNetworkGraphicsImage", "png");
        put("kind.jpegImage", "jpg", "jpeg", "jpe");
        put("kind.gifImage", "gif");
        put("kind.tiffImage", "tif", "tiff");
        put("kind.windowsBitmapImage", "bmp");
        put("kind.webpImage", "webp");
        put("kind.scalableVectorGraphicsImage", "svg");
        put("kind.iconImage", "icns");
        put("kind.windowsIconImage", "ico");
        put("kind.photoshopDocument", "psd");
        put("kind.rawCameraImage", "raw", "cr2", "nef", "arw", "dng");
        put("kind.heifImage", "heic", "heif");

        // Sound and moving pictures
        put("kind.mp3Audio", "mp3");
        put("kind.mpeg4Audio", "m4a", "m4b");
        put("kind.aacAudio", "aac");
        put("kind.waveformAudio", "wav");
        put("kind.aiffAudio", "aiff", "aif");
        put("kind.flacAudio", "flac");
        put("kind.oggVorbisAudio", "ogg", "oga");
        put("kind.opusAudio", "opus");
        put("kind.midiSequence", "mid", "midi");
        put("kind.quickTimeMovie", "mov", "qt");
        put("kind.mpeg4Movie", "mp4", "m4v");
        put("kind.matroskaMovie", "mkv");
        put("kind.aviMovie", "avi");
        put("kind.windowsMediaVideo", "wmv");
        put("kind.mpegMovie", "mpg", "mpeg");
        put("kind.webmMovie", "webm");
        put("kind.playlist", "m3u", "m3u8", "pls");

        // Archives and disks
        put("kind.zipArchive", "zip");
        put("kind.gzipArchive", "gz", "tgz");
        put("kind.bzip2Archive", "bz2");
        put("kind.xzArchive", "xz");
        put("kind.zstandardArchive", "zst");
        put("kind.tarArchive", "tar");
        put("kind.7ZipArchive", "7z");
        put("kind.rarArchive", "rar");
        put("kind.javaArchive", "jar", "war");
        put("kind.diskImage", "dmg", "iso", "img", "vhd", "vhdx", "vdi", "qcow2");
        put("kind.windowsInstallerPackage", "msi");
        put("kind.androidPackage", "apk", "xapk");
        put("kind.debianPackage", "deb");
        put("kind.rpmPackage", "rpm");

        // Programs and libraries
        put("kind.application", "exe", "com");
        put("kind.dynamicLibrary", "dll", "so", "dylib");
        put("kind.kernelExtension", "sys", "ko");
        put("kind.font", "ttf", "otf", "ttc", "woff", "woff2", "fon");
        put("kind.databaseFile", "db", "sqlite", "sqlite3", "mdb");
        put("kind.certificate", "cer", "crt", "pem", "p12", "pfx");
        put("kind.bundle", "bundle", "app", "framework", "plugin", "kext");
        put("kind.alias", "lnk", "url", "webloc");
        put("kind.clipping", "clipping");
        put("kind.smartFolder", "savedsearch");
        put("kind.virtualMachine", "vbox", "vmx", "vmdk");
        put("kind.torrentFile", "torrent");
        put("kind.subtitleFile", "srt", "vtt", "ass", "sub");
        put("kind.gpxTrack", "gpx");
        put("kind.calendarFile", "ics");
        put("kind.contactCard", "vcf");
        put("kind.emailMessage", "eml", "msg");
    }

    private static void put(String kind, String... extensions) {
        for (String e : extensions) BY_EXTENSION.put(e, kind);
    }

    /** What an alias is called, and what it points at, for Get Info. */
    public static String aliasDetail(Node node) {
        if (node == null || node.file == null) return "";
        org.fractalmicro.alias.Alias.Resolution found = org.fractalmicro.alias.Alias.resolve(node.file);
        if (!found.ok()) return "the original cannot be found";
        return switch (found.how()) {
            case PATH -> found.target().getAbsolutePath();
            case IDENTITY -> found.target().getAbsolutePath() + " (moved since this was made)";
            case NAME -> found.target().getAbsolutePath() + " (a new file of the same name)";
            case LOST -> "the original cannot be found";
        };
    }

    /** The kind, in the lower case that reads well in a sentence. */
    public static String of(Node node) {
        if (node == null) return named("kind.document");
        // Anything the Finder treats as an application says so, whether it is a program
        // or a shortcut to one; the Applications folder is mostly shortcuts.
        if (node.kind == Node.Kind.APPLICATION) return named("kind.application");
        if (node.kind == Node.Kind.ALIAS && pointsAtApplication(node.file)) return named("kind.application");
        switch (node.kind) {
            case FOLDER:  return named("kind.folder");
            case TRASH:   return named("kind.folder");
            case COMPUTER: return named("kind.computer");
            case NETWORK: return named("kind.network");
            case SEARCH:  return named("kind.smartFolder");
            case HARD_DISK: case EXTERNAL_DISK: case REMOVABLE_MEDIA: case SERVER:
                return named("kind.volume");
            default:
                break;
        }
        if (node.file == null) return node.kind == Node.Kind.APPLICATION ? "application" : "document";
        return ofFile(node.file);
    }

    /** Follows a shortcut to see whether what it points at is a program. */
    private static boolean pointsAtApplication(File shortcut) {
        if (shortcut == null) return false;
        File target = Apps.resolve(shortcut);
        if (target == null || target.equals(shortcut)) return false;
        if (target.isDirectory()) return false;
        return FS.isApplication(target);
    }

    public static String ofFile(File file) {
        if (file == null) return named("kind.document");
        if (file.isDirectory()) return named("kind.folder");
        if (FS.isApplication(file)) return named("kind.application");
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".lnk")
                && pointsAtApplication(file)) {
            return named("kind.application");
        }

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (extension.isEmpty()) return named("kind.document");

        // The table holds keys, so what comes out of it is looked up. What comes back from
        // Windows is not: it is Windows' own description, already in the language Windows
        // was installed in, and there is nothing here to translate it against.
        String known = BY_EXTENSION.get(extension);
        if (known != null) return named(known);

        synchronized (CACHE) {
            String cached = CACHE.get(extension);
            if (cached != null) return cached;
        }
        String windows = fromWindows(extension);
        synchronized (CACHE) {
            CACHE.put(extension, windows);
        }
        return windows;
    }

    /**
     * Windows' own description for an extension. Its generic answer, "XYZ File", means
     * it does not really know either, and an unknown type is a document.
     */
    private static String fromWindows(String extension) {
        String description = Shell32.typeName("unknown." + extension, false).trim();
        if (description.isEmpty()) return named("kind.document");
        if (description.toLowerCase(Locale.ROOT).endsWith(" file")) {
            String head = description.substring(0, description.length() - 5);
            if (head.equalsIgnoreCase(extension) || head.equals(head.toUpperCase(Locale.ROOT))) {
                return named("kind.document");
            }
        }
        return description;
    }

    /** The same thing with a capital, for the Kind field in Get Info. */
    public static String display(Node node) {
        String kind = of(node);
        return kind.isEmpty() ? kind : Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    }

    /** What a selected item is called: "selected Microsoft Word document". */
    public static String selectedDescription(Node node) {
        return org.fractalmicro.foundation.FMLocalized.filled(
            org.fractalmicro.foundation.FMString.of("kind.selected"),
            org.fractalmicro.foundation.FMString.of(of(node))).toString();
    }
    /**
     * One kind's name, in the language this account reads.
     *
     * The table holds keys rather than names, because a kind is something a person reads
     * in Get Info and hears from a screen reader, and neither of those should be in
     * English on a machine that is not.
     */
    private static String named(String key) {
        return org.fractalmicro.foundation.FMLocalized.of(
            org.fractalmicro.foundation.FMString.of(key)).toString();
    }

}
