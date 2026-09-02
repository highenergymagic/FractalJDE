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
import org.fractalmicro.foundation.FMURL;

/**
 * A mounted disk, as a program asking about one sees it.
 *
 * Cocoa reads these as resource values on a URL, one lookup each. All four at once here,
 * because a program listing the disks wants all of them for every disk and asking four
 * times is four chances for the disk to be pulled out in between.
 */
public record FMVolume(FMString name, FMURL url, FMString fileSystem,
                       long totalCapacity, long availableCapacity) {

    /** A drive with nothing in it has no capacity, which is not an error and not a zero. */
    public boolean isReady() { return totalCapacity > 0; }
}
