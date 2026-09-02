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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.bundle.LaunchServices;

import org.fractalmicro.appkit.FMAlert;
import org.fractalmicro.appkit.AppWindow;

import org.fractalmicro.os.DockSettings;
import org.fractalmicro.core.Recent;
import org.fractalmicro.fs.*;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;
import org.fractalmicro.nib.NibLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu bar. Command lives on the Alt key and Option on the Windows key, because
 * that is where they sit on a PC keyboard; the menus spell the shortcuts out with the
 * Mac symbols and Swing reports the real key names to assistive software.
 */
public class MainMenu extends JMenuBar {

    /** The program that draws the preference panes. Named rather than linked. */
    private static final String SYSTEM_PREFERENCES = "org.fractalmicro.systempreferences";

    /** The program that shows files. Named, never linked. */

    public static final int CMD = InputEvent.ALT_DOWN_MASK;
    public static final int OPT = InputEvent.META_DOWN_MASK;
    public static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    public static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Desktop desktop;
    private JMenu windowMenu = new JMenu("Window");
    private int fixedWindowItems;
    private final JMenu recentItems = new JMenu("Recent Items");
    private final JMenu recentFolders = new JMenu("Recent Folders");

    /** The bar’s own interface file, in AppKit’s framework, and what it read. */
    private static final FMString FRAMEWORK = FMString.of("AppKit");
    private static final FMString INTERFACE = FMString.of("MainMenu");
    private NibLoader loaded;
    private final List<JMenu> trayMenus = new ArrayList<>();
    private JMenuItem quickLookItem;
    private JMenuItem compressItem;
    private JMenuItem toolbarItem;
    private JMenuItem pathBarItem;
    private JMenuItem statusBarItem;
    private JMenuItem sidebarItem;
    private int firstStatusIndex;

    /* The menus themselves, kept so the bar can be laid out again when the front
       window belongs to another program. */
    private JMenu appleMenu;
    private JMenu helpMenuCache;

    /* The program whose menus show when no other program owns the bar. On this system
       that is the Finder, but the bar does not know that: it is told, at start-up, by
       whichever program is the one a person comes back to. */
    private String defaultApplication = "Finder";
    private List<JMenu> defaultMenus = new ArrayList<>();
    private final List<JMenu> statusMenus = new ArrayList<>();
    private String currentApplication = "Finder";
    private List<JMenu> applicationMenus = new ArrayList<>();

    public MainMenu(Desktop desktop) {
        this.desktop = desktop;
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        setOpaque(true);
        getAccessibleContext().setAccessibleName("Menu bar");

        readTheBar();
        buildWindowMenu();

        applyMenus();
        org.fractalmicro.win.TrayHost.onChange(this::rebuildTrayMenus);

    }


    /* --------------------------------------------------------------- menus */

    /**
     * Reads the bar’s own menus out of its interface file.
     *
     * The Fractal menu, Window and Help belong to the desktop rather than to any program
     * in it, so they are AppKit’s and live in AppKit’s framework beside its other
     * resources. Every program on the volume shows them, and a translation of them belongs
     * where they do rather than being copied into each program that displays one.
     *
     * A bar that could not read its file is a bar with nothing in it, which is worth
     * saying: the menu with Shut Down in it is not something to fail quietly about.
     */
    private void readTheBar() {
        try {
            loaded = NibLoader.inFramework(FRAMEWORK, INTERFACE);
        } catch (java.io.IOException notThere) {
            org.fractalmicro.core.Log.info("the menu bar could not be read: "
                                           + notThere.getMessage());
            appleMenu = new JMenu("Fractal");
            helpMenuCache = new JMenu("Help");
            return;
        }
        List<JMenu> menus = loaded.menus(new BarCommands());
        appleMenu = menus.isEmpty() ? new JMenu("Fractal") : menus.get(0);
        if (menus.size() > 1) {
            windowMenu = menus.get(1);
            fixedWindowItems = windowMenu.getItemCount();
        }
        appleMenu.setIcon(new LogoIcon());
        appleMenu.getAccessibleContext().setAccessibleName(appleMenu.getText());
        helpMenuCache = menus.size() > 2 ? menus.get(2) : new JMenu("Help");

        // Two things in this bar are lists rather than commands: what was opened recently,
        // and what is open now. Neither can be written down, so they are put in afterwards
        // at the place the file says they go.
        appleMenu.add(recentItems, indexAfter(appleMenu, "dockPreferences") + 1);
        rebuildRecents();
        nameTheAccount();
        showMagnification();
    }

    /** Where an item sits, so something can be put in beside it. */
    private int indexAfter(JMenu menu, String action) {
        JMenuItem item = loaded == null ? null : loaded.item(FMString.of(action));
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (menu.getItem(i) == item) return i;
        }
        return menu.getItemCount() - 1;
    }

    /**
     * Log Out names whoever is logged in.
     *
     * The file says the command with no name in it, because the name is not something a
     * file can know. %1$@ is where it goes, which is not the same place in every language.
     */
    private void nameTheAccount() {
        JMenuItem out = loaded == null ? null : loaded.item(FMString.of("logOut"));
        if (out == null) return;
        out.setText(FMLocalized.filled(LOG_OUT_NAMED,
            FMString.of(System.getProperty("user.name", ""))).toString());
    }

    /** The Dock item says which way it would turn magnification, so it follows the setting. */
    private void showMagnification() {
        JMenuItem item = loaded == null ? null : loaded.item(FMString.of("dockMagnification"));
        if (item == null) return;
        boolean on = DockSettings.magnification();
        item.setText(FMLocalized.of(on ? MAGNIFY_OFF : MAGNIFY_ON).toString());
        if (item instanceof JCheckBoxMenuItem box) box.setSelected(on);
    }

    /**
     * Everything the desktop’s own menus do, by the name each command sends.
     *
     * The three that end a session go through one place, because what they have in common
     * is the part worth getting right: they ask first, they name the smaller option, and
     * they say so when the host refuses.
     */
    private final class BarCommands implements NibLoader.Commands {
        @Override public void perform(FMString action) {
            switch (action.toString()) {
                case "aboutThisComputer" -> AboutWindow.showAboutComputer();
                case "softwareUpdate" -> Desktop.beep(FMLocalized.of(UP_TO_DATE).toString());
                case "fractalSoftware" -> Desktop.beep(FMLocalized.of(NO_SOFTWARE_PAGE).toString());
                case "systemPreferences", "dockPreferences" ->
                    Bundles.openPart(SYSTEM_PREFERENCES, "system");
                case "dockMagnification" -> {
                    DockSettings.setMagnification(!DockSettings.magnification());
                    showMagnification();
                }
                case "forceQuit" -> ForceQuitWindow.open();
                case "sleep" -> org.fractalmicro.win.Session.sleep();
                case "lockScreen" -> org.fractalmicro.win.Session.lock();
                case "restart" -> confirmSession(RESTART_QUESTION, RESTART_VERB,
                    () -> org.fractalmicro.win.Session.restart(false));
                case "shutDown" -> confirmSession(SHUT_DOWN_QUESTION, SHUT_DOWN_VERB,
                    () -> org.fractalmicro.win.Session.shutDown(false));
                case "logOut" -> confirmSession(LOG_OUT_QUESTION, LOG_OUT_VERB,
                    () -> org.fractalmicro.win.Session.logOut(false));

                case "minimize" -> desktop.minimizeFrontWindow();
                case "zoom" -> desktop.zoomFrontWindow();
                case "cycleWindows" -> desktop.cycleWindows(true);
                case "bringAllToFront" -> desktop.showAllWindows();

                case "help" -> HelpWindow.openHelp();
                case "keyboardShortcuts" -> HelpWindow.showShortcuts();

                default -> org.fractalmicro.core.Log.info(
                    "the menu bar asks for " + action + ", which it does not do");
            }
        }

        @Override public boolean isOn(FMString action) {
            return FMString.of("dockMagnification").sameAs(action) && DockSettings.magnification();
        }
    }

    /* ------------------------------------------------------- what the bar says */

    private static final FMString UP_TO_DATE = FMString.of("system.upToDate");
    private static final FMString NO_SOFTWARE_PAGE = FMString.of("system.noSoftwarePage");
    private static final FMString MAGNIFY_ON = FMString.of("system.turnMagnificationOn");
    private static final FMString MAGNIFY_OFF = FMString.of("system.turnMagnificationOff");
    private static final FMString LOG_OUT_NAMED = FMString.of("system.logOutNamed");
    private static final FMString RESTART_QUESTION = FMString.of("system.restartQuestion");
    private static final FMString RESTART_VERB = FMString.of("system.restart");
    private static final FMString SHUT_DOWN_QUESTION = FMString.of("system.shutDownQuestion");
    private static final FMString SHUT_DOWN_VERB = FMString.of("system.shutDown");
    private static final FMString LOG_OUT_QUESTION = FMString.of("system.logOutQuestion");
    private static final FMString LOG_OUT_VERB = FMString.of("system.logOut");
    private static final FMString CLOSES_EVERYTHING = FMString.of("system.closesEverything");
    private static final FMString CLOSES_EVERYTHING_TOO = FMString.of("system.closesEverythingToo");
    private static final FMString QUIT_DESKTOP = FMString.of("system.quitDesktop");
    private static final FMString REFUSED = FMString.of("system.hostRefused");
    private static final FMString NO_WINDOWS = FMString.of("system.noWindows");
    private static final FMString WOULD_NOT = FMString.of("system.hostWouldNot");

    /**
     * Asks before restarting, shutting down or logging out.
     *
     * These do the real thing to the machine, so the dialog says so plainly and offers the
     * smaller option of closing this program instead. The action button is named for what
     * it does and sits rightmost; Cancel is beside it; quitting only this desktop is the
     * third choice, kept away from the other two.
     */
    private void confirmSession(FMString question, FMString verb,
                                java.util.function.BooleanSupplier action) {
        FMString detail = FMLocalized.of(org.fractalmicro.win.Session.actingAsShell()
            ? CLOSES_EVERYTHING : CLOSES_EVERYTHING_TOO);
        // Both of the answers that are not Cancel end something that cannot be brought
        // back, so this asks the way such things are asked: Cancel is the default.
        int choice = FMAlert.confirmIrreversible(FMLocalized.of(question), detail,
                                               FMLocalized.of(verb),
                                               FMLocalized.of(QUIT_DESKTOP));
        if (choice == 0) {
            if (!action.getAsBoolean()) {
                FMAlert.tell(FMLocalized.filled(WOULD_NOT, FMLocalized.of(verb)),
                           FMLocalized.of(REFUSED));
            }
        } else if (choice == 2) {
            System.exit(0);
        }
    }







    /* ------------------------------------------------- the front program */

    /**
     * Puts the menus of the program that owns the front window into the bar.
     *
     * The bar belongs to whichever program is in front, so its own menus replace
     * Finder's: the program's name takes the second slot, and the menus it hands over
     * sit between that and Window. Passing no name gives Finder the bar back.
     */
    public void setApplication(String name, List<JMenu> menus) {
        String wanted = name == null || name.isBlank() ? defaultApplication : name;
        List<JMenu> extras = menus == null ? new ArrayList<>() : new ArrayList<>(menus);
        if (wanted.equals(currentApplication) && extras.equals(applicationMenus)) return;
        currentApplication = wanted;
        applicationMenus = extras;
        applyMenus();
    }

    /** Gives the bar back to the program it falls back to, when a front window closes. */
    public void showDefaultMenus() {
        setApplication(null, null);
    }

    /** Kept for callers that still say it the old way. */
    public void showFinderMenus() { showDefaultMenus(); }

    /**
     * Says which program owns the bar when nothing else does, and what its menus are.
     *
     * The bar is furniture: it holds whatever the program in front hands it. One program
     * is the one a person comes back to, and it says so here rather than being written
     * into the bar by name.
     */
    public void setDefaultApplication(String name, List<JMenu> menus) {
        defaultApplication = name == null || name.isBlank() ? "Finder" : name;
        defaultMenus = menus == null ? new ArrayList<>() : new ArrayList<>(menus);
        if (currentApplication == null || currentApplication.isBlank()) {
            currentApplication = defaultApplication;
        }
        applyMenus();
    }

    /** The name shown in the second slot: the program the bar currently belongs to. */
    public String currentApplication() { return currentApplication; }

    /**
     * Lays the bar out from scratch. Keeping it in one place keeps the index the status
     * items start at correct, which is how the keyboard reaches them.
     */
    private void applyMenus() {
        removeAll();
        trayMenus.clear();

        add(appleMenu);
        if (isDefaultInFront()) {
            for (JMenu m : defaultMenus) add(m);
        } else {
            add(applicationNameMenu());
            for (JMenu m : applicationMenus) add(m);
        }
        add(windowMenu);
        add(helpMenuCache);

        add(Box.createHorizontalGlue());
        firstStatusIndex = getMenuCount();
        for (JMenu m : statusMenus) add(m);
        rebuildTrayMenus();
        // Every shortcut in the bar can now say what it does, in the bar's own words.
        org.fractalmicro.a11y.Announcer.learn(this);
        // Laid out here and not merely marked for it. A menu that has never been laid out
        // has no size, and a bar that is not on a screen is never asked to lay itself out,
        // so the menus of a program in front were in the bar and painted nothing.
        revalidate();
        validate();
        doLayout();
        repaint();
    }

    /** Whether the bar is showing the program it falls back to. */
    private boolean isDefaultInFront() {
        return defaultApplication.equals(currentApplication);
    }

    /**
     * The program menu of anything that is not Finder. It carries the few items every
     * program has: what it is, its settings, and how to leave it.
     */
    private JMenu applicationNameMenu() {
        JMenu m = new JMenu(currentApplication);
        m.getAccessibleContext().setAccessibleName(currentApplication);
        Font f = m.getFont();
        if (f != null) m.setFont(f.deriveFont(Font.BOLD));
        m.add(item("About " + currentApplication, null,
                   e -> AboutWindow.showAboutApplication(currentApplication)));
        m.addSeparator();
        m.add(item("Preferences…", KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, CMD),
                   e -> {
                       javax.swing.JInternalFrame front = desktop.activeWindow();
                       if (front instanceof AppWindow app) app.showPreferences();
                       else Bundles.openPart(SYSTEM_PREFERENCES, "system");
                   }));
        m.addSeparator();
        m.add(item("Hide " + currentApplication, KeyStroke.getKeyStroke(KeyEvent.VK_H, CMD),
                   e -> desktop.hideAllWindows()));
        m.add(item("Hide Others", KeyStroke.getKeyStroke(KeyEvent.VK_H, CMD | OPT),
                   e -> desktop.hideOtherWindows()));
        m.add(item("Show All", null, e -> desktop.showAllWindows()));
        m.addSeparator();
        m.add(item("Quit " + currentApplication, KeyStroke.getKeyStroke(KeyEvent.VK_Q, CMD),
                   e -> desktop.closeWindowsOf(currentApplication)));
        return m;
    }

    /**
     * The notification area, as menus at the left of the status items. Each one is
     * named for the program's tooltip, so what it is called is what it is rather than
     * describing a picture.
     */
    public void rebuildTrayMenus() {
        for (JMenu menu : trayMenus) remove(menu);
        trayMenus.clear();

        int at = firstStatusIndex;
        for (org.fractalmicro.win.TrayHost.Icon icon : org.fractalmicro.win.TrayHost.icons()) {
            JMenu menu = new JMenu(icon.name());
            menu.setIcon(new TrayIcon(icon));
            menu.setText("");
            menu.getAccessibleContext().setAccessibleName(icon.name());
            menu.add(item("Open", null, e -> org.fractalmicro.win.TrayHost.click(icon, false)));
            menu.add(item("Show Menu", null, e -> org.fractalmicro.win.TrayHost.click(icon, true)));
            add(menu, at++);
            trayMenus.add(menu);
        }
        revalidate();
        repaint();
    }

    /** A notification icon drawn from the handle its program handed over. */
    private static class TrayIcon implements Icon {
        private static final int SIZE = 16;
        private final org.fractalmicro.win.TrayHost.Icon icon;
        private java.awt.Image image;

        TrayIcon(org.fractalmicro.win.TrayHost.Icon icon) { this.icon = icon; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            if (image == null) image = org.fractalmicro.win.IconImages.of(icon.iconHandle, SIZE);
            if (image != null) {
                g.drawImage(image, x, y + 3, null);
                return;
            }
            g.setColor(Aqua.highContrast() ? Color.WHITE : new Color(0x555555));
            g.fillOval(x + 4, y + 7, 8, 8);
        }

        @Override public int getIconWidth() { return SIZE + 4; }
        @Override public int getIconHeight() { return SIZE + 6; }
    }

    /* ------------------------------------------------------- status menus */







    /** Menu-bar index where the status items begin; used by the focus shortcut. */
    public int firstStatusIndex() { return firstStatusIndex; }

    /* ------------------------------------------------------------ helpers */











    public void windowsChanged() { buildWindowMenu(); }

    /**
     * The Window menu: written down at the top, and what is open underneath.
     *
     * The commands are read from the file once and left alone. Everything after the mark
     * is the list of open windows, which is rebuilt whenever one appears or goes, and
     * which no file could hold: it is what is true on the screen this second.
     */
    private void buildWindowMenu() {
        while (windowMenu.getItemCount() > fixedWindowItems) {
            windowMenu.remove(windowMenu.getItemCount() - 1);
        }
        windowMenu.addSeparator();
        List<JInternalFrame> frames = desktop.windows();
        if (frames.isEmpty()) {
            JMenuItem none = new JMenuItem(FMLocalized.of(NO_WINDOWS).toString());
            none.setEnabled(false);
            windowMenu.add(none);
        }
        for (JInternalFrame f : frames) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(f.getTitle());
            mi.setSelected(f == desktop.activeWindow());
            mi.addActionListener(e -> {
                f.toFront();
                try { f.setSelected(true); } catch (java.beans.PropertyVetoException ignored) { }
            });
            windowMenu.add(mi);
        }
    }



    public void rebuildRecents() {
        recentItems.removeAll();
        recentFolders.removeAll();
        List<File> items = Recent.items();
        if (items.isEmpty()) {
            JMenuItem none = new JMenuItem("None");
            none.setEnabled(false);
            recentItems.add(none);
        }
        for (File f : items) {
            recentItems.add(item(f.getName(), null, e -> LaunchServices.open(f)));
        }
        List<File> folders = Recent.folders();
        if (folders.isEmpty()) {
            JMenuItem none = new JMenuItem("None");
            none.setEnabled(false);
            recentFolders.add(none);
        }
        for (File f : folders) {
            recentFolders.add(item(f.getName().isEmpty() ? f.getPath() : f.getName(), null,
                                   e -> LaunchServices.openFolder(f)));
        }
        recentItems.addSeparator();
        recentItems.add(item("Clear Menu", null, e -> { Recent.clear(); rebuildRecents(); }));
    }
    /**
     * Puts the indicators at the right of the bar.
     *
     * The bar does not make these and does not know what they are. They come from
     * {@link SystemUIServer}, which loads them out of their own bundles, and the bar only
     * has to know that they go at the right and that the keyboard reaches them after
     * everything else.
     */
    public void setStatusItems(java.util.List<JMenu> items) {
        statusMenus.clear();
        if (items != null) statusMenus.addAll(items);
        applyMenus();
    }

    /** The Recent Folders menu, which the bar fills in and a program may show. */
    public JMenu recentFoldersMenu() { return recentFolders; }

    /** A menu item with a key and something to do. Public: programs build menus too. */
    public static JMenuItem item(String text, KeyStroke accel, ActionListener action) {
        JMenuItem mi = new JMenuItem(text);
        if (accel != null) mi.setAccelerator(accel);
        mi.addActionListener(action);
        return mi;
    }

    /** A menu item that shows whether something is on. */
    public static JCheckBoxMenuItem check(String text, KeyStroke accel, boolean on,
                                          ActionListener action) {
        JCheckBoxMenuItem mi = new JCheckBoxMenuItem(text, on);
        if (accel != null) mi.setAccelerator(accel);
        mi.addActionListener(action);
        return mi;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Aqua.paintMenuBar((Graphics2D) g, getWidth(), getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Aqua.MENU_BAR_HEIGHT);
    }

    /** The company mark in the corner, from the artwork when it is installed. */
    private static class LogoIcon implements Icon {
        private static final int HEIGHT = 15;

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean selected = c instanceof JMenu && ((JMenu) c).isSelected();
            Color colour = selected || Aqua.highContrast() ? Color.WHITE : new Color(0x2A2A2A);
            Image mark = org.fractalmicro.theme.BrandMark.image(HEIGHT, colour);
            if (mark != null) {
                g.drawImage(mark, x, y + 3, null);
                return;
            }
            Icons.paintLogo((Graphics2D) g, x, y, 14, colour);
        }

        @Override public int getIconWidth() {
            return org.fractalmicro.theme.BrandMark.available()
                ? org.fractalmicro.theme.BrandMark.widthFor(HEIGHT) : 14;
        }

        @Override public int getIconHeight() { return HEIGHT + 4; }
    }

    private static class SearchIcon implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Aqua.antialias(g2);
            boolean selected = c instanceof JMenu && ((JMenu) c).isSelected();
            g2.setColor(selected ? Color.WHITE : (Aqua.highContrast() ? Color.WHITE : new Color(0x2A2A2A)));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(x + 2, y + 2, 8, 8);
            g2.drawLine(x + 10, y + 10, x + 13, y + 13);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 15; }
        @Override public int getIconHeight() { return 15; }
    }
}
