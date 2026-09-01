# Replacing the Windows shell

What `explorer.exe` does for everyone else, and how much of it this can take over.

## Towards a shell replacement

Replacing `explorer.exe` means taking on the jobs it does for everyone else. In the
order they need doing, because each unlocks the next:

1. **See and drive other programs.** Done: the window list above, activate, hide, quit.
   Still to do: shell hooks instead of polling, and each program's own icon on its tile.
2. **A message window.** Done: a window class with a Java window procedure, a
   message-only window and a pump thread, in `org.fractalmicro.win.MessageWindow`. Nothing to
   look at, and the keystone for what follows.
3. **The notification area.** Done, as far as it can be done alongside Explorer: a
   window of class `Shell_TrayWnd` with a `TrayNotifyWnd` child, a decoder for the
   `WM_COPYDATA` that `Shell_NotifyIcon` really sends, and the icons shown as named
   menus at the left of the status items. Explorer holds that window class while it is
   running, so today this puts its hand up and hears nothing; the moment Explorer is
   not there, it hears everything. See below.
4. **Session and power.** Done. Shut Down, Restart, Log Out, Sleep and Lock do the
   real thing, through `ExitWindowsEx`, `SetSuspendState` and `LockWorkStation`, with
   the shutdown privilege taken first because it starts switched off in every process.
   The dialogs say plainly that every program will close, and offer the smaller choice
   of quitting this desktop instead.
5. **Start-up duties.** Done. The `Run` keys for the account and the machine, and both
   Startup folders, are read and started, but only when this really is the shell:
   running them under Explorer would start everything twice. `RunOnce` is read and
   never run, since its entries are deleted as they are used and getting that wrong
   during someone's install is not worth the tidiness. System Preferences has a Login
   Items pane listing what would start, with a button to start one now.
6. **Packaging and installing, reversibly.** An app image with its own runtime, then a
   tool that sets and unsets the shell. This is the step that can lock someone out of
   their own machine, so it wants a watchdog, a restore path, and a rehearsal on a
   spare account before it goes anywhere near a real one.
7. **Finder depth**, in parallel: inline renaming, drag and drop, copying with progress
   and a cancel button, undo, per-folder view settings, labels, a Spotlight index with a
   file watcher, Connect to Server, permissions in Get Info.
