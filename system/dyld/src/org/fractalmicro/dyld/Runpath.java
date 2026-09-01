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
package org.fractalmicro.dyld;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Turning a name in a load command into a file on this volume.
 *
 * A name beginning @rpath means nothing on its own. The image carrying it says what it
 * stands for in its LC_RPATH commands, and they are tried in the order given, which is how
 * the same library is found in the system's frameworks on one machine and inside an
 * application on another without either recording an absolute path. @loader_path stands
 * for the directory of whatever is doing the loading, and @executable_path for the
 * directory of the program that started.
 *
 * An absolute name is absolute within the volume this system is installed on rather than
 * the host's, because that is the root this system has.
 *
 * This belongs to the loader rather than to anything above it. The loader runs before the
 * libraries it is about to load, so it cannot use them: everything here is done with what
 * the runtime itself provides, which is the same reason the loader on a Mac is not allowed
 * to link the system library it is going to bring in.
 */
public final class Runpath {

    public static final String RPATH = "@rpath/";
    public static final String LOADER_PATH = "@loader_path";
    public static final String EXECUTABLE_PATH = "@executable_path";

    /** Where a name beginning @rpath is looked for when the image names nowhere else. */
    public static final String DEFAULT_FRAMEWORKS = "/System/Library/Frameworks";

    private final Path root;
    private final Path executable;

    /**
     * @param root       the volume this system is installed on
     * @param executable the program that started, for @executable_path, or null
     */
    public Runpath(Path root, Path executable) {
        this.root = root.toAbsolutePath().normalize();
        this.executable = executable == null ? null : executable.toAbsolutePath().getParent();
    }

    /**
     * Resolves one name.
     *
     * @param installPath what the load command says
     * @param runpaths    what @rpath stands for, from the image doing the linking
     * @param loader      where that image is, for @loader_path
     * @return the file, or null when there is nothing there
     */
    public Path resolve(String installPath, List<String> runpaths, Path loader) {
        if (installPath == null || installPath.isBlank()) return null;

        if (installPath.startsWith("/")) {
            return existing(withinVolume(root.resolve(installPath.substring(1))));
        }
        if (!installPath.startsWith(RPATH)) {
            Path base = base(installPath, loader);
            if (base == null) return null;
            String rest = installPath.startsWith("@")
                ? installPath.substring(installPath.indexOf('/') + 1) : installPath;
            return existing(withinVolume(base.resolve(rest).normalize()));
        }

        String wanted = installPath.substring(RPATH.length());
        List<String> where = runpaths.isEmpty() ? List.of(DEFAULT_FRAMEWORKS) : runpaths;
        for (String one : where) {
            Path base = base(one, loader);
            if (base == null) continue;
            Path found = existing(withinVolume(base.resolve(wanted).normalize()));
            if (found != null) return found;
        }
        return null;
    }

    /** What one runpath entry, or the front of a name, stands for. */
    private Path base(String text, Path loader) {
        if (text.startsWith(LOADER_PATH)) {
            if (loader == null) return null;
            return loader.resolve(trim(text, LOADER_PATH)).normalize();
        }
        if (text.startsWith(EXECUTABLE_PATH)) {
            if (executable == null) return null;
            return executable.resolve(trim(text, EXECUTABLE_PATH)).normalize();
        }
        if (text.startsWith("/")) return root.resolve(text.substring(1));
        return root.resolve(text);
    }

    private static String trim(String text, String prefix) {
        String rest = text.substring(prefix.length());
        while (rest.startsWith("/")) rest = rest.substring(1);
        // A name like @loader_path/../Frameworks/X keeps its tail; one that is only the
        // prefix resolves to the directory itself.
        int slash = rest.indexOf('/');
        return slash < 0 && rest.isEmpty() ? "" : rest;
    }

    private static Path existing(Path candidate) {
        return candidate != null && Files.isReadable(candidate) ? candidate : null;
    }

    /**
     * Refuses a path that climbs out of the volume.
     *
     * The name came from an executable that may not have been built here, and one with
     * enough .. in it would otherwise name any file on the machine.
     */
    private Path withinVolume(Path candidate) {
        Path resolved = candidate.toAbsolutePath().normalize();
        return resolved.startsWith(root) ? resolved : null;
    }
}
