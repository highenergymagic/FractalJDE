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
package org.fractalmicro.appkit;


import org.fractalmicro.foundation.FMString;

import org.fractalmicro.os.FMUserDefaults;

/**
 * The text settings that belong to the system rather than to one program.
 *
 * Spelling, smart quotes, smart dashes and the rest are not TextEdit's; they are the
 * text system's, and a field in a dialog gets them for the same reason a document does.
 * So they live in the global domain, under the key names that domain uses for them, and
 * every text control in this system reads them from here.
 *
 * A program may still have its own switch for its own documents. TextEdit does, because
 * its settings file has always had one, and where it does its answer wins for its
 * own text and this is the default underneath.
 */
public final class TextDefaults {
    private TextDefaults() {}

    public static final FMString SPELLING = FMString.of("NSAutomaticSpellingCorrectionEnabled");
    public static final FMString QUOTES = FMString.of("NSAutomaticQuoteSubstitutionEnabled");
    public static final FMString DASHES = FMString.of("NSAutomaticDashSubstitutionEnabled");
    public static final FMString LINKS = FMString.of("NSAutomaticLinkDetectionEnabled");
    public static final FMString DATA_DETECTORS = FMString.of("NSAutomaticDataDetectionEnabled");
    public static final FMString TEXT_REPLACEMENT = FMString.of("NSAutomaticTextReplacementEnabled");

    private static FMUserDefaults global() { return FMUserDefaults.of(FMUserDefaults.GLOBAL); }

    public static void installDefaults() {
        FMUserDefaults d = global();
        d.applyDefault(SPELLING, Boolean.TRUE);
        d.applyDefault(QUOTES, Boolean.FALSE);
        d.applyDefault(DASHES, Boolean.FALSE);
        d.applyDefault(LINKS, Boolean.TRUE);
        d.applyDefault(DATA_DETECTORS, Boolean.TRUE);
        d.applyDefault(TEXT_REPLACEMENT, Boolean.FALSE);
        d.save();
    }

    public static boolean checkSpelling() { return global().bool(SPELLING, true); }
    public static void setCheckSpelling(boolean on) { global().set(SPELLING, on); }

    public static boolean smartQuotes() { return global().bool(QUOTES, false); }
    public static void setSmartQuotes(boolean on) { global().set(QUOTES, on); }

    public static boolean smartDashes() { return global().bool(DASHES, false); }
    public static void setSmartDashes(boolean on) { global().set(DASHES, on); }

    public static boolean detectData() { return global().bool(DATA_DETECTORS, true); }
    public static void setDetectData(boolean on) { global().set(DATA_DETECTORS, on); }

    public static boolean detectLinks() { return global().bool(LINKS, true); }
}
