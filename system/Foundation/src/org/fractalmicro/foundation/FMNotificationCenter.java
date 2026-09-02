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

/**
 * Saying that something happened, without knowing who cares.
 *
 * This is how a lower layer tells a higher one that the world changed. Force Quit ends a
 * program and the file browser wants to reload; the window server has no business knowing
 * the browser exists, and the browser has no business being called by name. So one posts
 * and the other listens, and neither names the other.
 *
 * Notifications are delivered on the thread that posts them, in the order they were
 * observed. An observer that throws is complained about and the rest still hear it: one
 * listener having a bad day is not a reason for the others to miss the news.
 */
public final class FMNotificationCenter {

    /** Something ended, started, or changed about what is running. */
    public static final FMString PROGRAMS_CHANGED =
        FMString.of("FMProgramsDidChangeNotification");

    /** The volumes on this machine are not what they were. */
    public static final FMString VOLUMES_CHANGED =
        FMString.of("FMVolumesDidChangeNotification");

    /** Something on disk that is being shown has changed. */
    public static final FMString FILES_CHANGED =
        FMString.of("FMFilesDidChangeNotification");

    private static final FMNotificationCenter SHARED = new FMNotificationCenter();

    private final java.util.Map<String, java.util.List<Observer>> observers =
        new java.util.concurrent.ConcurrentHashMap<>();

    private FMNotificationCenter() {}

    /** The one everything uses. */
    public static FMNotificationCenter defaultCenter() { return SHARED; }

    /** What is told when something happens. */
    public interface Observer {
        void heard(FMString name);
    }

    /** Asks to be told. The same observer added twice is told twice, as it asked to be. */
    public void observe(FMString name, Observer observer) {
        if (name == null || observer == null) return;
        observers.computeIfAbsent(name.toString(),
            k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(observer);
    }

    public void stopObserving(FMString name, Observer observer) {
        java.util.List<Observer> listening = observers.get(name.toString());
        if (listening != null) listening.remove(observer);
    }

    /** Says it happened. Answers how many were listening. */
    public int post(FMString name) {
        java.util.List<Observer> listening = observers.get(name.toString());
        if (listening == null) return 0;
        int told = 0;
        for (Observer one : listening) {
            try {
                one.heard(name);
                told++;
            } catch (RuntimeException badDay) {
                FMLog.wrong(FMString.of("an observer of ").appending(name)
                                    .appending(FMString.of(" threw")), badDay);
            }
        }
        return told;
    }

    /** How many are listening for something, which is worth knowing in a check. */
    public int observerCount(FMString name) {
        java.util.List<Observer> listening = observers.get(name.toString());
        return listening == null ? 0 : listening.size();
    }
}
