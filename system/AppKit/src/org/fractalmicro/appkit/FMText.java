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

import org.fractalmicro.win.SpellChecker;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The text system: what every editable piece of text here gets.
 *
 * Spelling, smart quotes and dashes, the things in the text that are also something else,
 * and the services other programs offer for a selection. A field asking for a file name
 * gets what a document gets.
 *
 * {@link FMTextField} and {@link FMTextView} install it; anything else holding text can
 * call {@link #install}.
 *
 * None of it is done to text a person cannot edit: there would be nothing to do about it.
 */
public final class FMText {
    private FMText() {}

    /** How long after typing stops before the text is looked at. */
    private static final int SETTLE = 400;

    /** What was installed on one control, so it can be asked about afterwards. */
    public static final class Support {
        private final JTextComponent text;
        private final Spelling spelling;
        private final Marks detections;
        private final Timer settle;
        private boolean spellingOn;
        private boolean detectingOn;

        Support(JTextComponent text) {
            this.text = text;
            this.spelling = new Spelling(text);
            this.detections = new Marks(text);
            this.settle = new Timer(SETTLE, e -> refresh());
            settle.setRepeats(false);
        }

        /** Looks at the text again: spelling, then the things in it. */
        public void refresh() {
            if (!text.isEditable()) return;
            if (spellingOn && Spelling.available()) spelling.checkNow(); else spelling.clearMarks();
            if (detectingOn) detections.find(); else detections.clear();
        }

        public void textChanged() { settle.restart(); }

        public Spelling spelling() { return spelling; }
        public List<DataDetectors.Detection> detections() { return detections.found(); }

        public void setSpellingOn(boolean on) {
            spellingOn = on;
            refresh();
        }

        public void setDetectingOn(boolean on) {
            detectingOn = on;
            refresh();
        }

        public boolean spellingOn() { return spellingOn; }
        public boolean detectingOn() { return detectingOn; }
    }

    /**
     * Gives one text control the text system. Answers what was installed, so a program
     * that wants to drive it, such as a document window with its own menus, can.
     */
    public static Support install(JTextComponent text) {
        Support support = new Support(text);
        support.spellingOn = TextDefaults.checkSpelling();
        support.detectingOn = TextDefaults.detectData();

        text.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                support.textChanged();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                support.textChanged();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });

        installSubstitutions(text);
        installContextMenu(text, support);
        text.putClientProperty(Support.class, support);
        return support;
    }

    /** What was installed on a control, if anything was. */
    public static Support supportOf(JTextComponent text) {
        Object support = text.getClientProperty(Support.class);
        return support instanceof Support s ? s : null;
    }

    /* ------------------------------------------------------- substitutions */

    /**
     * Straight quotes become curly ones and two hyphens become a dash, where the settings
     * ask for it. Done as the text arrives, so it applies to typing and to pasting alike.
     */
    public static void installSubstitutions(JTextComponent text) {
        if (!(text.getDocument() instanceof AbstractDocument document)) return;
        document.setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int offset, String string,
                                               AttributeSet attributes)
                    throws BadLocationException {
                super.insertString(fb, offset, substitute(fb, offset, string), attributes);
            }
            @Override public void replace(FilterBypass fb, int offset, int length, String string,
                                          AttributeSet attributes) throws BadLocationException {
                super.replace(fb, offset, length, substitute(fb, offset, string), attributes);
            }
        });
    }

    private static String substitute(DocumentFilter.FilterBypass fb, int offset, String text)
            throws BadLocationException {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        if (TextDefaults.smartQuotes()) {
            char before = offset > 0 ? fb.getDocument().getText(offset - 1, 1).charAt(0) : ' ';
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < out.length(); i++) {
                char c = out.charAt(i);
                char previous = i == 0 ? before : sb.charAt(i - 1);
                boolean opening = previous == ' ' || previous == '\n' || previous == '\t'
                               || previous == '(' || previous == '[';
                if (c == '"') sb.append(opening ? '“' : '”');
                else if (c == '\'') sb.append(opening ? '‘' : '’');
                else sb.append(c);
            }
            out = sb.toString();
        }
        if (TextDefaults.smartDashes()) out = out.replace("--", "—");
        return out;
    }

    /* -------------------------------------------------------- context menu */

    /**
     * The menu on a piece of text: what to do about a misspelling if the click was on
     * one, then the usual editing commands, then what other programs offer.
     */
    public static void installContextMenu(JTextComponent text, Support support) {
        MouseAdapter opener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int at = text.viewToModel2D(e.getPoint());
                menuFor(text, support, at).show(text, e.getX(), e.getY());
            }
        };
        text.addMouseListener(opener);

        // The same menu from the keyboard, because a menu only the mouse can open is a
        // menu half the people using this cannot open at all.
        text.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_CONTEXT_MENU, 0), "contextMenu");
        text.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F10,
                                   java.awt.event.InputEvent.SHIFT_DOWN_MASK), "contextMenu");
        text.getActionMap().put("contextMenu", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                java.awt.Rectangle where = new java.awt.Rectangle();
                try {
                    where = text.modelToView2D(text.getCaretPosition()).getBounds();
                } catch (BadLocationException ignored) {
                    // The caret is somewhere impossible; the menu still opens, at the top.
                }
                menuFor(text, support, text.getCaretPosition())
                    .show(text, where.x, where.y + where.height);
            }
        });
    }

    /** The menu itself, built for wherever in the text it was asked for. */
    public static JPopupMenu menuFor(JTextComponent text, Support support, int at) {
        JPopupMenu menu = new JPopupMenu();
        menu.getAccessibleContext().setAccessibleName(word(FMString.of("text.menu")));

        SpellChecker.Mistake mistake = support == null ? null : support.spelling().at(at);
        if (mistake != null && text.isEditable()) {
            List<String> suggestions = mistake.suggestions();
            if (suggestions.isEmpty()) {
                JMenuItem none = new JMenuItem(word(FMString.of("text.noSuggestions")));
                none.setEnabled(false);
                menu.add(none);
            } else {
                for (String suggestion : suggestions) {
                    JMenuItem item = new JMenuItem(suggestion);
                    item.addActionListener(
                        e -> support.spelling().replace(mistake, FMString.of(suggestion)));
                    menu.add(item);
                }
            }
            menu.addSeparator();
            JMenuItem learn = new JMenuItem(word(FMString.of("text.learnSpelling")));
            learn.addActionListener(e -> support.spelling().learn(mistake));
            menu.add(learn);
            JMenuItem ignore = new JMenuItem(word(FMString.of("text.ignoreSpelling")));
            ignore.addActionListener(e -> support.spelling().ignore(mistake));
            menu.add(ignore);
            menu.addSeparator();
        }

        if (support != null) {
            for (DataDetectors.Detection detection : support.detections()) {
                if (at < detection.start() || at > detection.end()) continue;
                JMenuItem item = new JMenuItem(detection.kind().actionSaid().toString());
                item.getAccessibleContext().setAccessibleName(
                    org.fractalmicro.foundation.FMLocalized.filled(
                        org.fractalmicro.foundation.FMString.of("detected.spoken"),
                        detection.kind().actionSaid(), detection.text()).toString());
                item.addActionListener(e -> act(text, detection));
                menu.add(item);
                menu.addSeparator();
                break;
            }
        }

        menu.add(command(text, FMString.of("text.cut"), DefaultEditorKit.cutAction, text.isEditable()));
        menu.add(command(text, FMString.of("text.copy"), DefaultEditorKit.copyAction, true));
        menu.add(command(text, FMString.of("text.paste"), DefaultEditorKit.pasteAction, text.isEditable()));
        menu.addSeparator();
        menu.add(Services.menuFor(text));
        return menu;
    }

    private static String word(FMString key) {
        return org.fractalmicro.foundation.FMLocalized.of(key).toString();
    }

    private static JMenuItem command(JTextComponent text, FMString key, String action,
                                     boolean enabled) {
        String label = word(key);
        JMenuItem item = new JMenuItem(label);
        Action found = text.getActionMap().get(action);
        item.addActionListener(e -> {
            if (found != null) found.actionPerformed(new java.awt.event.ActionEvent(
                text, java.awt.event.ActionEvent.ACTION_PERFORMED, label));
        });
        item.setEnabled(enabled && found != null);
        return item;
    }

    /** Acting on something detected: open it, or put it where it can be used. */
    public static void act(JTextComponent text, DataDetectors.Detection detection) {
        text.setSelectionStart(detection.start());
        text.setSelectionEnd(detection.end());
        switch (detection.kind()) {
            case LINK, MAIL, ADDRESS ->
                org.fractalmicro.core.Shell.browse(DataDetectors.actionTarget(detection).toString());
            case PHONE -> FMPasteboard.general().setString(detection.text());
            case DATE -> org.fractalmicro.appkit.FMAlert.tell(detection.text(),
                org.fractalmicro.foundation.FMLocalized.of(
                    FMString.of("text.noCalendar")));
        }
    }

    /* ------------------------------------------------------------- marking */

    /** The dotted lines under the things in a piece of text that are also something else. */
    static final class Marks {
        private final JTextComponent text;
        private final java.util.List<Object> marks = new java.util.ArrayList<>();
        private java.util.List<DataDetectors.Detection> found = new java.util.ArrayList<>();

        Marks(JTextComponent text) { this.text = text; }

        java.util.List<DataDetectors.Detection> found() { return found; }

        java.util.List<DataDetectors.Detection> find() {
            clear();
            String contents;
            try {
                contents = text.getDocument().getText(0, text.getDocument().getLength());
            } catch (BadLocationException e) {
                return found;
            }
            found = DataDetectors.find(FMString.of(contents)).asList();
            Highlighter highlighter = text.getHighlighter();
            for (DataDetectors.Detection detection : found) {
                try {
                    marks.add(highlighter.addHighlight(detection.start(), detection.end(),
                                                       DataDetectors.DOTTED));
                } catch (BadLocationException ignored) {
                    // A detection past the end is nothing to mark.
                }
            }
            return found;
        }

        void clear() {
            Highlighter highlighter = text.getHighlighter();
            for (Object mark : marks) highlighter.removeHighlight(mark);
            marks.clear();
            found = new java.util.ArrayList<>();
        }
    }
}
