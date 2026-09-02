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

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.ui.*;
import org.fractalmicro.windowserver.AboutWindow;
import org.fractalmicro.windowserver.ForceQuitWindow;
import org.fractalmicro.windowserver.HelpWindow;
import org.fractalmicro.windowserver.Spotlight;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens everything once and reports what broke. Run with --selftest; it exercises the
 * windows, the four views and the menu commands that do not need a person, then counts
 * the focusable controls that have no accessible name.
 */
public final class SelfTest {
    private SelfTest() {}

    private static final List<String> failures = new ArrayList<>();
    private static int steps;

    public static void run(Desktop desktop) {
        // A checking run must give the settings back exactly as it found them.
        String viewStyle = org.fractalmicro.os.FinderSettings.preferredViewStyle().toString();
        boolean hadSidebar = org.fractalmicro.os.FinderSettings.showSidebar();
        boolean hadStatusBar = org.fractalmicro.os.FinderSettings.showStatusBar();
        boolean hadPathBar = org.fractalmicro.os.FinderSettings.showPathBar();
        boolean hadToolbar = org.fractalmicro.os.FinderSettings.showToolbar();
        try {
            runChecks(desktop);
        } finally {
            org.fractalmicro.os.FinderSettings.setPreferredViewStyle(org.fractalmicro.foundation.FMString.of(viewStyle));
            org.fractalmicro.os.FinderSettings.setShowSidebar(hadSidebar);
            org.fractalmicro.os.FinderSettings.setShowStatusBar(hadStatusBar);
            org.fractalmicro.os.FinderSettings.setShowPathBar(hadPathBar);
            org.fractalmicro.os.FinderSettings.setShowToolbar(hadToolbar);
        }
    }

    /**
     * The icons on the desktop, which are the file manager's view and not the screen's.
     *
     * The screen holds a control and does not know what kind it is, which is the point:
     * it is put there by whatever draws the desktop. The checks are allowed to know, and
     * this is the one place they say so.
     */
    private static DesktopIcons desktopIcons(Desktop desktop) {
        return (DesktopIcons) desktop.icons();
    }

    private static void runChecks(Desktop desktop) {
        step("new Finder window", () -> Finder.newWindow(FS.home()));
        step("icon view", () -> Finder.frontWindow().setViewMode("Icon"));
        step("list view", () -> Finder.frontWindow().setViewMode("List"));
        step("column view", () -> Finder.frontWindow().setViewMode("Column"));
        step("cover flow view", () -> Finder.frontWindow().setViewMode("Cover Flow"));
        step("arrange by size", () -> Finder.frontWindow().arrangeBy("Size"));
        step("select all", () -> Finder.frontWindow().selectAll());
        step("applications", Finder::goToApplications);
        step("utilities", Finder::goToUtilities);
        step("computer", () -> Finder.frontWindow().showComputer());
        step("network", () -> Finder.frontWindow().showNetwork());
        step("trash window", Finder::openTrash);
        step("go back", () -> Finder.frontWindow().goBack());
        step("enclosing folder", () -> Finder.newWindow(FS.documents()).goUp());
        step("toggle status bar", () -> Finder.frontWindow().toggleChrome("statusbar"));
        step("toggle sidebar", () -> Finder.frontWindow().toggleChrome("sidebar"));
        step("get info on a volume", () -> Finder.getInfo(Volumes.startupDisk()));
        step("get info on the desktop folder", () -> Finder.getInfo(FS.node(FS.desktopFolder())));
        step("quick look", () -> QuickLook.show(FS.node(FS.desktopFolder())));
        // System Preferences is a process of its own now, so what is checked here is the
        // window it asks for rather than a window this process can open. Whether it starts
        // is a matter for the checks that start programs.
        step("system preferences ships a window to open", () -> {
            java.io.File at = new java.io.File(
                "apps/SystemPreferences/resources/SystemPreferences.xib");
            if (!at.isFile()) return;
            try {
                org.fractalmicro.nib.Nib described = org.fractalmicro.nib.Xib.read(
                    org.fractalmicro.foundation.FMURL.of(at));
                if (described.controls().count() < 10) {
                    throw new IllegalStateException("too few controls: "
                                                    + described.controls().count());
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e.getMessage());
            }
        });
        step("view options", ViewOptionsWindow::open);
        step("about this computer", AboutWindow::showAboutComputer);
        step("about finder", AboutWindow::showAboutFinder);
        step("force quit", ForceQuitWindow::open);
        step("help", HelpWindow::openHelp);
        step("keyboard shortcuts", HelpWindow::showShortcuts);
        step("controls", ControlGallery::open);
        step("spotlight", Spotlight::open);
        step("desktop refresh", Finder::refreshAll);
        step("cycle windows", () -> desktop.cycleWindows(true));
        step("window menu rebuild", () -> desktop.mainMenu().windowsChanged());


        // The desktop checkboxes: does ticking one actually put icons on the desktop?
        checkDesktopPreferences(desktop);
        checkKinds();
        checkSpokenDescriptions(desktop);
        checkVolumeNames();
        checkWindowFocus(desktop);
        checkEmptyDriveHidden(desktop);
        checkVersion();
        checkRunningApplications(desktop);
        checkApplicationMenus(desktop);
        checkGlobalShortcuts();
        checkSessionAndStartup();
        steps += 7;
        int trayFailures = TrayTest.run(System.out);
        if (trayFailures > 0) failures.add(trayFailures + " notification area checks failed");

        steps += WordingTest.count();
        int wordingProblems = WordingTest.run(desktop, System.out);
        if (wordingProblems > 0) failures.add(wordingProblems + " wording checks failed");

        steps += TextSystemTest.count();
        int textFailures = TextSystemTest.run(desktop, System.out);
        if (textFailures > 0) failures.add(textFailures + " text system checks failed");

        steps += LanguageTest.count();
        int languageFailures = LanguageTest.run(System.out);
        if (languageFailures > 0) failures.add(languageFailures + " spelling and detector checks failed");

        steps += LicenseTest.count();
        int licenceFailures = LicenseTest.run(System.out);
        if (licenceFailures > 0) failures.add(licenceFailures + " licence checks failed");

        steps += BootTest.count();
        int bootFailures = BootTest.run(System.out);
        if (bootFailures > 0) failures.add(bootFailures + " start-up checks failed");

        steps += MenuBridgeTest.count();
        int menuFailures = MenuBridgeTest.run(desktop, System.out);
        if (menuFailures > 0) failures.add(menuFailures + " menu bridge checks failed");

        steps += WindowServerTest.count();
        int windowFailures = WindowServerTest.run(desktop, System.out);
        if (windowFailures > 0) failures.add(windowFailures + " window server checks failed");

        steps += BrowserTest.count();
        int browserFailures = BrowserTest.run(desktop, System.out);
        if (browserFailures > 0) failures.add(browserFailures + " file browser checks failed");

        steps += LayoutDescriptionTest.count();
        int shapeFailures = LayoutDescriptionTest.run(desktop, System.out);
        if (shapeFailures > 0) failures.add(shapeFailures + " described layout checks failed");

        steps += TaskTest.count();
        int taskFailures = TaskTest.run(System.out);
        if (taskFailures > 0) failures.add(taskFailures + " task checks failed");

        steps += ProcessTest.count();
        int processFailures = ProcessTest.run(System.out);
        if (processFailures > 0) failures.add(processFailures + " process checks failed");

        steps += HostileMessageTest.count();
        int hostileFailures = HostileMessageTest.run(System.out);
        if (hostileFailures > 0) failures.add(hostileFailures + " hostile message checks failed");

        steps += DyldTest.count();
        int dyldFailures = DyldTest.run(System.out);
        if (dyldFailures > 0) failures.add(dyldFailures + " loader checks failed");

        steps += LayerTest.count();
        int layerFailures = LayerTest.run(System.out);
        if (layerFailures > 0) failures.add(layerFailures + " layering checks failed");

        steps += VocabularyTest.count();
        int vocabularyFailures = VocabularyTest.run(System.out);
        if (vocabularyFailures > 0) failures.add(vocabularyFailures + " vocabulary checks failed");

        steps += DocumentTest.count();
        int documentFailures = DocumentTest.run(desktop, System.out);
        if (documentFailures > 0) failures.add(documentFailures + " document checks failed");

        steps += UndoTest.count();
        int undoFailures = UndoTest.run(System.out);
        if (undoFailures > 0) failures.add(undoFailures + " undo checks failed");

        steps += PlatformTest.count();
        int platformFailures = PlatformTest.run(System.out);
        if (platformFailures > 0) failures.add(platformFailures + " platform checks failed");

        steps += MenuExtraTest.count();
        int extraFailures = MenuExtraTest.run(desktop, System.out);
        if (extraFailures > 0) failures.add(extraFailures + " menu bar checks failed");

        steps += LayoutTest.count();
        int layoutFailures = LayoutTest.run(System.out);
        if (layoutFailures > 0) failures.add(layoutFailures + " layout checks failed");

        steps += XibTest.count();
        int xibFailures = XibTest.run(System.out);
        if (xibFailures > 0) failures.add(xibFailures + " interface checks failed");

        steps += ImageTest.count();
        int imageFailures = ImageTest.run(System.out);
        if (imageFailures > 0) failures.add(imageFailures + " system image checks failed");

        steps += LocalizationTest.count();
        int wordFailures = LocalizationTest.run(System.out);
        if (wordFailures > 0) failures.add(wordFailures + " localization checks failed");

        steps += PanelTest.count();
        int panelFailures = PanelTest.run(System.out);
        if (panelFailures > 0) failures.add(panelFailures + " save panel checks failed");

        steps += ProcessNamespaceTest.count();
        int namespaceFailures = ProcessNamespaceTest.run(System.out);
        if (namespaceFailures > 0) {
            failures.add(namespaceFailures + " process table checks failed");
        }

        steps += ValueTest.count();
        int valueFailures = ValueTest.run(System.out);
        if (valueFailures > 0) failures.add(valueFailures + " value checks failed");

        steps += LinkingTest.count();
        int linkFailures = LinkingTest.run(System.out);
        if (linkFailures > 0) failures.add(linkFailures + " linking checks failed");

        steps += SeparateWindowsTest.count();
        int separateFailures = SeparateWindowsTest.run(System.out);
        if (separateFailures > 0) failures.add(separateFailures + " window style checks failed");

        steps += ScreenTest.count();
        int screenFailures = ScreenTest.run(System.out);
        if (screenFailures > 0) failures.add(screenFailures + " screen checks failed");

        steps += AliasTest.count();
        int aliasFailures = AliasTest.run(System.out);
        if (aliasFailures > 0) failures.add(aliasFailures + " alias and label checks failed");

        steps += SizeTest.count();
        int sizeFailures = SizeTest.run(desktop, System.out);
        if (sizeFailures > 0) failures.add(sizeFailures + " size checks failed");

        steps += VerbosityTest.count();
        int verbosityFailures = VerbosityTest.run(desktop, System.out);
        if (verbosityFailures > 0) failures.add(verbosityFailures + " verbosity checks failed");

        steps += SpeechTest.count();
        int speechFailures = SpeechTest.run(desktop, System.out);
        if (speechFailures > 0) failures.add(speechFailures + " announcement checks failed");

        steps += SidebarTest.count();
        int sidebarFailures = SidebarTest.run(desktop, System.out);
        if (sidebarFailures > 0) failures.add(sidebarFailures + " sidebar checks failed");

        steps += AlertTest.count();
        int alertFailures = AlertTest.run(System.out);
        if (alertFailures > 0) failures.add(alertFailures + " alert checks failed");

        steps += SheetTest.count();
        int sheetFailures = SheetTest.run(System.out);
        if (sheetFailures > 0) failures.add(sheetFailures + " sheet checks failed");

        steps += ThemeTest.count();
        int themeFailures = ThemeTest.run(System.out);
        if (themeFailures > 0) failures.add(themeFailures + " control checks failed");

        steps += TextEditTest.count();
        int editorFailures = TextEditTest.run(System.out);
        if (editorFailures > 0) failures.add(editorFailures + " editor checks failed");

        steps += MachOTest.count();
        int programFailures = MachOTest.run(System.out);
        if (programFailures > 0) failures.add(programFailures + " program checks failed");

        steps += BundleTest.count();
        int bundleFailures = BundleTest.run(System.out);
        if (bundleFailures > 0) failures.add(bundleFailures + " bundle checks failed");

        steps += StringTest.count();
        int wordingFailures = StringTest.run(System.out);
        if (wordingFailures > 0) failures.add(wordingFailures + " wording or type checks failed");

        System.out.println();
        System.out.println("checked " + steps + " actions, " + failures.size() + " failed");
        for (String f : failures) System.out.println("  FAILED " + f);

        System.out.println();
        System.out.println("log file: " + org.fractalmicro.core.Log.file());
        System.out.println("desktop icons: " + desktopIcons(desktop).getModel().getSize());
        System.out.println();
        System.out.println("keyboard check:");
        try {
            SwingUtilities.invokeAndWait(() -> FocusTest.run(desktop, System.out));
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.out.println("FAIL  keyboard check: " + cause);
            cause.printStackTrace(System.out);
        }

        System.out.println();
        System.out.println("accessibility check:");
        int unnamed = AccessibilityDump.countUnnamedFocusables(desktop, System.out);
        System.out.println("focusable controls with no name: " + unnamed);
    }

    /** Kinds: the words Get Info shows and a screen reader reads. */
    private static void checkKinds() {
        System.out.println();
        System.out.println("kinds:");
        String[][] expected = {
            {"thing.notanext", "document"},
            {"letter.docx", "Microsoft Word document"},
            {"settings.plist", "property list"},
            {"photo.png", "Portable Network Graphics image"},
            {"notes.txt", "plain text document"},
            {"tool.exe", "application"},
            {"music.m4a", "MPEG-4 audio"},
            {"archive.zip", "ZIP archive"},
            {"page.pdf", "Portable Document Format (PDF)"},
            {"widget.bundle", "bundle"},
            {"noextension", "document"},
        };
        for (String[] row : expected) {
            String actual = org.fractalmicro.fs.Kinds.ofFile(new java.io.File(row[0]));
            System.out.println((actual.equals(row[1]) ? "ok    " : "FAIL  ")
                + row[0] + " is a " + actual);
            steps++;
            if (!actual.equals(row[1])) {
                failures.add(row[0] + " read as \"" + actual + "\", wanted \"" + row[1] + "\"");
            }
        }
        step("an application is an application", () -> {
            org.fractalmicro.fs.Node app = new org.fractalmicro.fs.Node(org.fractalmicro.fs.Node.Kind.APPLICATION,
                "Some App", new java.io.File("Some App.lnk"));
            String kind = org.fractalmicro.fs.Kinds.of(app);
            if (!"application".equals(kind)) throw new IllegalStateException(kind);
        });
        step("the company mark is installed", () -> {
            org.fractalmicro.theme.BrandMark.install();
            if (!org.fractalmicro.theme.BrandMark.available()) {
                throw new IllegalStateException("no artwork at " + org.fractalmicro.theme.BrandMark.file());
            }
        });
        step("a folder is a folder", () -> {
            String kind = org.fractalmicro.fs.Kinds.of(org.fractalmicro.fs.FS.node(org.fractalmicro.fs.FS.home()));
            if (!"folder".equals(kind)) throw new IllegalStateException(kind);
        });
        step("a volume is a volume", () -> {
            String kind = org.fractalmicro.fs.Kinds.of(org.fractalmicro.fs.Volumes.startupDisk());
            if (!"volume".equals(kind)) throw new IllegalStateException(kind);
        });
    }

    /** What the renderers hand a screen reader for a selected item. */
    private static void checkSpokenDescriptions(Desktop desktop) {
        System.out.println();
        System.out.println("spoken descriptions:");
        javax.swing.JList<org.fractalmicro.fs.Node> icons = desktopIcons(desktop);
        if (icons.getModel().getSize() > 0) {
            String description = descriptionFrom(icons, icons.getModel().getElementAt(0));
            System.out.println("      desktop item: " + description);
            steps++;
            // "selected icon" on the desktop, and nothing else except the label, which
            // is information a sighted person gets from the colour and a screen reader
            // has to be told.
            boolean asExpected = "selected icon".equals(description)
                || description.matches("selected icon, \\w+ label");
            if (!asExpected) {
                failures.add("desktop item described as " + description);
                System.out.println("FAIL  a desktop item is a selected icon");
            } else {
                System.out.println("ok    a desktop item is a selected icon");
            }
        }
        org.fractalmicro.fs.Node word = new org.fractalmicro.fs.Node(org.fractalmicro.fs.Node.Kind.FILE, "letter",
            new java.io.File("letter.docx"));
        javax.swing.JList<org.fractalmicro.fs.Node> sample = new javax.swing.JList<>();
        sample.setFixedCellWidth(100);
        IconCellRendererProbe probe = new IconCellRendererProbe();
        String spoken = probe.describe(sample, word);
        System.out.println("      icon view item: " + spoken);
        steps++;
        if (!"selected Microsoft Word document".equals(spoken)) {
            failures.add("icon view item described as " + spoken);
            System.out.println("FAIL  an icon view item names its type");
        } else {
            System.out.println("ok    an icon view item names its type");
        }
    }

    private static String descriptionFrom(javax.swing.JList<org.fractalmicro.fs.Node> list, org.fractalmicro.fs.Node node) {
        java.awt.Component c = list.getCellRenderer()
            .getListCellRendererComponent(list, node, 0, true, true);
        return ((javax.accessibility.Accessible) c).getAccessibleContext().getAccessibleDescription();
    }


    /**
     * The menu bar belongs to the front program. Opening a document should put
     * TextEdit's own menus in it, and closing the document should hand the bar back to
     * Finder; if it does not, every command in the bar afterwards is the wrong one.
     */
    private static void checkApplicationMenus(Desktop desktop) {
        System.out.println();
        System.out.println("the front program owns the menu bar:");
        org.fractalmicro.windowserver.MainMenu bar = desktop.mainMenu();
        String before = bar.currentApplication();

        // A program with a window in front, which is now the only kind there is: every
        // application runs in a process of its own and hands over a description. What is
        // being checked is the handover of the bar, so the description is a small one made
        // here rather than a real program started for the purpose.
        org.fractalmicro.windowserver.WindowServer server = org.fractalmicro.windowserver.WindowServer.sharedServer();
        server.start();

        try (org.fractalmicro.appkit.FMApplication app =
                 org.fractalmicro.appkit.FMApplication.named(org.fractalmicro.foundation.FMString.of("Editor"))) {
            boolean opened = app.showWindow(new org.fractalmicro.nib.Nib.Builder()
                .title(org.fractalmicro.foundation.FMString.of("A Document"))
                .size(320, 200).resizable(true)
                .add(org.fractalmicro.nib.Nib.ControlClass.FMRichText,
                     org.fractalmicro.foundation.FMString.of("body"),
                     org.fractalmicro.foundation.FMString.of("Document"),
                     org.fractalmicro.foundation.FMString.EMPTY, 0, 0, 320, 200)
                .menu(org.fractalmicro.foundation.FMString.of("Format"),
                      org.fractalmicro.nib.Nib.MenuItem.of(org.fractalmicro.foundation.FMString.of("Bold"),
                          org.fractalmicro.foundation.FMString.of("bold"),
                          org.fractalmicro.foundation.FMString.of("b"),
                          org.fractalmicro.foundation.FMString.of("command")))
                .build());
            drain();

            steps++;
            if (!opened) {
                failures.add("the program's window would not open: " + app.lastError());
                System.out.println("FAIL  a program in another process opens a window");
            } else {
                System.out.println("ok    a program in another process opens a window");
            }

            steps++;
            if (!"Editor".equals(bar.currentApplication())) {
                failures.add("the menu bar still says " + bar.currentApplication()
                             + " with the program's window in front");
                System.out.println("FAIL  the bar names the front program");
            } else {
                System.out.println("ok    the bar names the front program");
            }

            steps++;
            java.util.List<String> names = new java.util.ArrayList<>();
            for (int i = 0; i < bar.getMenuCount(); i++) {
                javax.swing.JMenu m = bar.getMenu(i);
                if (m != null && m.getText() != null && !m.getText().isEmpty()) {
                    names.add(m.getText());
                }
            }
            System.out.println("      menus: " + String.join(", ", names));
            if (!names.contains("Editor") || !names.contains("Format")
                || !names.contains("Window") || names.contains("Go")) {
                failures.add("the bar holds the wrong menus for the program: " + names);
                System.out.println("FAIL  the bar holds the program's menus");
            } else {
                System.out.println("ok    the bar holds the program's menus");
            }

            // The editing commands go to the view, which is the whole arrangement.
            steps++;
            app.setValue(org.fractalmicro.foundation.FMString.of("body"),
                         org.fractalmicro.foundation.FMString.of("some words"));
            drain();
            boolean chose = app.select(org.fractalmicro.foundation.FMString.of("body"), 0, 4);
            drain();
            org.fractalmicro.appkit.FMApplication.Selection chosen =
                app.selectionIn(org.fractalmicro.foundation.FMString.of("body"));
            if (!chose || !chosen.text().sameAs(org.fractalmicro.foundation.FMString.of("some"))) {
                failures.add("choosing a stretch of text across the boundary did not work: "
                             + chosen.text());
                System.out.println("FAIL  a program can choose a stretch of text");
            } else {
                System.out.println("ok    a program can choose a stretch of text");
            }

            steps++;
            boolean bolded = app.perform(org.fractalmicro.foundation.FMString.of("body"),
                                         org.fractalmicro.foundation.FMString.of("font-bold"));
            drain();
            if (!bolded) {
                failures.add("the view would not take an editing command: " + app.lastError());
                System.out.println("FAIL  an editing command reaches the view");
            } else {
                System.out.println("ok    an editing command reaches the view");
            }

            app.hideWindow();
            drain();
        } catch (Exception e) {
            failures.add("the menu bar checks ran: " + e);
            System.out.println("FAIL  the menu bar checks ran: " + e);
        }

        steps++;
        if (!"Finder".equals(bar.currentApplication())) {
            failures.add("the menu bar stayed with " + bar.currentApplication()
                         + " after the window closed");
            System.out.println("FAIL  the bar goes back to Finder");
        } else {
            System.out.println("ok    the bar goes back to Finder");
        }

        steps++;
        boolean restored = false;
        for (int i = 0; i < bar.getMenuCount(); i++) {
            javax.swing.JMenu m = bar.getMenu(i);
            if (m != null && "Go".equals(m.getText())) restored = true;
        }
        if (!restored) {
            failures.add("Finder's Go menu did not come back");
            System.out.println("FAIL  Finder's own menus come back");
        } else {
            System.out.println("ok    Finder's own menus come back");
        }

        if (!before.equals(bar.currentApplication())) bar.showFinderMenus();
    }

    /** Reaches the icon view's renderer without opening a window. */
    private static final class IconCellRendererProbe {
        String describe(javax.swing.JList<org.fractalmicro.fs.Node> list, org.fractalmicro.fs.Node node) {
            org.fractalmicro.ui.IconCellRenderer renderer = new org.fractalmicro.ui.IconCellRenderer(false, 64);
            java.awt.Component c = renderer.getListCellRendererComponent(list, node, 0, true, true);
            return ((javax.accessibility.Accessible) c).getAccessibleContext().getAccessibleDescription();
        }
    }

    /** Volume names: labels, not drive letters. */
    private static void checkVolumeNames() {
        System.out.println();
        System.out.println("volume names:");
        for (org.fractalmicro.fs.Node v : org.fractalmicro.fs.Volumes.all()) {
            System.out.println("      " + v.mountPoint + " is called " + v.name);
            steps++;
            boolean bare = v.name.matches("(?i)[a-z]:\\?") || v.name.endsWith(":)");
            if (bare) {
                failures.add("volume named " + v.name);
                System.out.println("FAIL  " + v.name + " is a drive letter, not a label");
            }
        }
    }

    /** The windows of other programs. The Dock is a list of these. */
    private static void checkRunningApplications(Desktop desktop) {
        System.out.println();
        System.out.println("running applications:");
        org.fractalmicro.core.WindowList.refresh();
        drain();
        try {
            Thread.sleep(1200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        drain();

        java.util.List<org.fractalmicro.core.WindowList.App> running =
            org.fractalmicro.core.WindowList.applications();
        System.out.println("      " + running.size() + " programs with windows");
        for (org.fractalmicro.core.WindowList.App app : running) {
            System.out.println("      " + app.name + ": " + app.windows.size()
                + (app.windows.size() == 1 ? " window" : " windows")
                + (app.allMinimized() ? ", all minimized" : ""));
        }
        step("other programs are found", () -> {
            if (running.isEmpty()) throw new IllegalStateException("nothing found");
        });
        step("every running program has a name", () -> {
            for (org.fractalmicro.core.WindowList.App app : running) {
                if (app.name == null || app.name.isBlank()) {
                    throw new IllegalStateException("a program with no name");
                }
            }
        });
        step("this desktop is not in its own Dock", () -> {
            long self = ProcessHandle.current().pid();
            for (org.fractalmicro.core.WindowList.App app : running) {
                if (app.pid == self) throw new IllegalStateException("it lists itself");
            }
        });
        step("the Dock lists what is running", () -> {
            desktop.dock().rebuild();
            drain();
            int tiles = countDockTiles(desktop.dock());
            if (tiles < running.size()) {
                throw new IllegalStateException(tiles + " tiles for " + running.size() + " programs");
            }
        });
    }

    private static int countDockTiles(java.awt.Container dock) {
        int count = 0;
        for (java.awt.Component c : dock.getComponents()) {
            if (c instanceof javax.swing.JButton) count++;
            else if (c instanceof java.awt.Container) count += countDockTiles((java.awt.Container) c);
        }
        return count;
    }

    /**
     * The message window and the system wide shortcuts. They are claimed, checked and
     * given straight back, so a checking run does not sit on Alt Space for a minute.
     */
    private static void checkGlobalShortcuts() {
        System.out.println();
        System.out.println("system wide shortcuts:");
        org.fractalmicro.win.MessageWindow window = org.fractalmicro.win.MessageWindow.sharedWindow();
        step("the message window is running", () -> {
            if (!window.isRunning()) throw new IllegalStateException("no message window");
        });

        int alt = org.fractalmicro.win.HotKeys.MOD_ALT;
        int win = org.fractalmicro.win.HotKeys.MOD_WIN;
        int ctrl = org.fractalmicro.win.HotKeys.MOD_CONTROL;
        int f2 = java.awt.event.KeyEvent.VK_F2;
        int f3 = java.awt.event.KeyEvent.VK_F3;

        org.fractalmicro.win.HotKeys.register(alt, java.awt.event.KeyEvent.VK_SPACE, "Spotlight", () -> { });
        org.fractalmicro.win.HotKeys.registerWithFallback(alt | win, java.awt.event.KeyEvent.VK_M,
                                                 ctrl, f2, "Menu bar", () -> { });
        org.fractalmicro.win.HotKeys.registerWithFallback(alt | win, java.awt.event.KeyEvent.VK_D,
                                                 ctrl, f3, "Dock", () -> { });
        org.fractalmicro.win.HotKeys.register(alt | win, java.awt.event.KeyEvent.VK_ESCAPE,
                                     "Force Quit", () -> { });

        java.util.Set<String> claimed = new java.util.HashSet<>();
        for (org.fractalmicro.win.HotKeys.Registration r : org.fractalmicro.win.HotKeys.registrations()) {
            if (r.claimed) claimed.add(r.name);
            System.out.println("      " + r.name + ": " + describeKey(r));
        }
        for (String name : new String[]{"Spotlight", "Menu bar", "Dock", "Force Quit"}) {
            steps++;
            boolean ok = claimed.contains(name);
            System.out.println((ok ? "ok    " : "FAIL  ") + name + " works from other programs");
            if (!ok) failures.add(name + " could not be claimed system wide");
        }
        org.fractalmicro.win.HotKeys.releaseAll();
        System.out.println("      released again");
    }

    /**
     * Session control and the login items. Nothing here ends anybody's session: the
     * calls are made in dry run, and the login items are listed rather than started.
     */
    private static void checkSessionAndStartup() {
        System.out.println();
        System.out.println("session and start-up:");
        System.out.println("      acting as the shell: " + org.fractalmicro.win.Session.actingAsShell()
            + " (Explorer running: " + org.fractalmicro.win.Session.explorerRunning() + ")");

        step("the shutdown privilege can be taken", () -> {
            if (!org.fractalmicro.win.Session.enableShutdownPrivilege()) {
                throw new IllegalStateException("refused");
            }
        });

        org.fractalmicro.win.Session.setDryRun(true);
        try {
            step("log out is wired up", () -> {
                if (!org.fractalmicro.win.Session.logOut(false)) throw new IllegalStateException("refused");
            });
            step("restart is wired up", () -> {
                if (!org.fractalmicro.win.Session.restart(false)) throw new IllegalStateException("refused");
            });
            step("shut down is wired up", () -> {
                if (!org.fractalmicro.win.Session.shutDown(false)) throw new IllegalStateException("refused");
            });
            step("sleep is wired up", () -> {
                if (!org.fractalmicro.win.Session.sleep()) throw new IllegalStateException("refused");
            });
            step("lock is wired up", () -> {
                if (!org.fractalmicro.win.Session.lock()) throw new IllegalStateException("refused");
            });
        } finally {
            org.fractalmicro.win.Session.setDryRun(false);
        }

        java.util.List<org.fractalmicro.core.Startup.Item> items = org.fractalmicro.core.Startup.items();
        System.out.println("      " + items.size() + " login items");
        for (org.fractalmicro.core.Startup.Item item : items) {
            System.out.println("      " + item.name + "  (" + item.source + ")");
        }
        step("login items are found", () -> {
            if (items.isEmpty()) throw new IllegalStateException("none");
        });
        step("login items are not started while Explorer is the shell", () -> {
            java.util.List<org.fractalmicro.core.Startup.Item> started = org.fractalmicro.core.Startup.runAll(false);
            if (!org.fractalmicro.win.Session.actingAsShell() && !started.isEmpty()) {
                throw new IllegalStateException("it started " + started.size());
            }
        });
        step("a quoted command line splits properly", () -> {
            java.util.List<String> parts = org.fractalmicro.core.Startup.splitCommand(
                '"' + "C:/Program Files/Thing/thing.exe" + '"' + " --quiet -x");
            if (parts.size() != 3 || !parts.get(0).endsWith("thing.exe")) {
                throw new IllegalStateException(String.valueOf(parts));
            }
        });
    }

    private static String describeKey(org.fractalmicro.win.HotKeys.Registration r) {
        StringBuilder sb = new StringBuilder();
        if ((r.modifiers & org.fractalmicro.win.HotKeys.MOD_CONTROL) != 0) sb.append("Ctrl ");
        if ((r.modifiers & org.fractalmicro.win.HotKeys.MOD_WIN) != 0) sb.append("Win ");
        if ((r.modifiers & org.fractalmicro.win.HotKeys.MOD_SHIFT) != 0) sb.append("Shift ");
        if ((r.modifiers & org.fractalmicro.win.HotKeys.MOD_ALT) != 0) sb.append("Alt ");
        return sb + java.awt.event.KeyEvent.getKeyText(r.keyCode);
    }

    /** The version and the build number it was stamped with. */
    private static void checkVersion() {
        System.out.println();
        System.out.println("version:");
        String number = org.fractalmicro.os.Version.number();
        String build = org.fractalmicro.os.Version.build();
        System.out.println("      FractalJDE " + number + " (" + build + ") built "
            + org.fractalmicro.os.Version.builtAt());
        step("the version is a semantic version", () -> {
            if (!number.matches("[0-9]+[.][0-9]+[.][0-9]+")) {
                throw new IllegalStateException(number);
            }
        });
        step("the build number decodes to the time it was built", () -> {
            String when = org.fractalmicro.os.Version.decodeBuild(build);
            if (when.isEmpty()) throw new IllegalStateException("cannot read " + build);
            System.out.println("      build " + build + " is " + when);
        });
    }

    /** An empty optical drive is not a device, on the desktop or in the sidebar. */
    private static void checkEmptyDriveHidden(Desktop desktop) {
        System.out.println();
        System.out.println("empty drives:");
        java.util.List<org.fractalmicro.fs.Node> empty = new java.util.ArrayList<>();
        for (org.fractalmicro.fs.Node v : org.fractalmicro.fs.Volumes.ofKind(org.fractalmicro.fs.Node.Kind.REMOVABLE_MEDIA)) {
            if (!v.isMounted()) empty.add(v);
        }
        if (empty.isEmpty()) {
            System.out.println("      no empty drive to check with");
            return;
        }
        for (org.fractalmicro.fs.Node drive : empty) {
            step(drive.name + " stays off the desktop", () -> {
                javax.swing.ListModel<org.fractalmicro.fs.Node> model = desktopIcons(desktop).getModel();
                for (int i = 0; i < model.getSize(); i++) {
                    if (model.getElementAt(i).name.equals(drive.name)) {
                        throw new IllegalStateException("it is on the desktop");
                    }
                }
            });
            step(drive.name + " stays out of the sidebar", () -> {
                FinderWindow w = Finder.frontWindow();
                if (w == null) w = Finder.newWindow(org.fractalmicro.fs.FS.home());
                if (sidebarMentions(w, drive.name)) {
                    throw new IllegalStateException("it is in the sidebar");
                }
            });
        }
    }

    private static boolean sidebarMentions(java.awt.Container root, String name) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JTree) {
                javax.swing.JTree tree = (javax.swing.JTree) c;
                for (int row = 0; row < tree.getRowCount(); row++) {
                    Object value = tree.getPathForRow(row).getLastPathComponent();
                    if (String.valueOf(value).equals(name)) return true;
                }
            }
            if (c instanceof java.awt.Container && sidebarMentions((java.awt.Container) c, name)) {
                return true;
            }
        }
        return false;
    }

    /** A window that opens has to take the keyboard with it. */
    private static void checkWindowFocus(Desktop desktop) {
        System.out.println();
        System.out.println("window focus:");
        step("a Finder window opens on its files", () -> {
            FinderWindow w = Finder.newWindow(org.fractalmicro.fs.FS.home());
            java.awt.Component target = desktop.focusInto(w);
            if (target == null) throw new IllegalStateException("nothing to focus");
            String name = target instanceof javax.accessibility.Accessible
                ? ((javax.accessibility.Accessible) target).getAccessibleContext().getAccessibleName()
                : null;
            if (name == null || !name.toLowerCase().contains("view")) {
                throw new IllegalStateException("focus went to " + name);
            }
        });
        step("About This Computer opens", AboutWindow::showAboutComputer);
        step("the About window takes the keyboard", () -> {
            javax.swing.JInternalFrame about = null;
            for (javax.swing.JInternalFrame f : desktop.windows()) {
                if ("About This Computer".equals(f.getTitle())) about = f;
            }
            if (about == null) throw new IllegalStateException("no About window");
            java.awt.Component target = desktop.focusInto(about);
            if (target == null) throw new IllegalStateException("nothing to focus");
            for (java.awt.Component p = target; p != null; p = p.getParent()) {
                if (p == about) return;
            }
            throw new IllegalStateException("focus target is outside the window");
        });
    }

    /** Turns each "show on the desktop" preference on and counts what appears. */
    private static void checkDesktopPreferences(Desktop desktop) {
        System.out.println();
        System.out.println("desktop preferences:");
        boolean hardDisks = org.fractalmicro.os.FinderSettings.showHardDisks();
        boolean external = org.fractalmicro.os.FinderSettings.showExternalDisks();
        boolean removable = org.fractalmicro.os.FinderSettings.showRemovableMedia();
        boolean servers = org.fractalmicro.os.FinderSettings.showServers();
        try {
            int off = countWith(desktop, false, false, false, false);
            int onlyDisks = countWith(desktop, true, false, false, false);
            int onlyServers = countWith(desktop, false, false, false, true);
            int all = countWith(desktop, true, true, true, true);
            int volumes = org.fractalmicro.fs.Volumes.all().size();
            System.out.println("      volumes known: " + volumes
                + ", nothing ticked: " + off
                + ", hard disks: " + onlyDisks
                + ", servers: " + onlyServers
                + ", everything: " + all);
            step("hard disks appear when ticked", () -> {
                if (onlyDisks <= off) throw new IllegalStateException("no change");
            });
            step("servers appear when ticked", () -> {
                boolean anyServers = org.fractalmicro.fs.Volumes.ofKind(org.fractalmicro.fs.Node.Kind.SERVER).isEmpty();
                if (!anyServers && onlyServers <= off) throw new IllegalStateException("no change");
            });
            step("everything ticked shows the most", () -> {
                if (all < onlyDisks) throw new IllegalStateException("fewer than with disks alone");
            });
        } finally {
            countWith(desktop, hardDisks, external, removable, servers);
        }
    }

    private static int countWith(Desktop desktop, boolean hard, boolean external,
                                 boolean removable, boolean servers) {
        org.fractalmicro.os.FinderSettings.setShowHardDisks(hard);
        org.fractalmicro.os.FinderSettings.setShowExternalDisks(external);
        org.fractalmicro.os.FinderSettings.setShowRemovableMedia(removable);
        org.fractalmicro.os.FinderSettings.setShowServers(servers);
        desktopIcons(desktop).refresh();
        drain();
        return desktopIcons(desktop).getModel().getSize();
    }

    private static void step(String what, Runnable body) {
        steps++;
        try {
            if (SwingUtilities.isEventDispatchThread()) body.run();
            else SwingUtilities.invokeAndWait(body);
            // Let anything queued on the event thread settle before the next step.
            drain();
            System.out.println("ok    " + what);
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            failures.add(what + ": " + cause);
            System.out.println("FAIL  " + what + ": " + cause);
        }
    }

    private static void drain() {
        if (SwingUtilities.isEventDispatchThread()) return;
        try {
            for (int i = 0; i < 3; i++) {
                SwingUtilities.invokeAndWait(() -> { });
            }
        } catch (Exception ignored) { }
    }
}
