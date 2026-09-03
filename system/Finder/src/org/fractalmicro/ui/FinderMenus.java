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

import org.fractalmicro.appkit.FMAlert;
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

    /**
     * The settings pane the file manager's own Preferences item opens.
     *
     * Named here rather than written into the command, so a check can put the two halves
     * side by side. It used to ask for one called "finder", which is not a pane, and the
     * settings quietly showed the first instead.
     */
    public static final String SETTINGS_PANE = "desktop";

    /**
     * What a command is called in the bar, for anything showing the same command.
     *
     * A contextual menu offers what the bar offers, so it takes the words from the bar.
     * Writing them again would be one command translated twice.
     */
    public static FMString titleFor(FMString action) {
        FinderMenus one = installed;
        return one == null || one.loaded == null ? FMString.EMPTY : one.loaded.titleOf(action);
    }

    /** The one the bar is using, so the words it read can be asked for. */
    private static FinderMenus installed;

    /** The interface file the bar is built from, inside the Finder's own bundle. */
    private static final FMString INTERFACE = FMString.of("FinderMenus");

    private final Desktop desktop;
    private NibLoader loaded;

    private FinderMenus(Desktop desktop) {
        this.desktop = desktop;
    }

    /**
     * One with no bar behind it, for asking what a command would answer.
     *
     * Whether a command applies is a question about the selection and the windows, not
     * about the menus, so it can be asked without any. The checks ask it that way because
     * the alternative is opening a menu, which needs a pointer.
     */
    public static FinderMenus forChecking() {
        return new FinderMenus(Desktop.sharedDesktop());
    }

    /**
     * Reads the Finder's menus and gives them to the bar as the program in front by
     * default, so they are what shows when no other program owns it.
     *
     * A bar with nothing in it means the file could not be read, which is worth saying
     * plainly: a Finder whose menus are empty is not one that is nearly working.
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

        installed = menus;
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
    /**
     * The Label submenu, kept because nothing else can validate it.
     *
     * Its items are made at run time from the labels somebody has used, so they carry no
     * command name and the menu machinery has nothing to ask about. Every other item greys
     * itself with nothing selected; this one stayed black until it was asked here.
     */
    private JMenu labels;

    private void addLiveMenus(List<JMenu> built) {
        for (JMenu menu : built) {
            if ("File".equals(menu.getName()) || isNamed(menu, "makeAlias")) {
                labels = Finder.labelMenu(this::selection);
                menu.add(labels, indexAfter(menu, "makeAlias"));
            }
            if (isNamed(menu, "goToFolder")) {
                menu.add(desktop.mainMenu().recentFoldersMenu(),
                         indexAfter(menu, "goDownloads"));
            }
        }
        findOpenWith();
    }

    /**
     * The Open With submenu, and the two items the file always keeps at the bottom of it.
     *
     * What goes above them is not in the file and cannot be: whichever installed programs
     * can open what happens to be selected is a question about the machine.
     */
    private JMenu openWith;
    private JMenuItem hostDefault;
    private JMenuItem chooseOne;

    private void findOpenWith() {
        hostDefault = loaded.item(FMString.of("openWithDefault"));
        chooseOne = loaded.item(FMString.of("openWithChosen"));
        if (hostDefault == null) return;
        if (hostDefault.getParent() instanceof javax.swing.JPopupMenu popup
                && popup.getInvoker() instanceof JMenu holding) {
            openWith = holding;
        }
    }

    /**
     * Fills Open With from what can actually open the selection.
     *
     * The programs that declared a type this file conforms to come first, best claim at
     * the top the way a Mac puts the default there, then the host's answer and Choose.
     */
    private void fillOpenWith() {
        if (openWith == null) return;
        openWith.removeAll();
        List<Node> chosen = selection();
        boolean any = false;
        for (org.fractalmicro.bundle.Bundle program : Finder.canOpen(chosen)) {
            JMenuItem item = new JMenuItem(program.displayName().toString());
            item.addActionListener(e -> Finder.openWith(program, selection()));
            openWith.add(item);
            any = true;
        }
        if (any) openWith.addSeparator();
        if (hostDefault != null) openWith.add(hostDefault);
        if (chooseOne != null) openWith.add(chooseOne);
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
     * Quick Look names the file it would show, Compress says how many, and the four that
     * hide something say Show once it is hidden. All are written down with the words they
     * have when nothing is selected, and this only ever replaces one of those.
     */
    private void followTheSelection(List<JMenu> built) {
        for (JMenu menu : built) {
            menu.addMenuListener(new javax.swing.event.MenuListener() {
                @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                    nameTheSelection();
                    nameTheUndo();
                    sayShowOrHide();
                    // Labelling needs something to label, the same as everything else in
                    // this menu. It is asked here rather than by the menu machinery
                    // because its items are made at run time and are not in the interface
                    // file to be asked about, but the question is the same question, so
                    // that a check can put it and get the answer the menu will show.
                    if (labels != null) labels.setEnabled(canPerform(FMString.of("label")));
                    fillOpenWith();
                }
                @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
                @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
            });
        }
    }

    /**
     * Says what Undo would undo, which is the point of an undo manager keeping names.
     *
     * "Undo" alone asks somebody to press a key and find out. "Undo Rename" is a promise
     * made before anything changes. The two words come together from the strings file,
     * since their order is not the same in every language.
     */
    private void nameTheUndo() {
        JMenuItem undo = loaded.item(FMString.of("undo"));
        JMenuItem redo = loaded.item(FMString.of("redo"));
        org.fractalmicro.foundation.FMUndoManager manager = Finder.undoManager();
        if (undo != null) {
            undo.setText(manager.canUndo()
                ? FMLocalized.filled(UNDO_NAMED,
                      FMLocalized.of(manager.undoActionName())).toString()
                : FMLocalized.of(UNDO_PLAIN).toString());
        }
        if (redo != null) {
            redo.setText(manager.canRedo()
                ? FMLocalized.filled(REDO_NAMED,
                      FMLocalized.of(manager.redoActionName())).toString()
                : FMLocalized.of(REDO_PLAIN).toString());
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
     * sends which name. A name with nothing under it is a command this version does not
     * have, which is said rather than ignored.
     */
    @Override public void perform(FMString action) {
        switch (action.toString()) {
            /* ------------------------------------------------------------- Finder */
            case "aboutFinder" -> AboutWindow.showAboutFinder();
            // The pane about the desktop, which is the file manager's own settings and the
            // nearest thing here to the General tab a Mac opens on. It used to ask for one
            // called "finder", which is not a pane, and landed on the desktop anyway
            // without anybody being told the name meant nothing.
            case "preferences" -> Bundles.openPart(SYSTEM_PREFERENCES, SETTINGS_PANE);
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
            // Named in the file, greyed by canPerform, and so unreachable from the menu.
            // Reached by a shortcut anyway, the answer is the one a Mac gives a key that
            // means nothing here.
            case "newBurnFolder", "burnToDisc", "cut", "showClipboard",
                 "customizeToolbar" -> Finder.beep();
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
            case "undo" -> { if (!Finder.undoManager().undo()) Finder.beep(); }
            case "redo" -> { if (!Finder.undoManager().redo()) Finder.beep(); }
            case "copy" -> Finder.copy(selection());
            case "paste" -> Finder.paste(currentFolder());
            case "selectAll" -> selectAll();

            /* --------------------------------------------------------------- View */
            case "viewAsIcons" -> setView("Icon");
            case "viewAsList" -> setView("List");
            case "viewAsColumns" -> setView("Column");
            case "viewAsCoverFlow" -> setView("Cover Flow");
            case "cleanUp" -> {
                Finder.refreshDesktop();
                FinderWindow w = Finder.frontWindow();
                if (w != null) w.reload();
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
    private static final FMString GO_TO_PROMPT = FMString.of("finder.goToFolderPrompt");
    private static final FMString GO_TO_TITLE = FMString.of("finder.goToFolderTitle");
    private static final FMString GO_TO_BUTTON = FMString.of("finder.goToFolderButton");
    private static final FMString NO_SUCH_FOLDER = FMString.of("finder.noSuchFolder");
    private static final FMString CHECK_SPELLING = FMString.of("finder.checkSpelling");
    private static final FMString UNDO_NAMED = FMString.of("finder.undoNamed");
    private static final FMString REDO_NAMED = FMString.of("finder.redoNamed");
    private static final FMString UNDO_PLAIN = FMString.of("finder.undoPlain");
    private static final FMString REDO_PLAIN = FMString.of("finder.redoPlain");

    /** Something went wrong that a person needs to know about. */
    private static void tell(FMString key) { Finder.tell(key); }

    /* ------------------------------------------------------------- what can be done */

    /**
     * Whether a command applies right now, asked of every item as its menu opens.
     *
     * The rules a person would say out loud: Get Info needs something chosen, Paste needs
     * something on the clipboard, Eject needs a disk. Most commands are not listed because
     * most always apply. A menu that offers Undo with nothing to undo is not a menu, it is
     * a list of the program's methods with shortcuts on them.
     */
    @Override public boolean canPerform(FMString action) {
        return switch (action.toString()) {
            case "undo" -> Finder.undoManager().canUndo();
            case "redo" -> Finder.undoManager().canRedo();

            // Something has to be chosen.
            case "open", "openWithDefault", "openWithChosen", "getInfo", "compress",
                 "duplicate", "makeAlias", "quickLook", "addToSidebar", "moveToTrash",
                 "print", "copy", "cleanUpSelection",
                 "label" -> !selection().isEmpty();

            // Chosen, and of a kind the command means anything for.
            case "showOriginal" -> isAlias(Finder.first(selection()));
            case "eject" -> isEjectable(Finder.first(selection()));

            // Somewhere to put something, and something to put.
            case "paste" -> currentFolder() != null && Finder.hasCopiedFiles();
            case "newFolder" -> currentFolder() != null;

            // A window to do it to.
            case "closeWindow", "goBack", "goForward", "goUp", "cleanUp", "selectAll",
                 "viewAsIcons", "viewAsList", "viewAsColumns", "viewAsCoverFlow",
                 "arrangeByName", "arrangeByDateModified", "arrangeBySize", "arrangeByKind",
                 "toggleToolbar", "togglePathBar", "toggleStatusBar", "toggleSidebar",
                 "showViewOptions" -> Finder.frontWindow() != null;

            // The Trash, which is a thing rather than a selection.
            case "emptyTrash", "secureEmptyTrash" -> !org.fractalmicro.fs.Trash.isEmpty();

            // Named in the file and not built, so they say no rather than beeping later.
            case "cut", "showClipboard", "newBurnFolder", "burnToDisc",
                 "customizeToolbar" -> false;

            default -> true;
        };
    }

    private static boolean isAlias(Node node) {
        return node != null && node.file != null
            && org.fractalmicro.alias.Alias.isAlias(node.file);
    }

    private static boolean isEjectable(Node node) {
        return node != null && node.isVolume() && node.isMounted()
            && node.kind != Node.Kind.HARD_DISK;
    }

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
        else Finder.beep();
    }

    private void inFrontWindow(java.util.function.Consumer<FinderWindow> what) {
        FinderWindow w = Finder.frontWindow();
        if (w != null) what.accept(w); else Finder.beep();
    }

    void toggle(String what) {
        FinderWindow w = Finder.frontWindow();
        if (w == null) { Finder.beep(); return; }
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
        String path = Finder.prompt(FMLocalized.of(GO_TO_PROMPT),
                                    FMLocalized.of(GO_TO_TITLE), FMString.EMPTY,
                                    FMLocalized.of(GO_TO_BUTTON));
        if (path == null || path.isBlank()) return;
        File dir = new File(path.replace("~", System.getProperty("user.home")));
        if (!dir.isDirectory()) {
            FMAlert.tell(FMLocalized.filled(NO_SUCH_FOLDER, FMString.of(path)),
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
