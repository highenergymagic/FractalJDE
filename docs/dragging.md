# Dragging

Picking files up and putting them somewhere else.

## What a drop does

There is one rule everybody who has used a Mac knows without being able to say it. Dragging
a file to another folder on the same disk moves it. Dragging it to a different disk copies
it. It is the right default because it is what the words mean: moving something between two
drawers of one desk is moving it, and there is no copy of a letter left behind, but between
two desks somebody has to carry it, so it is copied and they decide whether to throw the
first one away.

The keys held say otherwise, and they are the same three everywhere:

| held | what happens |
|---|---|
| nothing | move on one disk, copy across two |
| Option | copy, wherever it is going |
| Command | move, wherever it is going |
| Option and Command | an alias, and the file stays where it is |

[FMDragOperation.java](system/AppKit/src/org/fractalmicro/appkit/FMDragOperation.java) is
the whole of that rule and nothing else. Whether two paths are on one disk is asked of the
file store rather than worked out from the first letter of the path, which is right on this
host by accident and wrong wherever a folder is a mounted disk of its own. A disk that will
not answer is treated as another one, so the files are copied: a copy that should have been
a move leaves the original where it was and a person can tidy up after it, and a move that
should have been a copy cannot be tidied up after at all.

The answer is worked out while the mouse is still down and shown on the pointer, so somebody
can change their mind before letting go.

## Where a drop can land

| | |
|---|---|
| a folder or a disk, in any view | the files go into it |
| the white space in a window | the folder that window is showing |
| the desktop | the desktop folder |
| a place in the sidebar | the files go into that folder |
| between two places in the sidebar | the folder joins the list, as a shortcut |
| the Trash, on the desktop or in the Dock | the files are thrown away |
| a program in the Dock | that program opens them |

Dropping onto the white space is the one people use without noticing, and it has to work at
the edges: a window showing four files is mostly empty space, and every part of it that is
not an icon is the folder.

## What a drop refuses

A folder cannot be dropped into itself, or into anything inside itself. That is the case
that eats the folder rather than moving it: the move rewrites the path out from under the
copy that is walking it, and what survives depends on the order the walk happened to take.
Nothing can be moved into the folder it is already in either, because that would mean
nothing, though copying it there is Duplicate and is allowed.

A refusal is a pointer that will not take. That is the only warning worth giving, because it
arrives while the mouse is still down and costs nothing to heed.

## Spring-loaded folders

Resting a drag on a folder opens it, and the drag carries on inside. Without it, filing
something three levels down means putting it somewhere, opening your way down, and picking
it up again, all while holding something you cannot put down.

The clock starts again every time the answer changes, so crossing four folders on the way to
a fifth opens none of them. What counts is resting: moving off something and back onto it is
not resting on it.

Where it opens depends on where the drag is. A window goes into the folder. The sidebar
takes its window to the place, unless the pointer is near a row's edge, where letting go
would add to the list instead and springing open would move a window for no reason. The
desktop has no window to reuse, so it opens one.

However the drag ends, whether it was dropped there, dropped somewhere else, or given up on,
what springing opened is put back: a window that was already there returns to the folder it
was showing, and one that springing opened is shut. That half is the half people notice.
Without it a drag through four folders leaves four windows open, which is the thing spring
loading exists to save them from.

It is on by default with a delay of half a second, both in Finder → Preferences → Advanced
and under the names Mac OS X keeps them at, `SpringingEnabled` and `SpringingDelay`. The
delay is held between a fifth of a second and two: below the first a folder opens while
somebody is still crossing it on the way to somewhere else, and above the second nobody
waits long enough to find out that it would have.

## Replacing

Dropping something onto a folder that already has a file of that name asks first, and the
file being replaced goes to the Trash rather than being written over. Mac OS X writes over
it. That was always the sharpest edge in the Finder, the one action in the whole program
that destroyed a file with a single click and no way back, and there is no reason to copy
it.

## Undoing

Every drop registers the way back from itself, once for the whole drop rather than once per
file: a person dropped one thing, however many files were in their hand, and one Undo puts
all of it back. A move is undone by moving the files back to where they came from. A copy or
an alias is undone by removing what was made, deleted outright rather than put in the Trash,
because undoing something that made a copy should leave no trace of it and a Trash with the
copy in it is a trace.

The menu says which: Undo Move, Undo Copy, Undo Make Alias.

## How it is put together

[FMFileDragging.java](system/AppKit/src/org/fractalmicro/appkit/FMFileDragging.java) is in
the kit and not in the file manager, because a text editor accepting a dropped document asks
the same questions and the answers should not differ because a different program asked. A
view answers two of them: which files a drag starting now would carry, and what letting go
where the pointer is would do.

The second is answered with an operation rather than with a place, which is the shape Cocoa
uses and is the right one. The Trash takes a drop and is not a folder. The sidebar takes one
and makes a shortcut rather than moving anything. A view that could only ever answer "this
folder" would have no way to say either. `IntoFolders` is there for the ordinary kind, which
is most of them, and it carries the refusals so that every view refuses the same things.

Swing has nowhere to say that a drag has arrived over a view and nowhere to say it has left
again: a transfer handler is asked whether it would take a drop and is never told the pointer
went elsewhere, so a view that lit up on the first question would stay lit for the session.
The drop target underneath does know, and `install` listens to it.

## Other programs

The files travel as a file list, which on this host is `CF_HDROP`: the same thing every other
program on the machine copies files as. Dragging out of a window and into Explorer works, and
so does the other direction, and so does Copy in one and Paste in the other.
[FMPasteboard.java](system/AppKit/src/org/fractalmicro/appkit/FMPasteboard.java) reads and
writes it, and it is the same call for a drag and for a copy, because a drag carries what a
copy carries.
