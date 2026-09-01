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
import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.core.Recent;
import org.fractalmicro.core.Running;
import org.fractalmicro.core.Shell;
import org.fractalmicro.fs.*;
import org.fractalmicro.os.Defaults;
import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.win.Kernel32;

import javax.swing.*;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** The verbs: everything a menu item, a Dock tile or a double click can ask for. */
public final class Finder {
    private Finder() {}

    private static final List<File> clipboard = new ArrayList<>();

    /* ------------------------------------------------------------- windows */

    public static FinderWindow newWindow(File dir) {
        boolean computer = dir == null
            && FinderSettings.newWindowTargetCode().sameAs(org.fractalmicro.foundation.FMString.of("PfCm"));
        if (dir == null) dir = FinderSettings.newWindowTarget();
        FinderWindow w = new FinderWindow(dir);
        Desktop.get().addWindow(w);
        if (computer) w.showComputer();
        return w;
    }

    /** The frontmost Finder window, skipping Info and preference windows. */
    public static FinderWindow frontWindow() {
        JInternalFrame active = Desktop.get().activeWindow();
        if (active instanceof FinderWindow) return (FinderWindow) active;
        for (JInternalFrame f : Desktop.get().windows()) {
            if (f instanceof FinderWindow && f.isVisible() && !f.isClosed()) return (FinderWindow) f;
        }
        return null;
    }

    /* --------------------------------------------------------------- open */

    public static void open(Node n) {
        if (n == null) return;
        if (n.kind == Node.Kind.TRASH) { openTrash(); return; }
        if (n.isVolume() && !n.isMounted()) { beep(NO_DISC); return; }
        if (openBundle(n)) return;
        if (n.isContainer() && n.file != null) { newWindow(n.file); return; }
        if (n.file != null) launchApp(n);
    }

    /** True when this was an application bundle and it has been opened. */
    private static boolean openBundle(Node n) {
        if (n.file == null || !org.fractalmicro.bundle.Bundle.looksLikeBundle(n.file)) return false;
        org.fractalmicro.bundle.Bundle bundle = org.fractalmicro.bundle.Bundles.byFolder(n.file);
        if (bundle == null) return false;
        Recent.noteItem(n.file);
        if (!org.fractalmicro.bundle.Bundles.open(bundle, null)) {
            Alert.tell(FMString.of("The application " + '“' + bundle.displayName() + '”' + (" can") + '’' + "t be opened."),

                       FMString.of("Its bundle may be damaged or incomplete."));
        }
        return true;
    }

    /** Show Package Contents: browse inside a bundle, as Finder allows. */
    public static void showPackageContents(Node n) {
        if (n == null || n.file == null || !org.fractalmicro.bundle.Bundle.looksLikeBundle(n.file)) {
            beep(NOT_A_PACKAGE);
            return;
        }
        newWindow(n.file);
    }

    public static void openAll(List<Node> nodes) {
        for (Node n : nodes) open(n);
    }

    public static void launchApp(Node app) {
        if (app == null || app.file == null) { beep(NOTHING_TO_OPEN); return; }
        Recent.noteItem(app.file);
        Running.note(app.name, app.file);
        Shell.open(app.file);
    }

    public static void openTrash() {
        FinderWindow w = new FinderWindow(null);
        w.showTrash();
        Desktop.get().addWindow(w);
    }

    public static void goTo(File dir) {
        FinderWindow w = frontWindow();
        if (w != null) w.navigateTo(dir);
        else newWindow(dir);
    }

    public static void goToApplications() {
        FinderWindow w = frontWindow();
        if (w == null) w = newWindow(FS.home());
        w.showApplications(false);
    }

    public static void goToUtilities() {
        FinderWindow w = frontWindow();
        if (w == null) w = newWindow(FS.home());
        w.showApplications(true);
    }

    /* ------------------------------------------------------------ editing */

    public static void newFolder(File parent) {
        if (parent == null || !parent.isDirectory()) { beep(NO_FOLDER_OPEN); return; }
        File f = FS.newFolder(parent);
        refreshAll();
        rename(FS.node(f));
    }

    public static void rename(Node n) {
        if (n == null || n.file == null) { beep(SELECT_TO_RENAME); return; }
        if (n.isVolume()) { beep(NO_VOLUME_RENAME); return; }
        String current = n.file.getName();
        String next = prompt("Rename this item.", "Name:", current, "Rename");
        if (next == null || next.isBlank() || next.equals(current)) return;
        File dest = new File(n.file.getParentFile(), next);
        if (dest.exists()) {
            Alert.tell(FMString.of("The name " + '\u201c' + next + '\u201d' + " is already taken."),
                       FMString.of("Please choose a different name."));
            return;
        }
        if (!n.file.renameTo(dest)) beep(RENAME_FAILED);
        refreshAll();
    }

    public static void duplicate(List<Node> nodes) {
        for (Node n : nodes) {
            if (n.file == null) continue;
            try {
                FS.duplicate(n.file);
            } catch (IOException e) {
                beep(FMLocalized.filled(DUPLICATE_FAILED, FMString.of(n.name)));
            }
        }
        refreshAll();
    }

    /**
     * Make Alias. A real alias file: an empty data fork, the record in the resource
     * fork, and the alias flag in the Finder information. Not a symbolic link, which
     * needs rights this program should not be asking for and forgets its target the
     * moment the target moves.
     */
    public static void makeAlias(List<Node> nodes) {
        int made = 0;
        for (Node n : nodes) {
            if (n.file == null) continue;
            try {
                org.fractalmicro.alias.Alias.create(n.file, null);
                made++;
            } catch (IOException e) {
                beep(FMLocalized.filled(ALIAS_FAILED, FMString.of(e.getMessage())));
                break;
            }
        }
        if (made > 0) refreshAll();
    }

    /**
     * Follows an alias. When the original has moved, the record is written again with
     * where it is now, so the next open does not have to go looking.
     */
    public static File originalOf(File alias) {
        org.fractalmicro.alias.Alias.Resolution found = org.fractalmicro.alias.Alias.resolve(alias);
        if (!found.ok()) return null;
        if (found.how() != org.fractalmicro.alias.Alias.Found.PATH) {
            try {
                org.fractalmicro.alias.Alias.createAt(found.target(), alias);
                org.fractalmicro.core.Log.info("alias " + alias.getName() + " now points at "
                                      + found.target());
            } catch (IOException e) {
                org.fractalmicro.core.Log.info("could not bring " + alias.getName()
                                      + " up to date: " + e.getMessage());
            }
        }
        return found.target();
    }

    /* --------------------------------------------------------------- labels */

    /** Marks every selected item, or clears the mark when the label is none. */
    public static void label(List<Node> nodes, int label) {
        boolean everywhere = true;
        for (Node n : nodes) {
            if (n.file == null) continue;
            everywhere &= org.fractalmicro.fs.Labels.set(n.file, label);
        }
        if (!everywhere) {
            beep("This volume cannot hold labels on the files themselves; "
                 + "they are kept beside them instead.");
        }
        refreshAll();
    }

    /** Show Original, following an alias, a symbolic link or a Windows shortcut. */
    public static void showOriginal(Node n) {
        if (n == null || n.file == null) { beep(SELECT_AN_ALIAS); return; }
        File target = originalOf(n.file);
        try {
            if (target == null && Files.isSymbolicLink(n.file.toPath())) {
                target = Files.readSymbolicLink(n.file.toPath()).toFile();
            }
        } catch (IOException ignored) { }
        if (target == null) target = Apps.resolve(n.file);
        if (target == null || !target.exists() || target.equals(n.file)) {
            beep(FMLocalized.filled(NO_ORIGINAL, FMString.of(n.name)));
            return;
        }
        goTo(target.isDirectory() ? target : target.getParentFile());
    }

    public static void moveToTrash(List<Node> nodes) {
        List<File> files = new ArrayList<>();
        for (Node n : nodes) {
            if (n.isVolume()) { beep(NO_VOLUME_TRASH); continue; }
            if (n.file != null) files.add(n.file);
        }
        if (files.isEmpty()) { beep(NOTHING_SELECTED); return; }
        if (!Trash.canMoveToTrash()) { beep(NO_TRASH); return; }
        int moved = Trash.moveToTrash(files);
        if (moved < files.size()) beep(SOME_NOT_TRASHED);
        refreshAll();
    }

    public static void emptyTrash(boolean secure) {
        if (Trash.isEmpty()) { beep(TRASH_EMPTY); return; }
        if (FinderSettings.warnOnEmptyTrash()) {
            boolean go = Alert.confirm(Alert.Kind.CAUTION,
                FMString.of("Are you sure you want to permanently erase the items in the Trash?"),
                FMString.of("You can" + '\u2019' + "t undo this action."),
                FMString.of(secure ? "Secure Empty Trash" : "Empty Trash"));
            if (!go) return;
        }
        Trash.empty(secure);
        refreshAll();
    }

    public static void copy(List<Node> nodes) {
        clipboard.clear();
        for (Node n : nodes) if (n.file != null) clipboard.add(n.file);
        if (clipboard.isEmpty()) { beep(NOTHING_SELECTED); return; }
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new FileTransfer(new ArrayList<>(clipboard)), null);
    }

    public static void paste(File destination) {
        List<File> source = new ArrayList<>(clipboard);
        if (source.isEmpty()) source = clipboardFromSystem();
        if (source.isEmpty() || destination == null) { beep(NOTHING_TO_PASTE); return; }
        for (File f : source) {
            try {
                File dest = new File(destination, f.getName());
                int i = 2;
                while (dest.exists()) dest = new File(destination, f.getName() + " " + (i++));
                if (f.isDirectory()) FS.copyTree(f.toPath(), dest.toPath());
                else Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception e) {
                beep(FMLocalized.filled(PASTE_FAILED, FMString.of(f.getName())));
            }
        }
        refreshAll();
    }

    @SuppressWarnings("unchecked")
    private static List<File> clipboardFromSystem() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return new ArrayList<>((List<File>) t.getTransferData(DataFlavor.javaFileListFlavor));
            }
        } catch (Exception ignored) { }
        return new ArrayList<>();
    }

    /** Compress: a zip archive beside the selection. */
    public static void compress(List<Node> nodes) {
        List<File> files = new ArrayList<>();
        for (Node n : nodes) if (n.file != null) files.add(n.file);
        if (files.isEmpty()) { beep(NOTHING_SELECTED); return; }

        File parent = files.get(0).getParentFile();
        String base = files.size() == 1 ? files.get(0).getName() : "Archive";
        File zip = new File(parent, base + ".zip");
        int i = 2;
        while (zip.exists()) zip = new File(parent, base + " " + (i++) + ".zip");

        final File target = zip;
        Shell.async(() -> {
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(new java.io.FileOutputStream(target)))) {
                for (File f : files) addToZip(out, f, f.getName());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> beep(ARCHIVE_FAILED));
                return;
            }
            SwingUtilities.invokeLater(Finder::refreshAll);
        });
    }

    private static void addToZip(java.util.zip.ZipOutputStream out, File f, String path) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null || kids.length == 0) {
                out.putNextEntry(new java.util.zip.ZipEntry(path + "/"));
                out.closeEntry();
                return;
            }
            for (File k : kids) addToZip(out, k, path + "/" + k.getName());
            return;
        }
        out.putNextEntry(new java.util.zip.ZipEntry(path));
        Files.copy(f.toPath(), out);
        out.closeEntry();
    }

    /**
     * Add to Sidebar.
     *
     * Written through the one list of places rather than into the preference directly, so
     * that what is added here is what the save panel shows. The two used to keep their own
     * idea of it, and a folder dragged into the sidebar was in one and not the other.
     */
    public static void addToSidebar(List<Node> nodes) {
        int added = 0;
        for (Node n : nodes) {
            if (n.file != null && org.fractalmicro.fs.Places.addFavourite(n.file)) added++;
        }
        if (added == 0) { beep(ONLY_FOLDERS); return; }
        refreshAll();
    }

    public static void openWithChosen(List<Node> nodes) {
        if (nodes.isEmpty()) { beep(SELECT_SOMETHING); return; }
        String programs = System.getenv("ProgramFiles");
        JFileChooser chooser = new JFileChooser(
            programs == null ? org.fractalmicro.fs.Volumes.systemDrive() : programs);
        chooser.setDialogTitle("Choose Application");
        if (chooser.showOpenDialog(Desktop.get()) != JFileChooser.APPROVE_OPTION) return;
        File app = chooser.getSelectedFile();
        List<String> command = new ArrayList<>();
        command.add(app.getAbsolutePath());
        for (Node n : nodes) if (n.file != null) command.add(n.file.getAbsolutePath());
        Running.note(stripExtension(app.getName()), app);
        Shell.launch(command.toArray(new String[0]));
    }

    public static void print(Node n) {
        if (n == null || n.file == null || !n.file.isFile()) { beep(SELECT_TO_PRINT); return; }
        try {
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            if (!d.isSupported(java.awt.Desktop.Action.PRINT)) {
                beep(NO_PRINTER);
                return;
            }
            d.print(n.file);
        } catch (Exception e) {
            beep(PRINT_FAILED);
        }
    }

    /* -------------------------------------------------------------- info */

    public static void getInfo(Node n) {
        if (n == null) { beep(SELECT_SOMETHING); return; }
        Desktop.get().addWindow(new InfoWindow(n));
    }

    public static void eject(Node volume) {
        if (volume == null || !volume.isVolume()) { beep(SELECT_TO_EJECT); return; }
        String mount = volume.mountPoint == null
            ? volume.file.getAbsolutePath() : volume.mountPoint;
        Shell.async(() -> {
            boolean ok = Kernel32.ejectMedia(mount);
            SwingUtilities.invokeLater(() -> {
                if (!ok) beep(FMLocalized.filled(EJECT_FAILED, FMString.of(volume.name)));
                Volumes.refresh(null);
            });
        });
    }

    /** Ends a program this desktop started, through the process API. */
    private static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /**
     * Asks for a server and shows it.
     *
     * A menu bar extra offers this too, and asks for it by name rather than calling in:
     * mounting is the system's, but showing what was mounted is the browser's.
     */
    public static void connectToServer() {
        String path = Finder.prompt("Connect to the server:", "Server Address:", "\\\\", "Connect");
        if (path == null || path.isBlank()) return;
        File dir = new File(path);
        if (!dir.exists()) {
            Alert.tell(FMString.of("There was a problem connecting to the server " + '“' + path + '”' + "."),
                       FMString.of("Check the server name or address and try again."));
            return;
        }
        goTo(dir);
    }

    /* ------------------------------------------------------------ helpers */

    public static void refreshAll() {
        SwingUtilities.invokeLater(() -> {
            Desktop d = Desktop.get();
            if (d == null) return;
            d.icons().refresh();
            for (JInternalFrame f : d.windows()) {
                if (f instanceof FinderWindow) ((FinderWindow) f).reload();
            }
            d.dock().rebuild();
        });
    }

    /** Says what went wrong in the front window's status bar, and beeps. */
    /**
     * Says something, by the key it is filed under.
     *
     * A key rather than a sentence, because everything the Finder says is in its strings
     * table and this is the one place most of it goes through. The overload taking words
     * is for the few that are put together from something a person typed.
     */
    public static void beep(FMString key) {
        Desktop.beep(FMLocalized.of(key).toString());
    }

    public static void beep(String message) {
        Desktop.beep(message);
        FinderWindow w = frontWindow();
        if (w != null) w.setStatusText(message);
    }

    /** Asks for one piece of text, with a button named for what it will do. */
    public static String prompt(String message, String fieldLabel, String initial,
                                String actionButton) {
        return Alert.ask(FMString.of(message), FMString.of(fieldLabel),
                         FMString.of(initial), FMString.of(actionButton)).toString();
    }

    /** The contextual menu shared by the desktop and the Finder views. */
    public static JPopupMenu contextMenu(Supplier<List<Node>> selection, Supplier<File> folder) {
        JPopupMenu m = new JPopupMenu();
        m.add(item("Open", e -> openAll(selection.get())));
        m.add(item("Show Package Contents", e -> showPackageContents(first(selection.get()))));
        m.add(item("Get Info", e -> getInfo(first(selection.get()))));
        m.addSeparator();
        m.add(item("New Folder", e -> newFolder(folder.get())));
        m.add(item("Rename", e -> rename(first(selection.get()))));
        m.add(item("Duplicate", e -> duplicate(selection.get())));
        m.add(item("Make Alias", e -> makeAlias(selection.get())));
        m.add(labelMenu(() -> selection.get()));
        m.add(item("Compress", e -> compress(selection.get())));
        m.addSeparator();
        m.add(item("Copy", e -> copy(selection.get())));
        m.add(item("Paste Item", e -> paste(folder.get())));
        m.addSeparator();
        m.add(item("Move to Trash", e -> moveToTrash(selection.get())));
        m.add(item("Eject", e -> eject(first(selection.get()))));
        m.addSeparator();
        m.add(item("Show in Windows Explorer", e -> {
            Node n = first(selection.get());
            if (n != null && n.file != null) FS.reveal(n.file);
        }));
        return m;
    }

    public static Node first(List<Node> nodes) {
        return nodes == null || nodes.isEmpty() ? null : nodes.get(0);
    }

    /**
     * The Label submenu: none, then the seven colours by their current names. Each item
     * carries its colour as an icon, and its name as text, so it is a colour to look at
     * and a word to read.
     */
    public static JMenu labelMenu(java.util.function.Supplier<List<Node>> selection) {
        JMenu m = new JMenu("Label");
        m.getAccessibleContext().setAccessibleName("Label");
        for (int i = 0; i < org.fractalmicro.fs.Labels.COUNT; i++) {
            final int label = i;
            JMenuItem it = new JMenuItem(org.fractalmicro.fs.Labels.nameOf(i), new SwatchIcon(label));
            it.addActionListener(e -> label(selection.get(), label));
            m.add(it);
            if (i == 0) m.addSeparator();
        }
        return m;
    }

    /** The dot of colour beside a label's name. */
    private static final class SwatchIcon implements javax.swing.Icon {
        private final int label;

        SwatchIcon(int label) { this.label = label; }

        @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g0, int x, int y) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            org.fractalmicro.theme.Aqua.antialias(g);
            java.awt.Color colour = org.fractalmicro.fs.Labels.colorOf(label);
            if (colour == null) {
                g.setColor(new java.awt.Color(0x9A9A9A));
                g.drawOval(x + 1, y + 1, 10, 10);
            } else {
                g.setColor(colour);
                g.fillOval(x + 1, y + 1, 11, 11);
                g.setColor(colour.darker());
                g.drawOval(x + 1, y + 1, 11, 11);
            }
            g.dispose();
        }

        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 13; }
    }

    public static JMenuItem item(String text, java.awt.event.ActionListener a) {
        JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(a);
        return mi;
    }

    /** File list transfer for the system clipboard. */
    private static class FileTransfer implements Transferable {
        private final List<File> files;
        FileTransfer(List<File> files) { this.files = files; }
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return files;
        }
    }
    /* ------------------------------------------------- what the Finder says */

    private static final FMString NO_DISC = FMString.of("finder.noDisc");
    private static final FMString NOT_A_PACKAGE = FMString.of("finder.notAPackage");
    private static final FMString NOTHING_TO_OPEN = FMString.of("finder.nothingToOpen");
    private static final FMString NO_FOLDER_OPEN = FMString.of("finder.noFolderOpen");
    private static final FMString SELECT_TO_RENAME = FMString.of("finder.selectToRename");
    private static final FMString NO_VOLUME_RENAME = FMString.of("finder.noVolumeRename");
    private static final FMString RENAME_FAILED = FMString.of("finder.renameFailed");
    private static final FMString SELECT_AN_ALIAS = FMString.of("finder.selectAnAlias");
    private static final FMString NO_VOLUME_TRASH = FMString.of("finder.noVolumeTrash");
    private static final FMString NOTHING_SELECTED = FMString.of("finder.nothingSelected");
    private static final FMString NO_TRASH = FMString.of("finder.noTrash");
    private static final FMString SOME_NOT_TRASHED = FMString.of("finder.someNotTrashed");
    private static final FMString TRASH_EMPTY = FMString.of("finder.trashEmpty");
    private static final FMString NOTHING_TO_PASTE = FMString.of("finder.nothingToPaste");
    private static final FMString ARCHIVE_FAILED = FMString.of("finder.archiveFailed");
    private static final FMString ONLY_FOLDERS = FMString.of("finder.onlyFolders");
    private static final FMString SELECT_SOMETHING = FMString.of("finder.selectSomething");
    private static final FMString SELECT_TO_PRINT = FMString.of("finder.selectToPrint");
    private static final FMString NO_PRINTER = FMString.of("finder.noPrinter");
    private static final FMString PRINT_FAILED = FMString.of("finder.printFailed");
    private static final FMString SELECT_TO_EJECT = FMString.of("finder.selectToEject");
    private static final FMString DUPLICATE_FAILED = FMString.of("finder.duplicateFailed");
    private static final FMString ALIAS_FAILED = FMString.of("finder.aliasFailed");
    private static final FMString NO_ORIGINAL = FMString.of("finder.noOriginal");
    private static final FMString PASTE_FAILED = FMString.of("finder.pasteFailed");
    private static final FMString EJECT_FAILED = FMString.of("finder.ejectFailed");

}
