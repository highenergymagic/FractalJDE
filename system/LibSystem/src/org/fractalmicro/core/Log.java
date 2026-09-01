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
package org.fractalmicro.core;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A log file, because a desktop started with javaw has nowhere to print to and a
 * swallowed exception looks exactly like a feature that quietly does nothing.
 *
 * Written to ~/.fractaldt/Library/Logs/Fractal.log, where a Mac would keep it.
 */
public final class Log {

    /**
     * Where the log is written.
     *
     * The system library is the bottom of the stack and cannot ask the layer above it where
     * the volume is. Whoever knows says so once, at start-up, and until then the log goes
     * beside the runtime's own temporary files rather than nowhere.
     */
    private static volatile java.nio.file.Path where;

    public static void setDestination(java.nio.file.Path file) { where = file; }

    private static java.nio.file.Path destination() {
        java.nio.file.Path named = where;
        return named != null ? named
            : java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "Fractal.log");
    }
    private Log() {}

    private static Path file;
    private static boolean installed;

    public static synchronized Path file() {
        if (file == null) {
            file = destination();
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                System.err.println("no log directory: " + e.getMessage());
            }
        }
        return file;
    }

    /**
     * Catches what would otherwise vanish. Worker threads go through the default
     * handler; the event thread does not, so its queue is wrapped instead. Without
     * this an exception while building a window just prints to a console that a
     * program started with javaw does not have.
     */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
            error("uncaught on " + thread.getName(), error));
        java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(new java.awt.EventQueue() {
            @Override protected void dispatchEvent(java.awt.AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable t) {
                    error("uncaught while handling " + event.getClass().getSimpleName(), t);
                }
            }
        });
        write("--- started " + stamp() + " ---");
    }

    public static void info(String message) {
        write(stamp() + "  " + message);
    }

    public static void error(String message, Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        write(stamp() + "  " + message + "\n" + sw);
        System.err.println(message + ": " + error);
    }

    private static synchronized void write(String line) {
        try {
            Files.writeString(file(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // If even the log cannot be written there is nowhere left to complain to.
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    /** Keeps the file from growing without bound between runs. */
    public static synchronized void trim() {
        try {
            Path f = file();
            if (Files.exists(f) && Files.size(f) > 256 * 1024) Files.delete(f);
        } catch (IOException ignored) { }
    }
}
