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

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.spotlight.FMMetadataAttributes;
import org.fractalmicro.spotlight.FMMetadataImporter;

import java.io.File;

/**
 * What a program calls itself.
 *
 * A bundle already carries this; the importer is what puts it where a search can reach it,
 * so a program is found by its identifier or its version and not only by its name.
 */
public final class ApplicationImporter implements FMMetadataImporter {

    @Override
    public FMDictionary attributesFor(File file) {
        FMMutableDictionary said = FMMutableDictionary.empty();
        Bundle bundle = Bundle.read(file);
        if (bundle == null) return said.asDictionary();
        said.set(FMMetadataAttributes.DISPLAY_NAME, bundle.displayName());
        said.set(FMMetadataAttributes.BUNDLE_IDENTIFIER, bundle.identifier());
        said.set(FMMetadataAttributes.VERSION, bundle.version());
        return said.asDictionary();
    }
}
