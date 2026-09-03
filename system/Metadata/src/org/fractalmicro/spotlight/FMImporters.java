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
package org.fractalmicro.spotlight;

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.bundle.LaunchServices;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.uti.UTTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The importers, and which of them reads a given file.
 *
 * The same shape as the Quick Look generators and for the same reason: what can be read is
 * a question about what is installed, and the server that walks the disk should not be the
 * thing that knows how to read a property list.
 */
public final class FMImporters {
    private FMImporters() {}

    /** Under Library, beside the other places a plug-in goes. */
    public static final String FOLDER = "Spotlight";

    /** What one is called. */
    public static final String EXTENSION = ".mdimporter";

    /** The role an importer declares. Not one of the roles that open a file. */
    public static final FMString ROLE = FMString.of("MDImporter");

    private record Installed(Bundle bundle, FMMetadataImporter importer) {}

    private static List<Installed> installed;

    public static File importersFolder() {
        return OSPaths.systemLibrary().resolve(FOLDER).toFile();
    }

    /**
     * What the importers make of that file, or an empty dictionary.
     *
     * The first that answers wins, closest claim on the type first. An importer that fails
     * is passed over: a file that defeats one is not a reason to stop indexing.
     */
    public static FMDictionary attributesFor(File file) {
        if (file == null) return FMMutableDictionary.empty().asDictionary();
        for (Installed one : importersFor(file)) {
            try {
                FMDictionary said = one.importer().attributesFor(file);
                if (said != null && said.count() > 0) return said;
            } catch (RuntimeException failed) {
                FMLog.wrong(FMString.of("the importer ")
                                    .appending(one.bundle().displayName())
                                    .appending(FMString.of(" would not read a file")),
                            failed);
            }
        }
        return FMMutableDictionary.empty().asDictionary();
    }

    /** Which importer would read that file, by identifier, or nothing. */
    public static FMString importerFor(File file) {
        List<Installed> able = importersFor(file);
        return able.isEmpty() ? FMString.EMPTY : able.get(0).bundle().identifier();
    }

    /**
     * Every importer claiming the kind of thing that file is, closest claim first.
     *
     * The file's type, then what that type is a kind of, up to the root, so one written for
     * a kind of text is asked before the one that took every kind of text.
     */
    private static List<Installed> importersFor(File file) {
        FMString type = LaunchServices.typeOf(file);
        List<Installed> able = new ArrayList<>();
        for (FMString step : UTTypes.conformance(type)) {
            for (Installed one : all()) {
                if (!able.contains(one) && declares(one.bundle(), step)) able.add(one);
            }
        }
        return able;
    }

    /** Whether that bundle names exactly this type, as an importer rather than an editor. */
    private static boolean declares(Bundle bundle, FMString type) {
        for (Object entry : bundle.info().array(Bundle.DOCUMENT_TYPES)) {
            if (!(entry instanceof FMDictionary one)) continue;
            if (!ROLE.sameAs(one.string(Bundle.TYPE_ROLE, FMString.EMPTY))) continue;
            for (Object named : one.array(Bundle.CONTENT_TYPES)) {
                if (type.sameAs(FMString.describing(named))) return true;
            }
        }
        return false;
    }

    /** Read once. The server is long-lived, and a walk should not re-read a directory. */
    private static synchronized List<Installed> all() {
        if (installed != null) return installed;
        installed = new ArrayList<>();
        File[] found = importersFolder().listFiles();
        if (found == null) return installed;
        for (File each : found) {
            if (!each.isDirectory() || !each.getName().endsWith(EXTENSION)) continue;
            Bundle bundle = Bundle.read(each);
            if (bundle == null) {
                FMLog.say(FMString.of("an importer could not be read: " + each.getName()));
                continue;
            }
            try {
                if (Dyld.load(bundle) instanceof FMMetadataImporter made) {
                    installed.add(new Installed(bundle, made));
                } else {
                    FMLog.say(bundle.displayName()
                                    .appending(FMString.of(" is not an importer")));
                }
            } catch (Exception wouldNotLoad) {
                FMLog.wrong(FMString.of("the importer " + each.getName()
                                        + " would not load"), wouldNotLoad);
            }
        }
        return installed;
    }
}
