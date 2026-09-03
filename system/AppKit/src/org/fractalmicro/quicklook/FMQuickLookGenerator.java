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
package org.fractalmicro.quicklook;

import javax.swing.JComponent;

import java.io.File;

/**
 * Something that can show what is inside one kind of file.
 *
 * A bundle ending in .qlgenerator, loaded by the loader, so it needs a constructor taking
 * no arguments. What it handles is named in its Info.plist as types rather than suffixes,
 * which is what lets one written for images show a kind of image it never heard of.
 */
public interface FMQuickLookGenerator {

    /**
     * The view of that file, or nothing when this generator cannot make one.
     *
     * Nothing rather than an apology: the panel has a view for a file no generator handles,
     * and one that finds it cannot read what it was given should fall back to that.
     */
    JComponent preview(File file);
}
