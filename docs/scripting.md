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

## What is checked

`ScriptingTest` sends real events through the real server. A command nothing answers
comes back as not handled, with the number Cocoa uses. A command that refuses comes back
with its own number and a sentence, because an exception is a thing in one process and a
reply is a thing on a wire. Telling the Finder to open a folder that is not there is a
reply saying so, and no window.

## Not yet

The terminology, which is the file that says what the commands are called and which
objects they work on. The core suite, which is getting, setting, counting and making. And
a language to write it in.
