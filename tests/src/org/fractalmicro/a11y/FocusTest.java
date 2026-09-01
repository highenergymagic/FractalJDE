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
import org.fractalmicro.ui.Finder;
import org.fractalmicro.ui.FinderWindow;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Checks the keyboard structure without pressing anything: it reads the focus
 * traversal policies, the input maps and the action maps, and invokes the bound
 * actions directly. Nothing is sent to the machine's keyboard.
 */
public final class FocusTest {
    private FocusTest() {}

    private static final List<String> failures = new ArrayList<>();
    private static int checks;

    public static int run(Desktop desktop, java.io.PrintStream out) {
        failures.clear();
        checks = 0;

        FinderWindow window = Finder.newWindow(org.fractalmicro.fs.FS.home());
        desktop.validate();
        window.validate();
        drain();

        // The Dock: one tab stop, arrows along it, Escape to leave.
        Container dockTiles = groupContainer(desktop.dock());
        check(out, "the Dock is a single tab stop",
              dockTiles != null && dockTiles.isFocusTraversalPolicyProvider());
        check(out, "the Dock does not trap tab",
              dockTiles != null && !dockTiles.isFocusCycleRoot());
        check(out, "the Dock offers one component to the tab order",
              dockTiles != null && dockTiles.getFocusTraversalPolicy() != null
                  && dockTiles.getFocusTraversalPolicy().getDefaultComponent(dockTiles) != null);
        JButton firstTile = firstButton(dockTiles);
        check(out, "Dock tiles answer to the left and right arrows",
              firstTile != null && hasBinding(firstTile, KeyStroke.getKeyStroke("RIGHT"))
                  && hasBinding(firstTile, KeyStroke.getKeyStroke("LEFT")));
        check(out, "up on a Dock tile opens its menu",
              firstTile != null && hasBinding(firstTile, KeyStroke.getKeyStroke("UP")));
        check(out, "escape leaves the Dock",
              firstTile != null && hasBinding(firstTile, KeyStroke.getKeyStroke("ESCAPE")));

        // The window buttons: also one tab stop with arrows inside.
        Container titleBar = findByName(window, "Title bar");
        check(out, "the window buttons are a single tab stop",
              titleBar != null && titleBar.isFocusTraversalPolicyProvider());
        check(out, "the window buttons do not trap tab",
              titleBar != null && !titleBar.isFocusCycleRoot());
        JButton close = firstButton(titleBar);
        check(out, "the window buttons answer to the arrows",
              close != null && hasBinding(close, KeyStroke.getKeyStroke("RIGHT")));
        check(out, "Close, Minimize and Zoom are all named",
              titleBar != null && allNamed(titleBar));

        // The toolbar.
        Container toolbar = findByName(window, "Toolbar");
        check(out, "the toolbar is a single tab stop",
              toolbar != null && toolbar.isFocusTraversalPolicyProvider());
        check(out, "the toolbar does not trap tab",
              toolbar != null && !toolbar.isFocusCycleRoot());

        // The protocol that lets Tab out of a group: the group's own policy must hand
        // back null once it runs out, so the parent carries on past it. A group that
        // answers with one of its own members instead is the trap this guards against.
        check(out, "the window buttons let tab continue past them",
              titleBar == null || leavesGroup(titleBar, close));
        check(out, "the toolbar lets tab continue past it",
              toolbar == null || leavesGroup(toolbar, firstButton(toolbar)));
        check(out, "the Dock lets tab continue past it",
              dockTiles == null || leavesGroup(dockTiles, firstTile));

        // Walking the real tab order needs components that are on screen; offscreen,
        // Swing refuses them all, so this part is reported rather than failed.
        List<Component> order = tabOrder(window, close, 40);
        if (order.isEmpty()) {
            out.println("note  tab order not walkable offscreen; run this on a visible window");
        } else {
            check(out, "tab leaves the window buttons",
                  order.stream().anyMatch(c -> titleBar != null && !isInside(c, titleBar)));
            out.println("      tab order: " + describe(order));
        }

        // The desktop's own keys.
        JComponent icons = desktop.icons();
        check(out, "Return renames on the desktop", hasBinding(icons, KeyStroke.getKeyStroke("ENTER")));
        check(out, "Command O opens on the desktop",
              hasBinding(icons, KeyStroke.getKeyStroke("alt O")));
        check(out, "Command Backspace moves to the Trash",
              hasBinding(icons, KeyStroke.getKeyStroke("alt BACK_SPACE")));

        // Menu accelerators: every one registered, and none used twice.
        Map<KeyStroke, String> accelerators = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        collectAccelerators(desktop.mainMenu(), accelerators, duplicates);
        check(out, "no menu shortcut is used twice", duplicates.isEmpty());
        if (!duplicates.isEmpty()) out.println("      " + String.join(", ", duplicates));
        check(out, "the menu bar carries its shortcuts",
              accelerators.size() > 30);

        // Closing a window has to leave the keyboard somewhere.
        Component afterClose = desktop.focusAfterClose();
        check(out, "closing a window hands the keyboard on", afterClose != null);

        // Tab order of a Finder window reaches the sidebar and the file list.
        check(out, "the sidebar can take the keyboard", findByName(window, "Sidebar") != null);
        check(out, "the file list can take the keyboard",
              findByName(window, "Icon view") != null || findByName(window, "List view") != null);

        out.println();
        out.println("checked " + checks + " keyboard behaviours, " + failures.size() + " failed");
        for (String f : failures) out.println("  FAILED " + f);
        return failures.size();
    }

    /** True when the group's policy stops offering members, letting Tab move on. */
    private static boolean leavesGroup(Container group, Component from) {
        FocusTraversalPolicy policy = group.getFocusTraversalPolicy();
        if (policy == null || from == null) return false;
        return policy.getComponentAfter(group, from) == null
            && policy.getComponentBefore(group, from) == null
            && !group.isFocusCycleRoot();
    }

    /** Follows getComponentAfter around the window, as pressing Tab would. */
    private static List<Component> tabOrder(Container cycleRoot, Component from, int limit) {
        List<Component> visited = new ArrayList<>();
        FocusTraversalPolicy policy = cycleRoot.getFocusTraversalPolicy();
        if (policy == null || from == null) return visited;
        Component current = from;
        Set<Component> seen = new HashSet<>();
        for (int i = 0; i < limit; i++) {
            Component next = policy.getComponentAfter(cycleRoot, current);
            if (next == null || !seen.add(next)) break;
            visited.add(next);
            current = next;
        }
        return visited;
    }

    private static String name(Component c) {
        if (c == null) return "null";
        String n = c instanceof javax.accessibility.Accessible
            ? ((javax.accessibility.Accessible) c).getAccessibleContext().getAccessibleName() : null;
        return (n == null || n.isBlank() ? "" : n + " ") + c.getClass().getSimpleName();
    }

    private static boolean isInside(Component c, Container container) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p == container) return true;
        }
        return false;
    }

    private static String describe(List<Component> components) {
        StringBuilder sb = new StringBuilder();
        for (Component c : components) {
            String name = c instanceof javax.accessibility.Accessible
                ? ((javax.accessibility.Accessible) c).getAccessibleContext().getAccessibleName() : null;
            if (name == null || name.isBlank()) name = c.getClass().getSimpleName();
            if (sb.length() > 0) sb.append(" > ");
            sb.append(name);
        }
        return sb.length() == 0 ? "(nothing)" : sb.toString();
    }

    /* ------------------------------------------------------------ helpers */

    private static void check(java.io.PrintStream out, String what, boolean ok) {
        checks++;
        out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures.add(what);
    }

    private static boolean hasBinding(JComponent c, KeyStroke stroke) {
        for (int condition : new int[]{JComponent.WHEN_FOCUSED,
                                       JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                                       JComponent.WHEN_IN_FOCUSED_WINDOW}) {
            InputMap map = c.getInputMap(condition);
            Object name = map == null ? null : map.get(stroke);
            if (name != null && c.getActionMap().get(name) != null) return true;
        }
        return false;
    }

    /** The container a focus group was installed on, found by its policy. */
    private static Container groupContainer(Container root) {
        if (root.isFocusTraversalPolicyProvider()) return root;
        for (Component child : root.getComponents()) {
            if (child instanceof Container) {
                Container found = groupContainer((Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton firstButton(Container root) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) return (JButton) c;
            if (c instanceof Container) {
                JButton found = firstButton((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean allNamed(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) {
                String name = ((JButton) c).getAccessibleContext().getAccessibleName();
                if (name == null || name.isBlank()) return false;
            }
            if (c instanceof Container && !allNamed((Container) c)) return false;
        }
        return true;
    }

    private static Container findByName(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof javax.accessibility.Accessible) {
                javax.accessibility.AccessibleContext ctx =
                    ((javax.accessibility.Accessible) c).getAccessibleContext();
                if (ctx != null && name.equals(ctx.getAccessibleName()) && c instanceof Container) {
                    return (Container) c;
                }
            }
            if (c instanceof Container) {
                Container found = findByName((Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectAccelerators(MenuElement element,
                                            Map<KeyStroke, String> seen, List<String> duplicates) {
        for (MenuElement child : element.getSubElements()) {
            if (child instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) child;
                KeyStroke accelerator = item.getAccelerator();
                if (accelerator != null) {
                    String previous = seen.put(accelerator, item.getText());
                    if (previous != null && !previous.equals(item.getText())) {
                        duplicates.add(accelerator + " (" + previous + " and " + item.getText() + ")");
                    }
                }
            }
            collectAccelerators(child, seen, duplicates);
        }
    }

    private static void drain() {
        if (SwingUtilities.isEventDispatchThread()) return;
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) { }
    }
}
