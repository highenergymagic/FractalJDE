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

import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.FinderSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The places worth putting in a sidebar.
 *
 * Devices, then the folders a person keeps things in, then anything they dragged in
 * themselves. Which of those groups are shown is theirs to say, and one of them is a list
 * they wrote, so this is not a constant: it is read each time it is asked for.
 *
 * It lives here rather than in the file browser because the file browser is not the only
 * thing that shows it. The save panel shows the same places, and it has to be the same
 * places: somebody who dragged a folder into the sidebar expects to find it there when
 * they save, and a second list assembled somewhere else would be the same list right up
 * until one of them was changed.
 */
public final class Places {
    private Places() {}

    /** Where a person's own additions are kept, under the name Mac OS X uses. */
    public static final FMString FAVOURITES = FMString.of("SidebarFavourites");

    /** How a person's own additions are written down: paths, separated. */
    private static final String BETWEEN = "|";

    /** The headings, in the order they appear. */
    public static final FMString DEVICES = FMString.of("sidebar.devices");
    public static final FMString SHARED = FMString.of("sidebar.shared");
    public static final FMString PLACES = FMString.of("sidebar.places");
    public static final FMString SEARCH_FOR = FMString.of("sidebar.searchFor");

    /**
     * One thing in the sidebar.
     *
     * A place is usually a folder, and sometimes a thing there is no folder for: the
     * computer itself, the Trash, a saved search. Those carry a token instead of a file,
     * because what opens them is a decision for whatever is showing them.
     */
    public record Place(FMString name, File file, FMString token, Node.Kind kind) {

        public static Place folder(String name, File where) {
            return new Place(FMString.of(name), where, FMString.EMPTY, Node.Kind.FOLDER);
        }

        /**
         * One of the folders every account has, under the name this account reads.
         *
         * A home folder's name is on the disk and stays as it is. The seven inside it are
         * the system's own and are named in the language, which is what a Mac does with a
         * .localized file sitting in each of them.
         */
        public static Place systemFolder(FMString key, File where) {
            return new Place(FMLocalized.of(key), where, FMString.EMPTY, Node.Kind.FOLDER);
        }

        public static Place named(String name, FMString token, Node.Kind kind) {
            return new Place(FMString.of(name), null, token, kind);
        }

        /** Whether this is somewhere a panel could actually save into. */
        public boolean isRealFolder() { return file != null && file.isDirectory(); }
    }

    /** One heading and the places under it. */
    public record Group(FMString heading, List<Place> places) { }

    /**
     * Everything a sidebar would show, grouped as it is shown.
     *
     * A group with nothing in it is left out rather than shown empty, which is why a
     * machine with no servers has no SHARED heading rather than a heading over nothing.
     */
    public static List<Group> all() {
        List<Group> out = new ArrayList<>();

        if (FinderSettings.sidebarShowDevices()) {
            List<Place> devices = new ArrayList<>();
            devices.add(Place.named(System.getProperty("user.name") + "'s Fractal",
                                    FMString.of("computer"), Node.Kind.COMPUTER));
            for (Node volume : Volumes.all()) {
                // An empty optical drive shows nothing, the way it shows nothing on a Mac.
                if (volume.kind == Node.Kind.REMOVABLE_MEDIA && !volume.isMounted()) continue;
                if (volume.kind == Node.Kind.SERVER) continue;   // those go under SHARED
                devices.add(new Place(FMString.of(volume.name), volume.file,
                                      FMString.EMPTY, volume.kind));
            }
            add(out, DEVICES, devices);

            List<Place> shared = new ArrayList<>();
            for (Node server : Volumes.ofKind(Node.Kind.SERVER)) {
                shared.add(new Place(FMString.of(server.name), server.file,
                                     FMString.EMPTY, server.kind));
            }
            add(out, SHARED, shared);
        }

        if (FinderSettings.sidebarShowPlaces()) add(out, PLACES, folders());

        if (FinderSettings.sidebarShowSearch()) {
            List<Place> searches = new ArrayList<>();
            for (String which : new String[]{"today", "yesterday", "week",
                                             "images", "movies", "documents"}) {
                searches.add(Place.named(FMLocalized.of(savedSearchName(which)).toString(),
                                         FMString.of("search:" + which), Node.Kind.SEARCH));
            }
            add(out, SEARCH_FOR, searches);
        }
        return out;
    }

    /**
     * The folders under PLACES: the usual ones, then whatever was added.
     *
     * This is the part a save panel wants on its own. It has no use for a saved search or
     * for the Trash, and offering somebody the chance to save a document into the Trash is
     * not a kindness.
     */
    public static List<Place> folders() {
        List<Place> out = new ArrayList<>();
        out.add(Place.systemFolder(FMString.of("place.desktop"), FS.desktopFolder()));
        out.add(Place.folder(System.getProperty("user.name"), FS.home()));
        out.add(Place.systemFolder(FMString.of("place.documents"), FS.documents()));
        out.add(Place.systemFolder(FMString.of("place.downloads"), FS.downloads()));
        out.add(Place.systemFolder(FMString.of("place.music"), FS.music()));
        out.add(Place.systemFolder(FMString.of("place.pictures"), FS.pictures()));
        out.add(Place.systemFolder(FMString.of("place.movies"), FS.movies()));
        for (File one : favourites()) out.add(Place.folder(one.getName(), one));
        return out;
    }

    /** What a saved search is called. Named here because two places show the same list. */
    public static FMString savedSearchName(String which) {
        return switch (which) {
            case "today" -> FMString.of("search.today");
            case "yesterday" -> FMString.of("search.yesterday");
            case "week" -> FMString.of("search.pastWeek");
            case "images" -> FMString.of("search.allImages");
            case "movies" -> FMString.of("search.allMovies");
            default -> FMString.of("search.allDocuments");
        };
    }

    /** The folders somebody put in the sidebar themselves. */
    public static List<File> favourites() {
        List<File> out = new ArrayList<>();
        String written = FMUserDefaults.of(FinderSettings.FRACTAL).string(FAVOURITES).toString();
        if (written.isBlank()) return out;
        for (String path : written.split("\\" + BETWEEN)) {
            File one = new File(path);
            if (one.isDirectory()) out.add(one);
        }
        return out;
    }

    /** Adds a folder to the sidebar, answering whether it was not already there. */
    public static boolean addFavourite(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        List<File> already = favourites();
        for (File one : already) {
            if (one.equals(folder)) return false;
        }
        already.add(folder);
        write(already);
        return true;
    }

    private static void write(List<File> folders) {
        List<String> paths = new ArrayList<>();
        for (File one : folders) paths.add(one.getAbsolutePath());
        FMUserDefaults.of(FinderSettings.FRACTAL).set(FAVOURITES, String.join(BETWEEN, paths));
    }

    private static void add(List<Group> into, FMString heading, List<Place> places) {
        if (!places.isEmpty()) into.add(new Group(heading, places));
    }
}
