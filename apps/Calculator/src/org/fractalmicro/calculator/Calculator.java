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
 * This is the first program here written entirely against the system. Everything it uses
 * is an FM something: the numbers are {@link FMDecimal}, the text is {@link FMString}, the
 * clipboard is {@link FMPasteboard}, and the window is a description handed to
 * {@link FMApplication}. Nothing in this file names the runtime it happens to be compiled
 * for, which is the difference between a program written for a platform and a program that
 * merely runs on one.
 *
 * That includes the small things. A control is named by an FMString, not by a literal that
 * happens to be a runtime string; an operation is a value of this program's own kind, not
 * a one character string compared with a switch. Reaching for the runtime's text inside a
 * program written for a platform is the same mistake as calling open in a Cocoa
 * application: it works, and it means the layer above was not finished.
 *
 * The arithmetic is decimal on purpose. A calculator that answers 0.30000000000000004 to a
 * tenth plus two tenths is wrong in the way a person notices first.
 */
public final class Calculator implements org.fractalmicro.appkit.FMApplicationDelegate {

    public static final FMString NAME = FMString.of("Calculator");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("Calculator");

    /** The one control that holds anything, named once rather than spelled everywhere. */
    private static final FMString DISPLAY = FMString.of("display");

    private static final FMString ZERO_TEXT = FMString.of("0");
    private static final FMString POINT = FMString.of(".");

    /**
     * What is waiting to be done to the number on the display.
     *
     * A pending operation is one of four things, so it is one of four things. Keeping it
     * as text and comparing that text later would work, and would also mean the compiler
     * had nothing to say about a typo in one of the four places it is written.
     */
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
     * There is no main. The bundle names this class and the loader calls the framework's
     * application main, which reads that name, makes one, and sends it this. That is what
     * NSApplicationMain does and what every Cocoa program's main hands straight over to,
     * and none of it is anything this program knows better than its own bundle does.
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
