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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline spell checking: the red underline under a misspelled word.
 *
 * The words come from the system's own dictionaries, so a word learned here is learned
 * everywhere and a word learned elsewhere is known here. Checking runs a moment after
 * typing stops rather than on every keystroke, because checking a long document on every
 * letter is felt.
 *
 * The underline is a hint and not the answer. A red line under a word says that something
 * is wrong with it and nothing about what: the word itself, and what to put instead, are
 * in the spelling panel.
 */
public final class Spelling {

    /** How long after the last keystroke a document is checked. */
    private static final int SETTLE = 500;

    private final JTextComponent text;
    private final List<Object> marks = new ArrayList<>();
    private final List<SpellChecker.Mistake> mistakes = new ArrayList<>();
    private final Timer settle;

    public Spelling(JTextComponent text) {
        this.text = text;
        this.settle = new Timer(SETTLE, e -> checkNow());
        settle.setRepeats(false);
    }

    /** The text this is checking, for a panel that has to show and change it. */
    public JTextComponent text() { return text; }

    public static boolean available() { return SpellChecker.available(); }

    public static FMString describe() { return FMString.of(SpellChecker.describe()); }

    /** Whether words are checked as they are typed, across the system. */
    public static boolean asYouType() {
        return TextDefaults.checkSpelling();
    }

    public static void setAsYouType(boolean on) {
        TextDefaults.setCheckSpelling(on);
    }

    /** Called when the document changes; the check happens once typing stops. */
    public void textChanged() {
        if (!asYouType() || !available()) return;
        settle.restart();
    }

    /**
     * Checks the whole document now and marks what it finds.
     *
     * Three steps on two threads, because the two halves of this want different ones.
     * Reading the document and marking it are Swing, and Swing is the event thread's. The
     * checker underneath is the host's spelling service, and that one answers on a worker
     * and returns nothing at all on the event thread: the same words come back with one
     * mistake off it and none on it.
     *
     * That is why as-you-type checking found nothing for as long as it existed. Typing
     * restarts a timer, a Swing timer fires on the event thread, and the check it ran there
     * always came back empty. Nothing failed and no mistake was ever underlined.
     *
     * Called off the event thread this waits and answers. Called on it, the check is sent
     * to a worker and the marks appear when it comes back, because the alternative is
     * stopping the screen while the host looks up every word.
     */
    public List<SpellChecker.Mistake> checkNow() {
        if (!available()) {
            onEventThread(() -> { clearMarks(); mistakes.clear(); });
            return mistakes;
        }
        String contents = contents();
        if (contents == null) return mistakes;

        if (SwingUtilities.isEventDispatchThread()) {
            org.fractalmicro.core.Shell.async(() -> {
                List<SpellChecker.Mistake> found = SpellChecker.check(contents);
                onEventThread(() -> apply(found));
            });
            return mistakes;
        }
        List<SpellChecker.Mistake> found = SpellChecker.check(contents);
        onEventThread(() -> apply(found));
        return mistakes;
    }

    /** The text as it stands, read where a document is allowed to be read. */
    private String contents() {
        String[] held = new String[1];
        onEventThread(() -> {
            try {
                held[0] = text.getDocument().getText(0, text.getDocument().getLength());
            } catch (BadLocationException e) {
                held[0] = null;
            }
        });
        return held[0];
    }

    /**
     * Puts what was found in place and underlines it.
     *
     * The list is replaced rather than emptied and refilled, and the marking walks what was
     * found rather than the field it was put in. A list being walked is not a list to be
     * emptying, and that was the other half of this: two callers at once, one part way
     * through the loop when the other cleared it.
     */
    private void apply(List<SpellChecker.Mistake> found) {
        clearMarks();
        mistakes.clear();
        mistakes.addAll(found);
        Highlighter highlighter = text.getHighlighter();
        for (SpellChecker.Mistake mistake : found) {
            try {
                marks.add(highlighter.addHighlight(mistake.start(),
                    mistake.start() + mistake.length(), SQUIGGLE));
            } catch (BadLocationException ignored) {
                // A mistake past the end of the document is one the next check will not
                // find; there is nothing to mark.
            }
        }
    }

    private static void onEventThread(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException failed) {
            org.fractalmicro.core.Log.info("the spelling check did not finish: "
                                           + failed.getCause());
        }
    }

    public List<SpellChecker.Mistake> mistakes() { return mistakes; }

    /** The misspelling at a place in the text, if there is one. */
    public SpellChecker.Mistake at(int offset) {
        for (SpellChecker.Mistake mistake : mistakes) {
            if (offset >= mistake.start() && offset <= mistake.start() + mistake.length()) {
                return mistake;
            }
        }
        return null;
    }

    /** The first misspelling after a place, wrapping round to the start. */
    public SpellChecker.Mistake after(int offset) {
        for (SpellChecker.Mistake mistake : mistakes) {
            if (mistake.start() > offset) return mistake;
        }
        return mistakes.isEmpty() ? null : mistakes.get(0);
    }

    public FMString wordOf(SpellChecker.Mistake mistake) {
        try {
            return FMString.of(text.getDocument().getText(mistake.start(),
                                                          mistake.length()));
        } catch (BadLocationException e) {
            return FMString.of("");
        }
    }

    /** Puts one word right, and checks again so the marks keep up. */
    public void replace(SpellChecker.Mistake mistake, FMString replacement) {
        try {
            text.getDocument().remove(mistake.start(), mistake.length());
            text.getDocument().insertString(mistake.start(), replacement.toString(), null);
            checkNow();
        } catch (BadLocationException ignored) {
            // The document moved under the mistake; the next check will catch up.
        }
    }

    public void learn(SpellChecker.Mistake mistake) {
        SpellChecker.learn(wordOf(mistake).toString());
        checkNow();
    }

    public void ignore(SpellChecker.Mistake mistake) {
        SpellChecker.ignore(wordOf(mistake).toString());
        checkNow();
    }

    public void clearMarks() {
        Highlighter highlighter = text.getHighlighter();
        for (Object mark : marks) highlighter.removeHighlight(mark);
        marks.clear();
    }

    /** Selects a misspelling, so the word in question is the word in hand. */
    public void goTo(SpellChecker.Mistake mistake) {
        if (mistake == null) return;
        text.setSelectionStart(mistake.start());
        text.setSelectionEnd(mistake.start() + mistake.length());
        text.requestFocusInWindow();
    }

    /* ------------------------------------------------------------- the mark */

    /** The red line that goes under a misspelled word: drawn as a wave, as it is drawn. */
    private static final Highlighter.HighlightPainter SQUIGGLE =
        new DefaultHighlighter.DefaultHighlightPainter(null) {
            @Override public Shape paintLayer(Graphics g, int start, int end,
                                              Shape bounds, JTextComponent c,
                                              javax.swing.text.View view) {
                Rectangle area = bounds.getBounds();
                try {
                    Rectangle from = c.modelToView2D(start).getBounds();
                    Rectangle to = c.modelToView2D(end).getBounds();
                    area = from.union(to);
                } catch (BadLocationException e) {
                    return null;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xD0, 0x21, 0x29));
                int y = area.y + area.height - 2;
                GeneralPath wave = new GeneralPath();
                wave.moveTo(area.x, y);
                boolean up = true;
                for (int x = area.x; x < area.x + area.width; x += 2) {
                    wave.lineTo(x + 2, up ? y - 2 : y);
                    up = !up;
                }
                g2.draw(wave);
                g2.dispose();
                return area;
            }
        };
}
