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
package org.fractalmicro.spotlight;

import org.fractalmicro.foundation.FMString;

/**
 * The names the attributes go under, which are the names Spotlight uses.
 *
 * A word in a file is kMDItemTextContent wherever it was read, so a query written against
 * one importer works against the next.
 */
public final class FMMetadataAttributes {
    private FMMetadataAttributes() {}

    /** What a person would call it, which is not always the file name. */
    public static final FMString DISPLAY_NAME = FMString.of("kMDItemDisplayName");

    /** The words in it. What makes a search find a file by what it says. */
    public static final FMString TEXT_CONTENT = FMString.of("kMDItemTextContent");

    /** The type identifier, so a query can ask for a kind rather than a suffix. */
    public static final FMString CONTENT_TYPE = FMString.of("kMDItemContentType");

    /** The kind in words, the same words the Kind column shows. */
    public static final FMString KIND = FMString.of("kMDItemKind");

    public static final FMString PIXEL_WIDTH = FMString.of("kMDItemPixelWidth");
    public static final FMString PIXEL_HEIGHT = FMString.of("kMDItemPixelHeight");

    /** A program's version and identifier, which is how one is found by either. */
    public static final FMString VERSION = FMString.of("kMDItemVersion");
    public static final FMString BUNDLE_IDENTIFIER =
        FMString.of("kMDItemCFBundleIdentifier");

    /** Size and time, which the server knows without asking anybody. */
    public static final FMString SIZE = FMString.of("kMDItemFSSize");
    public static final FMString MODIFIED = FMString.of("kMDItemContentModificationDate");
}
