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
package org.fractalmicro;

import org.fractalmicro.core.Progress;
import org.fractalmicro.fs.Apps;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Trash;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.theme.AquaLaf;
import org.fractalmicro.windowserver.Desktop;

import javax.swing.*;

/**
 * Start-up. Installs the look, puts the desktop on screen, then fills in the disks,
 * the applications list and the Trash count in the background so nothing blocks.
 */
public final class Main {

    /**
     * Runs the checks, which are not part of what ships.
     *
     * Reached by name rather than linked, so a build without them says so instead of
     * failing to start.
     */
    private static void runChecks(org.fractalmicro.windowserver.Desktop desktop) {
        try {
            Class.forName("org.fractalmicro.a11y.SelfTest")
                 .getMethod("run", org.fractalmicro.windowserver.Desktop.class)
                 .invoke(null, desktop);
        } catch (ClassNotFoundException e) {
            System.out.println("this build has no checks in it");
        } catch (ReflectiveOperationException e) {
            Throwable why = e.getCause() == null ? e : e.getCause();
            System.out.println("the checks could not run: " + why);
        }
    }

    /** Opens a window by the name of the class that puts it up, for the checking modes. */
    private static void openByName(String className) {
        try {
            Class.forName(className).getMethod("open").invoke(null);
        } catch (ReflectiveOperationException notHere) {
            System.out.println("this build has no " + className);
        }
    }

    /** What this understands, for when it is given something it does not. */
    private static final String USAGE = String.join(System.lineSeparator(),
        "commands:",
        "  --selftest              open everything once and report what broke",
        "  --dump-accessibility    print the tree a screen reader sees",
        "  --osascript <script>    run a script against the session that is up",
        "  --screenshot <file>     draw the desktop into a picture without showing it",
        "  --open <path>           open a window on a folder at start",
        "  --open-app <id>         open a program by its bundle identifier",
        "  --controls              draw the controls on their own, for looking at",
        "  --install               put this build in place as the framework",
        "  --tasks [tree]          list what is running, flat or as a tree",
        "  --launchctl <command>   load, start, stop or list jobs",
        "  --program-info <path>   describe a program bundle",
        "  --native-report         say what the host system answered");


    private static javax.swing.JMenuItem findMenuItem(javax.swing.MenuElement element, String text) {
        for (javax.swing.MenuElement child : element.getSubElements()) {
            if (child instanceof javax.swing.JMenuItem) {
                javax.swing.JMenuItem item = (javax.swing.JMenuItem) child;
                if (text.equals(item.getText())) return item;
            }
            javax.swing.JMenuItem found = findMenuItem(child, text);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Runs a script against the session that is already up, and says what came back.
     *
     * The argument is the script, or the name of a file holding one. Nothing is drawn and
     * no session is started: a tool that started a desktop in order to ask it a question
     * would be answering about a desktop nobody is looking at.
     */
    private static int runScript(String what) {
        String text = what;
        java.io.File file = new java.io.File(what);
        if (file.isFile()) {
            try {
                text = java.nio.file.Files.readString(file.toPath());
            } catch (java.io.IOException unreadable) {
                System.err.println("could not read " + what);
                return 1;
            }
        }
        if (!org.fractalmicro.appkit.FMApplication.serverAvailable()) {
            System.err.println("no session is running");
            return 1;
        }
        org.fractalmicro.bundle.Bundles.scan();
        org.fractalmicro.scripting.FMAppleEventManager.sharedManager().setCourier(
            org.fractalmicro.windowserver.WindowServer.courier());
        try {
            org.fractalmicro.foundation.FMString answer =
                org.fractalmicro.scripting.FMScript.run(
                    org.fractalmicro.foundation.FMString.of(text));
            if (!answer.isEmpty()) System.out.println(answer);
            return 0;
        } catch (org.fractalmicro.scripting.FMScriptError refused) {
            System.err.println(refused.said());
            return 1;
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("swing.aatext", "true");

        boolean screenshot = false;
        String screenshotPath = null;
        boolean dumpTree = false;
        String openAtStart = null;
        boolean selfTest = false;
        boolean probe = false;
        boolean controls = false;
        String openApp = null;
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                screenshot = true;
                screenshotPath = args[++i];
            } else if ("--open-app".equals(args[i]) && i + 1 < args.length) {
                openApp = args[++i];
            } else if ("--controls".equals(args[i])) {
                controls = true;
            } else if ("--visible-probe".equals(args[i])) {
                probe = true;
            } else if ("--osascript".equals(args[i]) && i + 1 < args.length) {
                System.exit(runScript(args[++i]));
            } else if ("--native-report".equals(args[i])) {
                org.fractalmicro.win.Probe.report(System.out);
                return;
            } else if ("--dump-accessibility".equals(args[i])) {
                dumpTree = true;
            } else if ("--selftest".equals(args[i])) {
                selfTest = true;
            } else if ("--open".equals(args[i]) && i + 1 < args.length) {
                openAtStart = args[++i];
            } else if ("--tasks".equals(args[i])) {
                org.fractalmicro.bundle.Install.adoptFormerVolume();
            org.fractalmicro.os.OSPaths.ensure();
            // The system library is told where the volume is, and how to name a
            // running program; it cannot ask the layers above it.
            org.fractalmicro.core.Log.setDestination(
                org.fractalmicro.os.OSPaths.userLibrary().resolve("Logs").resolve("Fractal.log"));
            org.fractalmicro.core.WindowList.setNaming(org.fractalmicro.fs.Apps::nameForExecutable);
            // Foundation gets the index and the bundle reader from the layer that has
            // them; CoreServices gets a way to draw an icon from the layer that draws.
            org.fractalmicro.fs.Search.setIndex(new org.fractalmicro.fs.Search.Index() {
                @Override public boolean running() { return org.fractalmicro.mds.Metadata.running(); }
                @Override public java.util.List<java.io.File> search(String q, int limit) {
                    java.util.List<java.io.File> out = new java.util.ArrayList<>();
                    for (org.fractalmicro.mds.Metadata.Hit hit : org.fractalmicro.mds.Metadata.search(org.fractalmicro.foundation.FMString.of(q), limit)) {
                        out.add(hit.file());
                    }
                    return out;
                }
            });
            org.fractalmicro.fs.FS.setBundleNaming(f -> {
                org.fractalmicro.bundle.Bundle b = org.fractalmicro.bundle.Bundle.read(f);
                return b == null ? null : b.identifier().toString();
            });
            org.fractalmicro.bundle.Bundles.setIconWriter((name, into) -> {
                try {
                    javax.imageio.ImageIO.write(
                        org.fractalmicro.theme.AppIcons.forApplication(name, 128), "png", into);
                } catch (java.io.IOException e) {
                    org.fractalmicro.core.Log.info("no icon for " + name + ": " + e.getMessage());
                }
            });
                // From the table, which holds every process's tasks and not just this
                // one's. Asking this process alone would print a listing that is true and
                // almost empty.
                boolean asTree = i + 1 < args.length && "tree".equals(args[i + 1]);
                System.out.print(asTree
                    ? org.fractalmicro.kernel.TaskServer.describeAsTree()
                    : org.fractalmicro.kernel.TaskServer.describe());
                return;
            } else if ("--launchctl".equals(args[i])) {
                org.fractalmicro.os.OSPaths.ensure();
                // This command hands jobs over rather than owning them: what it starts is
                // still running when it has gone.
                org.fractalmicro.launchd.Launchd launchd = org.fractalmicro.launchd.Launchd.session();
                launchd.setStopOnExit(false);
                String command = i + 1 < args.length ? args[++i] : "list";
                String label = i + 1 < args.length ? args[++i] : null;
                // Listing says what is there; it does not start anything.
                if ("list".equals(command)) launchd.readAll();
                else launchd.loadAll();
                switch (command) {
                    case "start" -> System.out.println(label + ": "
                        + (launchd.start(label) ? "started" : "not started"));
                    case "stop" -> System.out.println(label + ": "
                        + (launchd.stop(label) ? "stopped" : "not running"));
                    case "install" -> {
                        try {
                            System.out.println("wrote " + org.fractalmicro.mds.Metadata.installJob());
                        } catch (java.io.IOException e) {
                            System.out.println("the job could not be written: " + e.getMessage());
                        }
                    }
                    default -> System.out.print(launchd.describe());
                }
                if (!"list".equals(command)) {
                    // Give a job just started a moment to claim its name, so the listing
                    // that follows says something true.
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.print(launchd.describe());
                }
                System.out.println("metadata server: "
                    + (org.fractalmicro.mds.Metadata.running() ? "listening" : "not listening"));
                return;
            } else if ("--install".equals(args[i])) {
                org.fractalmicro.os.OSPaths.ensure();
                boolean written = org.fractalmicro.bundle.Install.ensureInstalled();
                org.fractalmicro.bundle.Bundles.install();
                try {
                    System.out.println("job: " + org.fractalmicro.mds.Metadata.installJob());
                } catch (java.io.IOException e) {
                    System.out.println("the metadata job could not be written: " + e.getMessage());
                }
                System.out.println(written ? "installed" : "already installed");
                System.out.println(org.fractalmicro.bundle.Install.describe());
                for (org.fractalmicro.bundle.Bundle b : org.fractalmicro.bundle.Bundles.all()) {
                    System.out.println("  " + b.displayName() + ": "
                                       + org.fractalmicro.bundle.Dyld.describe(b));
                }
                return;
            } else if ("--program-info".equals(args[i]) && i + 1 < args.length) {
                org.fractalmicro.bundle.Bundle b = org.fractalmicro.bundle.Bundle.read(new java.io.File(args[++i]));
                if (b == null) {
                    System.out.println("not a program bundle");
                    return;
                }
                System.out.println(b.displayName() + " (" + b.identifier() + ")");
                System.out.println("  " + org.fractalmicro.bundle.Dyld.describe(b));
                System.out.println("  class path: " + org.fractalmicro.bundle.Dyld.classPath(b));
                return;
            } else if (args[i].startsWith("-")) {
                // An unrecognised flag must not fall through and start the whole desktop:
                // that is indistinguishable from a checking run that prints nothing and
                // never finishes.
                System.out.println("no such command: " + args[i]);
                System.out.println(USAGE);
                return;
            }
        }

        final boolean doShot = screenshot;
        final String shotPath = screenshotPath;
        final boolean doDump = dumpTree;
        final String startPath = openAtStart;
        final boolean doSelfTest = selfTest;
        final boolean doProbe = probe;
        final boolean doControls = controls;
        final String appToOpen = openApp;

        // Everything from here to the desktop being on screen takes several seconds, and
        // for most of them nothing used to be said, which from outside is exactly what
        // hanging looks like. Each stage says where it has got to as it starts it: to the
        // terminal if there is one, and to the boot screen, which reads the same lines.
        Progress.speakingAs("loginwindow");

        SwingUtilities.invokeLater(() -> {
            // Named, not just described. A session runs from one volume and opens the
            // programs installed on it, and for a while it said the first and did the
            // second somewhere else: booted onto an empty directory it laid out that
            // directory and then went on using the one at home. Everything looked right,
            // including the picture at the end.
            Progress.say("the volume is " + org.fractalmicro.os.OSPaths.ROOT);
            Progress.say("laying out the volume");
            org.fractalmicro.os.OSPaths.ensure();
            org.fractalmicro.os.FMUserDefaults.migrate();
            org.fractalmicro.bundle.Install.ensureInstalled();
            org.fractalmicro.bundle.Install.registerRunningFramework();
            org.fractalmicro.appkit.FMApplicationMain.install();
            // Notifications from other processes arrive here, because this is the process
            // with a screen to change when a setting somewhere else changes.
            org.fractalmicro.foundation.FMDistributedNotificationCenter.defaultCenter().receive();
            org.fractalmicro.core.Log.trim();
            org.fractalmicro.core.Log.install();
            org.fractalmicro.os.FinderSettings.installDefaults();
            org.fractalmicro.os.InterfaceStyle.installDefaults();
            org.fractalmicro.os.DockSettings.installDefaults();
            org.fractalmicro.theme.BrandMark.install();
            Progress.say("reading the programs");
            org.fractalmicro.bundle.Bundles.install();
            Progress.say("installing the look");
            AquaLaf.install();
            Progress.say("the desktop folder");
            FS.desktopFolder();

            // Checking modes build the desktop offscreen: it is laid out and painted into
            // an image, never shown, so it cannot take over the screen it is tested on.
            boolean offscreen = doShot || doDump || doSelfTest;
            // A window that is never shown cannot be a window of the host system, so the
            // checking modes keep everything inside the one frame. This has to be settled
            // before the desktop is built, because that is when it is read.
            if (offscreen || doProbe) org.fractalmicro.os.InterfaceStyle.forceContained();
            // And a board of its own. The checks copy and paste for real, on a machine
            // whose owner was in the middle of something.
            if (offscreen) org.fractalmicro.appkit.FMPasteboard.useAPrivateBoard();

            // The desktop is a task like anything else: it has a number, it is in this
            // process, and it says so.
            org.fractalmicro.kernel.Tasks.register("org.fractalmicro.finder", "Finder",
                org.fractalmicro.kernel.Task.Kind.SYSTEM, java.util.List.of());
            org.fractalmicro.kernel.Tasks.register("org.fractalmicro.dock", "Dock",
                org.fractalmicro.kernel.Task.Kind.SYSTEM, java.util.List.of());

            Progress.say("building the desktop");
            Desktop desktop = new Desktop();
            // The file manager takes over the desktop: the menus the bar shows when nothing
            // else is in front, and the icons on the back of the screen. Asked for by
            // identifier, like anything else the session starts, so this file names no
            // class inside it and the session image does not link it. Until it answers,
            // the bar has an Apple menu, a Window menu and nothing else, which is what a
            // machine with no file manager installed should look like.
            Progress.say("the menu bar");
            if (!org.fractalmicro.bundle.Bundles.openPart(
                    org.fractalmicro.bundle.LaunchServices.FILE_BROWSER,
                    org.fractalmicro.bundle.LaunchServices.DESKTOP)) {
                org.fractalmicro.core.Log.info("no file manager took the desktop");
            }
            // And the indicators on the right come from their own bundles, loaded by the
            // thing whose job that is. The bar does not know what a clock is.
            org.fractalmicro.windowserver.SystemUIServer.start(desktop.mainMenu());
            // The window server is the desktop: this is the process that owns the screen,
            // so this is the process programs elsewhere send their descriptions to.
            // The window server runs whenever a program might want a window, which
            // includes drawing a picture with a program open in it. Without it an
            // application in its own process has nowhere to draw and the picture comes
            // out as an empty desktop, with nothing having failed.
            if (!offscreen || doSelfTest || appToOpen != null) {
                Progress.say("the window server");
                org.fractalmicro.windowserver.WindowServer.sharedServer().start();
            }
            if (doProbe) {
                // A real, laid-out, showing window that cannot be seen or take the
                // keyboard: fully transparent and not focusable. It exists only long
                // enough to report what the desktop actually did.
                desktop.setFocusableWindowState(false);
                try {
                    desktop.setOpacity(0f);
                } catch (Exception e) {
                    System.out.println("this display cannot make a window invisible; probe skipped");
                    System.exit(2);
                }
                desktop.setVisible(true);
            } else if (offscreen) {
                desktop.addNotify();
                desktop.validate();
            } else {
                Progress.say("opening the screen");
                desktop.setVisible(true);
                desktop.openScreen();
                desktop.focusIcons();
                Runtime.getRuntime().addShutdownHook(new Thread(desktop::closeScreen));
            }
            desktop.setStatus(org.fractalmicro.foundation.FMLocalized.of(
                org.fractalmicro.foundation.FMString.of("desktop.ready")).toString());
            // The screen is up and has something on it. Whatever is watching a boot stops
            // watching here: the disks, the programs and the Trash count arrive afterwards
            // and fill themselves in, and waiting for them would hold a boot screen over a
            // desktop that is already usable.
            Progress.ready();

            Volumes.refresh(() -> {

                org.fractalmicro.bundle.LaunchServices.tellFileBrowser(
                    org.fractalmicro.bundle.LaunchServices.REFRESH);
                if (!offscreen) desktop.focusIcons();
            });
            Apps.refresh(desktop.dock()::rebuild);
            Trash.refresh();
            org.fractalmicro.core.WindowList.start();
            org.fractalmicro.win.TrayHost.start();
            if (!offscreen && !doProbe) {
                // The system's own programs start with the session. They are processes of
                // their own, so this only starts them: whether they are up is answered by
                // whether their names are being served.
                org.fractalmicro.core.Shell.async(() -> {
                    try {
                        org.fractalmicro.mds.Metadata.installJob();
                    } catch (java.io.IOException e) {
                        org.fractalmicro.core.Log.info("no metadata job: " + e.getMessage());
                    }
                    // The jobs themselves are task 1's to load, and it does that before
                    // this process exists. Loading them again here would start a second
                    // copy of anything that was not already running.
                });
            }
            if (!offscreen && !doProbe) org.fractalmicro.core.Shell.async(() -> org.fractalmicro.core.Startup.runAll(false));
            if (!offscreen && !doProbe) org.fractalmicro.windowserver.Shortcuts.installGlobalShortcuts(desktop);

            // A path on its own opens a Finder window; a path with a program opens the
            // document in that program instead.
            if (startPath != null && appToOpen == null) {
                org.fractalmicro.bundle.LaunchServices.openFolder(new java.io.File(startPath));
            }
            // Reached by name, like the checks, because it is one: a window of every
            // control drawn on its own, for looking at. It lives with the file manager and
            // the session does not link that, so naming the class here would be a session
            // that will not start without a file manager installed.
            if (doControls) openByName("org.fractalmicro.ui.ControlGallery");
            if (appToOpen != null) {
                // A program named alongside a document opens that document.
                java.util.List<java.io.File> documents = startPath == null
                    ? java.util.List.of() : java.util.List.of(new java.io.File(startPath));
                org.fractalmicro.bundle.Bundle bundle = org.fractalmicro.bundle.Bundles.byIdentifier(appToOpen);
                if (bundle == null || !org.fractalmicro.bundle.Bundles.open(bundle, documents)) {
                    org.fractalmicro.core.Log.info("no application with the identifier " + appToOpen);
                }
            }

            // Only the things that go stale: the volumes and the Trash. Starting the
            // window list or claiming the hotkeys again every half minute would undo
            // work that is already done.
            new Timer(30000, e -> {
                Volumes.refresh(null);
                Trash.refresh();
            }).start();

            if (doProbe) {
                Timer t = new Timer(3000, e -> {
                    // Click the menu items the way a person would, in a window that is
                    // on screen, and report what came of it.
                    for (String wanted : new String[]{
                            org.fractalmicro.foundation.FMLocalized.of(
                                org.fractalmicro.foundation.FMString.of("about.thisComputer"))
                                .toString(),
                            org.fractalmicro.foundation.FMLocalized.of(
                                org.fractalmicro.foundation.FMString.of("about.finder"))
                                .toString()}) {
                        javax.swing.JMenuItem item = findMenuItem(desktop.mainMenu(), wanted);
                        if (item == null) {
                            System.out.println("menu item missing: " + wanted);
                            continue;
                        }
                        try {
                            item.doClick();
                        } catch (Throwable t2) {
                            System.out.println("clicking " + wanted + " threw " + t2);
                            org.fractalmicro.core.Log.error("clicking " + wanted, t2);
                        }
                        boolean opened = desktop.windows().stream()
                            .anyMatch(f -> wanted.equals(f.getTitle()));
                        System.out.println(wanted + " opened: " + opened);
                    }

                    javax.swing.JList<?> icons = (javax.swing.JList<?>) desktop.icons();
                    java.awt.Rectangle cell = icons.getModel().getSize() > 0
                        ? icons.getCellBounds(0, 0) : null;
                    System.out.println("showing=" + icons.isShowing()
                        + " model=" + icons.getModel().getSize()
                        + " bounds=" + icons.getBounds()
                        + " firstCell=" + cell
                        + " visibleRowCount=" + icons.getVisibleRowCount()
                        + " preferred=" + icons.getPreferredSize()
                        + " frameShowing=" + desktop.isShowing());
                    desktop.dispose();
                    System.exit(0);
                });
                t.setRepeats(false);
                t.start();
            }
            if (doSelfTest) {
                Timer t = new Timer(2500, e -> {
                    Thread runner = new Thread(() -> {
                        runChecks(desktop);
                        if (!doShot) System.exit(0);
                    }, "selftest");
                    runner.setDaemon(true);
                    runner.start();
                });
                t.setRepeats(false);
                t.start();
            }
            if (doDump) {
                Timer t = new Timer(2500, e -> {
                    org.fractalmicro.a11y.AccessibilityDump.dump(desktop, System.out);
                    if (!doShot) System.exit(0);
                });
                t.setRepeats(false);
                t.start();
            }
            if (doShot) {
                // Give the self-test time to finish opening things before the picture.
                Timer t = new Timer(doSelfTest ? 20000 : 3000, e -> {
                    desktop.validate();
                    org.fractalmicro.a11y.Screenshot.capture(desktop, shotPath);
                    System.exit(0);
                });
                t.setRepeats(false);
                t.start();
            }
        });
    }
}
