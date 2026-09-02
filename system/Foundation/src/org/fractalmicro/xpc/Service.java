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
package org.fractalmicro.xpc;

import org.fractalmicro.core.Log;
import org.fractalmicro.win.Mutex;
import org.fractalmicro.win.Pipes;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A service: a name other programs can send messages to.
 *
 * The service registers a name, waits on it, and answers whatever arrives. Each client
 * gets its own connection and its own thread, so one client thinking hard about a reply
 * does not stop another being served.
 *
 * The name is the whole interface. Nothing else needs to know whether the service is in
 * this process, in another one, or not running at all, which is the point of naming
 * ports rather than passing objects around.
 */
public final class Service implements AutoCloseable {

    private final String name;
    private final Function<Message, Message> handler;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread accepting;
    private Mutex claim;

    public Service(String name, Function<Message, Message> handler) {
        this.name = name;
        this.handler = handler;
    }

    public String name() { return name; }

    public boolean isRunning() { return running.get(); }

    /** How long to wait for the port to be up before answering that the service is. */
    private static final long CLAIMING_MILLISECONDS = 2000;

    /**
     * Starts listening, and does not answer until the name is being served.
     *
     * The name is claimed in one step rather than looked at and then taken, because two
     * services starting at once would both see it free. The claim decides, and the loser
     * is told by the same call that tried.
     *
     * The port is made on the accepting thread, which on a busy machine has not
     * necessarily run yet, so this waits for it before answering: everything that starts a
     * service goes on to use the name, and saying yes early hands back something that is
     * not a service until a moment later.
     */
    public boolean start() {
        if (running.get()) return true;
        claim = Mutex.claim("org.fractalmicro.service." + name);
        if (claim == null) {
            Log.info("the name " + name + " is already served by something");
            return false;
        }
        running.set(true);
        accepting = new Thread(this::accept, "xpc-" + name);
        accepting.setDaemon(true);
        accepting.start();
        return waitForTheName();
    }

    /** Until the port is up, until the thread gives up on it, or until it has been a while. */
    private boolean waitForTheName() {
        long until = System.nanoTime() + CLAIMING_MILLISECONDS * 1_000_000L;
        while (System.nanoTime() < until) {
            // Set back by the accepting thread when the name turns out to be held by
            // something this cannot take it from.
            if (!running.get()) return false;
            if (Pipes.exists(name)) return true;
            try {
                Thread.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return running.get();
    }

    private void accept() {
        boolean first = true;
        while (running.get()) {
            // The first port of the name asks to be the first instance, so that a name
            // someone else already stood up is caught rather than quietly joined.
            Pipes.Port port = Pipes.listen(name, first);
            if (port == null) {
                if (!running.get()) return;
                if (first && Pipes.exists(name)) {
                    // Not a race that will clear: something else holds the name. Stop
                    // trying rather than spin, and let go of the claim we took.
                    Log.error("the port " + name + " is held by something else; not serving",
                              new IllegalStateException(name + " already served"));
                    running.set(false);
                    if (claim != null) claim.close();
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            first = false;
            Thread worker = new Thread(() -> serve(port), "xpc-" + name + "-client");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** One client, until it goes away. */
    private void serve(Pipes.Port port) {
        try (port) {
            while (running.get()) {
                byte[] request = port.read();
                if (request == null) return;
                Message reply;
                try {
                    reply = handler.apply(Message.parse(request));
                    if (reply == null) reply = Message.of("ok");
                } catch (Throwable t) {
                    // Throwable, not Exception: a hostile message can run the parser out of
                    // stack or memory, and those arrive as Errors. Catching them keeps one
                    // bad message from taking the whole service, or the whole runtime,
                    // down with it.
                    Log.error("the service " + name + " could not answer", t);
                    reply = Message.error(t.getMessage() == null ? t.toString() : t.getMessage());
                }
                if (!port.write(reply.toBytes())) return;
            }
        }
    }

    /** Stops listening. A client already being served finishes its reply. */
    @Override public void close() {
        if (!running.compareAndSet(true, false)) {
            if (claim != null) claim.close();
            return;
        }
        if (claim != null) claim.close();
        // The accepting thread is waiting for a connection, so it is woken by making one
        // and dropping it straight away.
        Pipes.Port waker = Pipes.connect(name, 200);
        if (waker != null) waker.close();
        if (accepting != null) accepting.interrupt();
    }
}
