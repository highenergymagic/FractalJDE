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
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data detectors: dates, addresses, phone numbers and links found in ordinary text.
 *
 * A web address is a place to go, an electronic mail address is someone to write to, a
 * telephone number is a call, a date is a day in a calendar, and a postal address is a
 * place. Finding them turns a document from text into text with handles on it.
 *
 * What is found here is found by pattern, and patterns are wrong sometimes. So nothing is
 * changed in the document and nothing happens on its own: a detection is an offer, drawn
 * as a dotted underline and listed in a menu, and it is acted on only when someone asks.
 */
public final class DataDetectors {
    private DataDetectors() {}

    /** What a detection turned out to be. */
    public enum Kind {
        LINK("Link", "Open Link"),
        MAIL("Email address", "New Message"),
        PHONE("Telephone number", "Copy Number"),
        DATE("Date", "Show Date"),
        ADDRESS("Address", "Show Map");

        public final FMString what;
        public final FMString action;

        Kind(String what, String action) {
            this.what = FMString.of(what);
            this.action = FMString.of(action);
        }
    }

    /** One thing found: where it is, what it is, and the text of it. */
    public record Detection(int start, int length, Kind kind, FMString text) {
        public int end() { return start + length; }

        /** What it is called: the kind of thing, then the thing. */
        public FMString spoken() {
            return kind.what.appending(FMString.of(": ")).appending(text);
        }
    }

    private static final Pattern LINK = Pattern.compile(
        "\\b(?:https?://|www\\.)[\\w.-]+(?:\\.[a-z]{2,})(?:/[^\\s<>\"]*)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern MAIL = Pattern.compile(
        "\\b[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+\\b");

    // Enough digits to be a number rather than a year, in the shapes people write them.
    private static final Pattern PHONE = Pattern.compile(
        "(?<![\\w.])(?:\\+\\d{1,3}[ .-]?)?(?:\\(\\d{2,5}\\)[ .-]?|\\d{3,5}[ .-])\\d{3,4}[ .-]?\\d{3,4}(?![\\w.])");

    private static final Pattern DATE = Pattern.compile(
        "\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}"
        + "|\\d{4}-\\d{2}-\\d{2}"
        + "|(?:\\d{1,2}\\s+)?(?:January|February|March|April|May|June|July|August|September"
        + "|October|November|December)\\s+\\d{1,2}?,?\\s*\\d{4}"
        + "|(?:Mon|Tues|Wednes|Thurs|Fri|Satur|Sun)day)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern ADDRESS = Pattern.compile(
        "\\b\\d{1,5}\\s+(?:[A-Z][\\w']*\\s+){1,4}"
        + "(?:Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr|Close|Court|Ct|Way|Place|Pl)\\b\\.?",
        Pattern.CASE_INSENSITIVE);

    /** Whether detecting is on at all, across the system. */
    public static boolean enabled() {
        return TextDefaults.detectData();
    }

    public static void setEnabled(boolean on) {
        TextDefaults.setDetectData(on);
    }

    /**
     * The dotted line drawn under something the text turned out to also contain. Kept
     * here with the detecting, because every text control draws it the same way.
     */
    public static final javax.swing.text.Highlighter.HighlightPainter DOTTED =
        new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(null) {
            @Override public java.awt.Shape paintLayer(java.awt.Graphics g, int start, int end,
                    java.awt.Shape bounds, javax.swing.text.JTextComponent c,
                    javax.swing.text.View view) {
                java.awt.Rectangle area;
                try {
                    area = c.modelToView2D(start).getBounds()
                            .union(c.modelToView2D(end).getBounds());
                } catch (javax.swing.text.BadLocationException e) {
                    return null;
                }
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setColor(new java.awt.Color(0x3A, 0x6E, 0xA5));
                g2.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER, 1f, new float[]{1f, 2f}, 0f));
                int y = area.y + area.height - 2;
                g2.drawLine(area.x, y, area.x + area.width, y);
                g2.dispose();
                return area;
            }
        };

    /**
     * Everything found in a piece of text, in the order it appears, with no two
     * detections overlapping: the first kind to claim a stretch of text keeps it, which
     * is why links are looked for before anything else.
     */
    public static FMArray<Detection> find(FMString text) {
        List<Detection> found = new ArrayList<>();
        if (text == null || text.isEmpty()) return FMArray.empty();
        String raw = text.toString();
        add(found, raw, LINK, Kind.LINK);
        add(found, raw, MAIL, Kind.MAIL);
        add(found, raw, ADDRESS, Kind.ADDRESS);
        add(found, raw, DATE, Kind.DATE);
        add(found, raw, PHONE, Kind.PHONE);
        found.sort((a, b) -> Integer.compare(a.start(), b.start()));
        FMMutableArray<Detection> out = FMMutableArray.empty();
        for (Detection one : found) out.add(one);
        return out.asArray();
    }

    private static void add(List<Detection> found, String text, Pattern pattern, Kind kind) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (overlaps(found, start, end)) continue;
            String text2 = matcher.group();
            // "Baker St." keeps its stop, because that is the abbreviation; "Baker
            // Street." does not, because that one belongs to the sentence.
            if (kind == Kind.ADDRESS && text2.endsWith(".")) {
                String withoutStop = text2.substring(0, text2.length() - 1);
                String lastWord = withoutStop.substring(withoutStop.lastIndexOf(' ') + 1);
                if (lastWord.length() > 3) {
                    text2 = withoutStop;
                    end--;
                }
            }
            found.add(new Detection(start, end - start, kind, FMString.of(text2)));
        }
    }

    private static boolean overlaps(List<Detection> found, int start, int end) {
        for (Detection d : found) {
            if (start < d.end() && d.start() < end) return true;
        }
        return false;
    }

    /** What acting on a detection means, as something that can be opened or copied. */
    public static FMString actionTarget(Detection detection) {
        FMString text = detection.text();
        return switch (detection.kind()) {
            case LINK -> text.lowercase().beginsWith(FMString.of("http"))
                ? text : FMString.of("https://").appending(text);
            case MAIL -> FMString.of("mailto:").appending(text);
            case ADDRESS -> FMString.of("https://www.openstreetmap.org/search?query=")
                .appending(FMString.of(java.net.URLEncoder.encode(text.toString(),
                    java.nio.charset.StandardCharsets.UTF_8)));
            case PHONE, DATE -> text;
        };
    }
}
