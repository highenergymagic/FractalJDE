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
package org.fractalmicro.textedit;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.os.Defaults;

/**
 * What TextEdit remembers between one time it runs and the next.
 *
 * Kept in this program's own preference domain, under the names the original used, so a
 * volume carried forward keeps its settings.
 *
 * There used to be more here: fonts, encodings, page breaks, the substitutions. Those
 * belonged to a version of this program that drew its own window and could act on them.
 * A setting nothing reads is worse than no setting, because it says the program can do
 * something it cannot, so they went with the features rather than being left behind.
 */
public final class Settings {
    private Settings() {}

    /** How big a new document's window is, in characters, as TextEdit has always said it. */
    public static final FMString WIDTH_IN_CHARS = FMString.of("WidthInChars");
    public static final FMString HEIGHT_IN_CHARS = FMString.of("HeightInChars");

    /** What was opened lately, newest first. */
    public static final FMString RECENTS = FMString.of("RecentDocuments");

    /** How many are kept, which is what a menu can show without becoming a list. */
    public static final int RECENTS_KEPT = 10;

    public static Defaults domain() { return Defaults.of(Defaults.TEXT_EDIT); }

    /** Fills in the values a fresh install would have. */
    public static void installDefaults() {
        Defaults d = domain();
        d.applyDefault(WIDTH_IN_CHARS, 75L);
        d.applyDefault(HEIGHT_IN_CHARS, 25L);
    }

    public static int windowWidthInChars() {
        return (int) Math.max(20, domain().integer(WIDTH_IN_CHARS, 75));
    }

    public static int windowHeightInChars() {
        return (int) Math.max(10, domain().integer(HEIGHT_IN_CHARS, 25));
    }

    /**
     * Remembers a document as one that was opened.
     *
     * Moved to the front rather than added, so opening the same one twice does not fill
     * the list with it, and trimmed at the end so the list stays a list of recent things.
     */
    public static void rememberRecent(FMURL where) {
        java.util.List<Object> recents =
            new java.util.ArrayList<>(domain().array(RECENTS).asList());
        String path = where.absolutePath().toString();
        recents.removeIf(one -> path.equals(String.valueOf(one)));
        recents.add(0, path);
        while (recents.size() > RECENTS_KEPT) recents.remove(recents.size() - 1);
        domain().set(RECENTS, recents);
    }

    /** The documents opened lately that are still there. */
    public static FMArray<FMURL> recents() {
        FMMutableArray<FMURL> out = FMMutableArray.empty();
        for (Object one : domain().array(RECENTS).asList()) {
            FMURL where = FMURL.ofPath(String.valueOf(one));
            if (where.isFile()) out.add(where);
        }
        return out.asArray();
    }
}
