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
 * A Kind is the description of a file's type, and the types are declared in
 * UTCoreTypes.plist rather than listed here, so a .plist is a property list and a .docx is
 * a Microsoft Word document whatever program happens to be registered for it on this
 * machine. A file whose extension no declaration claims falls back to the description
 * Windows gives, and anything Windows only knows as "XYZ File" is a document, which is the
 * name a Mac gives an unknown type.
 */
public final class Kinds {
    private Kinds() {}

    private static final Map<String, String> CACHE = new HashMap<>();

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

        // What the type says it is called, which is what a Kind column has always been:
        // the description of the file's type, not a lookup of its extension. The extension
        // is only how the type was arrived at.
        //
        // What comes back from Windows is not looked up. It is Windows' own description,
        // already in the language Windows was installed in, and there is nothing here to
        // translate it against.
        String known = describedType(extension);
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
     * The description key of the type an extension means, or nothing when none does.
     *
     * The table this used to hold went into UTCoreTypes.plist, where it is declarations
     * rather than a lookup: the same list, arranged so that everything else that needs to
     * know what a file is asks the same database.
     */
    private static String describedType(String extension) {
        org.fractalmicro.foundation.FMString type = org.fractalmicro.uti.UTTypes.preferredType(
            org.fractalmicro.foundation.FMString.of(extension));
        if (type == null) return null;
        org.fractalmicro.foundation.FMString key = org.fractalmicro.uti.UTTypes.descriptionKey(type);
        return key.isBlank() ? null : key.toString();
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
