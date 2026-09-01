# The Finder

Files, the things that stand for them, and the operations on them.

## Aliases

**Make Alias** (Command L) writes a real alias file, not a symbolic link:

```
Budget.txt alias
  data fork        empty
  AFP_Resource     a resource fork holding an 'alis' resource: the alias record
  AFP_AfpInfo      Finder information: type alis, creator MACS, the alias flag set
```

Those two streams are alternate data streams, which NTFS has and which are the same idea
as a resource fork, and they are the streams a Mac itself uses when it writes to a
volume like this one, so the names and the structures are not invented here.

[AliasRecord.java](system/Foundation/src/org/fractalmicro/alias/AliasRecord.java) writes the version 2 record the
Alias Manager defines: 150 bytes of fixed fields (kind, volume name, volume created,
parent directory, file name, file number, type and creator, the search levels) followed
by tagged entries for the parent name, the file name, the absolute path, the volume path
and the file reference number, ending with a tag of -1.

The point of all that is that an alias is not a path. [Alias.java](system/Foundation/src/org/fractalmicro/alias/Alias.java)
follows one the way the Alias Manager was documented to:

1. the path in the record, accepted only if what is there is still the same file
2. the file reference number, which finds the file wherever it has been moved or renamed to
3. the recorded name inside the recorded folder, for a file that was replaced

A symbolic link only ever has step one. The self test renames the original, then moves it
to another folder, and requires the alias to find it both times; when it does, the Finder
writes the record again with where the file is now, which is what the Finder does.

`Show Original` follows all three kinds: an alias, a symbolic link, or a Windows shortcut.
Get Info names the original, says when it had to be chased down, and gives the size of the
resource fork.

## Labels

**File ▸ Label** marks a file with one of the seven colours, or none. The label is not
kept in a database of this program's own: it is in bits 1 to 3 of the file's Finder flags,
where the Finder has always kept it, inside that same AFP_AfpInfo stream. Copy the file
somewhere else with anything at all and the label goes with it.

The names can be changed in the Labels pane of Finder preferences, and the label is drawn
as a coloured pill behind the name, in black or white text, whichever can be read against
that colour. The label is part of what the item is called, because a colour has no name:
an item is "selected Microsoft Word document, Red label".

A volume that cannot hold a second stream (FAT, exFAT, most network shares) is detected
by trying, and the records go to a property list beside them instead. That is worse in the
way sidecar files are always worse, so the program says which happened rather than quietly
pretending: `Sidecar.inUse()` reports it, the self test prints it, and marking a file on
such a volume says so.

## Renaming

Return, or a slow click on the name of something already selected, edits the name where
the name is: under the icon, or in its row. The part before the extension is selected,
because that is the part people change. Return keeps it, Escape leaves it, clicking
elsewhere keeps it.

The field is an `FMTextField` with spelling turned off, since a file name is not prose and
a red line under half of them would mean nothing.

## Shortcuts say what they did

What a window says about itself is what has focus. A shortcut usually changes something
that has no focus at all: a window closes, a program quits, the Trash empties. Nothing is
left to describe, and whatever the keyboard lands on next presents itself as though it had
been chosen on purpose.

So every shortcut says what it did. Command W says "Close Window"; Command Q says "Quit
TextEdit", naming whichever program is actually in front.

The words are the menu's own words, read out of the menu bar itself rather than written a
second time: a menu item and its shortcut are the same command and must not be able to
disagree. The bar is re-read whenever it is rebuilt, which is whenever the program in front
changes, so what Command Q says follows what Command Q does. A shortcut whose menu item is
switched off says nothing, because it did nothing.

The speaking is NVDA's, through the controller client it publishes for this. **The library
is bundled**, in `resources/nvda`, rather than looked for on the machine: a copy found lying
about is a copy nobody chose, of unknown version. It is written out beside the framework on
first use and loaded from there.

That library is NVDA's, not this project's. It is under the **LGPL 2.1**, included
unmodified, loaded at run time, which is the arrangement that licence is written for, and
is not the thing the licence section above rules out. Absorbing LGPL source into a
CDDL-headered file would be relicensing someone else's work. Shipping their library
untouched, and letting anyone replace the file with their own build of it, is what the LGPL
asks for. Its licence and readme travel with it.

Where NVDA is not running, nothing is said and nothing breaks.
