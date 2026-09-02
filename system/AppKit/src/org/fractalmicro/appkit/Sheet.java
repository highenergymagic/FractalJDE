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

import org.fractalmicro.foundation.FMString;


import org.fractalmicro.theme.Aqua;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * A sheet: an alert that belongs to one window.
 *
 * Aqua attaches a question to the document it concerns rather than floating it over the
 * screen. The panel drops from under the title bar and blocks its own window, while every
 * other window keeps working. That is the reason the original TextEdit asks about
 * converting a document this way.
 *
 * The call waits for an answer without stopping the event queue: a secondary loop keeps
 * painting and keeps the keyboard alive while the sheet is up.
 */
public final class Sheet {
    private Sheet() {}

    private static final int SIDE_MARGIN = 24;
    private static final int BOTTOM_MARGIN = 20;
    private static final int CONTROL_GAP = 8;
    private static final int GROUP_GAP = 12;
    private static final int SLIDE_STEPS = 8;
    private static final int SLIDE_DELAY = 16;

    /**
     * Shows a sheet on one window and waits for the answer. The first button is the
     * action button, drawn rightmost and used as the default; Escape answers Cancel.
     * Answers the index of the button pressed, or -1.
     *
     * A window that is not on screen, during a check for instance, gets the free standing
     * alert instead, because a sheet with nothing to hang from cannot be answered.
     */
    public static int show(JInternalFrame owner, FMAlert.Kind kind, FMString message,
                           FMString informative, FMString... buttons) {
        if (owner == null || !owner.isShowing()) {
            return FMAlert.show(kind, message, informative, buttons);
        }
        if (buttons.length == 0) buttons = new FMString[]{FMAlert.OK};

        JLayeredPane layers = owner.getLayeredPane();
        int[] answer = {-1};

        // Nothing behind the sheet answers to the mouse while it is up.
        JPanel blocker = new JPanel();
        blocker.setOpaque(false);
        blocker.addMouseListener(new MouseAdapter() { });
        blocker.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        blocker.setFocusable(false);

        JPanel panel = build(kind, message, informative, buttons, answer);
        Rectangle where = placement(owner, panel.getPreferredSize());
        int width = where.width;
        int height = where.height;
        int x = where.x;
        int top = where.y;

        layers.add(blocker, JLayeredPane.MODAL_LAYER);
        layers.add(panel, JLayeredPane.POPUP_LAYER);
        panel.setBounds(x, top - height, width, height);

        panel.getAccessibleContext().setAccessibleName(message.toString());
        panel.getAccessibleContext().setAccessibleDescription(informative.toString());

        java.awt.EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        java.awt.SecondaryLoop loop = queue.createSecondaryLoop();

        Runnable finish = () -> {
            layers.remove(panel);
            layers.remove(blocker);
            layers.repaint();
            loop.exit();
        };
        for (Component c : panel.getComponents()) attach(c, finish);
        collectButtons(panel).forEach(b -> b.addActionListener(e -> finish.run()));

        int cancel = indexOf(buttons, FMAlert.CANCEL_BUTTON);
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer[0] = cancel;
                finish.run();
            }
        });

        slideDown(panel, x, top, width, height);
        List<JButton> made = collectButtons(panel);
        if (!made.isEmpty()) {
            JButton action = made.get(made.size() - 1);
            SwingUtilities.invokeLater(action::requestFocusInWindow);
        }
        loop.enter();
        return answer[0];
    }

    private static void attach(Component c, Runnable finish) {
        // The buttons are collected separately; this is here for anything nested later.
    }

    /**
     * Where a sheet of this size sits on this window: as wide as it needs up to the
     * window's own width, centred across it, hanging from under the title bar.
     */
    public static Rectangle placement(JInternalFrame owner, Dimension preferred) {
        int width = Math.min(Math.max(preferred.width, 380),
                             Math.max(200, owner.getWidth() - 40));
        int height = preferred.height;
        return new Rectangle((owner.getWidth() - width) / 2, titleHeight(owner), width, height);
    }

    /**
     * Drops any panel from under a window's title bar and waits for it to finish.
     *
     * The alert form above is one thing that can be put in a sheet; a save panel is
     * another, and so is anything else that belongs to one document rather than to the
     * screen. What is shared is the whole of the behaviour: it hangs from the window, that
     * window cannot be used underneath it, every other window still can, and the call
     * returns once it is done.
     *
     * The panel is handed a way to say it has finished rather than being asked afterwards.
     * A sheet ends when its own buttons decide it does, and only the thing inside it knows
     * which of them mean that.
     *
     * Answers whether it was shown at all. A window that is not on screen cannot carry a
     * sheet, and the caller falls back to a panel that stands on its own.
     */
    public static boolean present(JInternalFrame owner, JComponent content,
                                  java.util.function.Consumer<Runnable> giveCloser) {
        if (owner == null || !owner.isShowing()) return false;

        JLayeredPane layers = owner.getLayeredPane();
        JPanel blocker = new JPanel();
        blocker.setOpaque(false);
        blocker.addMouseListener(new MouseAdapter() { });
        blocker.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        blocker.setFocusable(false);

        Dimension wanted = content.getPreferredSize();
        int width = Math.min(Math.max(wanted.width, 380), Math.max(200, owner.getWidth() - 24));
        int height = Math.min(wanted.height, Math.max(120, owner.getHeight() - 40));
        int x = (owner.getWidth() - width) / 2;
        int top = titleHeight(owner);

        layers.add(blocker, JLayeredPane.MODAL_LAYER);
        layers.add(content, JLayeredPane.POPUP_LAYER);
        content.setBounds(x, top - height, width, height);

        java.awt.EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        java.awt.SecondaryLoop loop = queue.createSecondaryLoop();
        giveCloser.accept(() -> {
            layers.remove(content);
            layers.remove(blocker);
            layers.repaint();
            loop.exit();
        });

        slideDown(content, x, top, width, height);
        loop.enter();
        return true;
    }

    /** The panel a sheet puts up. Separated so it can be checked without a screen. */
    public static JPanel panelFor(FMAlert.Kind kind, FMString message, FMString informative,
                                  FMString[] buttons, int[] answer) {
        return build(kind, message, informative, buttons, answer);
    }

    /** The buttons on a built sheet, left to right as they are laid out. */
    public static java.util.List<JButton> buttonsOf(Container panel) {
        return collectButtons(panel);
    }

    private static List<JButton> collectButtons(Container root) {
        List<JButton> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton button) out.add(button);
            else if (c instanceof Container container) out.addAll(collectButtons(container));
        }
        return out;
    }

    /** How far down the window the content starts: under the title bar. */
    private static int titleHeight(JInternalFrame owner) {
        Component north = owner.getUI() instanceof javax.swing.plaf.basic.BasicInternalFrameUI ui
            ? ui.getNorthPane() : null;
        return north == null ? 22 : north.getHeight();
    }

    private static void slideDown(JComponent panel, int x, int top, int width, int height) {
        Timer timer = new Timer(SLIDE_DELAY, null);
        final int[] step = {0};
        timer.addActionListener(e -> {
            step[0]++;
            double amount = Math.min(1.0, step[0] / (double) SLIDE_STEPS);
            panel.setBounds(x, (int) Math.round(top - height * (1 - amount)), width, height);
            panel.getParent().repaint();
            if (amount >= 1.0) timer.stop();
        });
        timer.start();
    }

    /* -------------------------------------------------------------- drawing */

    private static JPanel build(FMAlert.Kind kind, FMString message, FMString informative,
                                FMString[] buttons, int[] answer) {
        JPanel body = new JPanel(new BorderLayout(GROUP_GAP, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Aqua.antialias(g);
                g.setColor(new Color(0xEDEDED));
                g.fillRoundRect(0, -12, getWidth(), getHeight() + 12, 12, 12);
                g.setColor(new Color(0x8A8A8A));
                g.drawRoundRect(0, -12, getWidth() - 1, getHeight() + 11, 12, 12);
                g.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(20, SIDE_MARGIN, BOTTOM_MARGIN,
                                                       SIDE_MARGIN));
        body.add(FMAlert.iconLabel(kind), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel messageLabel = new JLabel("<html><body style='width:280px'>"
            + escape(message.toString()) + "</body></html>");
        messageLabel.setFont(Aqua.emphasizedSystemFont());
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(messageLabel);

        if (informative != null && !informative.isBlank()) {
            text.add(Box.createVerticalStrut(CONTROL_GAP));
            JLabel informativeLabel = new JLabel("<html><body style='width:280px'>"
                + escape(informative.toString()) + "</body></html>");
            informativeLabel.setFont(Aqua.smallFont());
            informativeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(informativeLabel);
        }

        text.add(Box.createVerticalStrut(GROUP_GAP + CONTROL_GAP));

        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createHorizontalGlue());
        for (int i = buttons.length - 1; i >= 0; i--) {
            final int index = i;
            JButton button = new JButton(buttons[i].toString());
            button.setFont(Aqua.systemFont());
            button.addActionListener(e -> answer[0] = index);
            if (i >= 2) row.add(Box.createHorizontalStrut(24 - CONTROL_GAP));
            row.add(button);
            if (i > 0) row.add(Box.createHorizontalStrut(CONTROL_GAP));
        }
        text.add(row);

        body.add(text, BorderLayout.CENTER);
        return body;
    }

    private static int indexOf(FMString[] buttons, FMString name) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].sameAs(name)) return i;
        }
        return -1;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
