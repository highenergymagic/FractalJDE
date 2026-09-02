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
    }

    /**
     * Where a program in a process of its own starts, which is not in the program.
     *
     * The loader maps the image and calls its entry point. On a Mac that entry point is
     * main, and what every Cocoa application writes in it is one line: return
     * NSApplicationMain(argc, argv). Nobody writes anything else there, because there is
     * nothing an application knows at that moment that its bundle has not already said.
     *
     * So the entry point of a program here is this, and not the program. It finds the
     * bundle the loader just opened, reads NSPrincipalClass out of it, makes one, and
     * sends it the messages an application expects. The program has no main at all: it has
     * a class that answers open, which is what a delegate is.
     *
     * What that removed was the same fifteen lines in every program: check that there is a
     * window server, make itself, show its window, register its handlers, read events until
     * told to stop, close. Only two of those are the program's business.
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
     * Loading goes through the loader so that opening a program from the desktop happens
     * the same way as opening it from outside: the code comes out of the executable in the
     * bundle, with the libraries it links behind it, and a program reaching for something
     * it did not link fails here as it would there.
     *
     * Unless this process is already the program, which is what a hosted program is. Then
     * its code is here and taking a second copy out of the bundle would be two copies of
     * every class it has: two of each static field, two of each type, and a desktop
     * holding a control made by one of them while everything else asks the other. That is
     * not isolation, it is a program talking to itself through a wall. A program with a
     * process of its own has no copy here and gets the bundle's, which is the only one.
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
     * executable links, the label is the bundle identifier, and what the program writes
     * goes beside every other program's log. A program does not have to carry code that
     * knows how to start itself.
     */
    private static boolean spawn(Bundle bundle, List<File> files) {
        FMString label = bundle.identifier();
        // Already running and opened on nothing is already open. Opened on something, it
        // is started again: a program with a process of its own has no way to be handed a
        // document once it is going, so a second one is what opens it.
        FMTask already = FMTask.running(label);
        if (already != null && already.isRunning() && (files == null || files.isEmpty())) {
            return true;
        }

        // The process starts with the loader and the program, which is what the kernel
        // hands over on a Mac. Everything else is found by following the load commands.
        // Putting the libraries on the class path instead would let the program reach any
        // of them, linked or not, which is the arrangement this system exists to avoid.
        FMMutableArray<FMString> command = FMMutableArray.empty();
        command.add(FMString.of(Dyld.javaCommand()));
        command.add(FMString.of("--enable-preview"));
        command.add(FMString.of("--enable-native-access=ALL-UNNAMED"));
        command.add(FMString.of("-D" + org.fractalmicro.dyld.Start.ROOT_PROPERTY
                                + "=" + OSPaths.ROOT));
        command.add(FMString.of("-cp"));
        command.add(FMString.of(Dyld.bootstrapClassPath()));
        command.add(FMString.of(Dyld.bootstrapClass()));
        command.add(FMString.of(bundle.machOExecutable().getAbsolutePath()));
        // What it was opened on goes to the program as its arguments, which is what a
        // program in a process of its own can be given: it cannot be handed an object.
        if (files != null) {
            for (File one : files) command.add(FMString.of(one.getAbsolutePath()));
        }

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
