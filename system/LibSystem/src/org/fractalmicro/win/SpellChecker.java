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
package org.fractalmicro.win;

import org.fractalmicro.core.Log;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Spelling, from the dictionaries this machine already has.
 *
 * Windows ships a spell checking service with dictionaries for the installed languages,
 * reachable as a COM object. Nothing is bundled here and there is no private dictionary:
 * words the user added in their own settings are the words this sees, which is the reason
 * to ask the system rather than ship a copy.
 *
 * Component objects, so calls go through a table of function pointers rather than by name:
 * each method is at a known place in it, the first three being the ones every component
 * has, and calling one means reading the table and passing the interface pointer as the
 * first argument.
 *
 *   ISpellCheckerFactory   3 get_SupportedLanguages  4 IsSupported  5 CreateSpellChecker
 *   ISpellChecker          4 Check                   5 Suggest      6 Add   7 Ignore
 *   IEnumSpellingError     3 Next
 *   ISpellingError         3 get_StartIndex  4 get_Length  5 get_CorrectiveAction
 *                          6 get_Replacement
 *   IEnumString            3 Next
 *
 * A machine with no dictionary for the language says so through {@link #available()}.
 */
public final class SpellChecker {
    private SpellChecker() {}

    private static final SymbolLookup OLE = Native.library("ole32.dll");

    private static final int COINIT_APARTMENTTHREADED = 0x2;
    private static final int CLSCTX_INPROC_SERVER = 0x1;
    private static final int S_OK = 0;
    private static final int S_FALSE = 1;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    private static final MethodHandle CO_INITIALIZE = Native.handle(OLE,
        "CoInitializeEx", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle CO_CREATE_INSTANCE = Native.handle(OLE,
        "CoCreateInstance", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CO_TASK_MEM_FREE = Native.handle(OLE,
        "CoTaskMemFree", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    /** {7AB36653-1796-484B-BDFA-E74F1DB7C1DC}, the spell checker factory. */
    private static final int[] CLSID_FACTORY = {0x7AB36653, 0x1796, 0x484B,
        0xBD, 0xFA, 0xE7, 0x4F, 0x1D, 0xB7, 0xC1, 0xDC};

    /** {8E018A9D-2415-4677-BF08-794EA61F94BB}, ISpellCheckerFactory. */
    private static final int[] IID_FACTORY = {0x8E018A9D, 0x2415, 0x4677,
        0xBF, 0x08, 0x79, 0x4E, 0xA6, 0x1F, 0x94, 0xBB};

    private static boolean started;
    private static boolean usable;
    private static MemorySegment checker = MemorySegment.NULL;
    private static String language = "";

    /** One misspelling: where it is, how long, and what to put there instead. */
    public record Mistake(int start, int length, List<String> suggestions) { }

    /* -------------------------------------------------------------- starting */


    /* ------------------------------------------------------- one thread owns it */

    /**
     * The thread the checking service belongs to.
     *
     * COM was started here as an apartment, which is what the spelling service wants, and
     * an apartment object may only be touched by the thread that made it. Which thread
     * that was is otherwise whichever one happened to ask first: on one machine the checks
     * ran on it and everything worked, and on another they did not and the service quietly
     * answered nothing at all.
     *
     * So it is not left to chance. One thread starts COM, makes the checker and does every
     * call to it, and everybody else asks that thread. Callers no longer have to know
     * which thread they are on, which they had no way of knowing anyway.
     */
    private static Thread ownerThread;

    private static final java.util.concurrent.ExecutorService OWNER =
        java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, "spelling");
            t.setDaemon(true);
            ownerThread = t;
            return t;
        });

    /**
     * Does the work on the thread that owns the service, and waits for it.
     *
     * Already on that thread, it is done here. Handing work to a single thread from that
     * same thread and then waiting for it is a wait that never ends, and the calls inside
     * this class ask each other questions.
     */
    private static <T> T onOwner(java.util.concurrent.Callable<T> work, T ifItCannot) {
        if (Thread.currentThread() == ownerThread) {
            try {
                return work.call();
            } catch (Exception failed) {
                Log.info("the spelling service could not answer: " + failed);
                return ifItCannot;
            }
        }
        try {
            return OWNER.submit(work).get();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return ifItCannot;
        } catch (java.util.concurrent.ExecutionException failed) {
            Log.info("the spelling service could not answer: " + failed.getCause());
            return ifItCannot;
        }
    }

    public static boolean available() {
        return onOwner(SpellChecker::availableHere, false);
    }

    public static String language() {
        return onOwner(SpellChecker::languageHere, "");
    }

    /** What is misspelled in this text, as the service on this machine sees it. */
    public static List<Mistake> check(String text) {
        return onOwner(() -> checkHere(text), new ArrayList<>());
    }

    public static List<String> suggest(String word) {
        return onOwner(() -> suggestHere(word), new ArrayList<>());
    }

    public static boolean learn(String word) {
        return onOwner(() -> learnHere(word), false);
    }

    public static boolean ignore(String word) {
        return onOwner(() -> ignoreHere(word), false);
    }

    private static boolean availableHere() {
        start();
        return usable;
    }

    /** The language being checked against, once there is one. */
    private static String languageHere() {
        start();
        return language;
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        try (Arena arena = Arena.ofConfined()) {
            int initialised = (int) CO_INITIALIZE.invokeExact(
                MemorySegment.NULL, COINIT_APARTMENTTHREADED);
            if (initialised != S_OK && initialised != S_FALSE
                    && initialised != RPC_E_CHANGED_MODE) {
                Log.info("the component service would not start: "
                         + String.format("0x%08X", initialised));
                return;
            }

            MemorySegment factoryOut = Arena.global().allocate(ValueLayout.ADDRESS);
            int made = (int) CO_CREATE_INSTANCE.invokeExact(
                guid(arena, CLSID_FACTORY), MemorySegment.NULL, CLSCTX_INPROC_SERVER,
                guid(arena, IID_FACTORY), factoryOut);
            if (made != S_OK) {
                Log.info("this machine has no spell checking service: "
                         + String.format("0x%08X", made));
                return;
            }
            MemorySegment factory = factoryOut.get(ValueLayout.ADDRESS, 0);

            for (String tag : languagesToTry()) {
                if (!isSupported(arena, factory, tag)) continue;
                MemorySegment checkerOut = Arena.global().allocate(ValueLayout.ADDRESS);
                MemorySegment name = Native.wide(Arena.global(), tag);
                int created = (int) call(factory, 5, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS))
                    .invokeExact(factory, name, checkerOut);
                if (created == S_OK) {
                    checker = checkerOut.get(ValueLayout.ADDRESS, 0);
                    language = tag;
                    usable = true;
                    Log.info("spelling is checked against " + tag);
                    break;
                }
            }
            release(factory);
            if (!usable) Log.info("no dictionary is installed for any language tried");
        } catch (Throwable t) {
            Log.error("the spell checking service could not be reached", t);
        }
    }

    /** The language of this session first, then the plain English the service always has. */
    private static List<String> languagesToTry() {
        List<String> tags = new ArrayList<>();
        java.util.Locale here = java.util.Locale.getDefault();
        String tag = here.toLanguageTag();
        if (!tag.isBlank() && !"und".equals(tag)) tags.add(tag);
        if (!tags.contains("en-GB")) tags.add("en-GB");
        if (!tags.contains("en-US")) tags.add("en-US");
        return tags;
    }

    private static boolean isSupported(Arena arena, MemorySegment factory, String tag)
            throws Throwable {
        MemorySegment name = Native.wide(arena, tag);
        MemorySegment answer = arena.allocate(ValueLayout.JAVA_INT);
        int result = (int) call(factory, 4, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
            .invokeExact(factory, name, answer);
        return result == S_OK && answer.get(ValueLayout.JAVA_INT, 0) != 0;
    }

    /* -------------------------------------------------------------- checking */

    /** Every misspelling in a piece of text, in the order they appear. */
    private static List<Mistake> checkHere(String text) {
        List<Mistake> mistakes = new ArrayList<>();
        if (text == null || text.isBlank() || !available()) return mistakes;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = Native.wide(arena, text);
            MemorySegment errorsOut = arena.allocate(ValueLayout.ADDRESS);
            int checked = (int) call(checker, 4, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                .invokeExact(checker, wide, errorsOut);
            if (checked != S_OK) return mistakes;

            MemorySegment errors = errorsOut.get(ValueLayout.ADDRESS, 0);
            try {
                while (true) {
                    MemorySegment errorOut = arena.allocate(ValueLayout.ADDRESS);
                    int next = (int) call(errors, 3, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                        .invokeExact(errors, errorOut);
                    if (next != S_OK) break;
                    MemorySegment error = errorOut.get(ValueLayout.ADDRESS, 0);
                    if (error.address() == 0) break;
                    try {
                        int start = (int) property(arena, error, 3);
                        int length = (int) property(arena, error, 4);
                        if (length <= 0 || start < 0 || start + length > text.length()) continue;
                        String word = text.substring(start, start + length);
                        mistakes.add(new Mistake(start, length, suggest(word)));
                    } finally {
                        release(error);
                    }
                }
            } finally {
                release(errors);
            }
        } catch (Throwable t) {
            Log.error("the spelling check failed", t);
        }
        return mistakes;
    }

    /** What the service would put in place of a word. */
    private static List<String> suggestHere(String word) {
        List<String> out = new ArrayList<>();
        if (word == null || word.isBlank() || !available()) return out;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = Native.wide(arena, word);
            MemorySegment enumOut = arena.allocate(ValueLayout.ADDRESS);
            int asked = (int) call(checker, 5, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                .invokeExact(checker, wide, enumOut);
            if (asked != S_OK && asked != S_FALSE) return out;

            MemorySegment strings = enumOut.get(ValueLayout.ADDRESS, 0);
            if (strings.address() == 0) return out;
            try {
                MethodHandle next = call(strings, 3, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
                while (out.size() < 10) {
                    MemorySegment one = arena.allocate(ValueLayout.ADDRESS);
                    MemorySegment got = arena.allocate(ValueLayout.JAVA_INT);
                    int result = (int) next.invokeExact(strings, 1, one, got);
                    if (result != S_OK || got.get(ValueLayout.JAVA_INT, 0) == 0) break;
                    MemorySegment text = one.get(ValueLayout.ADDRESS, 0);
                    if (text.address() == 0) break;
                    out.add(Native.readWide(text.reinterpret(1024)));
                    CO_TASK_MEM_FREE.invokeExact(text);
                }
            } finally {
                release(strings);
            }
        } catch (Throwable t) {
            Log.error("suggestions could not be had", t);
        }
        return out;
    }

    /** Adds a word to the person's own dictionary, where it stays for everything. */
    private static boolean learnHere(String word) {
        if (word == null || word.isBlank() || !available()) return false;
        return wordCall(word, 6);
    }

    /** Passes over a word for the rest of this session. */
    private static boolean ignoreHere(String word) {
        if (word == null || word.isBlank() || !available()) return false;
        return wordCall(word, 7);
    }

    private static boolean wordCall(String word, int slot) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = Native.wide(arena, word);
            int result = (int) call(checker, slot, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                .invokeExact(checker, wide);
            return result == S_OK;
        } catch (Throwable t) {
            Log.error("the word could not be given to the dictionary", t);
            return false;
        }
    }

    /* -------------------------------------------------------------- plumbing */

    /** One of the numbered properties that answer a single number. */
    private static long property(Arena arena, MemorySegment object, int slot) throws Throwable {
        MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
        int result = (int) call(object, slot, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS))
            .invokeExact(object, out);
        return result == S_OK ? out.get(ValueLayout.JAVA_INT, 0) : -1;
    }

    /**
     * The handle for one method of one component: the interface points at its table of
     * function pointers, and the method is at a known place in that table.
     */
    private static MethodHandle call(MemorySegment object, int slot, FunctionDescriptor how) {
        MemorySegment table = object.reinterpret(ValueLayout.ADDRESS.byteSize())
                                    .get(ValueLayout.ADDRESS, 0);
        MemorySegment method = table.reinterpret((slot + 1) * ValueLayout.ADDRESS.byteSize())
                                    .get(ValueLayout.ADDRESS, slot * ValueLayout.ADDRESS.byteSize());
        return Native.LINKER.downcallHandle(method, how);
    }

    /** Every component has Release as the third entry in its table. */
    private static void release(MemorySegment object) {
        if (object == null || object.address() == 0) return;
        try {
            int ignored = (int) call(object, 2,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                .invokeExact(object);
        } catch (Throwable t) {
            Log.info("a component would not let go: " + t);
        }
    }

    /** A GUID as its four fields: a long, two shorts, and eight bytes. */
    private static MemorySegment guid(Arena arena, int[] parts) {
        MemorySegment id = arena.allocate(16);
        id.set(ValueLayout.JAVA_INT, 0, parts[0]);
        id.set(ValueLayout.JAVA_SHORT, 4, (short) parts[1]);
        id.set(ValueLayout.JAVA_SHORT, 6, (short) parts[2]);
        for (int i = 0; i < 8; i++) id.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) parts[3 + i]);
        return id;
    }

    /** A line for the log and the checks: what is being checked against, if anything. */
    public static String describe() {
        return available() ? "spelling checked against " + language()
                           : "no spelling dictionary on this machine";
    }
}
