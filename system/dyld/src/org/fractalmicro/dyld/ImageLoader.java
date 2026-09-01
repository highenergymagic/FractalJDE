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
package org.fractalmicro.dyld;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The loader for one image, which resolves only what that image linked against.
 *
 * Java gives a class loader one parent and searches it first. That is the wrong shape for
 * a dynamic loader: an image does not have one parent, it has a list of libraries it
 * linked, in the order it named them, and it is allowed to see those and nothing else.
 *
 * So the search order is a linker's rather than Java's:
 *
 *   1. classes this loader has already defined
 *   2. the runtime, which every image gets and no image may shadow
 *   3. this image's own code
 *   4. the library the symbol table says the class comes from, and only that one
 *   5. failing a table entry, each linked image in the order the load commands named them
 *
 * Step four is the two level namespace. The linker already worked out, for every class
 * this image uses and does not define, which of its load commands supplies it, and wrote
 * that down as an ordinal. Following it is a lookup rather than a search, and two
 * libraries exporting the same name are not in conflict, because the question was never
 * "who has this" but "what did this image link it against".
 *
 * Step five is what a symbol with no entry gets, which is what every symbol got before
 * anyone recorded where they came from. It still only searches libraries this image
 * linked: not being in the table is not permission to look anywhere.
 *
 * Anything not found by then is not linked, and saying so is the point. A program that
 * reaches for a class from a library it never declared gets the answer a real loader gives:
 * there is no such symbol.
 *
 * The bytes come out of the image file itself. A Mach-O carries its code in a segment, and
 * mapping the image means reading that segment: there is no unpacking, no copy of the code
 * beside the executable, and nothing on disk that the executable is not.
 *
 * This defines its image's classes itself rather than handing the files to another loader
 * and delegating. The loader that defines a class is the one the runtime asks to resolve
 * every other class that class mentions. Delegating would make the inner loader the one
 * asked, and the inner loader has never heard of the linked images, so an image would load
 * and then fail on its first call into a library it linked perfectly legitimately.
 */
public class ImageLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    /** What to do about a class an image uses but never linked. */
    public interface Undeclared {
        /**
         * Answers the loader to fall back to, or null to let the failure stand.
         *
         * A build that has finished its layering wants the failure. A build still moving
         * code between libraries wants a list of what is still wrong, which is what the
         * reporting mode gives: the class resolves, and the reach is written down.
         */
        ClassLoader reached(String image, String className);
    }

    private final String image;
    private final Path file;
    /** This image's own files, as they are in the segment that carries them. */
    private final Map<String, byte[]> contents;
    private final Set<String> exports;
    private final Map<String, String> imports;
    private final List<ImageLoader> linked;
    private final List<ImageLoader> reexported;
    private final Undeclared undeclared;

    ImageLoader(String image, Path file, Map<String, byte[]> contents,
                Set<String> exports, Map<String, String> imports,
                List<ImageLoader> linked, List<ImageLoader> reexported,
                Undeclared undeclared) {
        super("image:" + image, ClassLoader.getPlatformClassLoader());
        this.image = image;
        this.file = file;
        this.contents = contents;
        this.exports = Set.copyOf(exports);
        this.imports = Map.copyOf(imports);
        this.linked = List.copyOf(linked);
        this.reexported = List.copyOf(reexported);
        this.undeclared = undeclared;
    }

    public String image() { return image; }

    /** The classes this image offers to anything that links it. */
    public Set<String> exports() { return exports; }

    /** For each class it uses and does not define, the library it was linked against. */
    public Map<String, String> imports() { return imports; }

    /** The images this one linked, in the order its load commands named them. */
    public List<ImageLoader> linked() { return linked; }

    @Override protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> found = findLoadedClass(name);
            if (found == null) found = runtimeClass(name);
            if (found == null) found = search(name, new java.util.HashSet<>());
            if (found == null) found = notLinked(name);
            if (resolve) resolveClass(found);
            return found;
        }
    }

    /**
     * The runtime's own classes, which every image gets.
     *
     * Asked before the image's own code so that nothing can put itself in front of
     * java.lang, and asked here rather than by delegating to a parent because the rest of
     * the search is not a parent's.
     */
    private Class<?> runtimeClass(String name) {
        try {
            return ClassLoader.getPlatformClassLoader().loadClass(name);
        } catch (ClassNotFoundException notThere) {
            return null;
        }
    }

    /**
     * Looks through this image and everything it linked, once each.
     *
     * The set of images already looked at is carried along because a library may be linked
     * by more than one thing in the graph, and two libraries may link each other.
     */
    Class<?> search(String name, Set<String> seen) {
        if (!seen.add(image)) return null;
        synchronized (getClassLoadingLock(name)) {
            Class<?> mine = findLoadedClass(name);
            if (mine != null) return mine;
            byte[] bytes = contents.get(name.replace('.', '/') + ".class");
            if (bytes != null) {
                definePackageOf(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        }

        // What the symbol table says. One library, named when this was linked.
        String from = imports.get(name);
        if (from != null && !from.isEmpty()) {
            ImageLoader supplier = linkedNamed(from);
            return supplier == null ? null : supplier.offered(name, seen);
        }

        for (ImageLoader dependency : linked) {
            Class<?> found = dependency.offered(name, seen);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * What this image offers to something that links it.
     *
     * Its own classes, and whatever it passes on. Not the libraries it links privately:
     * linking Foundation is permission to use Foundation, not permission to use everything
     * Foundation happens to use. A dependency of a dependency was never named by the
     * program and is not the program's to reach.
     */
    Class<?> offered(String name, Set<String> seen) {
        if (!seen.add(image)) return null;
        synchronized (getClassLoadingLock(name)) {
            Class<?> mine = findLoadedClass(name);
            if (mine != null) return mine;
            byte[] bytes = contents.get(name.replace('.', '/') + ".class");
            if (bytes != null) {
                definePackageOf(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        }
        for (ImageLoader passedOn : reexported) {
            Class<?> found = passedOn.offered(name, seen);
            if (found != null) return found;
        }
        return null;
    }

    /** The image loaded for one of this image's load commands. */
    private ImageLoader linkedNamed(String installName) {
        for (ImageLoader dependency : linked) {
            if (dependency.image.equals(installName)) return dependency;
        }
        return null;
    }

    /** A package has to exist before a class is defined into it. */
    private void definePackageOf(String className) {
        int dot = className.lastIndexOf('.');
        if (dot < 0) return;
        String name = className.substring(0, dot);
        if (getDefinedPackage(name) == null) {
            try {
                definePackage(name, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException alreadyThere) {
                // Another thread defined it between the check and here, which is fine.
            }
        }
    }

    /** A class this image never linked against. */
    private Class<?> notLinked(String name) throws ClassNotFoundException {
        ClassLoader fallback = undeclared == null ? null : undeclared.reached(image, name);
        if (fallback != null) return fallback.loadClass(name);
        throw new ClassNotFoundException(
            name + " is not in " + image + " or anything it links");
    }

    /**
     * A resource inside this image, named the way anything else would name it.
     *
     * The image is a file whose code segment is an archive, so a resource in it has a URL:
     * the archive, and the entry within it. Nothing is written anywhere to produce one.
     */
    @Override public URL getResource(String name) {
        if (contents.containsKey(name) && file != null) {
            try {
                return new java.net.URI("jar:" + file.toUri() + "!/" + name).toURL();
            } catch (Exception noSuchUrl) {
                // Fall through: the bytes are still reachable as a stream.
            }
        }
        for (ImageLoader dependency : linked) {
            URL found = dependency.getResource(name);
            if (found != null) return found;
        }
        return ClassLoader.getPlatformClassLoader().getResource(name);
    }

    @Override public java.io.InputStream getResourceAsStream(String name) {
        byte[] mine = contents.get(name);
        if (mine != null) return new java.io.ByteArrayInputStream(mine);
        for (ImageLoader dependency : linked) {
            java.io.InputStream found = dependency.getResourceAsStream(name);
            if (found != null) return found;
        }
        return ClassLoader.getPlatformClassLoader().getResourceAsStream(name);
    }

    /** Every image reachable from this one, which is what a listing of the graph shows. */
    public List<String> reachable() {
        List<String> out = new ArrayList<>();
        collect(out, new java.util.HashSet<>());
        return out;
    }

    private void collect(List<String> out, Set<String> seen) {
        if (!seen.add(image)) return;
        out.add(image);
        for (ImageLoader dependency : linked) dependency.collect(out, seen);
    }
}
