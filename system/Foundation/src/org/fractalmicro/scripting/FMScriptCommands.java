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
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;

import java.util.function.Supplier;

/**
 * The standard suite, written once for every program that has objects to show.
 *
 * Getting, setting, counting, whether something is there, and getting rid of it. None of
 * these is a program's own idea, so none of them is written in a program: a program says
 * what it holds and these are what can be done with any of it.
 */
public final class FMScriptCommands {
    private FMScriptCommands() {}

    /**
     * Says that this program answers the standard suite about that object graph.
     *
     * The root is asked for each time rather than kept, because what a program holds
     * changes while it runs and a root taken once is a root taken before the first window
     * was opened.
     */
    public static void install(FMAppleEventManager manager, FMString target,
                               Supplier<FMScriptable> root) {
        manager.setEventHandler(target, FMAppleEvent.CORE_SUITE, FMAppleEvent.GET_DATA,
            event -> plain(named(event, root).resolve(root.get())));

        manager.setEventHandler(target, FMAppleEvent.CORE_SUITE, FMAppleEvent.COUNT,
            event -> FMNumber.of(count(named(event, root).resolve(root.get()))));

        manager.setEventHandler(target, FMAppleEvent.CORE_SUITE, FMAppleEvent.EXISTS,
            event -> {
                try {
                    return FMNumber.of(named(event, root).resolve(root.get()) != null);
                } catch (FMScriptError notThere) {
                    return FMNumber.of(false);
                }
            });

        manager.setEventHandler(target, FMAppleEvent.CORE_SUITE, FMAppleEvent.SET_DATA,
            event -> set(event, root));

        manager.setEventHandler(target, FMAppleEvent.CORE_SUITE, FMAppleEvent.DELETE,
            event -> remove(named(event, root).resolve(root.get())));
    }

    /* ------------------------------------------------------------------ pieces */

    /** What the event was about, as a specifier, or a refusal saying it named nothing. */
    private static FMScriptObjectSpecifier named(FMAppleEvent event,
                                                 Supplier<FMScriptable> root) {
        FMScriptObjectSpecifier what =
            FMScriptObjectSpecifier.from(event.directObject());
        if (what == null) {
            throw new FMScriptError(FMString.of("that command did not say what it is about"));
        }
        if (root.get() == null) {
            throw new FMScriptError(FMString.of("this program has nothing to look in yet"));
        }
        return what;
    }

    private static Object set(FMAppleEvent event, Supplier<FMScriptable> root) {
        FMScriptObjectSpecifier what = named(event, root);
        if (!FMScriptObjectSpecifier.BY_PROPERTY.sameAs(what.form())) {
            throw new FMScriptError(FMString.of("only a property can be set"));
        }
        Object holder = what.container() == null
            ? root.get() : what.container().resolve(root.get());
        if (!(holder instanceof FMScriptable thing)) {
            throw new FMScriptError(FMString.of("that has no properties"));
        }
        Object value = event.parameter(FMAppleEvent.DATA);
        if (!thing.setProperty(what.wantClass(), value)) {
            throw new FMScriptError(FMString.of("the property " + what.wantClass()
                                                + " cannot be changed"));
        }
        return null;
    }

    private static Object remove(Object found) {
        if (found instanceof FMArray<?> many) {
            for (Object one : many) remove(one);
            return null;
        }
        if (!(found instanceof FMScriptable thing) || !thing.delete()) {
            throw new FMScriptError(FMString.of("that cannot be got rid of"));
        }
        return null;
    }

    private static long count(Object found) {
        if (found instanceof FMArray<?> many) return many.count();
        return found == null ? 0 : 1;
    }

    /**
     * An answer as something that can go in a message.
     *
     * An object on its own has no meaning in another process, so what crosses is what it
     * is called. A list of them crosses as a list of names, which is what a script asking
     * for every window of something wanted in the first place.
     */
    private static Object plain(Object found) {
        if (found instanceof FMScriptable thing) return thing.scriptName();
        if (found instanceof FMArray<?> many) {
            FMMutableArray<Object> out = FMMutableArray.empty();
            for (Object one : many) out.add(plain(one));
            return out.asArray();
        }
        return found;
    }
}
