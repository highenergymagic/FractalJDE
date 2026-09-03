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

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a command goes, and in what order.
 *
 * Whatever has the keyboard, then out through what it sits inside, then the window, then
 * the program. Each is offered the command and the first that can do it does. It is why a
 * menu item in Cocoa connects to First Responder rather than to anything in particular.
 *
 * With one object per window answering everything, Copy with the cursor in the search
 * field copied the files behind it.
 */
public final class FMResponderChain {
    private FMResponderChain() {}

    /**
     * The chain as it is now, from the keyboard outwards.
     *
     * The last one is the program, which is what the menus were built with. It is offered
     * the command after everything nearer the keyboard has declined, which is what makes it
     * the fallback rather than the only answer.
     */
    public static List<FMResponder> chain(FMResponder program) {
        return chain(focused(), program);
    }

    /**
     * The same, starting somewhere named rather than at the keyboard.
     *
     * Cocoa lets a chain be walked from any responder, and a check needs that: giving
     * something the keyboard means showing a window, and the checks are run on a machine
     * somebody else is using.
     */
    public static List<FMResponder> chain(Component from, FMResponder program) {
        List<FMResponder> found = new ArrayList<>();
        Component at = from;
        while (at != null) {
            if (at instanceof FMResponder responder && !found.contains(responder)) {
                found.add(responder);
            }
            at = at instanceof Container container && container.getParent() != null
                ? container.getParent() : at.getParent();
        }
        if (program != null && !found.contains(program)) found.add(program);
        return found;
    }

    /** Offers a command along the chain. Answers whether anything did it. */
    public static boolean sendAction(FMString action, FMResponder program) {
        return sendAction(focused(), action, program);
    }

    /** The same, offered from somewhere named rather than from the keyboard. */
    public static boolean sendAction(Component from, FMString action, FMResponder program) {
        for (FMResponder responder : chain(from, program)) {
            if (responder.canPerform(action) && responder.perform(action)) return true;
        }
        return false;
    }

    /** Whether anything in the chain could do it, which is what greys a menu item. */
    public static boolean canPerform(FMString action, FMResponder program) {
        return canPerform(focused(), action, program);
    }

    /** The same, asked from somewhere named. */
    public static boolean canPerform(Component from, FMString action, FMResponder program) {
        for (FMResponder responder : chain(from, program)) {
            if (responder.canPerform(action)) return true;
        }
        return false;
    }

    private static Component focused() {
        return java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    /** Whether the one that would do it says it is on, for an item that shows a tick. */
    public static boolean isOn(FMString action, FMResponder program) {
        for (FMResponder responder : chain(program)) {
            if (responder.canPerform(action)) return responder.isOn(action);
        }
        return false;
    }
}
