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

/**
 * A list being built.
 *
 * {@link FMArray} answers a new list every time something is added to it, which is right
 * for a value and wrong for a loop. This is the one to fill, and {@link #asArray} is how it
 * stops being one.
 */
public final class FMMutableArray<T> implements Iterable<T> {

    private final java.util.List<T> items = new java.util.ArrayList<>();

    public static <T> FMMutableArray<T> empty() { return new FMMutableArray<>(); }

    public FMMutableArray<T> add(T item) { items.add(item); return this; }

    public int count() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    public T at(int index) {
        return index < 0 || index >= items.size() ? null : items.get(index);
    }

    @Override public java.util.Iterator<T> iterator() { return items.iterator(); }

    /**
     * Whether something is already in it.
     *
     * A list being built is asked this as often as a finished one is, usually to keep from
     * adding the same thing twice, and having to finish the list to ask was a gap rather
     * than a rule.
     */
    public boolean contains(T what) { return items.contains(what); }

    public int indexOf(T what) { return items.indexOf(what); }

    /** Takes one out, for a list that is being corrected rather than built up. */
    public boolean remove(T what) { return items.remove(what); }

    /** What was built, as a value that will not change under anyone. */
    public FMArray<T> asArray() { return new FMArray<>(java.util.List.copyOf(items)); }

    @Override public String toString() { return items.toString(); }
}
