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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;

/**
 * Which languages this account reads, in the order it prefers them.
 *
 * A list rather than one language, because the answer to "what language is this person
 * reading" is usually more than one and always ordered. Somebody who reads German and then
 * English should get German where it exists and English where it does not, rather than the
 * key or a blank. Every system that has taken this seriously ends up with a list.
 *
 * It is kept where Mac OS X keeps it: AppleLanguages, in the global preference domain, as
 * an array of language codes. Nothing has been set on a fresh account, so the host system
 * is asked, and what it answers is the language the person chose when they set the machine
 * up.
 */
public final class Languages {
    private Languages() {}

    /** Where the list is kept, under the name Mac OS X keeps it under. */
    public static final FMString KEY = FMString.of("AppleLanguages");

    /** The language everything here is written in, and the last one tried. */
    public static final FMString DEVELOPMENT = FMString.of("en");

    /**
     * The languages to try, in order, ending with the one the program was written in.
     *
     * The development language is always last and always present. A search that could run
     * out would have to have an answer for running out, and "the words the program was
     * written with" is that answer whether or not anybody listed it.
     */
    /**
     * What every process is told when this changes.
     *
     * Mac OS X posts one of these because a language change is not a thing one program can
     * act on alone: every window on the screen is showing words that are now the wrong
     * ones, and each of them is in a process of its own.
     */
    public static final FMString CHANGED =
        FMString.of("org.fractalmicro.languagesChanged");

    /** What was asked for last, so that asking is not a read of the preferences. */
    private static volatile FMArray<FMString> cached;

    /**
     * The languages this account reads, most wanted first.
     *
     * Kept once asked. Every piece of text on the screen goes through here, and reading a
     * preference domain for each of them costs about a microsecond a word: nothing on its
     * own and a visible pause across a window full of them.
     */
    public static FMArray<FMString> preferred() {
        FMArray<FMString> already = cached;
        if (already != null) return already;
        FMArray<FMString> found = read();
        cached = found;
        return found;
    }

    /** Forgets what was asked for, so the next question reads the preferences again. */
    public static void forget() { cached = null; }

    private static FMArray<FMString> read() {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (Object one : Defaults.of(Defaults.GLOBAL).array(KEY)) {
            FMString code = FMString.describing(one);
            if (!code.isBlank() && !out.contains(code)) out.add(code);
        }
        if (out.count() == 0) {
            FMString host = FMString.of(java.util.Locale.getDefault().getLanguage());
            if (!host.isBlank()) out.add(host);
        }
        if (!out.contains(DEVELOPMENT)) out.add(DEVELOPMENT);
        return out.asArray();
    }

    /** The one to use, which is the first that is asked for. */
    public static FMString current() {
        FMArray<FMString> all = preferred();
        return all.count() == 0 ? DEVELOPMENT : all.at(0);
    }

    /** Sets the order, which is what a Language pane in the settings would write. */
    public static void setPreferred(FMArray<FMString> languages) {
        java.util.List<Object> codes = new java.util.ArrayList<>();
        for (FMString one : languages) codes.add(one.toString());
        Defaults.of(Defaults.GLOBAL).set(KEY, codes);
        forget();
        org.fractalmicro.foundation.FMLocalized.reload();
        // Every other process is showing words in the language that was wanted a moment
        // ago, and none of them is looking at this preference.
        org.fractalmicro.foundation.FMDistributedNotificationCenter.defaultCenter()
            .post(CHANGED);
    }
}
