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


import org.fractalmicro.fs.Node;
import org.fractalmicro.icns.IcnsFile;
import org.fractalmicro.os.OSPaths;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Where icons come from. Files are looked for under the pretend system volume, at the
 * paths Mac OS X keeps them at:
 *
 *   ~/.fractaldt/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/
 *   ~/.fractaldt/System/Library/CoreServices/Dock.app/Contents/Resources/
 *
 * with the file names Apple uses, so a copy of a real icon set can be dropped in and
 * simply works. Both .icns and .png are read. When nothing is found the drawn
 * lookalikes in {@link Icons} are used instead.
 */
public final class IconProvider {
    private IconProvider() {}

    private static final Map<String, Image> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** Apple's file names first, then plain ones for anyone assembling a set by hand. */
    private static String[] candidates(Node.Kind kind, boolean trashFull) {
        switch (kind) {
            case FOLDER:
                return new String[]{"GenericFolderIcon", "folder"};
            case APPLICATION:
                return new String[]{"GenericApplicationIcon", "application"};
            case ALIAS:
                return new String[]{"GenericAliasIcon", "AliasBadgeIcon", "alias"};
            case HARD_DISK:
                return new String[]{"GenericHardDiskIcon", "HardDriveIcon", "hard-disk"};
            case EXTERNAL_DISK:
                return new String[]{"GenericExternalDiskIcon", "GenericRemovableMediaIcon",
                                    "external-disk"};
            case REMOVABLE_MEDIA:
                return new String[]{"GenericCDROMIcon", "CDAudioVolumeIcon", "disc"};
            case SERVER:
                return new String[]{"GenericFileServerIcon", "server"};
            case NETWORK:
                return new String[]{"GenericNetworkIcon", "network"};
            case COMPUTER:
                return new String[]{"ComputerIcon", "GenericPCIcon", "computer"};
            case SEARCH:
                return new String[]{"SmartFolderIcon", "search"};
            case TRASH:
                return trashFull
                    ? new String[]{"TrashFull", "trashfull", "FullTrashIcon", "trash-full"}
                    : new String[]{"TrashEmpty", "trashempty", "TrashIcon", "trash-empty"};
            default:
                return new String[]{"GenericDocumentIcon", "document"};
        }
    }

    private static final String[] EXTENSIONS = {".icns", ".png", ".gif", ".jpg"};

    private static List<Path> searchPath() {
        return Arrays.asList(
            OSPaths.coreTypesResources(),
            OSPaths.dockResources(),
            OSPaths.systemLibrary().resolve("Extensions"),
            OSPaths.ROOT.resolve("Library/Icons"));
    }

    /** An icon from the installed set, or null when the set has nothing for this kind. */
    public static Image lookup(Node.Kind kind, int size, boolean trashFull) {
        String key = kind + (kind == Node.Kind.TRASH ? (trashFull ? ":full" : ":empty") : "") + "/" + size;
        Image cached = CACHE.get(key);
        if (cached != null) return cached;
        if (MISSING.contains(key)) return null;

        File file = find(candidates(kind, trashFull));
        if (file == null) {
            MISSING.add(key);
            return null;
        }
        BufferedImage image = load(file, size);
        if (image == null) {
            MISSING.add(key);
            return null;
        }
        Image scaled = image.getWidth() == size
            ? image
            : image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        CACHE.put(key, scaled);
        return scaled;
    }

    private static File find(String[] names) {
        for (Path dir : searchPath()) {
            for (String name : names) {
                for (String ext : EXTENSIONS) {
                    File f = dir.resolve(name + ext).toFile();
                    if (f.isFile()) return f;
                }
            }
        }
        return null;
    }

    private static BufferedImage load(File file, int size) {
        try {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".icns")) {
                return IcnsFile.read(file.toPath()).image(size);
            }
            return ImageIO.read(file);
        } catch (Exception e) {
            System.err.println("could not read icon " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Forgets everything, so a newly installed set is picked up. */
    public static void reload() {
        CACHE.clear();
        MISSING.clear();
    }

    /** True when at least the folder icon is present. */
    public static boolean installed() {
        return find(candidates(Node.Kind.FOLDER, false)) != null;
    }

    public static Path installLocation() {
        return OSPaths.coreTypesResources();
    }
}
