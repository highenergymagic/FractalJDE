# The programs

What ships, and what each one is written against.

## TextEdit

Its behaviour comes from Apple's published TextEdit source (version 1.9), sample code
under a BSD-style licence. That source is Objective-C against Cocoa, so it documents what
the program does rather than supplying anything to copy: the settings keys, the commands
and the wording follow it; the code is written fresh against Swing.

A document is rich text, held as RTF, or plain text, held as itself in a chosen encoding.
Everything else hangs off that distinction.

| Menu | What is there |
|---|---|
| File | New, Open…, Open Recent, Close, Save, Save As…, Duplicate, Revert to Saved, Show Properties, Print… |
| Edit | Undo, Redo, Cut, Copy, Paste, Delete, Select All, Find ▸ (Find…, Find Next, Find Previous, Use Selection for Find, Jump to Selection, Select Line…), Substitutions ▸ (Smart Quotes, Smart Dashes, Smart Copy/Paste) |
| Format | Font ▸ (Show Fonts, Bold, Italic, Underline, Bigger, Smaller, Show Colors), Text ▸ (Align Left, Center, Justify, Align Right, Show Ruler), Make Plain Text, Prevent Editing, Wrap to Page, Show Page Breaks |

**Make Plain Text** (Command Shift T) asks first, but only when there is something to
lose, which is what the original checks before putting the sheet up, and the alert is
that program's own: "Convert this document to plain text?", with "Making a rich text
document plain will lose all text styles (such as fonts and colors), images, attachments,
and document properties." Converting a document with no styling asks nothing.

The settings are in `org.fractalmicro.textedit`, under the key names that source uses:
`RichText`, `ShowPageBreaks`, `AddExtensionToNewPlainTextFiles`, `WidthInChars`,
`HeightInChars`, `PlainTextEncoding`, `PlainTextEncodingForWrite`, `IgnoreRichText`,
`IgnoreHTML`, `TabWidth`, `ShowRuler`, `SmartQuotes`, `SmartDashes`, `SmartCopyPaste`,
`NumberPagesWhenPrinting`, `WrapToFitWhenPrinting`, `AutosavingDelay`, `NSFont`,
`NSFixedPitchFont`. Preferences has the two panes the original has, New Document and
Open and Save.

Document properties (author, company, copyright, title, subject, comments, keywords)
are kept for a rich document and shown in a panel. They are held with the document rather
than written into the RTF, which is the one place this falls short of the original.

## Calculator

The first program here that is only a program. It has no windows of its own and draws
nothing: it hands over a description, waits for events, and does arithmetic in `BigDecimal`.
It is in Applications like anything else, its executable is a real Mach-O, and opening it
starts a process:

```
PID   Host   Kind          State     Memory   Name
0     self   system        running   18.6 MB  kernel_task
1     self   system        running   18.6 MB  launchd
2     self   system        running   18.6 MB  WindowServer
3     48728  application   running   67.4 MB  Calculator
```

Task 3 is a second virtual machine. Everything on the screen belongs to the window server;
everything about arithmetic belongs to Calculator; and if it stops, the desktop does not
notice.

## Running programs

The Dock lists what is actually running, read from the windows on screen rather than
from what this program happened to start: `EnumWindows` filtered the way a taskbar
filters (visible, titled, not a tool window, not owned, not cloaked), grouped by
executable so seven Notepad windows are one application, and named from the Start menu
entry pointing at the same program, so `7zFM.exe` reads as "7-Zip File Manager".

A tile's menu lists that program's windows; choosing one brings it forward, and Hide and
Quit act on all of them. Clicking the tile cycles its windows.

## The notification area

Putting an icon beside the clock is not a call to the system: `Shell_NotifyIcon` finds
the window of class `Shell_TrayWnd` and sends it a `WM_COPYDATA` carrying a signature,
an operation, and a `NOTIFYICONDATA`. Explorer owns that window, which is why the icons
vanish when Explorer is not running. This program can own it instead.

`org.fractalmicro.win.TrayHost` puts up the two windows programs look for, decodes the message in
both of its shapes. A 32 bit sender's handles are four bytes wide and everything after
them shifts, so the two structures differ by twenty bytes out of nine hundred, and the
size the sender declares is what says which is which. It also keeps the list of what has
been asked for. Icons appear in the menu bar as menus named after their tooltip, with
Open and Show Menu sending the left and right click messages back to the owning program.
The picture itself is drawn from the icon handle, which is shared across processes.

While Explorer is running it owns the class and this hears nothing, so the decoding is
checked instead: `--selftest` builds the messages a program would send, byte for byte,
in both shapes, and feeds them to the same code the system would reach.
