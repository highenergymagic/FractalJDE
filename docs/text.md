# Text

The text system, what checks the spelling, and what a service is.

## The text system

Spelling, smart quotes, detected things and services are not one program's features. They
belong to text, so they live in one place, `org.fractalmicro.appkit`, and every text control in
this system goes through it.

| | |
|---|---|
| `FMTextField` | a line of text: dialogs, panels, the Spotlight field, the rename field |
| `FMTextArea` | a box of text: Spotlight comments in Get Info |
| `FMTextView` | a document: TextEdit |
| `FMText` | what all three install, and what anything else can call |

Each one arrives with spelling, the substitutions, the detectors, and a menu carrying
suggestions for a misspelling, the action for anything detected under the pointer, the
editing commands, and the services. A field that holds a file name or a number turns the
parts off it has no use for, `field.plain()`, rather than being a different class.

The settings are the system's, in the global domain, under the names that domain uses:
`NSAutomaticSpellingCorrectionEnabled`, `NSAutomaticQuoteSubstitutionEnabled`,
`NSAutomaticDashSubstitutionEnabled`, `NSAutomaticDataDetectionEnabled`. Turning smart
quotes on turns them on in the Get Info comments box and in a TextEdit document alike,
because it is one setting and there is one text system.

The self test walks every window this program opens and fails if it finds an editable text
control that is not one of these. That check found the Spotlight comments box in Get Info
still using a plain one, which is exactly the field where spelling matters most.

## Spelling

The dictionaries are the ones this machine already has. Windows ships a spell checking
service, and [SpellChecker.java](system/LibSystem/src/org/fractalmicro/win/SpellChecker.java) asks it: a word learned
here is learned everywhere, and a word learned elsewhere is known here. No word list is
bundled and no dictionary of this program's own exists.

The service is a set of component objects, so the calls go through a table of function
pointers rather than by name. An interface pointer points at its table, `Check` is the
fifth entry of `ISpellChecker`, and calling it means reading the table, taking that entry
and calling it with the interface as its first argument. A machine with no dictionary says
so, and nothing pretends to check anything.

Misspellings are drawn with a red underline, which is of use only to someone who can see
it, so the same information is in the **Spelling and Grammar** panel as text: the word in
a field, the suggestions in a named list, and Change, Ignore, Learn and Find Next as
buttons. Working through a document with the keyboard alone reaches the same place as
working through it with the mouse.

Checking runs half a second after typing stops rather than on every keystroke, because
checking a long document on every letter is felt.

### Data detectors

A web address is a place to go, a mail address is someone to write to, a telephone number
is a call, a date is a day, and a postal address is a place.
[DataDetectors.java](system/AppKit/src/org/fractalmicro/appkit/DataDetectors.java) finds all five and draws a
dotted line under each.

Patterns are wrong sometimes, so nothing is changed in the document and nothing happens on
its own: a detection is an offer. **Edit ▸ Detected Data** lists what was found, naming
each one, "Email address: freya@example.com", with its action, so the dotted underline
is not the only way to know something is there.

Still not implemented, and worth saying rather than leaving to be discovered: services,
vertical layout, and the zoom control.

Activity Monitor lists processes and can end one.

## Services

A service is offered by one program and used from any other: make a new document out of
the selection, search for it, open it as an address, reveal it in the Finder, change its
case. Each says when it applies, so the menu offers what can actually be done with what is
selected rather than a list that will not work.
