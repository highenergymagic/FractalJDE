# The window server

A program describes a window and something else draws it. What that costs, what it
buys, and how the menu bar changes hands.

A program in another process now opens windows on this desktop. It sends a description; the
desktop makes the controls; events come back.

## Why the widgets and not the pixels

There are two ways to split a window server. The system this imitates takes the other one:
each program draws its own pixels and the server composites them. That could not be done
here without giving up what the whole project is for.

A window drawn into a picture has the appearance of controls and none of the controls.
Nothing can say what is in it, move through it, or act on it: it is an image of a window,
and the only thing that can use an image is an eye. If each program kept its own controls
in its own process instead, they would describe windows the host system does not have,
floating free of anything on screen. The separate-windows attempt already showed what
that failure looks like.

So the controls live where the windows live. There is one tree, in the process that owns
the screen, and everything in it is named, because a description that leaves a control
unnamed is **refused rather than drawn**, which is the only way to keep that true for
programs nobody here has written yet.

## Descriptions

That is what makes interface descriptions necessary rather than merely tidy: a window has
to arrive in one message, not a thousand calls setting properties one at a time. A
description is a property list, the same format as everything else here, naming a window
and its controls:

```
Window     Title, Width, Height, Resizable
Controls   Class, Identifier, AccessibleName, Text, Action, X, Y, Width, Height
```

The classes are `FMButton`, `FMLabel`, `FMTextField`, `FMTextView`, `FMRichText`,
`FMCheckBox`, `FMPopUpButton`, `FMSlider`, `FMProgressIndicator`, `FMTableView`,
`FMBrowser`, `FMSplitView`, `FMToolbar` and `FMSeparator`. Every one becomes the real
Swing control it names, drawn by the Aqua delegates like anything else.

### Things that hold other things

A control may say which one it sits inside, with `In`. That keeps a description a flat
list: everything that reads one reads a list, and nothing has to walk a tree. An interface
file nests instead, because that is what a view holding views looks like written down and
what Interface Builder writes, and the reader turns one shape into the other. They say the
same thing and the checks write one out as the other to prove it.

`FMSplitView` takes two, in the order they were described, and its divider starts where the
first one's width puts it. A description that says how wide the sidebar is has already said
where the divider goes; saying it twice is two chances to disagree.

`FMToolbar` takes as many as it is given, and an `FMSeparator` inside one is flexible space,
which is what `NSToolbarFlexibleSpaceItem` is. What comes before it goes left, what comes
after goes right. That is how a search field reaches the far end of a toolbar without
anybody measuring the window.

That is enough for the shape of a file browser window, which is a toolbar over a split view
with a list on one side and a folder on the other.

### A folder

`FMBrowser` is the first of them that is a view of something the program never sends. The
others hold what they were given: a label holds its words, a list holds its rows. A folder
holds what is on the disk, and the disk is on the drawing side along with the icons, the
kinds and the dates. So the description says which folder and how to show it, and that is
all that crosses. A window on a folder with a hundred thousand files in it is the same
short message as a window on an empty one.

What goes back the other way is small too:

| | |
|---|---|
| `setValue` | a path: show me this folder |
| `getValue` | what is chosen, or where it is when nothing is |
| `perform` | `viewAsIcons`, `viewAsList`, `viewAsColumns`, `goUp` |
| `find` | what to look for, and nothing to stop looking |

and two events, because choosing and opening are different questions. An action event says
somebody selected something; an `open` event says they asked for it. A program that could
not tell them apart would open a folder every time somebody looked at one. Both carry the
path chosen and the folder the browser was in, so a program never has to ask afterwards to
draw its own title.

The four things `perform` names are the selectors the Finder's menus already use, and they
are the browser's own action map rather than methods on it. One name therefore reaches
them from three directions: a menu item in an interface file, a key bound in the window,
and a program in another process with no way to hold a reference to anything.

## A program

```java
Nib nib = new Nib.Builder()
    .title("Counter").size(320, 170)
    .add(ControlClass.FMLabel, "caption", "Count", "Count:", 20, 20, 60, 20)
    .add(ControlClass.FMTextField, "total", "Total", "0", 90, 18, 200, 24)
    .button("more", "More", "increase", 190, 60, 100, 24, true)
    .build();

try (FMApplication app = FMApplication.named("Counter")) {
    app.openWindow(nib);
    app.on("increase", e -> app.set("total", String.valueOf(++count)));
    app.run();
}
```

`run()` asks for the next event and waits until there is one, so a program doing nothing
costs nothing. That loop is the whole of its main thread.

Two processes, checked together: the desktop at 108 MB serving, the program at 52 MB
describing, one window on screen holding eleven named controls.

## What a screen is

Two systems disagree here, and the disagreement is older than either of them. On one, the
menu bar belongs to the screen: there is one, at the top, showing the menus of whichever
program is in front. On the other it belongs to the window: every window carries its own.

GNUstep, which has had to run the same programs under both, does not pick a side. It
names the argument. `NSMenuInterfaceStyle` is a user default, and setting it to
`NSMacintoshInterfaceStyle` puts a horizontal menu at the top of the screen while
`NSWindows95InterfaceStyle` puts it in the window. The same key, with the same two values,
decides it here.

Underneath that is a second question GNUstep never has to ask, because X11 answers it for
them: whether a window of this system is a window of the host system. That is
`FractalWindowStyle`, and it takes `Separate` or `Contained`.

| | `Separate` (the default) | `Contained` |
|---|---|---|
| A Finder window is | a window Windows knows about, with its own place in Alt Tab | a drawing inside one big window |
| The menu bar is | a strip along the top of the screen | the menu bar of that one window |
| The Dock is | a strip along the bottom | inside that one window |
| Maximising a Windows program | stops at the reserved edges | covers everything |

The strips reserve their edges through `SHAppBarMessage`, which is the documented way for
something that is not the taskbar to own a strip of screen: register the window, ask
where a bar of that size may sit, then take it. That reservation is the whole difference
between a menu bar and a window that happens to be at the top: with it, a maximised
program stops underneath instead of burying it. If the shell refuses, which it does when
Explorer is not running the desktop, the strips still work and simply stay on top, and the
log says which happened.

A window in `Separate` mode is the same window it always was, with the same class, the
same title bar drawn by this program and the same accessible tree, living inside an undecorated
frame of the host system's. The title bar drawn here moves the real window; the buttons
act on it; closing it closes the frame. Nothing above that layer changed, which is why the
checks did not have to.

The checking runs force `Contained`, because a window that is never shown cannot be a
window of the host system, and a check that rewrote the settings to run would not be a
check.

## The menu bar belongs to the front program

The bar is not Finder's. When the front window belongs to another program, that
program's name takes the second slot in bold and its own menus fill the bar: TextEdit
puts File, Edit and Format there, and Finder's View and Go go away until a Finder window
is in front again. The program menu holds About, Preferences, Hide, Hide Others, Show
All and Quit, which closes that program's windows. A window says which program it
belongs to by implementing `org.fractalmicro.appkit.AppWindow`; anything that does not is Finder's.

### And so does a program in another process

A window was never the whole of what a program puts on the screen. A program with no menus
has no Close, no Copy, no Preferences and no Quit, so until the menus could cross the
process boundary too, every program had to stay inside the desktop to have a File menu,
which is what was really keeping them all in one process.

The menus travel in the same description as the window:

```
.menu("File", MenuItem.of("Close", "close", "w", "command"))
.menu("Edit", MenuItem.of("Copy", "copy", "c", "command"),
              MenuItem.line(),
              MenuItem.of("Clear", "clear", "Delete"))
```

Keys are written the way a person says them: the key, and which of command, shift, option
and control are held, rather than a number that only this program would understand. The window
server builds real menus from that description, hangs them off the window as
`applicationMenus()`, and the bar treats them like any other program's. Choosing one sends
an event back the same shape as pressing a button does, so a program answers both with the
same `app.on("copy", ...)`.

Three mistakes worth writing down, all of which only appeared once there were two programs
instead of one.

A menu built and added but never laid out is in the bar and paints nothing: it needs
`validate()` and `doLayout()`, not just `add()`.

A program started against the installed framework rather than the code that is running
fails to find its own main class whenever the framework could not be replaced, which is
whenever a daemon still has it open. Installing now asks the daemons to stop first, and a
program starts from the code that is running.

And the window server kept one queue of events for everybody. That was invisible while only
one program was ever elsewhere; with two, each takes whatever is at the front, so one is
told a window it has never heard of has closed while its own button press is read by the
other. Events belong to the program whose window they came from, and a program says who it
is when it asks for the next one.

## Where a command goes

A command is not sent to a program. It is offered to whatever has the keyboard, and if that
cannot do it the offer passes to whatever the thing sits inside, and so on out to the program
itself. The first that can do it does. That is the responder chain, and it is why a menu item
in Cocoa is connected to First Responder rather than to anything in particular: the item names
a command and does not know who will do the work.

Copy is the case that shows why. The file manager copies files. A text field copies text.
Both answer to the same name, and which one happens depends only on where the keyboard is,
with neither of the two having been told about the other.

Answering no is how the offer passes on, and it matters as much as answering yes. A field
with nothing selected in it cannot copy, says so, and Copy carries on to the window behind:
with the cursor in an empty search field, Copy still copies the files that are selected, which
is what a Mac does.

Before this there was one object per window answering every command, so Copy in the Finder
copied files whatever had the keyboard, including while somebody was typing in the search
field with text selected in it.

## Menus that ask the program

A menu in Cocoa is asked, every time it opens, which of its items can be used. It asks
whatever would perform the command, which is a method call, because the menu and the program
are one process. Here they are not. The desktop draws the menu and the program is somewhere
else, so the question is carried: as a menu opens, the window server sends the program the
list of commands in it and waits for the list that comes back.

Once for a whole menu rather than once an item. A menu is opened by a mouse going down and
has to be right by the time it is drawn, so what matters is the number of round trips and
not the number of questions in one.

The default costs a program nothing and is most of the value. A command is live when the
program has said what it does and grey when it has not, which is the same rule Cocoa uses,
where an item is grey when nobody in the responder chain implements its action. That alone
removes the whole of the old lie: before this, a description said whether an item was
enabled and that was the last anybody heard of it, so Save stayed black in a program with
nothing to save and choosing it sent a command the program ignored. What a menu did then,
seen from outside, was nothing.

What is left for a program to say is the part that changes while it runs, and it says it by
answering `onValidate`:

```java
app.onValidate(action -> !action.sameAs(SAVE) || document.hasChanges());
```

A program that does not answer within a quarter of a second is one that has stopped
answering, and its menus stay as the description left them. That is what a Mac does with a
program that has stopped too: the menus are still there and still say what the program said
they would.

The answer is never handed to the program as an event. It is a question the program answers,
not news it has to know to expect, because a program that had to remember to answer would be
a program whose menus lie by default.

## The desktop

The desktop shows `%USERPROFILE%\Desktop-Folder`, created on first run, plus whichever
volumes the four checkboxes in Finder → Preferences → General ask for. Fixed drives are
hard disks, removable ones are external disks, optical drives answer to "CDs, DVDs, and
iPods", and mapped network drives are mounted servers. An optical drive with no disc in
it appears nowhere, neither on the desktop nor in the sidebar, exactly as on a Mac; it
is still listed under Computer, which is about the machine rather than about what is
mounted.

Disks are named by their volume label, or by the name Windows itself shows with the
drive letter taken off: "Local Disk", "DVD RW Drive", "data". Never a bare drive letter.

A disk on the desktop reads as its name and nothing else. Its size, free space and file
system are in the status bar and in Get Info, where they were asked for.

### Kinds

Get Info's Kind field and the description an item carries come from a table of
the types Mac OS X names for itself: a .plist is a property list, a .docx is a Microsoft
Word document, a .png is a Portable Network Graphics image. Anything outside the table
falls back to the description Windows holds for that extension, and anything Windows
only knows as "XYZ File" is a document, which is what an unknown type is called on a
Mac. So `thing.notanext` really is a document, and a .docx really is not.

Anything the Finder treats as a program is an application, including the shortcuts the
Applications folder is made of: a `.lnk` pointing at an `.exe` reads as "selected
application", not as an alias.

A selected item on the desktop is described as "selected icon". In a Finder view it is
described by what it is: "selected application", "selected Microsoft Word document",
"selected property list", "selected bundle".

## Sheets

A question about a document is attached to that document, not floated over the screen.
[Sheet.java](system/AppKit/src/org/fractalmicro/appkit/Sheet.java) drops the panel from under the window's title
bar, stops that window taking clicks while it is up, and leaves every other window alone.
It waits for the answer on a secondary event loop, so painting and the keyboard keep
working; a window that is not on screen falls back to the free standing alert, because a
sheet with nothing to hang from cannot be answered.

A program in another process can put one up too, and the shape of that is the point. It is
not a window it opens and later closes: it is one message, `sheet`, carrying a description
and going out on the program's own window, and the reply is the answer. The reply says
which button ended it and what everything in it held at that moment, because a sheet exists
to collect exactly that and a program that had to ask afterwards would be asking a sheet
that is no longer there.

Every button in a described sheet ends it, which is what a button on a sheet is for. The
sheet's controls are its own rather than the window's: a sheet asking for a name and a
window holding a name would otherwise be two controls with one identifier, and the sheet's
would quietly become the one the program meant afterwards.
