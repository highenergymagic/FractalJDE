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

import org.fractalmicro.fs.Apps;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Trash;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.theme.AquaLaf;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.ui.Finder;

import javax.swing.*;

/**
 * Start-up. Installs the look, puts the desktop on screen, then fills in the disks,
 * the applications list and the Trash count in the background so nothing blocks.
 */
public final class Main {

    /**
     * Runs the checks, which are not part of what ships.
     *
     * A built system has no checking code in it: the applications and the framework are
     * what a person installs, and the checks are a separate thing built beside them. So
     * they are reached by name rather than linked, and a build without them says so
     * instead of failing to start.
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

    /** What this understands, for when it is given something it does not. */
    private static final String USAGE = String.join(System.lineSeparator(),
        "commands:",
        "  --selftest              open everything once and report what broke",
        "  --dump-accessibility    print the tree a screen reader sees",
        "  --screenshot <file>     draw the desktop into a picture without showing it",
        "  --open <path>           open a window on a folder at start",
        "  --open-app <id>         open a program by its bundle identifier",
        "  --controls              draw the controls on their own, for looking at",
        "  --install               put this build in place as the framework",
        "  --tasks                 list what is running",
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
                System.out.print(org.fractalmicro.kernel.TaskServer.describe());
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

        SwingUtilities.invokeLater(() -> {
            org.fractalmicro.os.OSPaths.ensure();
            org.fractalmicro.os.Defaults.migrate();
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
            org.fractalmicro.bundle.Bundles.install();
            AquaLaf.install();
            FS.desktopFolder();

            // Checking modes build the desktop offscreen: it is laid out and painted into
            // an image, never shown, so it cannot take over the screen it is tested on.
            boolean offscreen = doShot || doDump || doSelfTest;
            // A window that is never shown cannot be a window of the host system, so the
            // checking modes keep everything inside the one frame. This has to be settled
            // before the desktop is built, because that is when it is read.
            if (offscreen || doProbe) org.fractalmicro.os.InterfaceStyle.forceContained();

            // The desktop is a task like anything else: it has a number, it is in this
            // process, and it says so.
            org.fractalmicro.kernel.Tasks.register("org.fractalmicro.finder", "Finder",
                org.fractalmicro.kernel.Task.Kind.SYSTEM, java.util.List.of());
            org.fractalmicro.kernel.Tasks.register("org.fractalmicro.dock", "Dock",
                org.fractalmicro.kernel.Task.Kind.SYSTEM, java.util.List.of());

            Desktop desktop = new Desktop();
            // The Finder hands the bar its menus, the same way any other program does.
            // Until it does, the bar has an Apple menu, a Window menu and nothing else.
            org.fractalmicro.ui.FinderMenus.install(desktop);
            // And the indicators on the right come from their own bundles, loaded by the
            // thing whose job that is. The bar does not know what a clock is.
            org.fractalmicro.windowserver.SystemUIServer.start(desktop.mainMenu());
            // The window server is the desktop: this is the process that owns the screen,
            // so this is the process programs elsewhere send their descriptions to.
            if (!offscreen || doSelfTest) org.fractalmicro.windowserver.WindowServer.get().start();
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
                desktop.setVisible(true);
                desktop.openScreen();
                desktop.icons().requestFocusInWindow();
                Runtime.getRuntime().addShutdownHook(new Thread(desktop::closeScreen));
            }
            desktop.setStatus("Desktop ready");

            Volumes.refresh(() -> {
                desktop.icons().refresh();
                Finder.refreshAll();
                if (!offscreen) desktop.icons().requestFocusInWindow();
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
                Finder.newWindow(new java.io.File(startPath));
            }
            if (doControls) org.fractalmicro.ui.ControlGallery.open();
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
                    for (String wanted : new String[]{"About This Computer", "About Finder"}) {
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

                    javax.swing.JList<?> icons = desktop.icons();
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
