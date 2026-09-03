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
package org.fractalmicro.calculator;

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.appkit.FMPasteboard;
import org.fractalmicro.foundation.FMDecimal;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMString;

/**
 * Calculator: arithmetic, and nothing else.
 *
 * Decimal, not binary. A calculator that answers 0.30000000000000004 to a tenth plus two
 * tenths is wrong where a person notices first.
 *
 * Names no runtime type; VocabularyTest holds every program here to that.
 */
public final class Calculator implements org.fractalmicro.appkit.FMApplicationDelegate {

    public static final FMString NAME = FMString.of("Calculator");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("Calculator");

    /** The one control that holds anything, named once rather than spelled everywhere. */
    private static final FMString DISPLAY = FMString.of("display");

    private static final FMString ZERO_TEXT = FMString.of("0");
    private static final FMString POINT = FMString.of(".");

    /** What is waiting to be done to the number on the display. */
    private enum Operation {
        NONE, ADD, SUBTRACT, MULTIPLY, DIVIDE;

        FMDecimal apply(FMDecimal left, FMDecimal right) {
            return switch (this) {
                case ADD -> left.plus(right);
                case SUBTRACT -> left.minus(right);
                case MULTIPLY -> left.times(right);
                case DIVIDE -> left.dividedBy(right);
                case NONE -> right;
            };
        }
    }

    private final FMApplication app = FMApplication.sharedApplication();

    /** What is showing, what is waiting, and what is to be done with the two. */
    private FMDecimal showing = FMDecimal.ZERO;
    private FMDecimal waiting;
    private Operation pending = Operation.NONE;
    private boolean typing;

    /**
     * Opened, which is the whole of this program's start-up.
     *
     * There is no main: the bundle names this class, and FMApplicationMain makes one and
     * sends it this. NSApplicationMain, the same way round.
     */
    @Override public void open() {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }

        for (int digit = 0; digit <= 9; digit++) {
            FMString typed = FMString.describing(digit);
            app.on(digitAction(digit), event -> type(typed));
        }
        app.on(FMString.of("point"), event -> point());
        app.on(FMString.of("add"), event -> operate(Operation.ADD));
        app.on(FMString.of("subtract"), event -> operate(Operation.SUBTRACT));
        app.on(FMString.of("multiply"), event -> operate(Operation.MULTIPLY));
        app.on(FMString.of("divide"), event -> operate(Operation.DIVIDE));
        app.on(FMString.of("equals"), event -> equals());
        app.on(FMString.of("clear"), event -> clear());
        app.on(FMString.of("negate"), event -> show(showing.negated()));
        app.on(FMString.of("percent"), event -> show(showing.asPercent()));
        app.on(FMString.of("copy"), event -> FMPasteboard.general().setString(text()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        show(showing);
    }

    /** What the key for one digit sends back, which is also what its control is called. */
    private static FMString digitAction(int digit) {
        return FMString.of("digit ").appending(FMString.describing(digit));
    }

    /* ------------------------------------------------------------------ the sums */

    private void type(FMString digit) {
        FMString now = typing ? text() : FMString.EMPTY;
        if (now.sameAs(ZERO_TEXT)) now = FMString.EMPTY;
        set(now.appending(digit));
        typing = true;
    }

    private void point() {
        FMString now = typing ? text() : ZERO_TEXT;
        if (!now.contains(POINT)) set(now.appending(POINT));
        typing = true;
    }

    private void operate(Operation what) {
        equals();
        waiting = showing;
        pending = what;
        typing = false;
    }

    private void equals() {
        if (waiting == null || pending == Operation.NONE) {
            typing = false;
            return;
        }
        showing = pending.apply(waiting, showing);
        waiting = null;
        pending = Operation.NONE;
        typing = false;
        show(showing);
    }

    private void clear() {
        waiting = null;
        pending = Operation.NONE;
        typing = false;
        show(FMDecimal.ZERO);
    }

    /* --------------------------------------------------------------- the showing */

    private FMString text() {
        FMString shown = app.valueOf(DISPLAY);
        return shown.isBlank() ? ZERO_TEXT : shown;
    }

    /** Puts typed text in the display, and keeps the number in step with it. */
    private void set(FMString what) {
        app.setValue(DISPLAY, what);
        showing = FMDecimal.of(what);
    }

    /** Puts an answer in the display. */
    private void show(FMDecimal answer) {
        showing = answer;
        app.setValue(DISPLAY, answer.asString());
    }

}
