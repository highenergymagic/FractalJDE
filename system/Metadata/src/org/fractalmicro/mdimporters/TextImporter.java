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
package org.fractalmicro.mdimporters;

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.spotlight.FMMetadataAttributes;
import org.fractalmicro.spotlight.FMMetadataImporter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The words in a file that holds words.
 *
 * Declares public.text, so it reads a language nobody had written an importer for. What it
 * returns is what makes a search find a file by something it says rather than by its name.
 */
public final class TextImporter implements FMMetadataImporter {

    /** Enough of a file to find it by. The index is not a copy of the disk. */
    private static final int KEPT = 8 * 1024;

    /** Past this a file is a log or a dump, and reading it holds up the walk. */
    private static final long TOO_BIG = 4L * 1024 * 1024;

    @Override
    public FMDictionary attributesFor(File file) {
        FMMutableDictionary said = FMMutableDictionary.empty();
        if (file.length() > TOO_BIG) return said.asDictionary();
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            said.set(FMMetadataAttributes.TEXT_CONTENT,
                     FMString.of(text.length() > KEPT ? text.substring(0, KEPT) : text));
            said.set(FMMetadataAttributes.DISPLAY_NAME, FMString.of(file.getName()));
        } catch (IOException | RuntimeException notText) {
            // A file that claims to be text and is not. Nothing to say about it.
        }
        return said.asDictionary();
    }
}
