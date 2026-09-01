# Interface files and the words in them

A window is a file, not a function. What is in it and what it is called are separate
questions, and the second one is answered in a language the reader chose.

## Why a file

A window built in code is a window nobody can change without a compiler. That is a cost
paid three times over: whoever wants to move a control has to be a programmer, whoever
wants to translate it has to be a programmer, and whoever wants to know what is in it has
to read the program.

Interface Builder answered that in 1988 by putting the description in a file, and the
answer has not been improved on. The program says which file it wants. What is in the
window is somebody else's business, and it can be somebody who does not write Java.

The format is XML in the shape Interface Builder writes:

```xml
<document type="com.apple.InterfaceBuilder3.Cocoa.XIB" version="3.0">
  <objects>
    <window title="Calculator" id="window">
      <rect key="contentRect" x="196" y="240" width="232" height="300"/>
      <view key="contentView" id="content">
        <subviews>
          <button id="digit 7">
            <rect key="frame" x="12" y="52" width="46" height="40"/>
            <buttonCell key="cell" title="7"/>
            <accessibility key="accessibilityLabel" label="Seven"/>
            <connections><action selector="digit 7"/></connections>
          </button>
        </subviews>
      </view>
    </window>
  </objects>
</document>
```

This is a subset. Interface Builder writes a great deal describing how it was drawing at
the time, and none of that is the window. What is kept is the part that is: classes,
names, frames, connections and menus.

Identifiers are words rather than numbers. Interface Builder numbers its objects because it
makes them by clicking; a file written by hand does better with names, and the names are
the ones the program already uses for its controls.

A compiled interface would be a nib. There is no compiler here, so the file that ships is
the file that was written, which is the arrangement the format had before `ibtool`.

## Two ways of opening one

A program in a process of its own has no screen. It hands the description to the window
server, which builds the controls in the process that owns the screen and sends events
back. That is [the window server](windows.md), and it is how all six applications open
their windows.

The desktop's own parts are not in a process of their own. The Finder, the menu bar and the
panels AppKit puts up are already inside the window server, and asking it over a connection
to draw something it is holding in a field would be a message to itself. `NibLoader` is the
other half of the same idea: same file, same language, same place in the same bundle, built
directly.

What differs is only where a command goes when it is chosen. A program in its own process
is sent an event; something in this one is called.

## Commands, not positions

A menu item carries the name of what it sends, and the code that answers is a switch on that
name:

```java
case "emptyTrash" -> Finder.emptyTrash(false);
case "secureEmptyTrash" -> Finder.emptyTrash(true);
```

Nothing there knows which menu the item is in, where in that menu it sits, or what it is
called in the language somebody is reading. So the file can be rearranged, translated, or
have an item moved from one menu to another, and none of the code changes.

The reverse holds and is the half worth having: nothing in the code can quietly grow a menu
item that no translator was ever shown.

Two menus are still built rather than described, because their contents are not known until
the machine is running: the labels somebody has used, and the folders they were last in. A
file cannot say what those are.

## Where the words are

The file carries the English. A table beside it carries every other language, keyed on the
identifiers the file already uses:

```
apps/Calculator/resources/Calculator.xib
apps/Calculator/resources/en.lproj/Calculator.strings
apps/Calculator/resources/de.lproj/Calculator.strings
```

```
"window.title" = "Rechner";
"digit 7.accessibilityLabel" = "Sieben";
"menu File.title" = "Ablage";
```

A `.strings` file is an old-style ASCII property list, which is what Apple has always used
for this. The keys are `<identifier>.<property>`, so a translator is handed
`menu File.openWith.chooseApplication` and knows where in the bar it appears without being
told.

Text a program puts together while it is running is not in the window's table. It goes in
`Localizable.strings`, in the bundle it belongs to:

```java
private static final FMString NO_DISC = FMString.of("finder.noDisc");
...
beep(NO_DISC);
```

The key is an identifier rather than the English sentence. That is deliberate: with the
sentence as the key, the English lives in the source and everything else lives in a file,
and there is no way to check that the two agree. With an identifier, English is one
language among the others and a check can require it to be there.

## Which bundle

`FMLocalized` searches in order:

| | |
|---|---|
| the running program's bundle | `Contents/Resources/<language>.lproj/` |
| anything else in this process | registered by whatever it belongs to |
| every framework installed | `Versions/A/Resources/<language>.lproj/` |

The program first, so a program can say something differently from the framework it got it
from without either arranging it.

The middle row exists because the Finder, the Dock and the window server all run in one
process and only one of them can be the program that started it. The others say so.

AppKit's own words live in `AppKit.framework`, not copied into each program. Every program
on the volume shows the same menu bar and the same save panel, so a translation of them
belongs where they do.

## What is checked

`LocalizationTest` reads the source for every key asked for, reads every English table on
disk, and requires the two to agree. A key with no entry falls back to showing the key,
which reads as nearly-English in the language it was written in and as nothing at all in
any other. Nobody who speaks only the first will ever see it.

It also counts the sentences still written into the source. That number may fall and may not
rise, which is the only thing that makes a long job finish rather than drift.

`XibTest` checks that a description survives being written and read, that a translation
reaches the window, and the one that is easy to get wrong: that a key with no translation
keeps the words it was written with. A program that showed the key instead would be readable
in English and full of `digit 7.accessibilityLabel` everywhere else, and it is the languages
nobody on the project reads where that would go unseen longest.

## Reading the language

`AppleLanguages`, in the global preference domain, as a list most-wanted first. Changing it
posts a notification: every window on the screen is showing words that are now the wrong
ones, and each of them is in a process of its own.

Asking the preference on every lookup cost about a microsecond a word, which is nothing
alone and a visible pause across a window full of them. It is read once and kept until
something says it changed.
