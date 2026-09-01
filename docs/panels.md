# The save and open panels

Every program that saves asks the same question. A person should learn the answer once, so
the panel belongs to the system and not to any of them.

## What a program says

The program says what it wants out of the panel and nothing about how it looks:

```java
FMSavePanel panel = FMSavePanel.savePanel()
    .nameFieldStringValue(FMString.of("Untitled.rtf"))
    .directoryURL(lastPlace())
    .allowedFileTypes(typesFor(format))
    .formats(FORMAT_NAMES, FMString.EMPTY);

FMURL where = app.runSavePanel(panel);
if (where == null) return false;      // cancelled, which is a real answer
```

The names are Apple's, from `NSSavePanel`: `nameFieldStringValue`, `nameFieldLabel`,
`directoryURL`, `allowedFileTypes`, `prompt`, `title`, `message`, `canCreateDirectories`.
Somebody who knows the one knows this, which is worth more than a set of names chosen here.

`FMOpenPanel` is the same panel with the half that names a new file taken out. Opening is
choosing something already there, so there is nothing to type. Cocoa has it the same way,
an open panel being a save panel that does less, and it is why the two look alike from
across the room.

## Two shapes

Collapsed, it is a name and a pop-up of the usual places. That is the whole of what most
saves need.

```
Save As:  [ Untitled.rtf              ] [▼]
Where:    [ Documents                 ▾]
                    File Format: [ Rich Text Format ▾]
                              [ Cancel ]  [ Save ]
```

Expanded, from the triangle beside the name, it adds the sidebar, the browser, navigation
and a button to make a folder. Which it was left in is remembered under
`NSNavPanelExpandedStateForSaveMode`, where Mac OS X keeps it, because somebody who wants
the browser wants it every time.

## Three views

`FMBrowser` draws a folder three ways, and **⌘1 / ⌘2 / ⌘3** choose between them. They are the
same keys as a Finder window, because it is the same choice.

| | |
|---|---|
| Icons | recognising something by its shape |
| List | comparing files against each other by date and size |
| Columns | finding your way through a tree |

Switching changes how the folder is drawn and never which folder you are in. That is what
makes the three interchangeable rather than three places to be.

Columns are the default and the reason the browser exists. One column per level, side by
side; choosing a folder opens the next beside it, and everything to the right goes because
it described a route no longer being taken. What is on screen is the whole route rather
than the destination, which a list cannot show: in a list you know what is in the folder
you are in and nothing about how you got there or what was beside it.

Left and right arrows move between columns, up and down within one, so a column browser is
navigable without a mouse.

## What else is in the expanded panel

Back and Forward walk the folders already visited. The label between them names the folder
a save would land in, which is not decoration: in a column browser the deepest column can
be scrolled off the left.

The field on the right narrows the listing to what is being looked for, and **⌘F** puts the
keyboard in it, expanding the panel first if it was collapsed. What is searched is the
folder being looked at, not the disk. A panel is for finding something you know is there,
and the thing that searches everywhere is Spotlight.

**⇧⌘G** asks for a path and goes there, for somebody who already knows it. **⇧⌘N** makes a
folder. **⌘↑** goes to the enclosing one.

## The sidebar

The same places the Finder's sidebar shows, from the same list.

That is not a nicety. The list used to be written out inside the Finder, and the panel had
four folders of its own, so a folder dragged into the sidebar was in the Finder and not
when you went to save into it. `Places`, in Foundation, owns it now: devices, shared,
places, saved searches and whatever was added. Both read it, and Add to Sidebar writes
through it rather than poking the preference directly.

## Where it is drawn

A sheet, hanging from the document window it concerns, when there is one to hang from.
Aqua attaches a question to the document rather than floating it over the screen: the
panel blocks its own window and every other window keeps working. With no window to belong
to, which is a program that has not opened one yet or a check with no screen, it stands on
its own instead.

A program in a process of its own never builds it. It has no screen: the screen belongs to
the window server, and a window drawn anywhere else sits outside the desktop where nobody
is looking for it. So the program sends `savePanel` or `openPanel` and the panel is built
over the desktop, which is also what Cocoa does: the panel runs outside the asking
application there too.

TextEdit's Save was a one-line text prompt before this, and that is what the arrangement
was hiding: a text field has no browsing in it. Somebody who wanted to save into a folder
had to know its path and type it, which is a thing nobody does.

## Small things it gets right

A name typed without an extension gets the one the format implies. A name typed *with* one
keeps it, even a different one, because somebody who typed an extension meant it. Quietly
correcting them is how a file ends up called `notes.txt.rtf`.

Saving over something already there asks first. It is the one thing the panel does that
cannot be undone.

The listing shows folders always and files only of the kinds the panel handles, so a
program that opens text does not offer somebody a disk image and then refuse it.

Every column, list and grid announces the folder it shows, and every row its name and its
kind. An icon view draws real icons: without them it is a list with worse spacing, and
recognising something by its shape was the reason to choose that view.

## What is checked

`PanelTest` builds the panel and asks it questions rather than showing it. A modal panel
put up during a check is a check that never finishes.

It checks that the API uses `NSSavePanel`'s vocabulary, that extensions are completed and
preserved correctly, that an open panel says Open where a save panel says Save, that a
browser opens as one column and choosing a folder opens the second, that switching views
keeps the folder, that searching narrows and clearing restores, and that every column is
named.

`WindowServerTest` holds the two rules that made the old Save look like it did nothing:
every message the server names must be one it answers, and no program in a process of its
own may draw its own dialogs.

## Not there yet

No Tags field. No Cover Flow among the views, though the Finder has it. The sidebar in the
panel is flat: it shows the folders under PLACES but not the DEVICES and SHARED headings
the Finder renders from the same source.
