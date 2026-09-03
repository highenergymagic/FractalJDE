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
package org.fractalmicro.foundation;

import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Telling other processes that something happened.
 *
 * A process that wants to hear registers; a process with something to say posts. What
 * arrives is a name and a little text.
 *
 * Deliberately little: it carries that something happened, not the thing itself. Whoever
 * hears goes and looks, the value being on a volume both can read.
 */
public final class FMDistributedNotificationCenter {

    /** Where the notifications are handed round. */
    public static final String SERVICE = "org.fractalmicro.notifications";

    /** What this answers. */
    public static final String POST = "post";

    private static final FMDistributedNotificationCenter SHARED =
        new FMDistributedNotificationCenter();

    /** What each listener wants to hear about, and what to do when it happens. */
    private record Listening(FMString name, Observer observer) {}

    /** Something that wants to be told. */
    public interface Observer {
        void heard(FMString name, FMString about);
    }

    private final List<Listening> listeners = new CopyOnWriteArrayList<>();
    private volatile Service service;

    private FMDistributedNotificationCenter() {}

    public static FMDistributedNotificationCenter defaultCenter() { return SHARED; }

    /**
     * Starts listening for notifications from other processes.
     *
     * Only one process can hold the name, and it is whichever asks first: the desktop, in
     * practice, because it is the one with something to redraw. A process that does not
     * get it can still post, which is the direction that matters for everything else.
     */
    public synchronized boolean receive() {
        if (service != null) return true;
        Service made = new Service(SERVICE, this::answer);
        if (!made.start()) return false;
        service = made;
        return true;
    }

    public synchronized void stopReceiving() {
        if (service != null) service.close();
        service = null;
    }

    /** Whether this process is the one holding the name. */
    public boolean isReceiving() { return service != null; }

    /** Asks to be told when something of this name happens. */
    public void addObserver(FMString name, Observer observer) {
        listeners.add(new Listening(name, observer));
    }

    /**
     * Says that something happened.
     *
     * Anything listening in this process is told directly, and anything listening in
     * another is told over the connection. Both, because a program that posts should not
     * have to know which side of a boundary the things that care about it are on.
     */
    public void post(FMString name, FMString about) {
        deliver(name, about);
        if (service != null) return;
        // Looked for rather than waited for. Connecting waits to see whether a service that
        // was just started turns up, which is right when something is being started and
        // wrong here: this is a notification, it is being sent now, and if nothing holds the
        // name then nothing is listening. Waiting two seconds to find that out, on the event
        // thread, at every preference change, is a desktop that stops for two seconds.
        if (!Connection.available(SERVICE)) return;
        try {
            Connection.ask(SERVICE, Message.of(POST)
                .put("name", name.toString()).put("about", about.toString()));
        } catch (java.io.IOException nobodyListening) {
            // Nothing is holding the name. There is nobody to tell, which is not a failure:
            // a notification with no observers has always been a notification with no
            // observers.
        }
    }

    public void post(FMString name) { post(name, FMString.EMPTY); }

    private Message answer(Message request) {
        if (!POST.equals(request.type())) {
            return Message.error("the notification centre does not answer " + request.type());
        }
        deliver(FMString.of(request.string("name", "")),
                FMString.of(request.string("about", "")));
        return Message.of(POST).put("ok", Boolean.TRUE);
    }

    /**
     * Tells whatever asked to be told.
     *
     * One observer throwing does not stop the rest: they asked separately and none of them
     * agreed to be responsible for the others.
     */
    private void deliver(FMString name, FMString about) {
        for (Listening one : listeners) {
            if (!one.name().sameAs(name)) continue;
            try {
                one.observer().heard(name, about);
            } catch (RuntimeException thrown) {
                FMLog.say(FMString.of("a listener for ").appending(name)
                                  .appending(FMString.of(" threw: "))
                                  .appending(FMString.describing(thrown.getMessage())));
            }
        }
    }
}
