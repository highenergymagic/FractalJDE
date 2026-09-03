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

import org.fractalmicro.foundation.FMString;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a program's commands are called, as against what they are.
 *
 * The events carry codes and a person writes words, and this is the table between them.
 * It is a file in the bundle, the way an interface is a file in the bundle, so a program
 * can be spoken to by something that has never been compiled against it.
 *
 * The format is the one Apple's sdef uses, cut to the part that is the terminology.
 */
public final class FMScriptTerminology {

    /** The file's extension, and the Info.plist key naming which file it is. */
    public static final FMString EXTENSION = FMString.of("sdef");
    public static final FMString DEFINITION_KEY = FMString.of("OSAScriptingDefinition");

    private final Map<FMString, FMString> commands = new LinkedHashMap<>();
    private final Map<FMString, FMString> classes = new LinkedHashMap<>();
    private final Map<FMString, FMString> plurals = new LinkedHashMap<>();
    private final Map<FMString, FMString> properties = new LinkedHashMap<>();

    private FMScriptTerminology() {}

    /**
     * Reads one, or throws when the file will not parse.
     *
     * A program with no terminology is a program nothing can be told to do in words,
     * which is a thing a program is allowed to be. A broken one is not.
     */
    public static FMScriptTerminology read(File file) throws java.io.IOException {
        FMScriptTerminology out = new FMScriptTerminology();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(file);
            for (Element suite : childrenNamed(document.getDocumentElement(), "suite")) {
                out.readSuite(suite);
            }
        } catch (Exception unreadable) {
            throw new java.io.IOException("the terminology in " + file.getName()
                                          + " could not be read", unreadable);
        }
        return out;
    }

    private void readSuite(Element suite) {
        for (Element command : childrenNamed(suite, "command")) {
            put(commands, command.getAttribute("name"), command.getAttribute("code"));
        }
        for (Element made : childrenNamed(suite, "class")) {
            put(classes, made.getAttribute("name"), made.getAttribute("code"));
            put(plurals, made.getAttribute("plural"), made.getAttribute("code"));
            for (Element property : childrenNamed(made, "property")) {
                put(properties, property.getAttribute("name"), property.getAttribute("code"));
            }
        }
    }

    private static void put(Map<FMString, FMString> into, String word, String code) {
        if (word == null || word.isBlank() || code == null || code.isBlank()) return;
        into.put(FMString.of(word), FMString.of(code));
    }

    /* ------------------------------------------------------------- the questions */

    /** The eight characters a command's name stands for: its suite, then the command. */
    public FMString commandNamed(FMString word) { return commands.get(word); }

    /** The class one word names, whether it was written as one thing or as many. */
    public FMString classNamed(FMString word) {
        FMString found = classes.get(word);
        return found != null ? found : plurals.get(word);
    }

    /** Whether that word was the plural, which is how a script says every one of them. */
    public boolean isPlural(FMString word) {
        return !classes.containsKey(word) && plurals.containsKey(word);
    }

    public FMString propertyNamed(FMString word) { return properties.get(word); }

    /**
     * The word for one of the four characters, or nothing when it names no word here.
     *
     * The other direction, which is what a refusal needs: what comes back from a program
     * says cwin, and what a person wrote was window.
     */
    public FMString wordFor(FMString code) {
        for (Map.Entry<FMString, FMString> one : classes.entrySet()) {
            if (one.getValue().sameAs(code)) return one.getKey();
        }
        for (Map.Entry<FMString, FMString> one : properties.entrySet()) {
            if (one.getValue().sameAs(code)) return one.getKey();
        }
        return null;
    }

    /**
     * The same sentence with any code in it put into words.
     *
     * A program answers in the codes it works in, and a script was written in words. What
     * a person reads should be the words they wrote.
     */
    public FMString inWords(FMString said) {
        if (said == null || said.isEmpty()) return FMString.EMPTY;
        String out = said.toString();
        for (Map.Entry<FMString, FMString> one : classes.entrySet()) {
            out = out.replace(one.getValue().toString(), one.getKey().toString());
        }
        for (Map.Entry<FMString, FMString> one : properties.entrySet()) {
            out = out.replace(one.getValue().toString(), one.getKey().toString());
        }
        return FMString.of(out);
    }

    /** Every command in it, by the eight characters rather than by the word. */
    public java.util.List<FMString> commandCodes() {
        return new java.util.ArrayList<>(commands.values());
    }

    public java.util.List<FMString> commandWords() {
        return new java.util.ArrayList<>(commands.keySet());
    }

    public java.util.List<FMString> classWords() {
        return new java.util.ArrayList<>(classes.keySet());
    }

    public java.util.List<FMString> propertyWords() {
        return new java.util.ArrayList<>(properties.keySet());
    }

    /** The suite half and the command half of one of those eight characters. */
    public static FMString suiteOf(FMString code) {
        return FMAppleEvent.code(FMString.of(padded(code).substring(0, 4)));
    }

    public static FMString commandOf(FMString code) {
        return FMAppleEvent.code(FMString.of(padded(code).substring(4, 8)));
    }

    private static String padded(FMString code) {
        String value = code == null ? "" : code.toString();
        return (value + "        ").substring(0, 8);
    }

    /* ------------------------------------------------------------------ the XML */

    private static java.util.List<Element> childrenNamed(Element parent, String name) {
        java.util.List<Element> found = new java.util.ArrayList<>();
        if (parent == null) return found;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element one && one.getTagName().equals(name)) {
                found.add(one);
            }
        }
        return found;
    }
}
