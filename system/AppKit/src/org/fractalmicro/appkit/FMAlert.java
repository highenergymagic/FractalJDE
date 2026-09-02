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

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.BrandMark;
import org.fractalmicro.theme.Icons;
import org.fractalmicro.fs.Node;

import org.fractalmicro.appkit.FMTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An Aqua alert.
 *
 * The layout the guidelines specify, which most imitations get wrong. The icon is 64 by 64
 * at the top left. The message is one sentence in the emphasized system font, usually a
 * question. Below it, in the small system font, the informative text says what will happen;
 * the guidelines are explicit that it should not be omitted. Buttons sit bottom right, the
 * rightmost being the action button, Cancel to its left. Margins are 24 at the sides, 20 at
 * the bottom.
 *
 * Buttons are named for what they do. There is no OK button, since "OK" does not say what is being
 * agreed to.
 *
 * Buttons are passed in NSAlert's order: first is the action button and appears rightmost.
 *
 * The default button is the one Return presses wherever the keyboard focus happens to be.
 * That single fact drives {@link #confirmIrreversible} and the checks around it.
 */
public final class FMAlert {
    private FMAlert() {}

    public enum Kind { INFORMATIONAL, CAUTION }

    private static final int ICON_SIZE = 64;
    private static final int SIDE_MARGIN = 24;
    private static final int BOTTOM_MARGIN = 20;
    private static final int CONTROL_GAP = 8;
    private static final int GROUP_GAP = 12;
    /** A destructive third choice stands this far from the safe buttons. */
    private static final int DESTRUCTIVE_GAP = 24;

    /**
     * Shows an alert and waits. The first button is the action button, drawn rightmost
     * and used as the default; a button named Cancel is answered by Escape.
     */
    public static int show(Kind kind, FMString message, FMString informative,
                           FMString... buttons) {
        return show(kind, message, informative, 0, buttons);
    }

    /**
     * The same, naming the button the keyboard starts on and Return presses.
     *
     * A dialog whose action cannot be undone must not make that action the default.
     * Otherwise Return takes the irreversible action from wherever focus happens to be:
     * a Log Out / Cancel / Quit dialog defaulting to Log Out logs the user out on Return,
     * whichever button they were looking at.
     */
    public static int show(Kind kind, FMString message, FMString informative,
                           int defaultButton, FMString... buttons) {
        if (buttons.length == 0) buttons = new FMString[]{OK};
        Desktop desktop = Desktop.sharedDesktop();
        Window owner = desktop == null ? null : desktop;

        JDialog dialog = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setResizable(false);

        JPanel body = new JPanel(new BorderLayout(GROUP_GAP, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Aqua.antialias(g2);
                g2.setColor(Aqua.highContrast() ? Color.BLACK : new Color(0xEDEDED));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(0x8A8A8A));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(20, SIDE_MARGIN, BOTTOM_MARGIN, SIDE_MARGIN));

        body.add(iconLabel(kind), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel messageLabel = new JLabel("<html><body style='width:300px'>"
            + escape(message.toString()) + "</body></html>");
        messageLabel.setFont(Aqua.emphasizedSystemFont());
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(messageLabel);

        if (informative != null && !informative.isBlank()) {
            text.add(Box.createVerticalStrut(CONTROL_GAP));
            JLabel informativeLabel = new JLabel("<html><body style='width:300px'>"
                + escape(informative.toString()) + "</body></html>");
            informativeLabel.setFont(Aqua.smallFont());
            informativeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(informativeLabel);
        }

        text.add(Box.createVerticalStrut(GROUP_GAP + CONTROL_GAP));
        int[] answer = {-1};
        text.add(buttonRow(dialog, buttons, answer, defaultButton));

        body.add(text, BorderLayout.CENTER);
        dialog.setContentPane(body);

        // Escape answers Cancel, as it does everywhere in Aqua.
        int cancelIndex = indexOf(buttons, CANCEL_BUTTON);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                answer[0] = cancelIndex;
                dialog.dispose();
            }
        });

        dialog.getAccessibleContext().setAccessibleName(message.toString());
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return answer[0];
    }

    /**
     * Asks before doing something that cannot be undone. Cancel is always the default;
     * that rule is the reason this exists instead of being spelled out at each call site.
     *
     * @return 0 for the action, 1 for Cancel, 2 for a second irreversible choice
     */
    public static int confirmIrreversible(FMString message, FMString informative,
                                          FMString actionButton, FMString otherChoice) {
        return show(Kind.CAUTION, message, informative, CANCEL,
                    irreversibleButtons(actionButton, otherChoice));
    }

    /**
     * The buttons of an irreversible question, in answer order: the action, Cancel, then a
     * second irreversible choice if there is one.
     */
    public static FMString[] irreversibleButtons(FMString actionButton,
                                                 FMString otherChoice) {
        return otherChoice == null || otherChoice.isBlank()
            ? new FMString[]{actionButton, CANCEL_BUTTON}
            : new FMString[]{actionButton, CANCEL_BUTTON, otherChoice};
    }

    /**
     * Which button Return would press. Kept in one place so the rule can be tested without
     * opening a window and looking at it.
     */
    public static FMString defaultButtonName(FMString[] buttons, int defaultButton) {
        if (buttons == null || buttons.length == 0) return OK;
        return buttons[Math.max(0, Math.min(defaultButton, buttons.length - 1))];
    }

    /** Where Cancel sits in the buttons of an irreversible question: always second. */
    public static final int CANCEL = 1;

    /** The two buttons this file names itself, rather than being handed. */
    public static final FMString OK = FMString.of("OK");
    public static final FMString CANCEL_BUTTON = FMString.of("Cancel");

    /** An alert with one button, for saying something happened. */
    public static void tell(FMString message, FMString informative) {
        show(Kind.INFORMATIONAL, message, informative, OK);
    }

    /** Asks a question. True when the action button was pressed. */
    public static boolean confirm(Kind kind, FMString message, FMString informative,
                                  FMString actionButton) {
        return show(kind, message, informative, actionButton, CANCEL_BUTTON) == 0;
    }

    /* ------------------------------------------------------------- pieces */

    /** The alert icon. Shared with the sheet, which draws the same alert in place. */
    static JLabel iconLabel(Kind kind) {
        JLabel label = new JLabel();
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        Image mark = kind == Kind.CAUTION
            ? cautionImage()
            : BrandMark.image(ICON_SIZE, new Color(0x333333));
        if (mark == null) mark = Icons.forKind(Node.Kind.APPLICATION, ICON_SIZE);
        label.setIcon(new ImageIcon(mark));
        label.getAccessibleContext().setAccessibleName(
            kind == Kind.CAUTION ? "Caution" : "FractalJDE");
        return label;
    }

    /** The yellow triangle, badged with the company mark as the guidelines describe. */
    private static Image cautionImage() {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(ICON_SIZE, ICON_SIZE,
                                             java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Aqua.antialias(g);
        Polygon triangle = new Polygon(
            new int[]{ICON_SIZE / 2, ICON_SIZE - 2, 2},
            new int[]{2, ICON_SIZE - 6, ICON_SIZE - 6}, 3);
        g.setPaint(new GradientPaint(0, 0, new Color(0xFFE45C), 0, ICON_SIZE, new Color(0xE8A400)));
        g.fill(triangle);
        g.setColor(new Color(0x8A6400));
        g.draw(triangle);
        g.setColor(new Color(0x3A2A00));
        g.setFont(Aqua.systemFont().deriveFont(Font.BOLD, 30f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("!", (ICON_SIZE - fm.stringWidth("!")) / 2f, ICON_SIZE - 16f);

        Image badge = BrandMark.image(22, new Color(0x222222));
        if (badge != null) g.drawImage(badge, ICON_SIZE - 24, ICON_SIZE - 24, null);
        g.dispose();
        return image;
    }

    /**
     * The buttons, laid out right to left: the action button first and rightmost, then
     * Cancel, then anything else, with a gap before a destructive choice.
     */
    private static JComponent buttonRow(JDialog dialog, FMString[] buttons, int[] answer,
                                        int defaultButton) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createHorizontalGlue());

        List<JButton> made = new ArrayList<>();
        for (int i = buttons.length - 1; i >= 0; i--) {
            final int index = i;
            JButton button = new JButton(buttons[i].toString());
            button.setFont(Aqua.systemFont());
            button.addActionListener(e -> {
                answer[0] = index;
                dialog.dispose();
            });
            // A third choice is the destructive one; keep it away from the safe pair.
            if (i >= 2) row.add(Box.createHorizontalStrut(DESTRUCTIVE_GAP - CONTROL_GAP));
            row.add(button);
            if (i > 0) row.add(Box.createHorizontalStrut(CONTROL_GAP));
            made.add(button);
        }

        // Built right to left, so the first button given is the last one made. The default
        // must be both the Return target and where the keyboard starts, or Return does
        // something other than what is focused.
        FMString wantedName = defaultButtonName(buttons, defaultButton);
        int wanted = indexOf(buttons, wantedName);
        JButton start = made.get(made.size() - 1 - Math.max(0, wanted));
        SwingUtilities.invokeLater(() -> {
            dialog.getRootPane().setDefaultButton(start);
            start.requestFocusInWindow();
        });
        return row;
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

    /* -------------------------------------------------------------- input */

    /**
     * A dialog asking for one piece of text, with the same shape as an alert: a prompt,
     * a field, and buttons named for what they do.
     */
    public static FMString ask(FMString message, FMString fieldLabel, FMString initial,
                               FMString actionButton) {
        Desktop desktop = Desktop.sharedDesktop();
        JDialog dialog = new JDialog(desktop, "", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setResizable(false);

        JPanel body = new JPanel(new BorderLayout(GROUP_GAP, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Aqua.antialias(g2);
                g2.setColor(Aqua.highContrast() ? Color.BLACK : new Color(0xEDEDED));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(0x8A8A8A));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(20, SIDE_MARGIN, BOTTOM_MARGIN, SIDE_MARGIN));
        body.add(iconLabel(Kind.INFORMATIONAL), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel messageLabel = new JLabel(message.toString());
        messageLabel.setFont(Aqua.emphasizedSystemFont());
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(messageLabel);
        text.add(Box.createVerticalStrut(CONTROL_GAP));

        FMTextField field = new FMTextField(initial == null ? FMString.EMPTY : initial, 26);
        field.setFont(Aqua.systemFont());
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        field.getAccessibleContext().setAccessibleName(fieldLabel.toString());
        field.selectAll();

        JLabel label = new JLabel(fieldLabel.toString());
        label.setFont(Aqua.smallFont());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setLabelFor(field);
        text.add(label);
        text.add(Box.createVerticalStrut(4));
        text.add(field);
        text.add(Box.createVerticalStrut(GROUP_GAP + CONTROL_GAP));

        FMString[] answer = {null};
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createHorizontalGlue());

        JButton cancel = new JButton("Cancel");
        cancel.setFont(Aqua.systemFont());
        cancel.addActionListener(e -> dialog.dispose());
        JButton go = new JButton(actionButton.toString());
        go.setFont(Aqua.systemFont());
        go.addActionListener(e -> {
            answer[0] = FMString.of(field.getText()).trimmed();
            dialog.dispose();
        });
        row.add(cancel);
        row.add(Box.createHorizontalStrut(CONTROL_GAP));
        row.add(go);
        text.add(row);

        body.add(text, BorderLayout.CENTER);
        dialog.setContentPane(body);
        dialog.getRootPane().setDefaultButton(go);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        dialog.getAccessibleContext().setAccessibleName(message.toString());
        dialog.pack();
        dialog.setLocationRelativeTo(desktop);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        dialog.setVisible(true);
        return answer[0];
    }
}
