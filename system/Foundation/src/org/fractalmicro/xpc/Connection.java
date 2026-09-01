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

import org.fractalmicro.win.Pipes;

import java.io.IOException;

/**
 * A connection to a service: send a message, get the answer.
 *
 * Nothing here knows what is on the other end. A service in another process and one in
 * this process answer identically, so something can be moved into a process of its own
 * without changing any of its callers.
 */
public final class Connection implements AutoCloseable {

    /** How long to wait for a service that may still be starting. */
    public static final int WAIT = 2000;

    private final Pipes.Port port;
    private final String name;

    private Connection(String name, Pipes.Port port) {
        this.name = name;
        this.port = port;
    }

    /** Connects to a named service, or answers null if nothing is listening. */
    public static Connection to(String name) {
        return to(name, WAIT);
    }

    public static Connection to(String name, int waitMillis) {
        Pipes.Port port = Pipes.connect(name, waitMillis);
        return port == null ? null : new Connection(name, port);
    }

    /** Whether a service of this name is listening at all. */
    public static boolean available(String name) {
        return Pipes.exists(name);
    }

    /** Sends one message and waits for the answer. */
    public Message send(Message message) throws IOException {
        if (!port.write(message.toBytes())) throw new IOException("could not send to " + name);
        byte[] reply = port.read();
        if (reply == null) throw new IOException(name + " gave no answer");
        return Message.parse(reply);
    }

    /**
     * Sends one message without holding a connection open: ask, take the answer, done.
     */
    public static Message ask(String service, Message message) throws IOException {
        try (Connection connection = to(service)) {
            if (connection == null) throw new IOException(service + " is not running");
            return connection.send(message);
        }
    }

    @Override public void close() {
        port.close();
    }
}
