# Aqua: what the guidelines actually say

Notes taken from the Apple Human Interface Guidelines of the Leopard era, which is the
document Snow Leopard was built against, plus what can be read off screenshots. Numbers
here are Apple's, not guesses, and the code should match them. Where this program
deviates on purpose, it is written down at the bottom.

Sources:

- Apple Human Interface Guidelines, Leopard edition, mirrored at
  <https://leopard-adc.pepas.com/documentation/UserExperience/Conceptual/AppleHIGuidelines/>
  Chapters on Windows, Layout, Controls, Text and User Input.
- <https://en.wikipedia.org/wiki/Aqua_(user_interface)>
- Snow Leopard desktop screenshot, <https://en.wikipedia.org/wiki/File:Snow_Leopard_Desktop.png>

## Typography

| Role | Font | Size | Used for |
|---|---|---|---|
| System | Lucida Grande Regular | 13 | Menus, dialogs, full-size controls |
| Emphasized system | Lucida Grande Bold | 13 | Alert message text |
| Small system | Lucida Grande Regular | 11 | Alert informative text, column headings, help tags |
| Emphasized small system | Lucida Grande Bold | 11 | Group titles |
| Mini system | Lucida Grande Regular | 9 | Mini controls |
| Views | Lucida Grande Regular | 12 | **Text in lists and tables** |
| Label | Lucida Grande Regular | 10 | Toolbar button labels, tick marks |

The views font is the one most often got wrong: list rows and icon labels are 12, not
11. Column headings are 11.

## Capitalisation

- Menu titles, menu items, push buttons: **title case**. Capitalise every word except
  articles, coordinating conjunctions, and prepositions of four letters or fewer;
  always capitalise the first and last word.
- Checkboxes and radio buttons: **sentence case**.
- Dialog and alert messages: **sentence case**.
- Labels and headings that are not sentences: title case.

## Layout, in pixels

| What | Value |
|---|---|
| Window margin, sides and bottom | 20 |
| Alert margin, sides | 24 |
| Alert margin, bottom | 20 |
| Between related controls | 8 |
| Between groups of controls | 12 |
| Above and below a separator | 12 |
| Title bar to first control, regular and small | 14 |
| Title bar to first control, mini | 10 |
| Bottom bar height, regular controls | 32 |
| Bottom bar height, small controls | 22 |
| Alert icon | 64 × 64 |
| Group box side margin | 16 |

## Alerts

The shape of an alert:

1. An icon, 64 by 64, at the top left. Usually the application's icon; a caution symbol
   badged with the application icon only when data may be destroyed.
2. **Message text** in the emphasized system font: one complete sentence, most often a
   question.
3. **Informative text** in the small system font, saying what will happen and how to get
   out of it. The guidelines are blunt about this: "Do not leave out the informative
   text."
4. Buttons at the bottom right.

Buttons are the part most often done wrong:

- Name the button after the action it performs: **Erase**, **Save**, **Delete**,
  **Empty Trash**, **Shut Down**. Not OK.
- The **rightmost** button is the action button, the one that confirms the message.
- **Cancel** sits immediately to its left.
- A destructive third choice, such as Don't Save, sits at least **24 pixels** away from
  the safe buttons.
- The default button is coloured and pulses. Do not make a dangerous action the default.

## Reserved keyboard equivalents

From the guidelines' table of shortcuts no program may take:

Escape, Command Tab, Command Shift Tab, Command Option D, Command H, Command Option H,
Command Shift Q, Command Space, Command Option Escape, Command F5, Control F1,
Control F7, F9 to F12.

Command Space for Spotlight and Command Option Escape for Force Quit are in that list,
which is why this program claims exactly those and not a set of its own invention.

## Things read off screenshots

- The Snow Leopard desktop picture is a **plume**: a bright magenta and violet aurora
  rising from the bottom centre of the screen and fanning outwards, over near-black blue,
  with fine rays. It is not a set of horizontal bands.
- The menu bar is 22 pixels tall, slightly translucent, with rounded corners at the top
  of the screen.
- Traffic lights sit at the left of a 22 pixel title bar, about 13 pixels across, spaced
  20 pixels apart, and they are grey until the pointer is over the group or the window is
  in front.

## Wording this program uses, and where it comes from

| Situation | Message | Informative | Buttons |
|---|---|---|---|
| Empty Trash | Are you sure you want to permanently erase the items in the Trash? | You can't undo this action. | Cancel, Empty Trash |
| Shut down | Are you sure you want to shut down your computer now? | | Cancel, Shut Down |
| Restart | Are you sure you want to restart your computer now? | | Cancel, Restart |
| Log out | Are you sure you want to quit all applications and log out now? | | Cancel, Log Out |
| Force quit | Do you want to force "name" to quit? | You will lose any unsaved changes. | Cancel, Force Quit |

## Where the drawing comes from, and where it cannot

Three sources get suggested for this, and it is worth writing down what each one is
actually good for.

**Darwin** is the kernel, the C library and the low level pieces. AppKit and HIToolbox,
which is where Aqua's drawing lives, were never opened. There is nothing there to take.

**Darling** runs Mac binaries on Linux by reimplementing the system interfaces. It does
not reimplement Aqua's appearance; where it draws at all it leans on other toolkits.
Nothing there either.

**OpenJDK's Aqua look and feel**, `com.apple.laf`, is real and is the closest thing to
what this program needs. Two problems. It ships only in macOS builds: the Windows JDK
here has no trace of it, which was checked rather than assumed:

    unzip -l "$JAVA_HOME/lib/src.zip" | grep -i aqua      # nothing

and it is GPL version 2 with the classpath exception. That exception covers linking
against the runtime; it does not let its source be copied into a program under another
licence. Vendoring it would decide this program's licence, which is a decision to make
deliberately, not by pasting a file.

So the controls here are drawn from the published measurements, from screenshots, and
from the descriptions in the guidelines. Push buttons, checkboxes, radio buttons, text
fields and scroll bars are painted by `org.fractalmicro.theme.AquaPainter` and installed as Swing
UI delegates, which replace the painting and leave every bit of behaviour, keyboard
handling and accessibility where Swing put it.

Details worth keeping:

- The default button **pulses**. It did in 10.6 and stopped in 10.7, so it belongs here.
  One shared timer drives it and stops itself when no default button is on screen.
- Scroll arrows sit **together at the far end** of the bar, which is what 10.6 shipped
  with. Both buttons keep their own behaviour; only the layout is changed, so the upper
  arrow still scrolls up.
- A focused control gets a soft blue glow, drawn as three fading strokes rather than a
  hard rectangle.
- `--controls` opens a window with one of everything, for comparing against a screenshot.

## Where this program deviates, deliberately

- Command is Alt and Option is the Windows key, because that is where those keys are on
  a PC keyboard.
- Renaming happens in a dialog rather than under the icon.
- The desktop folder is ~/Desktop-Folder rather than ~/Desktop, so it cannot be confused
  with the Windows desktop.

## Aqua

There is a separate note, [docs/aqua.md](docs/aqua.md), holding what the Apple Human
Interface Guidelines of that era actually say, with the numbers, so the code can be
checked against a source rather than against a memory of a screenshot. The short of it:

- **Type.** System 13, small system 11, **views 12**, label 10, mini 9, all Lucida
  Grande. Lists, tables and icon labels use the views font; getting that one wrong at 11
  is the commonest tell.
- **Spacing.** Window margins 20, alert margins 24 at the sides and 20 at the bottom,
  8 between related controls, 12 between groups, 14 from the title bar to the first
  control.
- **Alerts.** A 64 pixel icon at the top left, a message in the emphasized system font
  written as a question, informative text under it in the small system font, and buttons
  at the bottom right. The rightmost button is the action button and is named for what
  it does; Cancel sits to its left; a destructive third choice stands 24 pixels clear of
  both. There is no OK button in this program except where an alert is only telling you
  something, because OK says nothing about what you are agreeing to.
- **Case.** Menu items and buttons in title case; checkboxes, radio buttons and alert
  messages in sentence case.
- **The desktop picture** is a plume rising from the bottom centre of the screen, not a
  set of horizontal bands.

`--selftest` checks the wording and the type: that each alert asks a question, that no
confirmation is answered with OK or Yes, that the messages are in sentence case, and
that the fonts and margins are the numbers above.

### The controls

Push buttons, checkboxes, radio buttons, text fields, scroll bars, sliders, pop-up
buttons, progress bars, tabs and column headings are drawn by this program now, not by
Metal: the glossy capsule button, the blue default button and its
pulse, the blue checkbox with its white tick, the round radio button, the white field
with a shadow inside its top edge, and the blue capsule thumb with both scroll arrows
together at the end of the bar as 10.6 shipped them. The slider is a sunken groove with a
round knob, or a pointed one when the slider has tick marks, which is the distinction
Aqua draws between the two kinds. The pop-up button carries the blue square with the two
facing arrows. The progress bar is a capsule with diagonal stripes, still on a bar that
knows how far along it is and moving on one that does not. Tabs are one capsule of joined
segments, centred over the box. Run with `--controls` for a window with one of everything.

Only the drawing is replaced. Every one of these is a Swing delegate over the standard
component, so the arrow keys, the tab order, type-ahead and the names are Swing's own and
keep working. What is drawn as a slider is a slider, and answers to everything a slider
answers to.

None of it is taken from anywhere. Aqua's own drawing was never opened: Darwin is the
kernel and the low level libraries, not AppKit, and Darling does not reimplement the
appearance either. OpenJDK's Aqua look and feel is real but ships only in macOS builds
and is GPL with the classpath exception, which covers linking rather than copying source
into a program under another licence; taking it would decide this program's licence by
accident. So these are drawn from the published measurements and from screenshots.
[docs/aqua.md](docs/aqua.md) has the details.

Still Metal underneath: spinners, trees, split panes and tool tips. Alerts that belong to
a document are sheets now; alerts about the system as a whole are still free standing
windows, which is what they are meant to be.

## What can come from GNUstep

Its libraries are LGPL, and the code is Objective-C against a runtime this program does
not have, so nothing is taken from them. What is taken is what the project documents:
`NSMenuInterfaceStyle` above, the user defaults it lists, and the way it names a screen
model rather than assuming one. That is the useful part, and it is a reading of published
documentation rather than a copy of anything.
