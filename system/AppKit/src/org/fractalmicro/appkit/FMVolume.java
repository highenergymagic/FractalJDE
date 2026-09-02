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
 * Cocoa answers this with resource values on a URL: the volume's name, its format, how big
 * it is and how much is left. The same four things, said once rather than as four separate
 * lookups, because a program listing the disks wants all of them for each and asking four
 * times is four chances for the disk to be pulled out in between.
 *
 * A disk with no capacity is a drive with nothing in it. That is not an error and not a
 * zero: it is a name in the list for somewhere a disk could go, which is what an empty
 * optical drive is.
 */
public record FMVolume(FMString name, FMURL url, FMString fileSystem,
                       long totalCapacity, long availableCapacity) {

    /** Whether there is a disk in it to ask about. */
    public boolean isReady() { return totalCapacity > 0; }
}
