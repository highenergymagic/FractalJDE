# Being told what to do

A program here can be told to do something by something that is not a person clicking on
it. What travels is an Apple event: a suite, a command in that suite, who it is for, and
what it is about.

```java
FMAppleEventManager.sharedManager().sendEvent(
    FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.OPEN_DOCUMENTS,
                    FMString.of("Finder"), FMString.of("C:/Users/freya")));
```

## Four characters

The codes are four characters and always have been: `aevt`, `odoc`, `core`, `getd`. They
are not an abbreviation of anything, they are not shown to anybody, and they are not
translated. That is the point of them. `quit` is the command whatever language the person
sending it reads, so a program whose every menu item has been translated is still a
program that can be told to quit.

The same separation the menu items needed. A window carries the name of what a command
sends and the words for it are somewhere else, and this is the same idea reaching outside
the program: what is said between programs is a code, and what is said to a person is a
sentence, and neither is the other.

Anything shorter than four characters is padded and anything longer is cut, at the moment
it is made, because a code that is not four characters is not a code and carrying one
quietly would put it in a message nothing can read.

## What is in one

| | |
|---|---|
| `----` | what the command is about, which Cocoa calls the direct object |
| `data` | what it is to be given: the second half of a set, and of a make |
| `kocl` | which kind of thing to make |
| `insh` | where a made thing goes |
| `errn`, `errs` | why it could not be done, as a number and as a sentence |

The parameters are a dictionary keyed by those codes, so an event is readable as it
stands, and it goes on the wire as a property list like everything else here.

## The required suite

Every program answers these whether it is scriptable or not, because they are how the
system opens it and closes it:

| | |
|---|---|
| `aevt/oapp` | started with nothing to open |
| `aevt/rapp` | opened again while already running, which is a click on a Dock tile |
| `aevt/odoc` | opened on documents, which are the direct object |
| `aevt/quit` | asked to stop |

`quit` and `rapp` belong to the application rather than to whatever the program wrote:
a program that had to write down what quit means is a program that can be told to quit
and not.

## How one travels

The window server carries them. It already holds a queue for every program and every
program is already reading from it, so an event goes on that queue and the answer comes
back the way a menu validation does: a ticket goes out with the event, and the program
sends the ticket back with the answer.

Nothing in Foundation knows that. The manager takes a courier, and whichever layer has a
connection to the session installs one. A program in its own process installs one that
goes over its connection; the desktop installs one that calls the server directly,
because asking it over a connection would be the process writing to itself.

A program that is not running is started first. That is what makes telling something to
do something work without opening it by hand, and it is why a script can begin with
`tell application "TextEdit"` and not care.

## Two names

An event can be addressed by bundle identifier or by the name on the screen. Both are
used: one program has another's identifier, and somebody writing a script has the name.
Either finds the same queue.

## The Finder

The Finder runs inside the window server, so an event for it has nowhere to travel to. It
says so, and the server hands those straight to the manager in this process rather than
looking for a queue.

That is also why a handler can be written down against a program's name. One process here
holds the Finder, the Dock and the desktop's own panels, and a command one of them
answers is not a command the others answer. A Mac has one program to a process and needs
no such thing.

Telling the Finder to quit relaunches it, which is what quitting the Finder means: the
windows go, the desktop is drawn again, and it is still running.

## Which thing

A command has to say what it is about, and it cannot say it with a pointer: the thing is
in another process and may not have existed when the script was written. So it says it as
a chain.

```java
FMScriptObjectSpecifier.property(FMScriptObjectSpecifier.NAME,
    FMScriptObjectSpecifier.at(FMScriptObjectSpecifier.ITEM, 1,
        FMScriptObjectSpecifier.at(FMScriptObjectSpecifier.WINDOW, 1, null)));
```

That is "the name of item 1 of window 1", and it is four steps read backwards: the
application, the window at that index, the item at that index, the property. Each step
says what kind of thing it wants and how to pick it out, and the ways to pick one are the
ways there have always been: by name, by index, every one of them, or a property of
whatever holds it. An index counts from one, and from the end when it is negative.

Nothing is held between one command and the next. The chain is resolved against the
program's objects as they are now, so a script that asks twice gets two honest answers
rather than one answer and one stale handle.

## What a program shows

A program says what it holds by answering three questions about each object: what kind of
thing it is, what one property holds, and what things of a kind are inside it. All three
are codes. Cocoa reads the same shape out of the terminology and reaches the objects with
key value coding; written out like this it suits a language that has no key value coding
to lean on.

The Finder shows its windows and its disks, a window shows what it is looking at and what
is in it, and an item shows its name, its path and its size. Setting the path of a window
moves it there, which is what a script means by telling a window to look somewhere else.

## The standard suite

Getting, setting, counting, whether something is there, and getting rid of it. None of
those is a program's own idea, so none of them is written in a program: a program says
what it holds, and `FMScriptCommands` answers the five for whatever that is.

| | |
|---|---|
| `core/getd` | what this names |
| `core/setd` | put that in it |
| `core/cnte` | how many |
| `core/doex` | is there one |
| `core/delo` | get rid of it |

An object crossing to another process crosses as its name, because an object is a thing
in one process and a message is a thing on a wire. Asking for every window of the Finder
gets the names of the windows, which is what the question meant.

Asking for something that is not there is a refusal rather than an empty answer, with one
exception: `doex` is the command whose whole purpose is asking, and it answers no.

## What the commands are called

The events carry codes and a person writes words. The table between them is a file in the
bundle, the way an interface is a file in the bundle, so something that has never been
compiled against a program can still put that program's commands into English.

```xml
<suite name="Standard Suite" code="core" description="Common commands.">
    <command name="count" code="corecnte" description="How many there are.">
        <direct-parameter type="specifier"/>
        <result type="integer"/>
    </command>
    <class name="window" code="cwin" plural="windows">
        <property name="name" code="pnam" type="text" access="r"/>
        <element type="item"/>
    </class>
</suite>
```

That is Apple's sdef, cut to the part that is the terminology. A command's code is eight
characters, being its suite and then the command. A class carries the word for one of
them and the word for many, because a script says "window 1" and "every window" and means
the same class either way.

`Info.plist` names the file under `OSAScriptingDefinition`, and it is looked for in the
language directories first, so a translated terminology is a file beside the words rather
than a different program.

A program with no terminology is still scriptable. What it has not got is anybody able to
say what it can do, which is the difference between the two.

## What is checked

`ScriptingTest` sends real events through the real server. A command nothing answers
comes back as not handled, with the number Cocoa uses. A command that refuses comes back
with its own number and a sentence, because an exception is a thing in one process and a
reply is a thing on a wire. Telling the Finder to open a folder that is not there is a
reply saying so, and no window.

`ScriptingTest` also asks the Finder how many windows it has, what the first one is
called, what it is looking at, and what is in it, all through the same chain a script
would use. Then it tells the window to look somewhere else and asks again.

And it lays the terminology against the handlers, both ways round, because either half
going stale is the same defect: a command nobody can name, or a name for a command
nothing answers.

## Not yet

A language to write it in.
