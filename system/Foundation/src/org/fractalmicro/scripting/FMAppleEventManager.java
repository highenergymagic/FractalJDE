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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where events a program answers are written down, and where events it sends go out.
 *
 * Both halves of the same idea. A program says which commands it knows how to do; the
 * manager finds the one an arriving event names and hands it over. Sending is the mirror
 * of that, and neither side knows how the event travelled.
 *
 * How it travels is somebody else's business, said here as a courier and installed by
 * whichever layer has a connection to the session. Nothing in this framework knows that
 * the window server is the one carrying them, and it should not.
 */
public final class FMAppleEventManager {

    private static final FMAppleEventManager SHARED = new FMAppleEventManager();

    public static FMAppleEventManager sharedManager() { return SHARED; }

    private FMAppleEventManager() {}

    /** What answers one command. Its answer becomes the reply. */
    public interface Handler {
        Object handle(FMAppleEvent event);
    }

    /** How an event reaches another program. Answers the reply's parameters. */
    public interface Courier {
        FMDictionary deliver(FMAppleEvent event, long waitMillis);
    }

    /** How long to wait for a program to answer before deciding it will not. */
    public static final long WAIT_MILLIS = 5000;

    private final Map<FMString, Handler> handlers = new ConcurrentHashMap<>();

    private volatile Courier courier;

    /** Says how events leave this process. Called once, by the layer that can. */
    public void setCourier(Courier how) { this.courier = how; }

    public boolean canSend() { return courier != null; }

    /**
     * Says that this program answers one command.
     *
     * Named by suite and command rather than by a class with a method for each, so a
     * program can answer a command nobody had written down when it was compiled.
     */
    public void setEventHandler(FMString eventClass, FMString eventID, Handler handler) {
        setEventHandler(FMString.EMPTY, eventClass, eventID, handler);
    }

    /**
     * The same, for one program in particular.
     *
     * A Mac has one program to a process and needs no such thing. Here the Finder, the
     * Dock and the desktop's panels are one process, and a command one of them answers is
     * not a command the others answer: without the name they would take each other's.
     */
    public void setEventHandler(FMString target, FMString eventClass, FMString eventID,
                                Handler handler) {
        if (handler == null) removeEventHandler(target, eventClass, eventID);
        else handlers.put(nameOf(target, eventClass, eventID), handler);
    }

    public void removeEventHandler(FMString eventClass, FMString eventID) {
        removeEventHandler(FMString.EMPTY, eventClass, eventID);
    }

    public void removeEventHandler(FMString target, FMString eventClass, FMString eventID) {
        handlers.remove(nameOf(target, eventClass, eventID));
    }

    public boolean answers(FMString eventClass, FMString eventID) {
        return handlers.containsKey(nameOf(FMString.EMPTY, eventClass, eventID));
    }

    /**
     * Every command written down against one program, as eight characters each.
     *
     * The suite and the command joined, which is how a terminology writes one, so what a
     * program answers and what its terminology says can be laid side by side.
     */
    public java.util.List<FMString> commandsFor(FMString target) {
        String prefix = (target == null ? FMString.EMPTY : target) + " ";
        java.util.List<FMString> out = new java.util.ArrayList<>();
        for (FMString key : handlers.keySet()) {
            String written = key.toString();
            if (written.startsWith(prefix)) {
                out.add(FMString.of(written.substring(prefix.length())));
            }
        }
        out.sort(java.util.Comparator.comparing(FMString::toString));
        return out;
    }

    /**
     * Answers an event that has arrived, as a reply.
     *
     * A handler that throws is a command that could not be done, which is a reply saying
     * so rather than an exception crossing a process boundary. The number is the one
     * Cocoa uses for a command it has no handler for.
     */
    public FMDictionary handle(FMAppleEvent event) {
        // Whoever it is addressed to first, and whoever answers for the whole process
        // after that, which in a process with one program in it is the only one there is.
        Handler handler = handlers.get(
            nameOf(event.target(), event.eventClass(), event.eventID()));
        if (handler == null) {
            handler = handlers.get(
                nameOf(FMString.EMPTY, event.eventClass(), event.eventID()));
        }
        if (handler == null) {
            return failure(EVENT_NOT_HANDLED,
                FMString.of("this program does not answer " + event.eventClass()
                            + "/" + event.eventID()));
        }
        try {
            return answer(handler.handle(event));
        } catch (FMScriptError refused) {
            return failure(refused.number(), refused.said());
        } catch (RuntimeException broke) {
            return failure(EVENT_FAILED, FMString.describing(broke.getMessage()));
        }
    }

    /** errAEEventNotHandled, which is what a program says about a command it has never heard of. */
    public static final long EVENT_NOT_HANDLED = -1708;

    /** errAEEventFailed, for a command it knows and could not carry out. */
    public static final long EVENT_FAILED = -10000;

    /**
     * Sends an event and waits for the answer.
     *
     * Waits, because a command whose answer nobody collects is a command nobody can tell
     * went wrong. A program that is not running is started first, which is what makes
     * telling something to do something work without opening it by hand.
     */
    public FMDictionary sendEvent(FMAppleEvent event) {
        return sendEvent(event, WAIT_MILLIS);
    }

    public FMDictionary sendEvent(FMAppleEvent event, long waitMillis) {
        Courier how = courier;
        if (how == null) {
            return failure(EVENT_FAILED, FMString.of("nothing is carrying events here"));
        }
        return how.deliver(event, waitMillis);
    }

    /** Whether a reply says the command could not be done. */
    public static boolean failed(FMDictionary reply) {
        return reply != null && reply.has(FMAppleEvent.ERROR_NUMBER);
    }

    /** Why, in words, or nothing when it did not fail. */
    public static FMString whyFailed(FMDictionary reply) {
        return reply == null ? FMString.EMPTY
                             : reply.string(FMAppleEvent.ERROR_STRING, FMString.EMPTY);
    }

    /** What came back, which is under the same key the question was under. */
    public static Object result(FMDictionary reply) {
        return reply == null ? null : reply.value(FMAppleEvent.DIRECT_OBJECT);
    }

    /* ------------------------------------------------------------------ pieces */

    /** A reply carrying an answer, or carrying nothing, which is also an answer. */
    public static FMDictionary answer(Object result) {
        FMMutableDictionary reply = FMMutableDictionary.empty();
        if (result != null) reply.set(FMAppleEvent.DIRECT_OBJECT, result);
        return reply.asDictionary();
    }

    public static FMDictionary failure(long number, FMString said) {
        FMMutableDictionary reply = FMMutableDictionary.empty();
        reply.set(FMAppleEvent.ERROR_NUMBER, number);
        reply.set(FMAppleEvent.ERROR_STRING, said == null ? FMString.EMPTY : said);
        return reply.asDictionary();
    }

    private static FMString nameOf(FMString target, FMString eventClass, FMString eventID) {
        return (target == null ? FMString.EMPTY : target)
            .appending(FMString.of(" "))
            .appending(FMAppleEvent.code(eventClass))
            .appending(FMAppleEvent.code(eventID));
    }
}
