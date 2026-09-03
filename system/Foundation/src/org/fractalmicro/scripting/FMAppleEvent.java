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
package org.fractalmicro.scripting;

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;

/**
 * One thing said to a program by something outside it.
 *
 * An Apple event names a suite, a command in that suite, and who it is for. What it
 * carries is a dictionary keyed the same way, so an event is readable as it stands: the
 * direct object is under "----" because that is the key it has had since 1991.
 *
 * The codes are four characters. They are not an abbreviation of anything and they are
 * not translated: "quit" is the command whatever language the person sending it speaks,
 * which is exactly the separation the words in a menu need and now have.
 */
public final class FMAppleEvent {

    /* ------------------------------------------------------------------ suites */

    /** The suite every program answers, whether it is scriptable or not. */
    public static final FMString REQUIRED_SUITE = FMString.of("aevt");

    /** The standard suite: getting, setting, counting, making, deleting. */
    public static final FMString CORE_SUITE = FMString.of("core");

    /* ---------------------------------------------------- the required suite */

    /** Started with nothing to open. */
    public static final FMString OPEN_APPLICATION = FMString.of("oapp");

    /** Opened again while already running, which is a click on a Dock tile. */
    public static final FMString REOPEN = FMString.of("rapp");

    /** Opened on documents. The direct object is the list of them. */
    public static final FMString OPEN_DOCUMENTS = FMString.of("odoc");

    /** Asked to print documents rather than open them. */
    public static final FMString PRINT_DOCUMENTS = FMString.of("pdoc");

    /** Asked to stop. A program may refuse, which is what an unsaved document is for. */
    public static final FMString QUIT = FMString.of("quit");

    /** What comes back. Never sent by hand: the manager makes one out of an answer. */
    public static final FMString ANSWER = FMString.of("ansr");

    /* -------------------------------------------------------- the core suite */

    public static final FMString GET_DATA = FMString.of("getd");
    public static final FMString SET_DATA = FMString.of("setd");
    public static final FMString COUNT = FMString.of("cnte");
    public static final FMString MAKE = FMString.of("crel");
    public static final FMString DELETE = FMString.of("delo");
    public static final FMString EXISTS = FMString.of("doex");

    /* ---------------------------------------------------------------- keys */

    /** What the command is about. Cocoa calls it the direct object. */
    public static final FMString DIRECT_OBJECT = FMString.of("----");

    /** What it is to be given: the second half of a set, and of a make. */
    public static final FMString DATA = FMString.of("data");

    /** Which kind of thing to make, for a command that makes one. */
    public static final FMString OBJECT_CLASS = FMString.of("kocl");

    /** Where a made thing goes. */
    public static final FMString INSERT_AT = FMString.of("insh");

    /** Why it could not be done, as a number and as a sentence. */
    public static final FMString ERROR_NUMBER = FMString.of("errn");
    public static final FMString ERROR_STRING = FMString.of("errs");

    /* --------------------------------------------------------------- the event */

    private final FMString eventClass;
    private final FMString eventID;
    private final FMString target;
    private final FMDictionary parameters;

    public FMAppleEvent(FMString eventClass, FMString eventID, FMString target,
                        FMDictionary parameters) {
        this.eventClass = code(eventClass);
        this.eventID = code(eventID);
        this.target = target == null ? FMString.EMPTY : target;
        this.parameters = parameters == null ? FMDictionary.EMPTY : parameters;
    }

    /** An event with nothing but a direct object, which most of them are. */
    public static FMAppleEvent of(FMString eventClass, FMString eventID, FMString target,
                                  Object directObject) {
        FMMutableDictionary parameters = FMMutableDictionary.empty();
        if (directObject != null) parameters.set(DIRECT_OBJECT, directObject);
        return new FMAppleEvent(eventClass, eventID, target, parameters.asDictionary());
    }

    public FMString eventClass() { return eventClass; }
    public FMString eventID() { return eventID; }

    /** Who it is for, as a bundle identifier. */
    public FMString target() { return target; }

    public FMDictionary parameters() { return parameters; }

    public Object parameter(FMString key) { return parameters.value(key); }

    public FMString stringParameter(FMString key) {
        return parameters.string(key, FMString.EMPTY);
    }

    /** What the command is about, which is where most events keep their subject. */
    public Object directObject() { return parameters.value(DIRECT_OBJECT); }

    /** Whether this event names the same command as those two codes. */
    public boolean is(FMString suite, FMString command) {
        return eventClass.sameAs(code(suite)) && eventID.sameAs(code(command));
    }

    /**
     * A code as it goes on the wire: four characters, padded with spaces.
     *
     * Shorter is padded and longer is cut, because a code that is not four characters is
     * not a code and quietly carrying one would put it in a message nothing can read.
     */
    public static FMString code(FMString written) {
        String value = written == null ? "" : written.toString();
        if (value.length() > 4) value = value.substring(0, 4);
        return FMString.of((value + "    ").substring(0, 4));
    }

    @Override public String toString() {
        return eventClass + "/" + eventID
             + (target.isEmpty() ? "" : " to " + target)
             + " " + parameters;
    }
}
