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
package org.fractalmicro.windowserver;


import org.fractalmicro.appkit.AppFrame;
import org.fractalmicro.appkit.AppWindow;

import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.fs.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen itself: menu bar across the top, desktop with its icons and windows in
 * the middle, Dock along the bottom.
 */
public class Desktop extends JFrame {

    private static Desktop instance;

    public static Desktop get() { return instance; }

    /**
     * How big the screen is, or how big it is being asked to pretend to be.
     *
     * Normally the screen. A build drawing the desktop into a picture has no screen worth
     * the name and every reason to choose a size: the machine that runs the checks is
     * whatever the runner happened to be, and a picture of the desktop at whatever
     * resolution that machine had is not a picture of the desktop.
     */
    public static final String SCREEN_PROPERTY = "org.fractalmicro.screen";

    private static Rectangle screenBounds() {
        String asked = System.getProperty(SCREEN_PROPERTY, "");
        if (!asked.isBlank()) {
            int by = asked.indexOf('x');
            try {
                if (by > 0) {
                    int width = Integer.parseInt(asked.substring(0, by).trim());
                    int height = Integer.parseInt(asked.substring(by + 1).trim());
                    if (width > 0 && height > 0) return new Rectangle(0, 0, width, height);
                }
            } catch (NumberFormatException notASize) {
                // Said wrong, so not said. The real screen is the honest answer.
            }
            org.fractalmicro.core.Log.info("could not read " + SCREEN_PROPERTY
                                           + "=" + asked + "; using the screen");
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }

    /**
     * Ends a running program, by name.
     *
     * Everything with the same executable goes, because one program is usually several
     * processes and quitting means quitting. What a person calls the program comes from
     * the list of what is running, which is where the name they clicked came from.
     */
    public static void quitApplication(String name) {
        org.fractalmicro.core.Running.Entry entry = null;
        for (org.fractalmicro.core.Running.Entry e : org.fractalmicro.core.Running.all()) {
            if (e.name.equalsIgnoreCase(name)) entry = e;
        }
        if (entry == null) {
            beep(name + " is not running.");
            return;
        }
        String executable = withoutExtension(
            entry.launcher == null ? name : entry.launcher.getName())
            .toLowerCase(java.util.Locale.ROOT);
        ProcessHandle.allProcesses()
            .filter(p -> p.info().command()
                .map(c -> new java.io.File(c).getName()
                    .toLowerCase(java.util.Locale.ROOT).startsWith(executable))
                .orElse(false))
            .forEach(ProcessHandle::destroy);
        org.fractalmicro.core.Running.forget(name);
        org.fractalmicro.foundation.FMNotificationCenter.defaultCenter()
            .post(org.fractalmicro.foundation.FMNotificationCenter.PROGRAMS_CHANGED);
    }

    private static String withoutExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /**
     * Says something went wrong, out loud and in the status line.
     *
     * The sound and the line along the bottom of the screen belong to the screen. A
     * program that also has somewhere of its own to put the message says this first and
     * then adds its own.
     */
    public static void beep(String message) {
        java.awt.Toolkit.getDefaultToolkit().beep();
        Desktop d = get();
        if (d != null) d.setStatus(message);
    }

    private final JDesktopPane pane = new Surface();

    /**
     * What is shown on the desktop, once something has put something there.
     *
     * Nothing here, on purpose. On a Mac the desktop is a folder and the icons on it are a
     * view of that folder, drawn by the Finder, which is a program like any other. The
     * screen provides the back of itself and does not know what goes on it: this used to
     * hold a Finder class, which meant the layer that draws could not be built until the
     * file manager had been.
     *
     * Null until {@link #setIcons} is called, and a screen with nothing at the back of it
     * still works. That is the honest state of a machine whose file manager is not running.
     */
    private JComponent icons;
    private final Dock dock = new Dock();
    private final JLayeredPane stage = new Stage();
    private MainMenu menu;
    private String status = "";

    public Desktop() {
        super("Finder");
        instance = this;
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        pane.setDesktopManager(new DefaultDesktopManager());

        stage.add(pane, JLayeredPane.DEFAULT_LAYER);
        stage.add(dock, JLayeredPane.PALETTE_LAYER);

        setContentPane(new JPanel(new BorderLayout()));
        getContentPane().add(stage, BorderLayout.CENTER);

        menu = new MainMenu(this);
        separateWindows = org.fractalmicro.os.InterfaceStyle.separateWindows();
        // The one place the two systems disagree. Macintosh style puts the bar
        // along the top of the screen, owned by the front program; Windows style gives it
        // to this window like any other menu bar.
        if (!separateWindows || !org.fractalmicro.os.InterfaceStyle.screenMenuBar()) {
            setJMenuBar(menu);
        }

        setBounds(screenBounds());

        if (separateWindows) {
            // Back of the screen: wallpaper and icons, everything else in front of it.
            setAlwaysOnTop(false);
            stage.remove(dock);

        }

        getAccessibleContext().setAccessibleName("Finder");

        Shortcuts.install(this);

        // A preference changed in another process arrives as a notification; what it
        // does then is exactly what a change in this one does, because by the time
        // anything is told the value is already written and both are reading the same file.
        org.fractalmicro.foundation.FMDistributedNotificationCenter.defaultCenter().addObserver(
            FMUserDefaults.CHANGED, (name, about) -> {
                org.fractalmicro.foundation.FMArray<org.fractalmicro.foundation.FMString> parts =
                    about.split(org.fractalmicro.foundation.FMString.of(" "));
                if (parts.count() >= 2) {
                    FMUserDefaults.announce(parts.at(0).toString(), parts.at(1).toString());
                }
            });

        FMUserDefaults.onChange((domain, key) -> {
            if (FMUserDefaults.UNIVERSAL_ACCESS.equals(domain)) {
                Wallpaper.invalidate();
                repaint();
            }
        });
    }

    /**
     * The windows that are windows of the host system, one per open window, when this
     * system is running that way. Empty when everything lives inside this one frame.
     */
    private final List<AppFrame> frames = new ArrayList<>();
    private ScreenBar menuBar;
    private ScreenBar dockBar;
    private boolean separateWindows;

    public JDesktopPane pane() { return pane; }

    /** Whether each window is a window of the host system in its own right. */
    public boolean separateWindows() { return separateWindows; }
    /** What is at the back of the screen, or null when nothing has put anything there. */
    public JComponent icons() { return icons; }

    /** Puts the keyboard on it. Answers false when there is nothing there to put it on. */
    public boolean focusIcons() {
        return icons != null && icons.requestFocusInWindow();
    }

    /**
     * Puts a view at the back of the screen, behind every window.
     *
     * The file manager calls this at start-up with its view of the desktop folder. Called
     * again it replaces what was there, which is what happens when the file manager is
     * restarted and the old view belongs to a process that has gone.
     */
    public void setIcons(JComponent view) {
        if (icons != null) pane.remove(icons);
        icons = view;
        if (view != null) pane.add(view, Integer.valueOf(JLayeredPane.DEFAULT_LAYER - 1));
        stage.revalidate();
        pane.repaint();
    }
    public Dock dock() { return dock; }
    public MainMenu mainMenu() { return menu; }

    /** The status line. It is shown in windows; it is not bolted onto icon names. */
    public void setStatus(String text) {
        status = text == null ? "" : text;
    }

    /**
     * Brings this desktop in front of whatever program is there, which a shortcut
     * pressed inside another program has to do before it can show anything.
     */
    public void bringToFront() {
        toFront();
        long self = ProcessHandle.current().pid();
        for (org.fractalmicro.win.User32.Win w : org.fractalmicro.win.User32.windowsOfProcess(self)) {
            if (org.fractalmicro.win.User32.activate(w.handle)) break;
        }
        requestFocus();
    }

    /** Sends the keyboard to the Dock, remembering where it came from. */
    public void focusDock() {
        dock.takeFocus();
    }

    public String status() { return status; }

    /** Places a new window a little down and to the right of the last one. */
    public void addWindow(JInternalFrame frame) {
        if (separateWindows) {
            addAsScreenWindow(frame);
            return;
        }
        int offset = 22 * (pane.getAllFrames().length % 8);
        Dimension size = frame.getSize();
        if (size.width == 0) size = frame.getPreferredSize();
        frame.setBounds(60 + offset, 40 + offset, size.width, size.height);
        // However a window closes, focus has to land somewhere.
        frame.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                menu.windowsChanged();
                focusAfterClose();
                updateApplicationMenus();
            }
            @Override public void internalFrameActivated(javax.swing.event.InternalFrameEvent e) {
                updateApplicationMenus();
            }
        });
        pane.add(frame);
        frame.setVisible(true);
        try {
            frame.setSelected(true);
        } catch (java.beans.PropertyVetoException ignored) { }
        menu.windowsChanged();
        updateApplicationMenus();
        focusInto(frame);
    }

    /**
     * Hands the menu bar to the program that owns the front window, and back to Finder
     * when the front window is Finder's or there is none.
     */
    public void updateApplicationMenus() {
        JInternalFrame front = activeWindow();
        // Rebuilding for a window that already owns the bar discards the menus it holds,
        // so only rebuild when the front window actually changed.
        if (front == menuOwner) return;
        menuOwner = front;
        if (front instanceof AppWindow app) {
            menu.setApplication(app.applicationName(), app.applicationMenus());
        } else {
            menu.showFinderMenus();
        }
    }

    /** Quits a program by closing every window belonging to it. */
    public void closeWindowsOf(String application) {
        for (JInternalFrame f : windows()) {
            boolean mine = f instanceof AppWindow app
                ? app.applicationName().equals(application)
                : "Finder".equals(application);
            if (mine) f.doDefaultCloseAction();
        }
        updateApplicationMenus();
    }

    /** The window whose program currently owns the menu bar. */
    private JInternalFrame menuOwner;

    /**
     * Puts the strips on the screen and reserves their edges. Called once the desktop is
     * showing, because a window has to exist before the shell can be told about it.
     */
    public void openScreen() {
        if (!separateWindows) return;
        if (org.fractalmicro.os.InterfaceStyle.screenMenuBar()) {
            menuBar = new ScreenBar(this, menu, org.fractalmicro.win.AppBar.ABE_TOP,
                                    ScreenBar.menuBarHeight(), "Fractal Menu Bar",
                                    "Menu bar");
            menuBar.show(true);
        }
        JPanel dockHolder = new JPanel(new java.awt.FlowLayout(
            java.awt.FlowLayout.CENTER, 0, 0));
        dockHolder.setOpaque(false);
        dockHolder.add(dock);
        dockBar = new ScreenBar(this, dockHolder, org.fractalmicro.win.AppBar.ABE_BOTTOM,
                                dock.getPreferredSize().height + 8, "Fractal Dock", "Dock");
        dockBar.show(true);

        // Local shortcuts go through the focus manager rather than a window binding, so
        // they already work whichever window is in front. Where they send focus does not
        // follow automatically; that is handled below.
    }

    /** Gives the reserved edges back. Left claimed, they would shrink the desktop. */
    public void closeScreen() {
        if (menuBar != null) menuBar.release();
        if (dockBar != null) dockBar.release();
    }

    /** The strip holding the menu bar, when there is one. */
    public ScreenBar menuBarWindow() { return menuBar; }

    /** The strip holding the Dock, when it has one of its own. */
    public ScreenBar dockWindow() { return dockBar; }

    /**
     * Brings the window holding the menu bar forward and gives it the keyboard. In one
     * window there is nothing to do; in separate windows the bar is somewhere else, and
     * selecting a menu in a window that is not in front does nothing at all.
     */
    public void focusMenuBarWindow() {
        if (menuBar == null) {
            requestFocus();
            return;
        }
        menuBar.toFront();
        menuBar.requestFocus();
        menu.requestFocusInWindow();
    }

    /** The same for the Dock, which is also a window of its own when windows are separate. */
    public void focusDockWindow() {
        if (dockBar == null) {
            focusDock();
            return;
        }
        dockBar.toFront();
        dockBar.requestFocus();
        dock.requestFocusInWindow();
    }

    /** Called when one of the host system's windows belonging to this program comes up. */
    public void frameActivated(AppFrame frame) {
        if (frame == null) return;
        frames.remove(frame);
        frames.add(0, frame);
        menu.windowsChanged();
        updateApplicationMenus();
    }

    /**
     * The same window, wrapped in a host window. The class, title bar and accessible tree
     * are unchanged; the difference is that the host system now knows it exists.
     */
    private void addAsScreenWindow(JInternalFrame window) {
        AppFrame frame = new AppFrame(window);
        frame.placeAt(frames.size(), ScreenBar.workArea());
        window.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                frames.remove(frame);
                menu.windowsChanged();
                focusAfterClose();
                updateApplicationMenus();
            }
        });
        frames.add(0, frame);
        frame.setVisible(true);
        frame.toFront();
        menu.windowsChanged();
        updateApplicationMenus();
        focusInto(window);
    }

    public List<JInternalFrame> windows() {
        List<JInternalFrame> out = new ArrayList<>();
        for (AppFrame frame : frames) {
            if (!frame.window().isClosed()) out.add(frame.window());
        }
        for (JInternalFrame f : pane.getAllFrames()) out.add(f);
        return out;
    }

    /** The host system window carrying one of this system's windows, if there is one. */
    public AppFrame frameOf(JInternalFrame window) {
        for (AppFrame frame : frames) {
            if (frame.window() == window) return frame;
        }
        return null;
    }

    /**
     * Moves focus into a newly opened window. Without it the window is drawn and the
     * keyboard is still in the last one, so typing goes somewhere nobody is looking.
     */
    public Component focusInto(JInternalFrame frame) {
        Component target = null;
        // A window that knows better than its focus policy says so. A file browser opens
        // on its files, not on the search field at the end of its toolbar.
        if (frame instanceof org.fractalmicro.appkit.KeyWindow window) {
            target = window.initialFirstResponder();
        }
        if (target == null) {
            java.awt.FocusTraversalPolicy policy = frame.getFocusTraversalPolicy();
            if (policy != null) target = policy.getDefaultComponent(frame);
        }
        if (target == null) target = frame.getMostRecentFocusOwner();
        if (target == null) target = frame;
        final Component chosen = target;
        SwingUtilities.invokeLater(() -> {
            if (!chosen.requestFocusInWindow()) chosen.requestFocus();
        });
        return chosen;
    }

    /**
     * The window commands apply to. Usually the selected frame; when nothing has been
     * selected yet, the frontmost visible one stands in.
     */
    public JInternalFrame activeWindow() {
        // With host windows, the front one is whichever the host last reported.
        for (AppFrame frame : frames) {
            if (!frame.window().isClosed()) return frame.window();
        }
        JInternalFrame selected = pane.getSelectedFrame();
        if (selected != null && selected.isVisible() && !selected.isClosed()) return selected;
        for (JInternalFrame f : pane.getAllFrames()) {
            if (f.isVisible() && !f.isClosed() && !f.isIcon()) return f;
        }
        return null;
    }

    /** Cycles forward or backward through open windows, like Command backtick. */
    public void cycleWindows(boolean forward) {
        JInternalFrame[] all = pane.getAllFrames();
        if (all.length == 0) return;
        List<JInternalFrame> visible = new ArrayList<>();
        for (JInternalFrame f : all) if (f.isVisible() && !f.isIcon()) visible.add(f);
        if (visible.isEmpty()) return;
        JInternalFrame current = pane.getSelectedFrame();
        int idx = visible.indexOf(current);
        int next = idx < 0 ? 0 : (idx + (forward ? 1 : visible.size() - 1)) % visible.size();
        JInternalFrame target = visible.get(next);
        target.toFront();
        try {
            target.setSelected(true);
        } catch (java.beans.PropertyVetoException ignored) { }
    }

    public void closeFrontWindow() {
        JInternalFrame f = activeWindow();
        if (f == null) { beep("No window is open."); return; }
        f.doDefaultCloseAction();
        menu.windowsChanged();
        focusAfterClose();
    }

    /**
     * Puts the keyboard somewhere sensible once a window has gone: the next window if
     * there is one, otherwise the desktop. Returns what it aimed at, so the keyboard
     * test can check it.
     */
    public Component focusAfterClose() {
        JInternalFrame next = activeWindow();
        if (next != null) {
            try {
                next.setSelected(true);
            } catch (java.beans.PropertyVetoException ignored) { }
            Component view = next.getMostRecentFocusOwner();
            Component target = view != null ? view : next;
            target.requestFocusInWindow();
            return target;
        }
        // The desktop itself when there is nothing at the back of it, so that the keyboard
        // lands somewhere rather than nowhere.
        Component back = icons != null ? icons : pane;
        back.requestFocusInWindow();
        return back;
    }

    public void minimizeFrontWindow() {
        JInternalFrame f = activeWindow();
        if (f == null || !f.isIconifiable()) { beep("No window to minimize"); return; }
        try { f.setIcon(true); } catch (java.beans.PropertyVetoException ignored) { }
    }

    public void zoomFrontWindow() {
        JInternalFrame f = activeWindow();
        if (f == null) { beep("No window to zoom"); return; }
        try { f.setMaximum(!f.isMaximum()); } catch (java.beans.PropertyVetoException ignored) { }
    }

    public void hideAllWindows() {
        for (JInternalFrame f : pane.getAllFrames()) f.setVisible(false);
        if (icons != null) icons.requestFocusInWindow();
        setStatus("All windows hidden");
    }

    public void hideOtherWindows() {
        JInternalFrame front = activeWindow();
        for (JInternalFrame f : pane.getAllFrames()) if (f != front) f.setVisible(false);
        setStatus("Other windows hidden");
    }

    public void showAllWindows() {
        for (JInternalFrame f : pane.getAllFrames()) {
            f.setVisible(true);
            try { if (f.isIcon()) f.setIcon(false); } catch (java.beans.PropertyVetoException ignored) { }
        }
        setStatus("All windows shown");
    }

    /** The desktop background, with the wallpaper painted behind everything. */
    private static class Surface extends JDesktopPane {
        Surface() {
            setOpaque(true);
            setBackground(new Color(0x1A0B3D));
            setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
            getAccessibleContext().setAccessibleName("Desktop background");
        }

        @Override protected void paintComponent(Graphics g) {
            Wallpaper.paint((Graphics2D) g, getWidth(), getHeight());
        }
    }

    /** Keeps the desktop filling the screen and the Dock centred at the bottom. */
    private class Stage extends JLayeredPane {
        @Override public void doLayout() {
            int w = getWidth(), h = getHeight();
            pane.setBounds(0, 0, w, h);
            if (icons != null) icons.setBounds(0, 0, w, h - dockHeight());
            Dimension d = dock.getPreferredSize();
            int dw = Math.min(d.width, w - 40);
            dock.setBounds((w - dw) / 2, h - d.height, dw, d.height);
        }

        private int dockHeight() { return dock.getPreferredSize().height + 6; }
    }
}
