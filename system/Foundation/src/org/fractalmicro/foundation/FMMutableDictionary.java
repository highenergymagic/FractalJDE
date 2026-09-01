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
package org.fractalmicro.foundation;

import java.util.LinkedHashMap;

/**
 * A dictionary while it is still being built.
 *
 * Foundation has always kept the two apart, and the reason is not tidiness: a dictionary
 * handed to something else should not change underneath it. Something assembling one holds
 * this, and hands over an {@link FMDictionary} when it is done.
 */
public final class FMMutableDictionary extends FMDictionary {

    private FMMutableDictionary() {
        super(new LinkedHashMap<>());
    }

    public static FMMutableDictionary empty() { return new FMMutableDictionary(); }

    /** One made from what another holds, to be changed without changing that one. */
    public static FMMutableDictionary from(FMDictionary other) {
        FMMutableDictionary out = new FMMutableDictionary();
        out.values.putAll(other.values);
        return out;
    }

    public void set(FMString key, Object value) { values.put(key, value); }

    public void set(FMString key, boolean value) { values.put(key, FMNumber.of(value)); }

    public void set(FMString key, long value) { values.put(key, FMNumber.of(value)); }

    public void remove(FMString key) { values.remove(key); }

    public void removeAll() { values.clear(); }

    /** What has been built, which will not change when this one does. */
    public FMDictionary asDictionary() { return FMDictionary.of(values); }
}
