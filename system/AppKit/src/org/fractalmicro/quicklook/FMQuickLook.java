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
package org.fractalmicro.quicklook;

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.LaunchServices;
import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.uti.UTTypes;

import javax.swing.JComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * What is inside a file, without opening the program that made it.
 *
 * The window is the Finder's. Which generator shows a file is here, because the answer is
 * the same wherever the question comes from.
 */
public final class FMQuickLook {
    private FMQuickLook() {}

    /** Under Library, beside the other places a plug-in goes. */
    public static final String FOLDER = "QuickLook";

    /** What one is called. */
    public static final String EXTENSION = ".qlgenerator";

    /** The role a generator declares. Not one of the roles that open a file. */
    public static final FMString ROLE = FMString.of("QLGenerator");

    private record Installed(Bundle bundle, FMQuickLookGenerator generator) {}

    private static List<Installed> installed;

    public static File generatorsFolder() {
        return OSPaths.systemLibrary().resolve(FOLDER).toFile();
    }

    /**
     * The view of that file, or nothing when no generator will show it.
     *
     * A generator that fails is passed over. One bad plug-in is not a reason for the panel
     * to have nothing in it, and the next one along may well manage.
     */
    public static JComponent previewOf(File file) {
        if (file == null || !file.isFile()) return null;
        for (Installed one : generatorsFor(file)) {
            try {
                JComponent view = one.generator().preview(file);
                if (view != null) return view;
            } catch (RuntimeException failed) {
                FMLog.wrong(FMString.of("the Quick Look generator ")
                                    .appending(one.bundle().displayName())
                                    .appending(FMString.of(" would not show a file")),
                            failed);
            }
        }
        return null;
    }

    /**
     * Which generator would show that file, by identifier, or nothing.
     *
     * The panel does not need to ask this. It is how anything else finds out what would
     * happen without a window being made to find out.
     */
    public static FMString generatorFor(File file) {
        List<Installed> able = generatorsFor(file);
        return able.isEmpty() ? FMString.EMPTY : able.get(0).bundle().identifier();
    }

    /**
     * Every generator that claims the kind of thing that file is, closest claim first.
     *
     * The file's type, then what that type is a kind of, up to the root: a generator
     * naming public.png is asked before one naming public.image, which is asked before one
     * that took public.data. That is the whole use of declaring a type rather than a suffix.
     */
    private static List<Installed> generatorsFor(File file) {
        FMString type = LaunchServices.typeOf(file);
        List<Installed> able = new ArrayList<>();
        for (FMString step : UTTypes.conformance(type)) {
            for (Installed one : all()) {
                if (!able.contains(one) && declares(one.bundle(), step)) able.add(one);
            }
        }
        return able;
    }

    /** Whether that bundle names exactly this type, as a generator rather than an editor. */
    private static boolean declares(Bundle bundle, FMString type) {
        for (Object entry : bundle.info().array(Bundle.DOCUMENT_TYPES)) {
            FMDictionary one = asDictionary(entry);
            if (one == null) continue;
            if (!ROLE.sameAs(one.string(Bundle.TYPE_ROLE, FMString.EMPTY))) continue;
            for (Object named : one.array(Bundle.CONTENT_TYPES)) {
                if (type.sameAs(FMString.describing(named))) return true;
            }
        }
        return false;
    }

    /** A declaration as a dictionary, whichever of the two shapes it was read back in. */
    private static FMDictionary asDictionary(Object value) {
        if (value instanceof FMDictionary already) return already;
        if (!(value instanceof java.util.Map<?, ?> map)) return null;
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<?, ?> one : map.entrySet()) {
            out.put(String.valueOf(one.getKey()), one.getValue());
        }
        return FMDictionary.fromMap(out);
    }

    /** Read once. A generator arriving while the desktop runs waits for the next start. */
    private static synchronized List<Installed> all() {
        if (installed != null) return installed;
        installed = new ArrayList<>();
        File[] found = generatorsFolder().listFiles();
        if (found == null) return installed;
        for (File each : found) {
            if (!each.isDirectory() || !each.getName().endsWith(EXTENSION)) continue;
            Bundle bundle = Bundle.read(each);
            if (bundle == null) {
                FMLog.say(FMString.of("a Quick Look generator could not be read: "
                                      + each.getName()));
                continue;
            }
            try {
                if (Dyld.load(bundle) instanceof FMQuickLookGenerator made) {
                    installed.add(new Installed(bundle, made));
                } else {
                    FMLog.say(bundle.displayName()
                                    .appending(FMString.of(" is not a Quick Look generator")));
                }
            } catch (Exception wouldNotLoad) {
                FMLog.wrong(FMString.of("the Quick Look generator " + each.getName()
                                        + " would not load"), wouldNotLoad);
            }
        }
        return installed;
    }
}
