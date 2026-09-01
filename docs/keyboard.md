# Keyboard

Every command has a key, and the keys are the ones Mac OS X used.

## Keyboard

Command is Alt and Option is the Windows key, because that is where they sit on a PC
keyboard. Menus draw the shortcuts with the Mac symbols; Swing reports the real key
names to assistive software.

| Keys | What happens |
|---|---|
| Alt Space | Spotlight |
| Alt Windows M | Menu bar; again for the status menus |
| Ctrl F2 | Menu bar, the Mac binding |
| Alt Windows D, Ctrl F3 | Dock |
| Alt N | New Finder window |
| Shift Alt N | New folder |
| Alt O, Alt Down | Open |
| Return | Rename |
| Alt W | Close window |
| Alt I | Get Info |
| Alt D | Duplicate |
| Alt L | Make alias |
| Alt R | Show original |
| Alt T | Add to sidebar |
| Alt Y | Quick Look |
| Alt Backspace | Move to Trash |
| Shift Alt Backspace | Empty Trash |
| Alt C, Alt V | Copy and paste |
| Alt A | Select all |
| Alt 1 to Alt 4 | Icon, list, column, Cover Flow |
| Alt J | Show View Options |
| Alt Up | Enclosing folder |
| Alt [ , Alt ] | Back, forward |
| Shift Alt A / U / H / D / C / O / K | Applications, Utilities, Home, Desktop, Computer, Documents, Network |
| Windows Alt L | Downloads |
| Shift Alt G | Go to Folder |
| Alt K | Connect to Server |
| Alt M | Minimize |
| Alt ` | Cycle through windows |
| Alt , | Finder Preferences |
| Windows Alt Escape | Force Quit |
| Alt F | Find |
| Alt / | Show or hide the status bar |

### From inside other programs

Four shortcuts are claimed from Windows itself, so they work while the keyboard is in a
mail client or a browser: Spotlight, the menu bar, the Dock and Force Quit. Windows
hands the keystroke to this program before the program in front sees it, and the desktop
comes forward before it acts.

Windows keeps Win+D and Win+M for Explorer and will not give them up while Explorer is
running, so the menu bar and the Dock fall back to their Mac bindings, Control F2 and
Control F3, and take the Windows combinations if they ever come free. Alt Space, which
Windows normally uses for a window's own menu, belongs to Spotlight while this desktop
runs, and is given back when it stops.

### Clusters

The window buttons, the toolbar and the Dock each behave as one stop in the tab order,
with the arrow keys moving inside them, which is how Mac OS X behaves with full
keyboard access switched on. Escape leaves a cluster and puts the keyboard back where
it came from, however it arrived: by Tab, by a shortcut or by a click.

Each cluster is a focus traversal policy provider and deliberately not a focus cycle
root. A cycle root keeps Tab inside itself, which is exactly the trap this is meant to
avoid; the keyboard test checks for it. Up on a Dock tile opens that tile's menu, where Keep in Dock lives;
keeping a tile writes it into `persistent-apps` in `org.fractalmicro.dock.plist`, so it is
still there next time.

## Screen readers

Swing reaches Windows screen readers through the Java Access Bridge. If nothing is
spoken, run `jabswitch -enable` once and sign out and back in.

Items read as their names. Views are named "Icon view", "List view", and stop there:
nothing recites the arrow keys at you, nothing bolts a disk's capacity onto its name,
nothing tacks "press to open" onto a button. The root window is called Finder, because
that is what it is. Where something needs explaining it is in the status bar,
in Get Info, or in Help, not in a name a screen reader reads a hundred times an hour.

A window that opens takes the keyboard with it: a Finder window lands on its file list,
everything else on its first control. Without
that a new window is drawn but the keyboard stays behind, which sounds exactly like
nothing happening. About This Computer keeps its figures in a small table for the same
reason: a table can be walked, a column of labels cannot be.

Menus are ordinary `JMenuBar`, `JMenu` and `JMenuItem`; only the painting is replaced.
Desktop icons are a `JList`, list view a `JTable`, the sidebar a `JTree`, column view a
row of `JList`s. Cover Flow's covers are marked decorative and the table under them
carries the same items.
