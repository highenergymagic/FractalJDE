# Types

What a file is, and how everything that needs to know finds out.

## A type is not an extension

`public.png` conforms to `public.image`, which conforms to `public.data`, which conforms to
`public.item`. Asking whether something is a picture is asking whether its type conforms to
`public.image`, and that answer is still right for a kind of picture nobody had heard of when
the question was written. That is the whole reason for having types rather than a list of
extensions: the list has to be edited every time the world changes, and the tree does not.

An extension is only how a type is arrived at. One extension names one type; a type may have
several extensions.

## Where they come from

Declared, in the same place Cocoa declares them: a bundle's Info.plist, under
`UTExportedTypeDeclarations` for the types it owns and `UTImportedTypeDeclarations` for the
ones it understands but somebody else owns.

The system's own are written in
[UTCoreTypes.plist](system/Foundation/resources/UTCoreTypes.plist) beside Foundation's words,
and the build copies them into Foundation's Info.plist when the volume is laid out. A file of
its own rather than a table in code, because a list of what a PNG is called is data and
belongs somewhere a person can read without a compiler.

```xml
<dict>
    <key>UTTypeIdentifier</key><string>public.png</string>
    <key>UTTypeDescription</key><string>kind.portableNetworkGraphicsImage</string>
    <key>UTTypeConformsTo</key><array><string>public.image</string></array>
    <key>UTTypeTagSpecification</key>
    <dict>
        <key>public.filename-extension</key><array><string>png</string></array>
        <key>public.mime-type</key><string>image/png</string>
    </dict>
</dict>
```

The identifiers in the `public` domain are the ones every system that does this uses, and so
are the ones third parties own: `com.adobe.pdf` is Adobe's name for a PDF wherever it is read.
What this system invents for itself is under `org.fractalmicro`.

`UTTypeDescription` holds a key rather than words, looked up in `Localizable.strings` the way
every other piece of text is, so the Kind column reads in the language the account asked for.

## What asks

The Kind column of a window is the description of the file's type. It used to be a table of
extensions to English words kept next to a separate table of which program opens what, which
meant two lists to keep true and two chances for them to disagree about what a `.docx` is.

A program asks through the workspace, which is where `NSWorkspace` puts it:

```java
FMWorkspace workspace = FMWorkspace.sharedWorkspace();
FMString type = workspace.typeOfFile(file);
if (workspace.type(type, UTTypes.IMAGE)) { ... }
```

The first declaration of an identifier wins, and the first claim on an extension wins, so an
application installed later cannot take a file's type away from the system or from a program
that was there first.

A declaration is data, and data can say that a type conforms to itself. The walk up the tree
has a limit for that reason: one bad declaration should not hang the Kind column.

## Who can open what

A program says what it opens in its own Info.plist, by type:

```xml
<key>CFBundleDocumentTypes</key>
<array>
    <dict>
        <key>CFBundleTypeName</key><string>TextEdit document</string>
        <key>CFBundleTypeRole</key><string>Editor</string>
        <key>LSHandlerRank</key><string>Default</string>
        <key>LSItemContentTypes</key>
        <array><string>public.text</string><string>public.rtf</string></array>
    </dict>
</array>
```

Naming the family rather than the extensions is the whole point. TextEdit says
`public.text` and has never heard of Java, and a `.java` file is offered to it anyway,
because that is what the type says a `.java` is.

The Open With submenu is that list. It used to be two fixed items in the interface file,
which is what a submenu looks like when nothing knows the answer. What can open the selection
is a question about the machine, so it cannot be in the file: the programs come first, best
claim at the top the way a Mac puts the default there, then the host's own answer and Choose
underneath.

Claims are ranked the way Launch Services ranks them. An exact match on the type beats a
claim on a family it belongs to, so an editor naming `public.plain-text` is offered before
one that only says `public.text`, and `LSHandlerRank` (Owner, Default, Alternate, None)
breaks the tie after that.

## Who can show what

The same declaration, with a different role. A Quick Look generator is a bundle ending in
`.qlgenerator` in `System/Library/QuickLook`, and it says what it can show the same way a
program says what it can open:

```xml
<key>CFBundleDocumentTypes</key>
<array>
    <dict>
        <key>CFBundleTypeRole</key><string>QLGenerator</string>
        <key>LSItemContentTypes</key><array><string>public.image</string></array>
    </dict>
</array>
```

Three ship: Image for `public.image`, Text for `public.text`, PropertyList for
`com.apple.property-list`. Between them that is most of a volume, and none of them names a
suffix. A PNG reaches the image one because the tree says a PNG is an image, and a language
nobody had written a generator for previews as text because the tree says source is text.

The panel asks for the file's type, then what that type is a kind of, up to the root, and
takes the first generator that claims a step. A generator naming `public.png` would be asked
before one naming `public.image`, and that one before anything that took `public.data`.

The role is what keeps the two lists apart. A generator declares `public.image` exactly as a
program that opens images would, so Launch Services reads the role and offers Editor, Viewer
and Shell for opening a file and passes over everything else. Without that, Open With would
offer a plug-in that has no window to open.

The Finder used to decide this itself, with a list of five suffixes that were images and nine
that were text. Adding a kind meant editing the file browser.

## What does not have a type

`public.data`. A file nothing has declared a type for is still data, and something asking
whether it can be opened as data should be told yes rather than shrugged at.
