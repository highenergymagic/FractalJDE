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
import org.fractalmicro.appkit.FMDragOperation;
import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.core.Recent;
import org.fractalmicro.core.Running;
import org.fractalmicro.core.Shell;
import org.fractalmicro.fs.*;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.win.Kernel32;

import javax.swing.*;
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

    /**
     * Whether there is anything to paste.
     *
     * The clipboard this program filled, rather than the host's. Asking the host means
     * asking for the contents, which for a large copy is the copy, and it is asked every
     * time the Edit menu opens.
     */
    public static boolean hasCopiedFiles() { return !clipboard.isEmpty(); }

    /* ------------------------------------------------------------- windows */

    public static FinderWindow newWindow(File dir) {
        boolean computer = dir == null
            && FinderSettings.newWindowTargetCode().sameAs(org.fractalmicro.foundation.FMString.of("PfCm"));
        if (dir == null) dir = FinderSettings.newWindowTarget();
        FinderWindow w = new FinderWindow(dir);
        Desktop.sharedDesktop().addWindow(w);
        if (computer) w.showComputer();
        return w;
    }

    /** The frontmost Finder window, skipping Info and preference windows. */
    public static FinderWindow frontWindow() {
        JInternalFrame active = Desktop.sharedDesktop().activeWindow();
        if (active instanceof FinderWindow) return (FinderWindow) active;
        for (JInternalFrame f : Desktop.sharedDesktop().windows()) {
            if (f instanceof FinderWindow && f.isVisible() && !f.isClosed()) return (FinderWindow) f;
        }
        return null;
    }

    /* --------------------------------------------------------------- open */

    public static void open(Node n) {
        if (n == null) return;
        if (n.kind == Node.Kind.TRASH) { openTrash(); return; }
        if (n.isVolume() && !n.isMounted()) { tell(NO_DISC); return; }
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
            FMAlert.tell(FMLocalized.filled(CANNOT_OPEN, bundle.displayName()),
                         FMLocalized.of(CANNOT_OPEN_WHY));
        }
        return true;
    }

    /** Show Package Contents: browse inside a bundle, as Finder allows. */
    public static void showPackageContents(Node n) {
        if (n == null || n.file == null || !org.fractalmicro.bundle.Bundle.looksLikeBundle(n.file)) {
            beep();
            return;
        }
        newWindow(n.file);
    }

    public static void openAll(List<Node> nodes) {
        for (Node n : nodes) open(n);
    }

    public static void launchApp(Node app) {
        if (app == null || app.file == null) { beep(); return; }
        Recent.noteItem(app.file);
        Running.note(app.name, app.file);
        Shell.open(app.file);
    }

    public static void openTrash() {
        FinderWindow w = new FinderWindow(null);
        w.showTrash();
        Desktop.sharedDesktop().addWindow(w);
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
        if (parent == null || !parent.isDirectory()) { beep(); return; }
        File f = FS.newFolder(parent);
        wayBack(UNDO_NEW_FOLDER, () -> removeAll(List.of(f)));
        refreshAll();
        rename(FS.node(f));
    }

    /**
     * The Finder's own way back.
     *
     * One for the program rather than one per window, because what it undoes is not in a
     * window: a file renamed in one is renamed in every window showing that folder and on
     * the desktop. Cocoa puts one on a document; the Finder's documents are the disk.
     */
    private static final org.fractalmicro.foundation.FMUndoManager UNDO =
        new org.fractalmicro.foundation.FMUndoManager();

    public static org.fractalmicro.foundation.FMUndoManager undoManager() { return UNDO; }

    /** Names an undo the way a menu will say it: Undo Rename, not Undo. */
    private static void wayBack(FMString name, Runnable how) {
        UNDO.registerUndo(name, how);
    }

    private static final FMString UNDO_RENAME = FMString.of("finder.undoRename");
    private static final FMString UNDO_NEW_FOLDER = FMString.of("finder.undoNewFolder");
    private static final FMString UNDO_DUPLICATE = FMString.of("finder.undoDuplicate");
    private static final FMString UNDO_MAKE_ALIAS = FMString.of("finder.undoMakeAlias");
    private static final FMString UNDO_MOVE = FMString.of("finder.undoMove");
    private static final FMString UNDO_COPY = FMString.of("finder.undoCopy");
    private static final FMString UNDO_LABEL = FMString.of("finder.undoLabel");

    public static void rename(Node n) {
        if (n == null || n.file == null) { beep(); return; }
        if (n.isVolume()) { tell(NO_VOLUME_RENAME); return; }
        String current = n.file.getName();
        String next = prompt(FMLocalized.of(RENAME_PROMPT), FMLocalized.of(RENAME_LABEL),
                             FMString.of(current), FMLocalized.of(RENAME_BUTTON));
        if (next == null || next.isBlank() || next.equals(current)) return;
        File dest = new File(n.file.getParentFile(), next);
        if (dest.exists()) {
            FMAlert.tell(FMLocalized.filled(NAME_TAKEN, FMString.of(next)),
                         FMLocalized.of(NAME_TAKEN_WHY));
            return;
        }
        if (!n.file.renameTo(dest)) {
            tell(RENAME_FAILED);
        } else {
            // Registered after it worked, and closing over what it needs rather than over
            // the node: the node describes a file that no longer has that name.
            File back = n.file;
            wayBack(UNDO_RENAME, () -> {
                if (dest.renameTo(back)) refreshAll(); else tell(RENAME_FAILED);
            });
        }
        refreshAll();
    }

    public static void duplicate(List<Node> nodes) {
        List<File> made = new ArrayList<>();
        for (Node n : nodes) {
            if (n.file == null) continue;
            try {
                made.add(FS.duplicate(n.file));
            } catch (IOException e) {
                tell(FMLocalized.filled(DUPLICATE_FAILED, FMString.of(n.name)));
            }
        }
        if (!made.isEmpty()) wayBack(UNDO_DUPLICATE, () -> removeAll(made));
        refreshAll();
    }

    /**
     * Takes back something this program made, which is the way back from making it.
     *
     * Deleted rather than put in the Trash. Undoing something that made a copy should leave
     * no trace of it, and a Trash with the copy in it is a trace: somebody would empty it
     * later and be told they were about to lose a file they never knowingly made.
     */
    private static void removeAll(List<File> files) {
        for (File one : files) {
            if (one == null || !one.exists()) continue;
            if (one.isDirectory()) deleteTree(one); else one.delete();
        }
        refreshAll();
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    /**
     * Make Alias. A real alias file: an empty data fork, the record in the resource
     * fork, and the alias flag in the Finder information. Not a symbolic link, which
     * needs rights this program should not be asking for and forgets its target the
     * moment the target moves.
     */
    public static void makeAlias(List<Node> nodes) {
        List<File> made = new ArrayList<>();
        for (Node n : nodes) {
            if (n.file == null) continue;
            try {
                made.add(org.fractalmicro.alias.Alias.create(n.file, null));
            } catch (IOException e) {
                tell(FMLocalized.filled(ALIAS_FAILED, FMString.of(e.getMessage())));
                break;
            }
        }
        if (!made.isEmpty()) {
            wayBack(UNDO_MAKE_ALIAS, () -> removeAll(made));
            refreshAll();
        }
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
        // What each one had, taken before anything is set, because afterwards it is gone.
        java.util.Map<File, Integer> was = new java.util.LinkedHashMap<>();
        for (Node n : nodes) {
            if (n.file != null) was.put(n.file, org.fractalmicro.fs.Labels.of(n.file));
        }
        for (Node n : nodes) {
            if (n.file == null) continue;
            everywhere &= org.fractalmicro.fs.Labels.set(n.file, label);
        }
        if (!was.isEmpty()) {
            wayBack(UNDO_LABEL, () -> {
                was.forEach((file, had) -> {
                    org.fractalmicro.fs.Labels.set(file, had);
                    org.fractalmicro.fs.Labels.forget(file);
                });
                refreshAll();
            });
        }
        // Nothing failed, so nothing is said. The label is set either way; where it is
        // kept is this program's business and not something to interrupt somebody about.
        if (!everywhere) {
            org.fractalmicro.core.Log.info("labels on this volume are kept beside the files");
        }
        refreshAll();
    }

    /** Show Original, following an alias, a symbolic link or a Windows shortcut. */
    public static void showOriginal(Node n) {
        if (n == null || n.file == null) { beep(); return; }
        File target = originalOf(n.file);
        try {
            if (target == null && Files.isSymbolicLink(n.file.toPath())) {
                target = Files.readSymbolicLink(n.file.toPath()).toFile();
            }
        } catch (IOException ignored) { }
        if (target == null) target = Apps.resolve(n.file);
        if (target == null || !target.exists() || target.equals(n.file)) {
            tell(FMLocalized.filled(NO_ORIGINAL, FMString.of(n.name)));
            return;
        }
        goTo(target.isDirectory() ? target : target.getParentFile());
    }

    public static void moveToTrash(List<Node> nodes) {
        List<File> files = new ArrayList<>();
        for (Node n : nodes) {
            if (n.isVolume()) { tell(NO_VOLUME_TRASH); continue; }
            if (n.file != null) files.add(n.file);
        }
        if (files.isEmpty()) { beep(); return; }
        if (!Trash.canMoveToTrash()) { tell(NO_TRASH); return; }
        int moved = Trash.moveToTrash(files);
        if (moved < files.size()) tell(SOME_NOT_TRASHED);
        refreshAll();
    }

    public static void emptyTrash(boolean secure) {
        if (Trash.isEmpty()) { beep(); return; }
        if (FinderSettings.warnOnEmptyTrash()) {
            boolean go = FMAlert.confirm(FMAlert.Kind.CAUTION,
                FMLocalized.of(EMPTY_TRASH_ASK),
                FMLocalized.of(EMPTY_TRASH_WHY),
                FMLocalized.of(secure ? SECURE_EMPTY_BUTTON : EMPTY_TRASH_BUTTON));
            if (!go) return;
        }
        Trash.empty(secure);
        refreshAll();
    }

    public static void copy(List<Node> nodes) {
        clipboard.clear();
        org.fractalmicro.foundation.FMMutableArray<org.fractalmicro.foundation.FMURL> urls =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (Node n : nodes) {
            if (n.file == null) continue;
            clipboard.add(n.file);
            urls.add(org.fractalmicro.foundation.FMURL.of(n.file));
        }
        if (clipboard.isEmpty()) { beep(); return; }
        org.fractalmicro.appkit.FMPasteboard.general().setFiles(urls.asArray());
    }

    public static void paste(File destination) {
        List<File> source = new ArrayList<>(clipboard);
        if (source.isEmpty()) source = clipboardFromSystem();
        if (source.isEmpty() || destination == null) { beep(); return; }
        for (File f : source) {
            try {
                File dest = new File(destination, f.getName());
                int i = 2;
                while (dest.exists()) dest = new File(destination, f.getName() + " " + (i++));
                if (f.isDirectory()) FS.copyTree(f.toPath(), dest.toPath());
                else Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception e) {
                tell(FMLocalized.filled(PASTE_FAILED, FMString.of(f.getName())));
            }
        }
        refreshAll();
    }

    /* ---------------------------------------------------------------- dropping */

    /**
     * What letting go of a drag does.
     *
     * One way in for every view and the desktop, because a drop means the same thing
     * wherever it lands: these files, into that folder, doing this. The view decided which
     * while the mouse was down. The way back is registered for the whole drop rather than
     * each file, since a person dropped one thing however many were in their hand.
     */
    public static boolean receiveDrop(List<File> files, File into, FMDragOperation how) {
        if (files == null || files.isEmpty() || into == null || how == FMDragOperation.NONE) {
            return false;
        }
        return switch (how) {
            case MOVE -> moveInto(files, into);
            case COPY -> copyInto(files, into);
            case LINK -> aliasInto(files, into);
            case NONE -> false;
        };
    }

    /** Moves files into a folder, remembering where each came from. */
    private static boolean moveInto(List<File> files, File into) {
        List<File[]> moved = new ArrayList<>();
        for (File f : files) {
            if (f == null || !f.exists()) continue;
            File dest = new File(into, f.getName());
            if (dest.exists()) {
                if (!replace(dest)) return finish(moved, UNDO_MOVE);
                // The one being replaced goes to the Trash rather than being written over.
                // Mac OS X writes over it, and that was always the sharpest edge in the
                // Finder: the one action in the whole program that destroyed a file with a
                // single click and no way back. There is no reason to copy that.
                Trash.moveToTrash(List.of(dest));
            }
            try {
                FS.moveTo(f, dest);
                moved.add(new File[]{dest, f});
            } catch (IOException e) {
                tell(FMLocalized.filled(MOVE_FAILED, FMString.of(f.getName())));
                return finish(moved, UNDO_MOVE);
            }
        }
        return finish(moved, UNDO_MOVE);
    }

    private static boolean replace(File existing) {
        return FMAlert.confirm(FMAlert.Kind.CAUTION,
            FMLocalized.filled(ALREADY_THERE, FMString.of(existing.getName())),
            FMLocalized.of(REPLACED_TO_TRASH),
            FMLocalized.of(REPLACE));
    }

    private static boolean finish(List<File[]> moved, FMString name) {
        if (!moved.isEmpty()) {
            List<File[]> what = new ArrayList<>(moved);
            wayBack(name, () -> {
                for (File[] pair : what) {
                    try {
                        if (pair[0].exists()) FS.moveTo(pair[0], pair[1]);
                    } catch (IOException e) {
                        tell(FMLocalized.filled(MOVE_FAILED, FMString.of(pair[0].getName())));
                    }
                }
                refreshAll();
            });
        }
        refreshAll();
        return !moved.isEmpty();
    }

    /**
     * Copies files into a folder.
     *
     * Into the folder they are already in as well, which is what Option-dragging onto its
     * own window means. That is why the name is asked for: a copy landing beside the
     * original has to be called something else.
     */
    private static boolean copyInto(List<File> files, File into) {
        List<File> made = new ArrayList<>();
        for (File f : files) {
            if (f == null || !f.exists()) continue;
            try {
                File dest = FS.freeNameIn(f, into);
                FS.copyTo(f, dest);
                made.add(dest);
            } catch (IOException e) {
                tell(FMLocalized.filled(PASTE_FAILED, FMString.of(f.getName())));
            }
        }
        if (!made.isEmpty()) wayBack(UNDO_COPY, () -> removeAll(made));
        refreshAll();
        return !made.isEmpty();
    }

    /** Makes an alias to each in a folder, which is what dragging with both keys means. */
    private static boolean aliasInto(List<File> files, File into) {
        List<File> made = new ArrayList<>();
        for (File f : files) {
            if (f == null || !f.exists()) continue;
            try {
                made.add(org.fractalmicro.alias.Alias.create(f, into));
            } catch (IOException e) {
                tell(FMLocalized.filled(ALIAS_FAILED, FMString.of(e.getMessage())));
                break;
            }
        }
        if (!made.isEmpty()) wayBack(UNDO_MAKE_ALIAS, () -> removeAll(made));
        refreshAll();
        return !made.isEmpty();
    }

    /** Files somebody copied in another program, which paste here as they would there. */
    private static List<File> clipboardFromSystem() {
        List<File> out = new ArrayList<>();
        for (org.fractalmicro.foundation.FMURL url
                : org.fractalmicro.appkit.FMPasteboard.general().files()) {
            out.add(url.asFile());
        }
        return out;
    }

    /** Compress: a zip archive beside the selection. */
    public static void compress(List<Node> nodes) {
        List<File> files = new ArrayList<>();
        for (Node n : nodes) if (n.file != null) files.add(n.file);
        if (files.isEmpty()) { beep(); return; }

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
                SwingUtilities.invokeLater(() -> tell(ARCHIVE_FAILED));
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
        if (added == 0) { tell(ONLY_FOLDERS); return; }
        refreshAll();
    }

    /**
     * The programs that can open what is selected, for the Open With menu.
     *
     * Asked of the file's type rather than its name, so an editor that declared
     * public.text is offered for a kind of text it never heard of. Empty for most files,
     * and the menu still has the host's default and Choose under it.
     */
    public static List<org.fractalmicro.bundle.Bundle> canOpen(List<Node> nodes) {
        Node first = first(nodes);
        if (first == null || first.file == null || first.file.isDirectory()) return List.of();
        return org.fractalmicro.bundle.LaunchServices.applicationsFor(first.file);
    }

    /** Opens the selection with one named program, which is what choosing one means. */
    public static void openWith(org.fractalmicro.bundle.Bundle program, List<Node> nodes) {
        List<File> files = new ArrayList<>();
        for (Node n : nodes) if (n.file != null) files.add(n.file);
        if (program == null || files.isEmpty()) { beep(); return; }
        org.fractalmicro.bundle.Bundles.openFiles(program.identifier().toString(), files);
    }

    public static void openWithChosen(List<Node> nodes) {
        if (nodes.isEmpty()) { beep(); return; }
        String programs = System.getenv("ProgramFiles");
        JFileChooser chooser = new JFileChooser(
            programs == null ? org.fractalmicro.fs.Volumes.systemDrive() : programs);
        chooser.setDialogTitle(FMLocalized.of(CHOOSE_APPLICATION).toString());
        if (chooser.showOpenDialog(Desktop.sharedDesktop()) != JFileChooser.APPROVE_OPTION) return;
        File app = chooser.getSelectedFile();
        List<String> command = new ArrayList<>();
        command.add(app.getAbsolutePath());
        for (Node n : nodes) if (n.file != null) command.add(n.file.getAbsolutePath());
        Running.note(stripExtension(app.getName()), app);
        Shell.launch(command.toArray(new String[0]));
    }

    public static void print(Node n) {
        if (n == null || n.file == null || !n.file.isFile()) { beep(); return; }
        try {
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            if (!d.isSupported(java.awt.Desktop.Action.PRINT)) {
                tell(NO_PRINTER);
                return;
            }
            d.print(n.file);
        } catch (Exception e) {
            tell(PRINT_FAILED);
        }
    }

    /* -------------------------------------------------------------- info */

    public static void getInfo(Node n) {
        if (n == null) { beep(); return; }
        Desktop.sharedDesktop().addWindow(new InfoWindow(n));
    }

    public static void eject(Node volume) {
        if (volume == null || !volume.isVolume()) { beep(); return; }
        String mount = volume.mountPoint == null
            ? volume.file.getAbsolutePath() : volume.mountPoint;
        Shell.async(() -> {
            boolean ok = Kernel32.ejectMedia(mount);
            SwingUtilities.invokeLater(() -> {
                if (!ok) tell(FMLocalized.filled(EJECT_FAILED, FMString.of(volume.name)));
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
        String path = Finder.prompt(FMLocalized.of(CONNECT_PROMPT), FMLocalized.of(CONNECT_LABEL),
                                    FMString.of("\\\\"), FMLocalized.of(CONNECT_BUTTON));
        if (path == null || path.isBlank()) return;
        File dir = new File(path);
        if (!dir.exists()) {
            FMAlert.tell(FMLocalized.filled(CONNECT_FAILED, FMString.of(path)),
                         FMLocalized.of(CONNECT_FAILED_WHY));
            return;
        }
        goTo(dir);
    }

    /* ------------------------------------------------------------ helpers */

    /**
     * The icons on the desktop, which are this program's view of a folder.
     *
     * The screen holds it, because the screen holds everything that is drawn, but it is
     * this program's and only this program knows what it is. Null before the desktop has
     * been taken over, and on a screen where it never was.
     */
    public static DesktopIcons desktopIcons() {
        Desktop d = Desktop.sharedDesktop();
        return d != null && d.icons() instanceof DesktopIcons view ? view : null;
    }

    /** Looks at the desktop folder again, when there is a view of it to look with. */
    public static void refreshDesktop() {
        DesktopIcons icons = desktopIcons();
        if (icons != null) icons.refresh();
    }

    public static void refreshAll() {
        SwingUtilities.invokeLater(() -> {
            Desktop d = Desktop.sharedDesktop();
            if (d == null) return;
            DesktopIcons icons = desktopIcons();
            if (icons != null) icons.refresh();
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
    /** A key that means nothing where it was pressed. No words: there are none to say. */
    public static void beep() { Desktop.beep(); }

    /**
     * Something went wrong that a person needs to know about, said in an alert.
     *
     * An alert rather than a line along the bottom, because a failure is a thing to be
     * dismissed rather than noticed. The difference is whether the program can be sure the
     * message was read.
     */
    public static void tell(FMString key) {
        FMAlert.tell(FMLocalized.of(key), FMString.EMPTY);
    }

    public static void tell(FMString message, FMString informative) {
        FMAlert.tell(message, informative);
    }

    /** Asks for one piece of text, with a button named for what it will do. */
    public static String prompt(FMString message, FMString fieldLabel, FMString initial,
                                FMString actionButton) {
        return FMAlert.ask(message, fieldLabel, initial, actionButton).toString();
    }

    /**
     * The contextual menu shared by the desktop and the Finder views.
     *
     * Most of its words come from the menu bar, because it offers the same commands and a
     * command translated twice is a command that can be translated two ways. The three
     * that name their own are the three a Mac words differently here.
     */
    public static JPopupMenu contextMenu(Supplier<List<Node>> selection, Supplier<File> folder) {
        JPopupMenu m = new JPopupMenu();
        m.add(item(inTheBar("open"), e -> openAll(selection.get())));
        m.add(item(FMLocalized.of(SHOW_PACKAGE_CONTENTS),
                   e -> showPackageContents(first(selection.get()))));
        m.add(item(inTheBar("getInfo"), e -> getInfo(first(selection.get()))));
        m.addSeparator();
        m.add(item(inTheBar("newFolder"), e -> newFolder(folder.get())));
        m.add(item(inTheBar("rename"), e -> rename(first(selection.get()))));
        m.add(item(inTheBar("duplicate"), e -> duplicate(selection.get())));
        m.add(item(inTheBar("makeAlias"), e -> makeAlias(selection.get())));
        m.add(labelMenu(() -> selection.get()));
        m.add(item(inTheBar("compress"), e -> compress(selection.get())));
        m.addSeparator();
        m.add(item(inTheBar("copy"), e -> copy(selection.get())));
        m.add(item(FMLocalized.of(PASTE_ITEM), e -> paste(folder.get())));
        m.addSeparator();
        m.add(item(inTheBar("moveToTrash"), e -> moveToTrash(selection.get())));
        m.add(item(inTheBar("eject"), e -> eject(first(selection.get()))));
        m.addSeparator();
        m.add(item(FMLocalized.of(SHOW_IN_EXPLORER), e -> {
            Node n = first(selection.get());
            if (n != null && n.file != null) FS.reveal(n.file);
        }));
        return m;
    }

    /**
     * What the menu bar calls a command.
     *
     * Empty before the bar has been read, which happens only in a checking run with no
     * menus, and an item with no words is better than a second copy of them here.
     */
    private static FMString inTheBar(String action) {
        return FinderMenus.titleFor(FMString.of(action));
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

    public static JMenuItem item(FMString text, java.awt.event.ActionListener a) {
        JMenuItem mi = new JMenuItem(text.toString());
        mi.addActionListener(a);
        return mi;
    }

    /* ------------------------------------------------- what the Finder says */

    private static final FMString NO_DISC = FMString.of("finder.noDisc");
    private static final FMString NO_VOLUME_RENAME = FMString.of("finder.noVolumeRename");
    private static final FMString RENAME_FAILED = FMString.of("finder.renameFailed");
    private static final FMString NO_VOLUME_TRASH = FMString.of("finder.noVolumeTrash");
    private static final FMString NO_TRASH = FMString.of("finder.noTrash");
    private static final FMString SOME_NOT_TRASHED = FMString.of("finder.someNotTrashed");
    private static final FMString ARCHIVE_FAILED = FMString.of("finder.archiveFailed");
    private static final FMString ONLY_FOLDERS = FMString.of("finder.onlyFolders");
    private static final FMString NO_PRINTER = FMString.of("finder.noPrinter");
    private static final FMString PRINT_FAILED = FMString.of("finder.printFailed");
    private static final FMString DUPLICATE_FAILED = FMString.of("finder.duplicateFailed");
    private static final FMString ALIAS_FAILED = FMString.of("finder.aliasFailed");
    private static final FMString NO_ORIGINAL = FMString.of("finder.noOriginal");
    private static final FMString PASTE_FAILED = FMString.of("finder.pasteFailed");
    private static final FMString CANNOT_OPEN = FMString.of("finder.cannotOpen");
    private static final FMString CANNOT_OPEN_WHY = FMString.of("finder.cannotOpenWhy");
    private static final FMString RENAME_PROMPT = FMString.of("finder.renamePrompt");
    private static final FMString RENAME_LABEL = FMString.of("finder.renameLabel");
    private static final FMString RENAME_BUTTON = FMString.of("finder.renameButton");
    private static final FMString NAME_TAKEN = FMString.of("finder.nameTaken");
    private static final FMString NAME_TAKEN_WHY = FMString.of("finder.nameTakenWhy");
    private static final FMString EMPTY_TRASH_ASK = FMString.of("finder.emptyTrashAsk");
    private static final FMString EMPTY_TRASH_WHY = FMString.of("finder.emptyTrashWhy");
    private static final FMString EMPTY_TRASH_BUTTON = FMString.of("finder.emptyTrashButton");
    private static final FMString SECURE_EMPTY_BUTTON = FMString.of("finder.secureEmptyTrashButton");
    private static final FMString CHOOSE_APPLICATION = FMString.of("finder.chooseApplication");
    private static final FMString CONNECT_PROMPT = FMString.of("finder.connectPrompt");
    private static final FMString CONNECT_LABEL = FMString.of("finder.connectLabel");
    private static final FMString CONNECT_BUTTON = FMString.of("finder.connectButton");
    private static final FMString CONNECT_FAILED = FMString.of("finder.connectFailed");
    private static final FMString CONNECT_FAILED_WHY = FMString.of("finder.connectFailedWhy");
    private static final FMString SHOW_PACKAGE_CONTENTS = FMString.of("finder.showPackageContents");
    private static final FMString PASTE_ITEM = FMString.of("finder.pasteItem");
    private static final FMString SHOW_IN_EXPLORER = FMString.of("finder.showInExplorer");
    private static final FMString MOVE_FAILED = FMString.of("finder.moveFailed");
    private static final FMString ALREADY_THERE = FMString.of("finder.alreadyThere");
    private static final FMString REPLACED_TO_TRASH = FMString.of("finder.replacedToTrash");
    private static final FMString REPLACE = FMString.of("finder.replace");
    private static final FMString EJECT_FAILED = FMString.of("finder.ejectFailed");

}
