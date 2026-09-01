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
 * An ordered list of things, which does not change once it is made.
 *
 * Unchanging because that is what makes a value safe to hand to another program without
 * either end wondering who owns it. A method that wants a different list answers a new one:
 * {@link #appending} does not add, it answers a list one longer.
 *
 * A program that needs to build a long list a piece at a time should use
 * {@link FMMutableArray} and ask it for an {@link #FMArray} at the end, because appending
 * one at a time here copies each time, which is fine for a menu and wrong for a directory.
 */
public final class FMArray<T> implements Iterable<T> {

    private static final FMArray<Object> NOTHING = new FMArray<>(java.util.List.of());

    private final java.util.List<T> items;

    FMArray(java.util.List<T> items) {
        this.items = items;
    }

    @SuppressWarnings("unchecked")
    public static <T> FMArray<T> empty() {
        return (FMArray<T>) NOTHING;
    }

    @SafeVarargs
    public static <T> FMArray<T> of(T... items) {
        return new FMArray<>(java.util.List.of(items));
    }

    public int count() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** The item at a position, or nothing at all if there is no such position. */
    public T at(int index) {
        return index < 0 || index >= items.size() ? null : items.get(index);
    }

    public T first() { return at(0); }

    public T last() { return at(items.size() - 1); }

    public boolean contains(T what) { return items.contains(what); }

    public int indexOf(T what) { return items.indexOf(what); }

    /** A list one longer. This one is unchanged. */
    public FMArray<T> appending(T item) {
        java.util.List<T> out = new java.util.ArrayList<>(items);
        out.add(item);
        return new FMArray<>(java.util.List.copyOf(out));
    }

    public FMArray<T> appending(FMArray<T> others) {
        java.util.List<T> out = new java.util.ArrayList<>(items);
        out.addAll(others.items);
        return new FMArray<>(java.util.List.copyOf(out));
    }

    @Override public java.util.Iterator<T> iterator() {
        return items.iterator();
    }

    /** The runtime's own list, for the layers that still speak it. */
    public java.util.List<T> asList() { return items; }

    @Override public boolean equals(Object other) {
        return other instanceof FMArray<?> a && items.equals(a.items);
    }

    @Override public int hashCode() { return items.hashCode(); }

    @Override public String toString() { return items.toString(); }
}
