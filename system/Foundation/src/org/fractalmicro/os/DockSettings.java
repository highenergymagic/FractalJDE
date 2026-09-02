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
package org.fractalmicro.os;


import org.fractalmicro.foundation.FMString;

import java.io.File;
import java.util.*;

/**
 * The Dock's settings, stored in org.fractalmicro.dock, under the key names the format uses:
 * tilesize, magnification, largesize, orientation, and persistent-apps as an array of
 * file tiles carrying a file-label and a _CFURLString.
 */
public final class DockSettings {
    private DockSettings() {}

    public static final FMString TILE_SIZE = FMString.of("tilesize");
    public static final FMString MAGNIFICATION = FMString.of("magnification");
    public static final FMString LARGE_SIZE = FMString.of("largesize");
    public static final FMString ORIENTATION = FMString.of("orientation");
    public static final FMString PERSISTENT_APPS = FMString.of("persistent-apps");

    /** One pinned tile. */
    public static final class Tile {
        public final String label;
        public final File file;

        public Tile(String label, File file) {
            this.label = label;
            this.file = file;
        }
    }

    private static FMUserDefaults dock() { return FMUserDefaults.of(FMUserDefaults.DOCK); }

    public static void installDefaults() {
        FMUserDefaults d = dock();
        d.applyDefault(TILE_SIZE, 48.0);
        d.applyDefault(MAGNIFICATION, Boolean.FALSE);
        d.applyDefault(LARGE_SIZE, 128.0);
        d.applyDefault(ORIENTATION, "bottom");
        d.applyDefault(PERSISTENT_APPS, new ArrayList<>());
        d.save();
    }

    public static int tileSize() { return (int) Math.round(dock().real(TILE_SIZE, 48)); }
    public static void setTileSize(int px) { dock().set(TILE_SIZE, (double) px); }

    public static boolean magnification() { return dock().bool(MAGNIFICATION, false); }
    public static void setMagnification(boolean v) { dock().set(MAGNIFICATION, v); }

    public static int largeSize() { return (int) Math.round(dock().real(LARGE_SIZE, 128)); }

    public static FMString orientation() {
        return dock().string(ORIENTATION, FMString.of("bottom"));
    }

    /* ------------------------------------------------------ pinned tiles */

    @SuppressWarnings("unchecked")
    public static List<Tile> persistentApps() {
        List<Tile> out = new ArrayList<>();
        for (Object entry : dock().array(PERSISTENT_APPS)) {
            if (!(entry instanceof Map)) continue;
            Map<String, Object> tile = (Map<String, Object>) entry;
            Object data = tile.get(FMString.of("tile-data"));
            if (!(data instanceof Map)) continue;
            Map<String, Object> tileData = (Map<String, Object>) data;
            String label = String.valueOf(tileData.getOrDefault("file-label", ""));
            Object fileData = tileData.get(FMString.of("file-data"));
            String url = fileData instanceof Map
                ? String.valueOf(((Map<String, Object>) fileData).getOrDefault("_CFURLString", ""))
                : "";
            if (url.isEmpty()) continue;
            out.add(new Tile(label, new File(FinderSettings.fromFileUrl(url))));
        }
        return out;
    }

    public static boolean isPinned(File file) {
        if (file == null) return false;
        for (Tile t : persistentApps()) {
            if (t.file != null && t.file.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    public static void pin(String label, File file) {
        if (file == null || isPinned(file)) return;
        java.util.List<Object> apps = new ArrayList<>(dock().array(PERSISTENT_APPS).asList());
        apps.add(tileFor(label, file));
        dock().set(PERSISTENT_APPS, apps);
    }

    @SuppressWarnings("unchecked")
    public static void unpin(File file) {
        if (file == null) return;
        java.util.List<Object> apps = new ArrayList<>(dock().array(PERSISTENT_APPS).asList());
        apps.removeIf(entry -> {
            if (!(entry instanceof Map)) return false;
            Object data = ((Map<String, Object>) entry).get(FMString.of("tile-data"));
            if (!(data instanceof Map)) return false;
            Object fileData = ((Map<String, Object>) data).get(FMString.of("file-data"));
            if (!(fileData instanceof Map)) return false;
            String url = String.valueOf(((Map<String, Object>) fileData).getOrDefault("_CFURLString", ""));
            return FinderSettings.fromFileUrl(url).equalsIgnoreCase(file.getAbsolutePath());
        });
        dock().set(PERSISTENT_APPS, apps);
    }

    private static Map<String, Object> tileFor(String label, File file) {
        Map<String, Object> fileData = new LinkedHashMap<>();
        fileData.put("_CFURLString", FinderSettings.toFileUrl(file));
        fileData.put("_CFURLStringType", 15L);

        Map<String, Object> tileData = new LinkedHashMap<>();
        tileData.put("file-label", label);
        tileData.put("file-data", fileData);
        tileData.put("file-type", 41L);

        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("GUID", (long) Math.abs((label + file.getAbsolutePath()).hashCode()));
        tile.put("tile-type", "file-tile");
        tile.put("tile-data", tileData);
        return tile;
    }
}
