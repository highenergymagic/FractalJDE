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

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMTask;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.os.OSPaths;

import java.io.File;
import java.util.List;

/**
 * Starting a program that has been asked for.
 *
 * On a Mac this is NSApplicationMain: the bundle is already found and its code already
 * loaded, and what remains is to read NSPrincipalClass out of the Info.plist, make one,
 * and send it the messages a program expects on the main thread. LaunchServices does not
 * do that work. It finds the bundle and hands over, which is why this lives in AppKit,
 * above it, where the main thread and the windows are.
 *
 * A bundle naming a main class in its Info.plist is started as a process instead. That is
 * a departure from a Mac, where every program is its own process and the question does not
 * arise; here most programs are hosted by the desktop and a few are not, and the bundle is
 * the honest place to say which.
 */
public final class FMApplicationMain implements Bundles.Launcher {

    /** The Info.plist key that says a program wants a process of its own. */
    public static final FMString OWN_PROCESS = Bundles.OWN_PROCESS;

    private FMApplicationMain() {}

    /** Tells LaunchServices that this is what opening a program means. */
    public static void install() {
        Bundles.setLauncher(new FMApplicationMain());
        // Events sent from this process go through the server directly. It is in here,
        // so asking it over a connection would be this process writing to itself.
        org.fractalmicro.scripting.FMAppleEventManager.sharedManager().setCourier(
            (event, wait) ->
                org.fractalmicro.windowserver.WindowServer.sharedServer().send(event, wait));
    }

    /**
     * Where a program in a process of its own starts, which is not in the program.
     *
     * On a Mac the entry point is main, and every Cocoa application writes one line there:
     * return NSApplicationMain. So the entry point here is this and not the program. It
     * finds the bundle the loader opened, reads NSPrincipalClass, makes one and sends it
     * the messages an application expects. The program has no main: it has a class that
     * answers open, which is what a delegate is.
     */
    public static void main(String[] arguments) {
        Bundle bundle = openingBundle();
        if (bundle == null) {
            org.fractalmicro.core.Log.info("no bundle to start; nothing said which program this is");
            return;
        }
        install();

        FMApplication app = FMApplication.named(bundle.displayName());
        FMApplication.becomeShared(app);
        if (!FMApplication.serverAvailable()) {
            org.fractalmicro.foundation.FMLog.say(
                FMString.of("there is no window server to draw a window on"));
            return;
        }

        Object instance;
        try {
            instance = Dyld.load(bundle);
        } catch (Exception notLoadable) {
            org.fractalmicro.core.Log.error("could not start " + bundle.displayName(),
                                            notLoadable);
            return;
        }
        if (!(instance instanceof FMApplicationDelegate program)) {
            org.fractalmicro.core.Log.info(bundle.displayName() + " is not an application");
            return;
        }

        // Opened on nothing, or on whatever it was started with, which is how a program in
        // a process of its own is handed a document: it cannot be passed an object.
        if (arguments == null || arguments.length == 0) program.open();
        else program.openURLs(locations(filesNamed(arguments)));

        // The run loop, which is the entry point's and not the program's. A program that
        // owned it would have to remember to close afterwards, and one that forgot would
        // leave its connection open until the process ended.
        //
        // Unless it opened no window, which some programs do not: Terminal hands a folder
        // to the host's command line and is finished. Waiting for events on a connection
        // with no window is waiting for something that cannot arrive.
        if (app.windowId() < 0) {
            app.close();
            return;
        }
        // Being opened again on a document is the same question as being opened on one, so
        // it goes to the same method. The program does not have to know it was already
        // running, which is the whole of what it had to know before.
        app.onOpenFiles(program::openURLs);
        app.onClose(app::stop);
        app.run();
        app.close();
    }

    /**
     * The bundle this process was started for.
     *
     * The loader says where the executable is, because a process cannot find that out by
     * looking at itself. The bundle is the directory that executable lives inside, which is
     * what a bundle is.
     */
    private static Bundle openingBundle() {
        String executable = System.getProperty(
            org.fractalmicro.dyld.Start.EXECUTABLE_PROPERTY, "");
        if (executable.isBlank()) return null;
        // Contents/Fractal/<name>, so the bundle is three levels up from the executable.
        File at = new File(executable).getParentFile();
        for (int up = 0; up < 3 && at != null; up++, at = at.getParentFile()) {
            Bundle found = Bundle.read(at);
            if (found != null && !found.identifier().isEmpty()) return found;
        }
        return null;
    }

    private static List<File> filesNamed(String[] arguments) {
        List<File> out = new java.util.ArrayList<>();
        for (String one : arguments) out.add(new File(one));
        return out;
    }

    @Override public boolean open(Bundle bundle, List<File> files) {
        if (bundle.flag(OWN_PROCESS)) return spawn(bundle, files);
        return deliver(bundle, app -> {
            if (files == null || files.isEmpty()) app.open();
            else app.openURLs(locations(files));
        });
    }

    @Override public boolean openPart(Bundle bundle, String part) {
        return deliver(bundle, app -> app.openPart(FMString.of(part)));
    }

    @Override public boolean openText(Bundle bundle, String text) {
        return deliver(bundle, app -> app.openText(FMString.of(text)));
    }

    /**
     * Makes the principal class and says something to it on the main thread.
     *
     * Through the loader, so opening a program from the desktop happens the same way as
     * from outside. Unless this process is already the program, which is what a hosted one
     * is: a second copy out of the bundle would be two of every class, two of each static
     * field, and a desktop holding a control made by one while everything asks the other.
     */
    private static boolean deliver(Bundle bundle, java.util.function.Consumer<FMApplicationDelegate> what) {
        if (bundle == null) return false;
        Object instance;
        try {
            instance = hosted(bundle);
            if (instance == null) instance = Dyld.load(bundle);
        } catch (Exception notLoadable) {
            org.fractalmicro.core.Log.error("could not open " + bundle.displayName(), notLoadable);
            return false;
        }
        if (!(instance instanceof FMApplicationDelegate app)) {
            org.fractalmicro.core.Log.info(bundle.displayName() + " is not an application");
            return false;
        }
        Runnable run = () -> what.accept(app);
        if (javax.swing.SwingUtilities.isEventDispatchThread()) run.run();
        else javax.swing.SwingUtilities.invokeLater(run);
        return true;
    }

    /**
     * The program, if this process is already carrying it.
     *
     * Answers null when it is not, which is the ordinary case and means it comes out of
     * its bundle.
     */
    private static Object hosted(Bundle bundle) {
        String principal = bundle.principalClass().toString();
        if (principal.isEmpty()) return null;
        try {
            return Class.forName(principal, true, FMApplicationMain.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError notHere) {
            return null;
        }
    }

    /**
     * Starts a program in a process of its own, or brings back the one already running.
     *
     * Nothing here is particular to any program: the class path is the closure of what the
     * executable links and the label is the bundle identifier, so a program does not have
     * to carry code that knows how to start itself.
     */
    private static boolean spawn(Bundle bundle, List<File> files) {
        FMString label = bundle.identifier();
        // Already running and opened on nothing is already open.
        //
        // Opened on something, the one that is running is told, which is what a Mac does
        // and what this could not do until the window server would carry the message. It
        // used to start a second copy: two TextEdits, two Dock tiles, two of everything
        // either had open, and no way for a person to tell which was which.
        //
        // A program that is running but has put no window up has nothing listening yet, and
        // that answers false, so one is started as before.
        FMTask already = FMTask.running(label);
        if (already != null && already.isRunning()) {
            if (files == null || files.isEmpty()) return true;
            List<String> paths = new java.util.ArrayList<>();
            for (File one : files) paths.add(one.getAbsolutePath());
            if (org.fractalmicro.windowserver.WindowServer.sharedServer()
                    .reopen(bundle.displayName().toString(), paths)) {
                return true;
            }
        }

        // The process starts with the loader and the program, which is what the kernel
        // hands over on a Mac. Everything else is found by following the load commands.
        // Putting the libraries on the class path instead would let the program reach any
        // of them, linked or not, which is the arrangement this system exists to avoid.
        //
        // The loader says what that command is, because it is the same command wherever a
        // program is started from and there is nothing about it that is AppKit's.
        FMMutableArray<FMString> command = FMMutableArray.empty();
        for (String word : Dyld.commandFor(bundle, files)) command.add(FMString.of(word));

        FMURL log = FMURL.of(OSPaths.userLibrary().resolve("Logs").toFile())
                         .appending(bundle.identifier().appending(FMString.of(".log")));
        FMTask started = FMTask.launch(label, bundle.displayName(),
                                       command.asArray(), log);
        return started != null && started.isRunning();
    }

    /** The runtime's files as the locations a program is given. */
    private static FMArray<FMURL> locations(List<File> files) {
        FMMutableArray<FMURL> out = FMMutableArray.empty();
        for (File f : files) out.add(FMURL.of(f));
        return out.asArray();
    }
}
