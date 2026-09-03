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
package org.fractalmicro.scripting;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;

import java.util.ArrayList;
import java.util.List;

/**
 * A script, in words, turned into the events those words stand for.
 *
 * The words are not this file's. Every one of them comes out of the terminology of the
 * program being spoken to, which is what makes the language the program's rather than
 * this parser's: a program that ships a word for something can be told to do it, and one
 * that does not, cannot.
 *
 * What is here is the grammar, which is small on purpose. Telling something to do
 * something is the whole of it.
 */
public final class FMScript {

    /** How the terminology of a program is found. Installed by the layer that can. */
    public interface Dictionaries {
        FMScriptTerminology forProgram(FMString name);
    }

    private static volatile Dictionaries dictionaries;

    public static void setDictionaries(Dictionaries how) { dictionaries = how; }

    /* --------------------------------------------------------------- running one */

    /**
     * Runs a script and answers what the last command answered.
     *
     * A script that will not parse, and a command a program refuses, are both a refusal
     * with a sentence saying why. Nothing half runs: what has already happened has
     * happened, which is what running a list of commands means.
     */
    public static FMString run(FMString text) {
        List<String> words = tokens(text == null ? "" : text.toString());
        Reader reading = new Reader(words);
        FMString program = reading.tellApplication();
        FMScriptTerminology terminology = terminologyFor(program);
        Object answer = null;
        for (List<String> line : reading.commands()) {
            answer = perform(program, terminology, line);
        }
        return said(answer);
    }

    /**
     * An answer as a script would write it.
     *
     * Yes and no are true and false here. YES is what an object writes and true is what a
     * script says, and the two have never been the same word.
     */
    private static FMString said(Object answer) {
        if (answer instanceof FMNumber number
                && number.kind() == FMNumber.Kind.TRUTH) {
            return FMString.of(number.isTrue() ? "true" : "false");
        }
        return FMString.describing(answer);
    }

    private static FMScriptTerminology terminologyFor(FMString program) {
        Dictionaries how = dictionaries;
        FMScriptTerminology found = how == null ? null : how.forProgram(program);
        if (found == null) {
            throw new FMScriptError(FMString.of("there is no terminology for " + program));
        }
        return found;
    }

    /* ------------------------------------------------------------- one command */

    private static Object perform(FMString program, FMScriptTerminology words,
                                  List<String> line) {
        if (line.isEmpty()) return null;
        FMString verb = words.commandNamed(FMString.of(line.get(0)));
        if (verb == null) {
            throw new FMScriptError(FMString.of(program + " has no command called "
                                                + line.get(0)));
        }
        List<String> rest = line.subList(1, line.size());
        Object value = null;
        int to = indexOfWord(rest, "to");
        if (to >= 0) {
            value = literal(rest.subList(to + 1, rest.size()));
            rest = rest.subList(0, to);
        }
        FMMutableDictionary parameters = FMMutableDictionary.empty();
        if (!rest.isEmpty()) {
            Object about = rest.size() == 1 && isLiteral(rest.get(0))
                ? literal(rest) : specifier(words, rest).asDictionary();
            parameters.set(FMAppleEvent.DIRECT_OBJECT, about);
        }
        if (value != null) parameters.set(FMAppleEvent.DATA, value);

        FMDictionary reply = FMAppleEventManager.sharedManager().sendEvent(
            new FMAppleEvent(FMScriptTerminology.suiteOf(verb),
                             FMScriptTerminology.commandOf(verb),
                             program, parameters.asDictionary()));
        if (FMAppleEventManager.failed(reply)) {
            throw new FMScriptError(words.inWords(FMAppleEventManager.whyFailed(reply)));
        }
        return FMAppleEventManager.result(reply);
    }

    /* -------------------------------------------------------------- specifiers */

    /**
     * "name of item 1 of window 1", read the way it is written.
     *
     * The words are split on "of" and the chain is built from the far end inwards, which
     * is the order they are written in reverse: the last one names what holds all of it.
     */
    private static FMScriptObjectSpecifier specifier(FMScriptTerminology words,
                                                     List<String> said) {
        List<List<String>> steps = splitOn(said, "of");
        FMScriptObjectSpecifier chain = null;
        for (int i = steps.size() - 1; i >= 0; i--) {
            chain = step(words, steps.get(i), chain);
        }
        return chain;
    }

    private static FMScriptObjectSpecifier step(FMScriptTerminology words, List<String> said,
                                                FMScriptObjectSpecifier in) {
        if (said.isEmpty()) throw new FMScriptError(FMString.of("something is missing"));
        boolean every = "every".equals(said.get(0)) || "all".equals(said.get(0));
        if (every) said = said.subList(1, said.size());
        if (said.isEmpty()) throw new FMScriptError(FMString.of("every what"));

        FMString word = FMString.of(said.get(0));
        FMString property = words.propertyNamed(word);
        FMString wanted = words.classNamed(word);
        if (wanted == null && property != null) {
            if (said.size() > 1) {
                throw new FMScriptError(FMString.of(word + " is a property, not a thing"));
            }
            return FMScriptObjectSpecifier.property(property, in);
        }
        if (wanted == null) {
            throw new FMScriptError(FMString.of("nothing here is called " + word));
        }
        if (every || (said.size() == 1 && words.isPlural(word))) {
            return FMScriptObjectSpecifier.every(wanted, in);
        }
        if (said.size() == 1) {
            throw new FMScriptError(FMString.of("which " + word));
        }
        String which = said.get(1);
        if (isQuoted(which)) {
            return FMScriptObjectSpecifier.named(wanted, FMString.of(unquoted(which)), in);
        }
        try {
            return FMScriptObjectSpecifier.at(wanted, Long.parseLong(which), in);
        } catch (NumberFormatException notANumber) {
            throw new FMScriptError(FMString.of("there is no " + word + " " + which));
        }
    }

    /* ---------------------------------------------------------------- literals */

    private static boolean isLiteral(String word) {
        if (isQuoted(word) || "true".equals(word) || "false".equals(word)) return true;
        try {
            Long.parseLong(word);
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static Object literal(List<String> said) {
        if (said.isEmpty()) throw new FMScriptError(FMString.of("there is no value here"));
        String word = said.get(0);
        if (isQuoted(word)) return FMString.of(unquoted(word));
        if ("true".equals(word)) return FMNumber.of(true);
        if ("false".equals(word)) return FMNumber.of(false);
        try {
            return FMNumber.of(Long.parseLong(word));
        } catch (NumberFormatException notANumber) {
            return FMString.of(word);
        }
    }

    private static boolean isQuoted(String word) {
        return word.length() >= 2 && word.charAt(0) == '"' && word.endsWith("\"");
    }

    private static String unquoted(String word) {
        return word.substring(1, word.length() - 1);
    }

    /* ------------------------------------------------------------------ reading */

    /** What a script says, one command to a line, and who it is being said to. */
    private static final class Reader {
        private final List<String> words;
        private int at;

        Reader(List<String> words) { this.words = words; }

        /** The opening, which every script here has: tell application "Name". */
        FMString tellApplication() {
            if (words.size() < 3 || !"tell".equals(words.get(0))
                    || !"application".equals(words.get(1))
                    || !isQuoted(words.get(2))) {
                throw new FMScriptError(
                    FMString.of("a script starts by telling an application something"));
            }
            at = 3;
            return FMString.of(unquoted(words.get(2)));
        }

        /**
         * The commands, whether written after "to" or on lines of their own.
         *
         * One line is one command. There is nothing that spans two, so the line is the
         * whole of the grammar above a command.
         */
        List<List<String>> commands() {
            List<List<String>> out = new ArrayList<>();
            List<String> line = new ArrayList<>();
            if (at < words.size() && "to".equals(words.get(at))) at++;
            for (int i = at; i < words.size(); i++) {
                String word = words.get(i);
                if ("\n".equals(word)) {
                    if (!line.isEmpty()) out.add(new ArrayList<>(line));
                    line.clear();
                } else if ("end".equals(word)) {
                    break;
                } else {
                    line.add(word);
                }
            }
            if (!line.isEmpty()) out.add(line);
            return out;
        }
    }

    /**
     * The words of a script, with a quoted run kept whole and the ends of lines kept.
     *
     * Line ends matter because a line is a command. Everything else that is not a word is
     * space, which a script has as much of as somebody felt like typing.
     */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                word.append(c);
                if (c == '"') {
                    out.add(word.toString());
                    word.setLength(0);
                    quoted = false;
                }
                continue;
            }
            if (c == '"') {
                flush(out, word);
                word.append(c);
                quoted = true;
            } else if (c == '\n' || c == '\r') {
                flush(out, word);
                if (out.isEmpty() || !"\n".equals(out.get(out.size() - 1))) out.add("\n");
            } else if (Character.isWhitespace(c)) {
                flush(out, word);
            } else {
                word.append(c);
            }
        }
        flush(out, word);
        return out;
    }

    private static void flush(List<String> out, StringBuilder word) {
        if (word.length() > 0) {
            out.add(word.toString());
            word.setLength(0);
        }
    }

    private static int indexOfWord(List<String> said, String word) {
        for (int i = 0; i < said.size(); i++) {
            if (word.equals(said.get(i))) return i;
        }
        return -1;
    }

    private static List<List<String>> splitOn(List<String> said, String word) {
        List<List<String>> out = new ArrayList<>();
        List<String> part = new ArrayList<>();
        for (String one : said) {
            if (word.equals(one)) {
                out.add(new ArrayList<>(part));
                part.clear();
            } else if (!"the".equals(one)) {
                part.add(one);
            }
        }
        out.add(part);
        return out;
    }

    private FMScript() {}
}
