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

Text too long to be one entry in a table is a file of its own, in the same language
directories: `Help.txt` beside `Localizable.strings`. `FMLocalized.resource` finds it the
same way, so a program can carry its own copy of something a framework also has. Fractal
Help was a page of prose in a Java string until then, which is a page no translator can
open and no writer can correct without a compiler.

The list of keyboard shortcuts is not translated at all, because it is not written down.
It is read out of the menu bar, which already holds every shortcut and what it does in
the language this account reads. What was there before was forty rows of a keystroke and
a sentence, kept true by somebody remembering to.

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

It also requires the words to be in a table the program can actually read. A framework's
words are on the volume for everything to read; a program's are read by that program. A key
asked for in one program and written down in another shows as the key itself on screen, and
the merged view a check takes of every table on disk is exactly the view that hides it.

Then it counts the sentences still written into the source. That count was a ratchet for a
while, a number that could fall and could not rise, because moving a few hundred sentences
out of the code takes longer than one sitting. It is zero now, so it is a rule again.

Two things are not counted, and both are named rather than guessed at. A line writing to the
log is not translated, here or on a Mac: the log is read by whoever is looking into
something going wrong and it says the same thing to all of them. And a short list of names
is not words at all: the programs and folders as they are written on the volume, the font
families asked of the host, and what a binary format is called.

The trap underneath all of this is not a missing word but a working program that stops
working when it is translated. A list holds titles somebody reads, and a program acts on
what they mean, and those are not the same string. System Profiler was switching on the
title of the row that had been picked, so its window would have shown Hardware for every
section in any language but English. An event carries the position now, which is the same
everywhere, and the same rule applies to a view mode, a sort order and a pop-up item: the
value is an identifier and is spelled as one.

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

## Bindings

A control can say which setting it shows, and from then on nothing else is involved:

```xml
<button id="show labels" type="check">
    <buttonCell key="cell" title="Show labels behind names"/>
    <connections>
        <binding name="value" keyPath="values.finder.ShowLabels"/>
    </connections>
</button>
```

The control reads the setting when the window opens, writes it when somebody uses it, and
follows it when something else writes it. The program that described the window is not told
about any of that and has no code that could get it wrong. A binding sits beside the action
in the same connections element because the two are the same kind of thing: something the
control is joined to that nobody had to write code for.

The key path is written the way `NSUserDefaultsController` writes one, `values.` and then the
setting. There are several preference domains here where a Mac has one, so the domain is the
middle part: `values.finder.ShowLabels`, `values.global.AppleShowAllExtensions`,
`values.dock.tilesize`.

Following it across processes is free. A setting written in one program is a distributed
notification in all of them, so a window bound to something changed elsewhere catches up
without anything asking it to.

What this took out of System Preferences was a table of fourteen switches, each with a getter
and a setter, and every one of them a chance for the switch and the setting to disagree. What
is left in that table is which pane each control is on, because the description has no notion
of a pane yet.

The one control still wired by hand is the spring-loading delay, because the slider is in
tenths of a second and the setting is in seconds. Cocoa binds through an `NSValueTransformer`
for exactly that and there is not one here yet.

**A bound setting needs a registered default.** An unset key reads as nothing, so the control
comes up empty while the rest of the system goes on using the fallback written in its code:
the switch says one thing and the machine does another, with no error anywhere. It happened
to Show Labels the first time this was tried, and there is now a check that reads every
binding in every interface file and fails when one has nothing to start from.
