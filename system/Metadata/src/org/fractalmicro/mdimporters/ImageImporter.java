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

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.io.File;
import java.util.Iterator;

/**
 * How big the picture is.
 *
 * The header rather than the picture: a search for something a hundred megapixels wide
 * should not cost a hundred megapixels of memory to answer.
 */
public final class ImageImporter implements FMMetadataImporter {

    @Override
    public FMDictionary attributesFor(File file) {
        FMMutableDictionary said = FMMutableDictionary.empty();
        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            if (stream == null) return said.asDictionary();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return said.asDictionary();
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                said.set(FMMetadataAttributes.PIXEL_WIDTH, reader.getWidth(0));
                said.set(FMMetadataAttributes.PIXEL_HEIGHT, reader.getHeight(0));
                said.set(FMMetadataAttributes.DISPLAY_NAME, FMString.of(file.getName()));
            } finally {
                reader.dispose();
            }
        } catch (Exception notAnImage) {
            // A file that claims to be a picture and is not.
        }
        return said.asDictionary();
    }
}
