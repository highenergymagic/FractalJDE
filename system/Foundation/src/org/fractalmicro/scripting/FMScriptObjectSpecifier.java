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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;

/**
 * Which thing a command is about, said as a chain rather than as a pointer.
 *
 * "the name of window 1" is three of these one inside another: a property of an element
 * at an index of the application. Nothing is held on to between one command and the next,
 * which is what lets a script name something in a program that was not running when the
 * script was written.
 */
public final class FMScriptObjectSpecifier {

    /* --------------------------------------------------------------- the classes */

    public static final FMString APPLICATION = FMString.of("capp");
    public static final FMString WINDOW = FMString.of("cwin");
    public static final FMString DOCUMENT = FMString.of("docu");
    public static final FMString ITEM = FMString.of("cobj");
    public static final FMString FOLDER = FMString.of("cfol");
    public static final FMString FILE = FMString.of("file");
    public static final FMString DISK = FMString.of("cdis");

    /* ------------------------------------------------------------ the properties */

    public static final FMString NAME = FMString.of("pnam");
    public static final FMString CLASS = FMString.of("pcls");
    public static final FMString INDEX = FMString.of("pidx");
    public static final FMString PATH = FMString.of("psxp");
    public static final FMString SIZE = FMString.of("ptsz");
    public static final FMString BOUNDS = FMString.of("pbnd");
    public static final FMString VERSION = FMString.of("vers");
    public static final FMString SELECTION = FMString.of("sele");

    /* --------------------------------------------------------------- the forms */

    /** By what it is called. */
    public static final FMString BY_NAME = FMString.of("name");

    /** By where it sits, counting from one, and from the end when it is negative. */
    public static final FMString BY_INDEX = FMString.of("indx");

    /** A property of whatever holds it. */
    public static final FMString BY_PROPERTY = FMString.of("prop");

    /** Every one of them. */
    public static final FMString EVERY = FMString.of("all ");

    /* ----------------------------------------------------------------- the keys */

    private static final FMString WANT = FMString.of("want");
    private static final FMString FORM = FMString.of("form");
    private static final FMString DATA = FMString.of("seld");
    private static final FMString FROM = FMString.of("from");

    private final FMString wantClass;
    private final FMString form;
    private final Object data;
    private final FMScriptObjectSpecifier container;

    public FMScriptObjectSpecifier(FMString wantClass, FMString form, Object data,
                                   FMScriptObjectSpecifier container) {
        this.wantClass = FMAppleEvent.code(wantClass);
        this.form = FMAppleEvent.code(form);
        this.data = data;
        this.container = container;
    }

    /** The one everything else hangs off: the program being spoken to. */
    public static FMScriptObjectSpecifier application() {
        return new FMScriptObjectSpecifier(APPLICATION, EVERY, null, null);
    }

    public static FMScriptObjectSpecifier named(FMString wantClass, FMString name,
                                                FMScriptObjectSpecifier in) {
        return new FMScriptObjectSpecifier(wantClass, BY_NAME, name, in);
    }

    public static FMScriptObjectSpecifier at(FMString wantClass, long index,
                                             FMScriptObjectSpecifier in) {
        return new FMScriptObjectSpecifier(wantClass, BY_INDEX, FMNumber.of(index), in);
    }

    public static FMScriptObjectSpecifier every(FMString wantClass,
                                                FMScriptObjectSpecifier in) {
        return new FMScriptObjectSpecifier(wantClass, EVERY, null, in);
    }

    public static FMScriptObjectSpecifier property(FMString code,
                                                   FMScriptObjectSpecifier of) {
        return new FMScriptObjectSpecifier(code, BY_PROPERTY, code, of);
    }

    public FMString wantClass() { return wantClass; }
    public FMString form() { return form; }
    public Object data() { return data; }
    public FMScriptObjectSpecifier container() { return container; }

    /* -------------------------------------------------------- crossing the wire */

    /** As a dictionary, which is how it travels inside an event. */
    public FMDictionary asDictionary() {
        FMMutableDictionary out = FMMutableDictionary.empty();
        out.set(WANT, wantClass);
        out.set(FORM, form);
        if (data != null) out.set(DATA, data);
        if (container != null) out.set(FROM, container.asDictionary());
        return out.asDictionary();
    }

    /** And back again. Answers nothing when what came across is not one of these. */
    public static FMScriptObjectSpecifier from(Object value) {
        if (!(value instanceof FMDictionary written)) return null;
        if (!written.has(WANT) || !written.has(FORM)) return null;
        return new FMScriptObjectSpecifier(
            written.string(WANT, FMString.EMPTY),
            written.string(FORM, FMString.EMPTY),
            written.value(DATA),
            from(written.value(FROM)));
    }

    /* ---------------------------------------------------------------- resolving */

    /**
     * What this names, given the program to look in.
     *
     * An object, a list of them, or a plain value where the last step was a property.
     * Nothing found is a refusal rather than a null, because a script asking for window 9
     * of a program with two windows has made a mistake and should hear about it.
     */
    public Object resolve(FMScriptable root) {
        Object within = container == null ? root : container.resolve(root);
        if (BY_PROPERTY.sameAs(form)) return propertyOf(within);
        FMArray<FMScriptable> among = elementsOf(within, wantClass);
        if (EVERY.sameAs(form)) return among;
        if (BY_NAME.sameAs(form)) {
            FMString wanted = FMString.describing(data);
            for (FMScriptable one : among) {
                if (one.scriptName().sameAs(wanted)) return one;
            }
            throw new FMScriptError(FMString.of("there is nothing called " + wanted));
        }
        if (BY_INDEX.sameAs(form)) {
            long index = data instanceof FMNumber number ? number.asWhole() : 0;
            int count = among.count();
            int at = (int) (index < 0 ? count + index : index - 1);
            if (at < 0 || at >= count) {
                throw new FMScriptError(FMString.of("there is no " + wantClass
                                                    + " number " + index));
            }
            return among.at(at);
        }
        throw new FMScriptError(FMString.of("this system cannot find things by " + form));
    }

    private Object propertyOf(Object within) {
        if (!(within instanceof FMScriptable thing)) {
            throw new FMScriptError(FMString.of("that has no properties"));
        }
        Object found = thing.property(wantClass);
        if (found == null) {
            throw new FMScriptError(FMString.of("there is no property " + wantClass));
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static FMArray<FMScriptable> elementsOf(Object within, FMString wantClass) {
        if (within instanceof FMScriptable thing) return thing.elements(wantClass);
        if (within instanceof FMArray<?> many) {
            FMMutableArray<FMScriptable> out = FMMutableArray.empty();
            for (Object one : many) {
                if (one instanceof FMScriptable thing) {
                    for (FMScriptable inside : thing.elements(wantClass)) out.add(inside);
                }
            }
            return out.asArray();
        }
        throw new FMScriptError(FMString.of("that holds nothing to look in"));
    }

    @Override public String toString() {
        return wantClass + " by " + form + (data == null ? "" : " " + data)
             + (container == null ? "" : " of " + container);
    }
}
