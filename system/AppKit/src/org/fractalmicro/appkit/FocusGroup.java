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
package org.fractalmicro.appkit;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A cluster of controls that behaves the way Mac OS X clusters behave under full
 * keyboard access: Tab moves to the group as a whole, arrow keys move inside it, and
 * Tab again leaves. The window buttons, the toolbar and the Dock all use this.
 *
 * The container is a focus traversal policy provider, not a focus cycle root: a cycle
 * root would keep Tab inside the group forever. As a provider it offers exactly one
 * member to the tab order, the one last used, and the arrow keys move between them.
 */
public final class FocusGroup {

    public enum Axis { HORIZONTAL, VERTICAL }

    private final JComponent container;
    private final List<JComponent> members = new ArrayList<>();
    private int index;
    private Runnable onEscape;
    private Component cameFrom;

    private FocusGroup(JComponent container, Axis axis, List<JComponent> members) {
        this.container = container;
        this.members.addAll(members);

        // A policy provider, deliberately not a focus cycle root: a cycle root keeps
        // Tab inside itself, which is exactly the trap this is meant to avoid. As a
        // provider, the container offers one stop and the parent carries on past it.
        container.setFocusCycleRoot(false);
        container.setFocusTraversalPolicyProvider(true);
        container.setFocusTraversalPolicy(new SingleStopPolicy());

        int back = axis == Axis.HORIZONTAL ? KeyEvent.VK_LEFT : KeyEvent.VK_UP;
        int forward = axis == Axis.HORIZONTAL ? KeyEvent.VK_RIGHT : KeyEvent.VK_DOWN;

        for (int i = 0; i < this.members.size(); i++) {
            JComponent member = this.members.get(i);
            final int position = i;
            member.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    index = position;
                    // Remember where the keyboard came from, whichever way it arrived:
                    // by Tab, by a shortcut, or by a click.
                    Component from = e.getOppositeComponent();
                    if (from != null && !members.contains(from)) cameFrom = from;
                }
            });
            bind(member, back, -1);
            bind(member, forward, 1);
            bind(member, KeyEvent.VK_HOME, Integer.MIN_VALUE);
            bind(member, KeyEvent.VK_END, Integer.MAX_VALUE);
            member.getInputMap(JComponent.WHEN_FOCUSED)
                  .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "org.fractalmicro.leaveGroup");
            member.getActionMap().put("org.fractalmicro.leaveGroup", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (onEscape != null) onEscape.run();
                    else restore(cameFrom);
                }
            });
        }
    }

    public static FocusGroup horizontal(JComponent container, JComponent... members) {
        return new FocusGroup(container, Axis.HORIZONTAL, Arrays.asList(members));
    }

    public static FocusGroup horizontal(JComponent container, List<JComponent> members) {
        return new FocusGroup(container, Axis.HORIZONTAL, members);
    }

    /** What Escape does while the keyboard is inside this group. */
    public FocusGroup onEscape(Runnable action) {
        onEscape = action;
        return this;
    }

    /** Where the keyboard was before it came into this group. */
    public Component cameFrom() { return cameFrom; }

    public void focusFirst() {
        if (members.isEmpty()) return;
        index = Math.min(index, members.size() - 1);
        members.get(index).requestFocusInWindow();
    }

    public List<JComponent> members() { return List.copyOf(members); }

    private void bind(JComponent member, int keyCode, int delta) {
        String name = "org.fractalmicro.move." + keyCode;
        member.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        member.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { move(delta); }
        });
    }

    private void move(int delta) {
        if (members.isEmpty()) return;
        int next;
        if (delta == Integer.MIN_VALUE) next = 0;
        else if (delta == Integer.MAX_VALUE) next = members.size() - 1;
        else next = Math.floorMod(index + delta, members.size());
        for (int tries = 0; tries < members.size(); tries++) {
            JComponent candidate = members.get(next);
            if (candidate.isVisible() && candidate.isEnabled() && candidate.isFocusable()) {
                index = next;
                candidate.requestFocusInWindow();
                return;
            }
            next = Math.floorMod(next + (delta < 0 ? -1 : 1), members.size());
        }
    }

    /** Offers the tab order exactly one component: whichever member was last used. */
    private final class SingleStopPolicy extends FocusTraversalPolicy {
        private JComponent current() {
            if (members.isEmpty()) return null;
            return members.get(Math.min(index, members.size() - 1));
        }

        // Returning the same component for after and before keeps Swing from walking
        // into the other members; the parent policy then steps past the whole group.
        @Override public Component getComponentAfter(Container root, Component from) { return null; }
        @Override public Component getComponentBefore(Container root, Component from) { return null; }
        @Override public Component getFirstComponent(Container root) { return current(); }
        @Override public Component getLastComponent(Container root) { return current(); }
        @Override public Component getDefaultComponent(Container root) { return current(); }
    }

    /** Remembers where the keyboard was, so Escape can put it back. */
    public static Component focusOwner() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    public static void restore(Component previous) {
        if (previous != null && previous.isShowing()) previous.requestFocusInWindow();
    }
}
