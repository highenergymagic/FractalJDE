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
package org.fractalmicro.a11y;

import org.fractalmicro.core.Log;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.win.Native;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saying something out loud.
 *
 * A screen reader reads what has focus, so it cannot read what merely happened: a window
 * closing, a program quitting, a view changing. When a key does one of those, this says it.
 *
 * Spoken by NVDA, through the controller client it publishes for this. The library is
 * bundled rather than looked for, so the version is the one it was tested against.
 *
 * That library is NVDA's, under the GNU Lesser General Public License version 2.1,
 * included unmodified and loaded at run time, which is the arrangement that licence is
 * written for. Its licence and readme sit beside it in resources/nvda, and the file can be
 * replaced with another build of it without changing anything here.
 *
 * Without NVDA running, {@link #available()} says so and every call is a no-op.
 */
public final class Speech {
    private Speech() {}

    /** What the bundled library is called, per architecture. */
    private static final String RESOURCES = "/nvda/";

    private static volatile boolean started;
    private static volatile boolean usable;
    private static MethodHandle speakText;
    private static MethodHandle cancelSpeech;
    private static MethodHandle brailleMessage;
    private static MethodHandle testIfRunning;
    private static Path library;

    /** Whether a screen reader is there to be spoken to. */
    public static boolean available() {
        start();
        if (!usable) return false;
        try {
            // Answers zero when NVDA is running, and an error code when it is not, so this
            // is asked every time rather than remembered: NVDA may start after this program did.
            return (int) testIfRunning.invokeExact() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether the library itself was found and loaded, whatever is running. */
    public static boolean loaded() {
        start();
        return usable;
    }

    public static Path libraryPath() {
        start();
        return library;
    }

    /**
     * Says something, if there is anything to say it. Speech that arrives late is worse
     * than none, so this never waits: a screen reader that is busy is not this program's
     * problem to solve.
     */
    public static void say(String what) {
        if (what == null || what.isBlank() || !available()) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = Native.wide(arena, what);
            int result = (int) speakText.invokeExact(text);
            if (result != 0) Log.info("nothing was said: error " + result);
        } catch (Throwable t) {
            Log.info("speech failed: " + t);
        }
    }

    /** Says something, stopping whatever was being said first. */
    public static void interrupt(String what) {
        if (!available()) return;
        try {
            int ignored = (int) cancelSpeech.invokeExact();
        } catch (Throwable t) {
            Log.info("speech could not be stopped: " + t);
        }
        say(what);
    }

    /** Puts something on a braille display, for anyone reading rather than listening. */
    public static void braille(String what) {
        if (what == null || what.isBlank() || !available()) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = Native.wide(arena, what);
            int ignored = (int) brailleMessage.invokeExact(text);
        } catch (Throwable t) {
            Log.info("braille failed: " + t);
        }
    }

    /** Speaks it and shows it on screen. */
    public static void announce(String what) {
        say(what);
        braille(what);
    }

    /* ------------------------------------------------------------- loading */

    private static synchronized void start() {
        if (started) return;
        started = true;
        try {
            library = unpack();
            if (library == null) return;
            SymbolLookup client = SymbolLookup.libraryLookup(library, Arena.global());
            speakText = Native.handle(client, "nvdaController_speakText",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            cancelSpeech = Native.handle(client, "nvdaController_cancelSpeech",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            brailleMessage = Native.handle(client, "nvdaController_brailleMessage",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            testIfRunning = Native.handle(client, "nvdaController_testIfRunning",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            usable = true;
            Log.info("the screen reader client is loaded from " + library);
        } catch (Throwable t) {
            Log.info("no screen reader client: " + t);
        }
    }

    /**
     * Puts the bundled library somewhere it can be loaded from.
     *
     * A library inside a jar cannot be loaded from there, so it is written out once, beside
     * the rest of this system's own files, and loaded from there afterwards.
     */
    private static Path unpack() throws IOException {
        String name = fileName();
        Path target = OSPaths.ROOT.resolve("System/Library/Frameworks/NVDA")
                                  .resolve(name);
        try (InputStream in = Speech.class.getResourceAsStream(RESOURCES + name)) {
            if (in == null) {
                // Running from the source tree rather than from a jar.
                Path beside = Path.of("resources", "nvda", name);
                if (Files.isReadable(beside)) return beside.toAbsolutePath();
                Log.info("the screen reader client is not bundled here: " + name);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            Files.createDirectories(target.getParent());
            if (!Files.isReadable(target) || Files.size(target) != bytes.length) {
                Path temporary = target.resolveSibling(name + ".part");
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            copyBeside("LICENSE.txt", target.getParent());
            copyBeside("readme.md", target.getParent());
            return target;
        }
    }

    /** The licence travels with the library, because that is a condition of using it. */
    private static void copyBeside(String name, Path folder) {
        try (InputStream in = Speech.class.getResourceAsStream(RESOURCES + name)) {
            if (in == null) return;
            Path target = folder.resolve(name);
            byte[] bytes = in.readAllBytes();
            if (!Files.isReadable(target) || Files.size(target) != bytes.length) {
                Files.write(target, bytes);
            }
        } catch (IOException e) {
            Log.info("the screen reader licence could not be written: " + e.getMessage());
        }
    }

    /** Which build of the library this machine needs. */
    private static String fileName() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (architecture.contains("aarch64") || architecture.contains("arm")) {
            return "nvdaControllerClientArm64.dll";
        }
        return architecture.contains("64") ? "nvdaControllerClient64.dll"
                                           : "nvdaControllerClient32.dll";
    }

    /** A line for the log and the checks: what is loaded and whether anyone is listening. */
    public static String describe() {
        if (!loaded()) return "no screen reader client is loaded";
        return "the screen reader client is loaded and NVDA is "
             + (available() ? "running" : "not running");
    }
}
