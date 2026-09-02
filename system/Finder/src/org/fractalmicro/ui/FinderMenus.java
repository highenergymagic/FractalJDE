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
package org.fractalmicro.ui;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.appkit.Alert;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.nib.NibLoader;
import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.windowserver.AboutWindow;

import org.fractalmicro.windowserver.Spotlight;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.MainMenu;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The Finder's own menus.
 *
 * These used to be built by the menu bar, which meant the bar knew what a folder was, what
 * Empty Trash meant, and how to arrange icons by kind. A bar that knows those things is not
 * a bar, it is the Finder with some other programs allowed to visit.
 *
 * They are no longer built here either. What is in the bar is in FinderMenus.xib, beside
 * the Finder in its own bundle: the menus, their order, what each command is called, and
 * the keys that do it without opening a menu at all. This file says what each command
 * does, and nothing else.
 *
 * That division is the point of an interface file. A command is matched by the name it
 * sends, so the file can be rearranged, translated into a language nobody here reads, or
 * have an item moved from one menu to another, and none of the code below changes. The
 * reverse holds too: nothing here can quietly grow a menu item that no translator was
 * ever shown.
 *
 * Two menus are still built rather than described, because their contents are not known
 * until the machine is running: the labels a person has used, and the folders they were
 * last in. A file cannot say what those are.
 */
public final class FinderMenus implements NibLoader.Commands {

    private static final String SYSTEM_PREFERENCES = "org.fractalmicro.systempreferences";

    /** The interface file the bar is built from, inside the Finder's own bundle. */
    private static final FMString INTERFACE = FMString.of("FinderMenus");

    private final Desktop desktop;
    private NibLoader loaded;

    private FinderMenus(Desktop desktop) {
        this.desktop = desktop;
    }

    /**
     * Reads the Finder's menus and gives them to the bar as the program in front by
     * default, so they are what shows when no other program owns it.
     *
     * A bar with nothing in it is what happens when the file cannot be read, and it is
     * worth saying so plainly: every command in the desktop is in that file, and a Finder
     * whose menus are empty is not a Finder that is nearly working.
     */
    public static FinderMenus install(Desktop desktop) {
        FinderMenus menus = new FinderMenus(desktop);
        // The icons on the desktop, which are a view of a folder and so are the file
        // manager's rather than the screen's. The screen keeps somewhere to put them and
        // is told what goes there, the same way it is told which menus to show.
        desktop.setIcons(new DesktopIcons());
        // The Finder runs inside the process the session started, so it is not that
        // process's main bundle and its words are not found without it saying so.
        FMLocalized.searchAlso(
            OSPaths.coreServices().resolve("Finder.app/Contents/Resources"));
        try {
            menus.loaded = NibLoader.inBundle(
                OSPaths.coreServices().resolve("Finder.app"), INTERFACE);
        } catch (IOException notThere) {
            org.fractalmicro.core.Log.info("the Finder's menus could not be read: "
                                           + notThere.getMessage());
            return menus;
        }

        List<JMenu> built = menus.loaded.menus(menus);
        menus.addLiveMenus(built);
        desktop.mainMenu().setDefaultApplication("Finder", built);
        menus.followTheSelection(built);
        return menus;
    }

    /**
     * The two menus whose contents are not in the file.
     *
     * A label menu shows the labels, and a recent folders menu shows the folders somebody
     * was last in. Neither is a list a description could hold: it would be a list of what
     * happened to be true on the machine the file was written on.
     */
    private void addLiveMenus(List<JMenu> built) {
        for (JMenu menu : built) {
            if ("File".equals(menu.getName()) || isNamed(menu, "makeAlias")) {
                menu.add(Finder.labelMenu(this::selection), indexAfter(menu, "makeAlias"));
            }
            if (isNamed(menu, "goToFolder")) {
                menu.add(desktop.mainMenu().recentFoldersMenu(),
                         indexAfter(menu, "goDownloads"));
            }
        }
    }

    private boolean isNamed(JMenu menu, String action) {
        JMenuItem item = loaded.item(FMString.of(action));
        if (item == null) return false;
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (menu.getItem(i) == item) return true;
        }
        return false;
    }

    private int indexAfter(JMenu menu, String action) {
        JMenuItem item = loaded.item(FMString.of(action));
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (menu.getItem(i) == item) return i + 1;
        }
        return menu.getItemCount();
    }

    /**
     * The handful of items that read differently depending on what is in front.
     *
     * Quick Look names the file it would show, Compress says how many things it would
     * compress, and the four that hide something say Show once it is hidden. All of them
     * are written down with the words they have when nothing is selected, and what happens
     * here is only ever a replacement of one of those, in the language it was read in.
     */
    private void followTheSelection(List<JMenu> built) {
        for (JMenu menu : built) {
            menu.addMenuListener(new javax.swing.event.MenuListener() {
                @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                    nameTheSelection();
                    sayShowOrHide();
                }
                @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
                @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
            });
        }
    }

    private void nameTheSelection() {
        JMenuItem quickLook = loaded.item(FMString.of("quickLook"));
        JMenuItem compress = loaded.item(FMString.of("compress"));
        if (quickLook == null || compress == null) return;

        Node first = Finder.first(selection());
        List<Node> chosen = selection();
        if (first == null) {
            quickLook.setText(FMLocalized.of(QUICK_LOOK).toString());
            compress.setText(FMLocalized.of(COMPRESS).toString());
            return;
        }
        FMString name = FMString.of(first.name);
        quickLook.setText(FMLocalized.filled(QUICK_LOOK_ONE, name).toString());
        compress.setText(chosen.size() > 1
            ? FMLocalized.filled(COMPRESS_MANY, FMString.of(String.valueOf(chosen.size()))).toString()
            : FMLocalized.filled(COMPRESS_ONE, name).toString());
    }

    private void sayShowOrHide() {
        FinderWindow w = Finder.frontWindow();
        setChrome("toggleToolbar", w == null || w.toolbarVisible(), HIDE_TOOLBAR, SHOW_TOOLBAR);
        setChrome("togglePathBar", w == null || w.pathBarVisible(), HIDE_PATH_BAR, SHOW_PATH_BAR);
        setChrome("toggleStatusBar", w == null || w.statusBarVisible(),
                  HIDE_STATUS_BAR, SHOW_STATUS_BAR);
        setChrome("toggleSidebar", w == null || w.sidebarVisible(), HIDE_SIDEBAR, SHOW_SIDEBAR);
    }

    private void setChrome(String action, boolean showing, FMString hide, FMString show) {
        JMenuItem item = loaded.item(FMString.of(action));
        if (item != null) item.setText(FMLocalized.of(showing ? hide : show).toString());
    }

    /* --------------------------------------------------------- what the commands do */

    /**
     * Every command in the Finder's bar, by the name it sends.
     *
     * One switch rather than a listener per item, because the file already says which item
     * sends which name and a second copy of that arrangement here would be the thing that
     * goes out of date. A name with nothing under it is a command in the file that this
     * version does not have, which is said rather than ignored.
     */
    @Override public void perform(FMString action) {
        switch (action.toString()) {
            /* ------------------------------------------------------------- Finder */
            case "aboutFinder" -> AboutWindow.showAboutFinder();
            case "preferences" -> Bundles.openPart(SYSTEM_PREFERENCES, "finder");
            case "emptyTrash" -> Finder.emptyTrash(false);
            case "secureEmptyTrash" -> Finder.emptyTrash(true);
            case "revealInExplorer" -> {
                FinderWindow w = Finder.frontWindow();
                if (w != null && w.currentFolder() != null) FS.reveal(w.currentFolder());
                else FS.reveal(FS.desktopFolder());
            }
            case "openTerminalHere" -> {
                // The folder goes to the program, rather than the program coming here to
                // look for it. Terminal runs in a process of its own and has no way to see
                // a window of the Finder's, which is the arrangement rather than a limit.
                FinderWindow w = Finder.frontWindow();
                File dir = w != null && w.currentFolder() != null ? w.currentFolder() : FS.home();
                Bundles.openFiles("org.fractalmicro.terminal", List.of(dir));
            }
            case "hideFinder" -> desktop.hideAllWindows();
            case "hideOthers" -> desktop.hideOtherWindows();
            case "showAll" -> desktop.showAllWindows();

            /* --------------------------------------------------------------- File */
            case "newWindow" -> Finder.newWindow(null);
            case "newFolder" -> Finder.newFolder(currentFolder());
            case "newSmartFolder" -> Spotlight.open();
            case "newBurnFolder", "burnToDisc" -> beep(NO_BURNING);
            case "open", "openWithDefault" -> Finder.openAll(selection());
            case "openWithChosen" -> Finder.openWithChosen(selection());
            case "print" -> Finder.print(Finder.first(selection()));
            case "closeWindow" -> desktop.closeFrontWindow();
            case "getInfo" -> Finder.getInfo(Finder.first(selection()));
            case "compress" -> Finder.compress(selection());
            case "duplicate" -> Finder.duplicate(selection());
            case "makeAlias" -> Finder.makeAlias(selection());
            case "quickLook" -> QuickLook.show(Finder.first(selection()));
            case "showOriginal" -> Finder.showOriginal(Finder.first(selection()));
            case "addToSidebar" -> Finder.addToSidebar(selection());
            case "moveToTrash" -> Finder.moveToTrash(selection());
            case "eject" -> Finder.eject(Finder.first(selection()));
            case "find" -> Spotlight.open();

            /* --------------------------------------------------------------- Edit */
            case "undo" -> beep(NOTHING_TO_UNDO);
            case "redo" -> beep(NOTHING_TO_REDO);
            case "cut" -> beep(NO_CUTTING);
            case "copy" -> Finder.copy(selection());
            case "paste" -> Finder.paste(currentFolder());
            case "selectAll" -> selectAll();
            case "showClipboard" -> beep(NO_CLIPBOARD);

            /* --------------------------------------------------------------- View */
            case "viewAsIcons" -> setView("Icon");
            case "viewAsList" -> setView("List");
            case "viewAsColumns" -> setView("Column");
            case "viewAsCoverFlow" -> setView("Cover Flow");
            case "cleanUp" -> {
                Finder.refreshDesktop();
                FinderWindow w = Finder.frontWindow();
                if (w != null) w.reload();
                beep(TIDIED_UP);
            }
            case "cleanUpSelection" -> {
                FinderWindow w = Finder.frontWindow();
                if (w != null) w.reload(); else Finder.refreshDesktop();
            }
            case "arrangeByName" -> arrangeBy("Name");
            case "arrangeByDateModified" -> arrangeBy("Date Modified");
            case "arrangeBySize" -> arrangeBy("Size");
            case "arrangeByKind" -> arrangeBy("Kind");
            case "toggleToolbar" -> toggle("toolbar");
            case "togglePathBar" -> toggle("pathbar");
            case "toggleStatusBar" -> toggle("statusbar");
            case "toggleSidebar" -> toggle("sidebar");
            case "customizeToolbar" -> beep(NO_CUSTOM_TOOLBAR);
            case "showViewOptions" -> ViewOptionsWindow.open();

            /* ----------------------------------------------------------------- Go */
            case "goBack" -> inFrontWindow(FinderWindow::goBack);
            case "goForward" -> inFrontWindow(FinderWindow::goForward);
            case "goUp" -> inFrontWindow(FinderWindow::goUp);
            case "goComputer" -> showComputer();
            case "goHome" -> Finder.goTo(FS.home());
            case "goDesktop" -> Finder.goTo(FS.desktopFolder());
            case "goNetwork" -> showNetwork();
            case "goApplications" -> Finder.goToApplications();
            case "goDocuments" -> Finder.goTo(FS.documents());
            case "goUtilities" -> Finder.goToUtilities();
            case "goDownloads" -> Finder.goTo(FS.downloads());
            case "goToFolder" -> goToFolder();
            case "connectToServer" -> Finder.connectToServer();

            default -> org.fractalmicro.core.Log.info(
                "the Finder's menus ask for " + action + ", which it does not do");
        }
    }

    /* ------------------------------------------------------- the words it still says */

    private static final FMString QUICK_LOOK = FMString.of("finder.quickLook");
    private static final FMString QUICK_LOOK_ONE = FMString.of("finder.quickLookNamed");
    private static final FMString COMPRESS = FMString.of("finder.compress");
    private static final FMString COMPRESS_ONE = FMString.of("finder.compressNamed");
    private static final FMString COMPRESS_MANY = FMString.of("finder.compressMany");
    private static final FMString HIDE_TOOLBAR = FMString.of("finder.hideToolbar");
    private static final FMString SHOW_TOOLBAR = FMString.of("finder.showToolbar");
    private static final FMString HIDE_PATH_BAR = FMString.of("finder.hidePathBar");
    private static final FMString SHOW_PATH_BAR = FMString.of("finder.showPathBar");
    private static final FMString HIDE_STATUS_BAR = FMString.of("finder.hideStatusBar");
    private static final FMString SHOW_STATUS_BAR = FMString.of("finder.showStatusBar");
    private static final FMString HIDE_SIDEBAR = FMString.of("finder.hideSidebar");
    private static final FMString SHOW_SIDEBAR = FMString.of("finder.showSidebar");
    private static final FMString NO_BURNING = FMString.of("finder.noBurning");
    private static final FMString NOTHING_TO_UNDO = FMString.of("finder.nothingToUndo");
    private static final FMString NOTHING_TO_REDO = FMString.of("finder.nothingToRedo");
    private static final FMString NO_CUTTING = FMString.of("finder.noCutting");
    private static final FMString NO_CLIPBOARD = FMString.of("finder.noClipboard");
    private static final FMString TIDIED_UP = FMString.of("finder.tidiedUp");
    private static final FMString NO_CUSTOM_TOOLBAR = FMString.of("finder.noCustomToolbar");
    private static final FMString NO_WINDOW = FMString.of("finder.noWindowOpen");
    private static final FMString GO_TO_PROMPT = FMString.of("finder.goToFolderPrompt");
    private static final FMString GO_TO_TITLE = FMString.of("finder.goToFolderTitle");
    private static final FMString GO_TO_BUTTON = FMString.of("finder.goToFolderButton");
    private static final FMString NO_SUCH_FOLDER = FMString.of("finder.noSuchFolder");
    private static final FMString CHECK_SPELLING = FMString.of("finder.checkSpelling");

    private static void beep(FMString key) { Finder.beep(FMLocalized.of(key).toString()); }

    /* ------------------------------------------------------------------ the actions */

    void selectAll() {
        FinderWindow w = Finder.frontWindow();
        if (w != null) w.selectAll();
        else {
            DesktopIcons icons = Finder.desktopIcons();
            icons.requestFocusInWindow();
            icons.setSelectionInterval(0, icons.getModel().getSize() - 1);
        }
    }

    void setView(String view) {
        FinderWindow w = Finder.frontWindow();
        if (w != null) w.setViewMode(view);
        else FinderSettings.setPreferredViewStyle(FinderSettings.viewCodeFor(FMString.of(view)));
    }

    private void arrangeBy(String key) {
        FinderWindow w = Finder.frontWindow();
        if (w != null) w.arrangeBy(FinderSettings.arrangeKeyFor(key));
        else beep(NO_WINDOW);
    }

    private void inFrontWindow(java.util.function.Consumer<FinderWindow> what) {
        FinderWindow w = Finder.frontWindow();
        if (w != null) what.accept(w); else beep(NO_WINDOW);
    }

    void toggle(String what) {
        FinderWindow w = Finder.frontWindow();
        if (w == null) { beep(NO_WINDOW); return; }
        w.toggleChrome(what);
    }

    void showComputer() {
        FinderWindow w = Finder.frontWindow();
        if (w == null) w = Finder.newWindow(FS.home());
        w.showComputer();
    }

    void showNetwork() {
        FinderWindow w = Finder.frontWindow();
        if (w == null) w = Finder.newWindow(FS.home());
        w.showNetwork();
    }

    void goToFolder() {
        String path = Finder.prompt(FMLocalized.of(GO_TO_PROMPT).toString(),
                                    FMLocalized.of(GO_TO_TITLE).toString(), "",
                                    FMLocalized.of(GO_TO_BUTTON).toString());
        if (path == null || path.isBlank()) return;
        File dir = new File(path.replace("~", System.getProperty("user.home")));
        if (!dir.isDirectory()) {
            Alert.tell(FMLocalized.filled(NO_SUCH_FOLDER, FMString.of(path)),
                       FMLocalized.of(CHECK_SPELLING));
            return;
        }
        Finder.goTo(dir);
    }

    private List<Node> selection() {
        FinderWindow w = Finder.frontWindow();
        if (w != null) return w.selection();
        DesktopIcons icons = Finder.desktopIcons();
        return icons == null ? java.util.List.of() : icons.selection();
    }

    private File currentFolder() {
        FinderWindow w = Finder.frontWindow();
        if (w != null && w.currentFolder() != null) return w.currentFolder();
        return FS.desktopFolder();
    }
}
