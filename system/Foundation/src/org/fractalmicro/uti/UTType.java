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
package org.fractalmicro.uti;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;

/**
 * One declared type: what it is called, what it is, and what says so.
 *
 * A type is a name in a tree rather than a filename extension. public.png conforms to
 * public.image, which conforms to public.data, which conforms to public.item, and asking
 * whether something is an image is asking about that tree. The answer is then right for a
 * kind of image nobody had heard of when the question was written, which is the whole
 * reason for having types at all rather than a list of extensions.
 *
 * The tags are how a type reaches the world outside: which filename extensions mean it,
 * and which MIME type. A type may have several extensions, and one extension names exactly
 * one type, because the other way round is what an extension is for.
 */
public record UTType(FMString identifier, FMString description,
                     FMArray<FMString> conformsTo, FMArray<FMString> extensions,
                     FMString mimeType) {

    /** What a declaration is written under, in the shape a nib or an Info.plist uses. */
    public static final FMString IDENTIFIER = FMString.of("UTTypeIdentifier");
    public static final FMString DESCRIPTION = FMString.of("UTTypeDescription");
    public static final FMString CONFORMS_TO = FMString.of("UTTypeConformsTo");
    public static final FMString TAGS = FMString.of("UTTypeTagSpecification");

    /** The tag classes. There are more of these on a Mac; these are the two that are read. */
    public static final FMString EXTENSION_TAG = FMString.of("public.filename-extension");
    public static final FMString MIME_TAG = FMString.of("public.mime-type");

    /** Where a bundle says what it owns, and what it merely understands. */
    public static final FMString EXPORTED = FMString.of("UTExportedTypeDeclarations");
    public static final FMString IMPORTED = FMString.of("UTImportedTypeDeclarations");

    /**
     * Reads one declaration, or nothing at all when it names no type.
     *
     * A tag may be written as one string or as a list of them, because both are allowed and
     * a declaration with a single extension is usually written the short way.
     */
    public static UTType from(FMDictionary values) {
        FMString identifier = values.string(IDENTIFIER, FMString.EMPTY);
        if (identifier.isBlank()) return null;

        FMDictionary tags = values.dictionary(TAGS);
        return new UTType(identifier,
            values.string(DESCRIPTION, FMString.EMPTY),
            strings(values.value(CONFORMS_TO)),
            lowercased(strings(tags.value(EXTENSION_TAG))),
            tags.string(MIME_TAG, FMString.EMPTY));
    }

    /** Whether this type is one of the ones a file gets by having that extension. */
    public boolean claims(FMString extension) {
        return extensions.contains(extension.lowercase());
    }

    private static FMArray<FMString> strings(Object value) {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        if (value instanceof FMString one) {
            if (!one.isBlank()) out.add(one);
        } else if (value instanceof java.util.List<?> list) {
            for (Object each : list) {
                if (each != null) out.add(FMString.describing(each));
            }
        } else if (value instanceof FMArray<?> array) {
            for (Object each : array) {
                if (each != null) out.add(FMString.describing(each));
            }
        }
        return out.asArray();
    }

    private static FMArray<FMString> lowercased(FMArray<FMString> of) {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (FMString one : of) out.add(one.lowercase());
        return out.asArray();
    }
}
