/*CDDL HEADER START
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://illumos.org/license/CDDL.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *
 * CDDL HEADER END
 * Copyright (C) 2026 by Fractal Microsystems, Inc.
 * Use is subject to license terms.
 */

//! The graphical launcher: what starts the system when nobody is watching a terminal.
//!
//! Started from a terminal, the system narrates its own start-up and the terminal is where
//! it goes. Started by double-clicking, or set as the Windows shell so that it comes up at
//! logon instead of Explorer, there is no terminal and nothing to look at for the several
//! seconds it takes. So this puts up a boot screen, reads the same narration off the
//! program it started, and shows the line the system is on.
//!
//! It is a separate program, and small, for the reason a boot loader always is: it has to
//! work on a machine where nothing else does yet. On a first start there is no volume, no
//! framework and no Java class anywhere on the disk except inside the jar it is about to
//! run, and this is the part that has to find a runtime, start that jar, and say something
//! while it happens.
//!
//! It stays for as long as the system does. As the shell it is the process Windows watches
//! to know whether anybody is logged in, so it waits for what it started rather than
//! starting it and standing aside.

#![windows_subsystem = "windows"]

mod screen;
mod win;

use screen::{Scene, Surface, TICK_MILLISECONDS};
use win::*;

use std::cell::RefCell;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::Instant;

/// How long the boot screen will stay up before deciding nothing more is coming.
///
/// Not a guess at how long starting takes, which is seconds; a bound on how long a screen
/// nobody can get past is allowed to last when something has gone wrong in a way that
/// hangs rather than stops. Escape takes it down before that.
const GIVE_UP_AFTER_SECONDS: u64 = 180;

/// What the reader thread has heard, and what the screen draws.
struct Heard {
    /// The last thing the system said it was doing.
    saying: String,
    /// It said it was up.
    ready: bool,
    /// The last lines of everything, for saying what happened when it was not.
    tail: Vec<String>,
    /// Nothing more is coming: the program has ended, or its output has.
    over: bool,
}

static HEARD: Mutex<Heard> = Mutex::new(Heard {
    saying: String::new(),
    ready: false,
    tail: Vec::new(),
    over: false,
});

/// How many lines are kept for a failure to be explained with.
const TAIL: usize = 20;

fn main() {
    unsafe {
        // Said before any window exists, or Windows draws this at a quarter size on a
        // doubled display and scales it up, which on a screen that is mostly one flat
        // colour and one mark is very obvious.
        SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    }

    let arguments: Vec<String> = std::env::args().skip(1).collect();
    if let Some(at) = arguments.iter().position(|a| a == "--draw") {
        let into = arguments.get(at + 1).cloned().unwrap_or_default();
        let size = arguments.iter().position(|a| a == "--size")
            .and_then(|at| arguments.get(at + 1))
            .and_then(|said| measure(said))
            .unwrap_or((1440, 900));
        std::process::exit(draw_to_file(&into, size.0, size.1));
    }

    let here = std::env::current_exe().ok()
        .and_then(|exe| exe.parent().map(Path::to_path_buf))
        .unwrap_or_else(|| PathBuf::from("."));

    if let Some(at) = arguments.iter().position(|a| a == "--where") {
        let into = arguments.get(at + 1).cloned().unwrap_or_default();
        std::process::exit(report(&here, &into));
    }

    let jar = match find_jar(&here) {
        Some(jar) => jar,
        None => {
            complain("FractalJDE.jar is not here.\n\nThe launcher starts the jar sitting \
                      beside it. Keep Fractal.exe, FractalJDE.jar and BaseSystem.dmg in \
                      one directory.");
            std::process::exit(70);
        }
    };
    let java = match find_java(&here) {
        Some(java) => java,
        None => {
            complain("No Java runtime was found.\n\nFractalJDE needs Java 21 or newer. \
                      Install one, or set FRACTAL_JAVA to the javaw.exe to use.");
            std::process::exit(70);
        }
    };

    let log = open_log();
    let mut child = match start(&java, &jar, &arguments) {
        Ok(child) => child,
        Err(why) => {
            complain(&format!("{} would not start:\n\n{}", java.display(), why));
            std::process::exit(70);
        }
    };

    listen(&mut child, log);
    let session = is_a_session(&arguments);
    if session {
        unsafe { boot_screen() };
    }

    let code = child.wait().map(|status| status.code().unwrap_or(0)).unwrap_or(1);
    let heard = HEARD.lock().unwrap();
    if session && !heard.ready {
        // It never said it was up, so whatever went wrong went wrong before there was a
        // screen to say it on. The last thing it did say is the only account of it there is.
        complain(&format!("FractalJDE stopped while starting.\n\n{}\n\nThe whole of it is in \
                           {}", heard.tail.join("\n"), log_file().display()));
    }
    std::process::exit(code);
}

/* -------------------------------------------------------------------- starting */

/// Whether what is being started is somebody logging in, or something being asked.
///
/// The system takes commands as well as coming up: draw a picture of the desktop, list
/// what is running, describe a program, run the checks. None of those puts a screen up and
/// none of them ever says it is ready, so a boot screen over the top of one would be a
/// grey rectangle covering the machine until it gave up waiting.
fn is_a_session(arguments: &[String]) -> bool {
    const COMMANDS: [&str; 9] = ["--screenshot", "--selftest", "--dump-accessibility",
                                 "--native-report", "--tasks", "--install", "--launchctl",
                                 "--program-info", "--controls"];
    !arguments.iter().any(|given| COMMANDS.contains(&given.as_str()))
}

fn start(java: &Path, jar: &Path, arguments: &[String]) -> std::io::Result<Child> {
    use std::os::windows::process::CommandExt;
    /// Started without a console of its own. This program has none to share, and without
    /// this Windows would give the runtime one: a black window behind the boot screen.
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;

    // Settings before the jar and everything else after it, which is where each belongs:
    // -D and -X are the runtime's and mean nothing to the program, and an argument in the
    // wrong half of that line is quietly ignored by whichever of them ends up with it.
    let (settings, passed): (Vec<&String>, Vec<&String>) = arguments.iter()
        .partition(|given| given.starts_with("-D") || given.starts_with("-X"));

    let mut command = Command::new(java);
    command
        .arg("--enable-preview")
        .arg("--enable-native-access=ALL-UNNAMED")
        .args(settings)
        .arg("-jar")
        .arg(jar)
        .args(passed)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .creation_flags(CREATE_NO_WINDOW);
    if let Some(directory) = jar.parent() {
        command.current_dir(directory);
    }
    command.spawn()
}

/// Reads everything the system says, on a thread for each of the two ways it says it.
fn listen(child: &mut Child, log: Option<std::fs::File>) {
    let log = Arc::new(Mutex::new(log));
    let mut streams: Vec<Box<dyn Read + Send>> = Vec::new();
    if let Some(out) = child.stdout.take() {
        streams.push(Box::new(out));
    }
    if let Some(errors) = child.stderr.take() {
        streams.push(Box::new(errors));
    }
    let open = streams.len();
    let left = Arc::new(Mutex::new(open));
    for stream in streams {
        let log = Arc::clone(&log);
        let left = Arc::clone(&left);
        std::thread::spawn(move || {
            pump(stream, &log);
            let mut left = left.lock().unwrap();
            *left -= 1;
            if *left == 0 {
                HEARD.lock().unwrap().over = true;
            }
        });
    }
    if open == 0 {
        HEARD.lock().unwrap().over = true;
    }
}

/// One stream, line by line, into the log and into what the screen is saying.
fn pump(mut stream: Box<dyn Read + Send>, log: &Arc<Mutex<Option<std::fs::File>>>) {
    let mut buffer = [0u8; 4096];
    let mut line: Vec<u8> = Vec::new();
    loop {
        let read = match stream.read(&mut buffer) {
            Ok(0) | Err(_) => break,
            Ok(read) => read,
        };
        for &byte in &buffer[..read] {
            if byte == b'\n' {
                finish(&String::from_utf8_lossy(&line), log);
                line.clear();
            } else if byte != b'\r' {
                line.push(byte);
            }
        }
    }
    if !line.is_empty() {
        finish(&String::from_utf8_lossy(&line), log);
    }
}

fn finish(line: &str, log: &Arc<Mutex<Option<std::fs::File>>>) {
    if let Ok(mut log) = log.lock() {
        if let Some(file) = log.as_mut() {
            let _ = writeln!(file, "{}", line);
        }
    }
    let mut heard = HEARD.lock().unwrap();
    heard.tail.push(line.to_string());
    if heard.tail.len() > TAIL {
        heard.tail.remove(0);
    }
    if let Some(said) = narration(line) {
        if said == "ready" {
            heard.ready = true;
        } else {
            heard.saying = said.to_string();
        }
    }
}

/// What the system says while it is coming up, picked out of everything else it prints.
///
/// The shape is fixed by org.fractalmicro.core.Progress, at the other end:
///
/// ```text
///    3.4  loginwindow: installing the look
/// ```
///
/// Seconds since the machine started, then who is talking, then what they are doing. A
/// stack trace, a warning from the runtime or a line of somebody's program output does not
/// look like that, so anything else is logged and not shown.
fn narration(line: &str) -> Option<&str> {
    let rest = line.trim_start();
    let digits = rest.find(|c: char| !c.is_ascii_digit())?;
    if digits == 0 || rest.as_bytes().get(digits) != Some(&b'.') {
        return None;
    }
    let rest = &rest[digits + 1..];
    let after = rest.find(|c: char| !c.is_ascii_digit())?;
    if after == 0 {
        return None;
    }
    let rest = rest[after..].trim_start();
    let colon = rest.find(": ")?;
    if colon == 0 || rest[..colon].contains(' ') {
        return None;
    }
    Some(rest[colon + 2..].trim())
}

/* --------------------------------------------------------------------- finding */

/// The jar this is a launcher for.
///
/// Beside this program, which is where a release puts both. Failing that, upwards: built
/// out of a checkout this program sits several directories down inside the build, and the
/// jar is in build/ at the top of it.
fn find_jar(here: &Path) -> Option<PathBuf> {
    let mut at = Some(here);
    for _ in 0..6 {
        let directory = at?;
        for candidate in [directory.join("FractalJDE.jar"),
                          directory.join("build").join("FractalJDE.jar")] {
            if candidate.is_file() {
                return Some(candidate);
            }
        }
        at = directory.parent();
    }
    None
}

/// A Java runtime to start it with.
///
/// In the order somebody would want them tried: what they said to use, what shipped with
/// this copy, what the machine is set up for, what is on the path, and finally what an
/// installer left behind in the registry, which is the case where Java is on the machine
/// and nothing on the path knows it.
///
/// javaw before java, both here and inside a runtime, because java.exe is a console
/// program and the console it would want is one this launcher does not have.
fn find_java(here: &Path) -> Option<PathBuf> {
    if let Ok(said) = std::env::var("FRACTAL_JAVA") {
        let named = PathBuf::from(said);
        if named.is_file() {
            return Some(named);
        }
    }
    if let Some(found) = in_runtime(&here.join("runtime")) {
        return Some(found);
    }
    if let Ok(home) = std::env::var("JAVA_HOME") {
        if let Some(found) = in_runtime(Path::new(&home)) {
            return Some(found);
        }
    }
    if let Some(found) = on_path("javaw.exe").or_else(|| on_path("java.exe")) {
        return Some(found);
    }
    for family in ["SOFTWARE\\JavaSoft\\JDK", "SOFTWARE\\JavaSoft\\JRE",
                   "SOFTWARE\\JavaSoft\\Java Runtime Environment"] {
        let current = win::registry_string(HKEY_LOCAL_MACHINE, family, "CurrentVersion");
        if let Some(version) = current {
            let key = format!("{}\\{}", family, version);
            if let Some(home) = win::registry_string(HKEY_LOCAL_MACHINE, &key, "JavaHome") {
                if let Some(found) = in_runtime(Path::new(&home)) {
                    return Some(found);
                }
            }
        }
    }
    None
}

fn in_runtime(home: &Path) -> Option<PathBuf> {
    for name in ["javaw.exe", "java.exe"] {
        let candidate = home.join("bin").join(name);
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    None
}

fn on_path(name: &str) -> Option<PathBuf> {
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path)
        .map(|directory| directory.join(name))
        .find(|candidate| candidate.is_file())
}

/* ----------------------------------------------------------------------- the log */

/// Where a boot log goes, which is where a boot log has always gone.
fn log_file() -> PathBuf {
    let volume = std::env::var("USERPROFILE")
        .map(|home| PathBuf::from(home).join(".fractaldt"))
        .unwrap_or_else(|_| std::env::temp_dir());
    volume.join("private").join("var").join("log").join("boot.log")
}

/// The log for this start, which replaces the last one. It is what happened this time.
fn open_log() -> Option<std::fs::File> {
    let file = log_file();
    if let Some(directory) = file.parent() {
        let _ = std::fs::create_dir_all(directory);
    }
    std::fs::File::create(&file)
        .or_else(|_| std::fs::File::create(std::env::temp_dir().join("Fractal-boot.log")))
        .ok()
}

/* ------------------------------------------------------------------ the screen */

/// What draws, kept where the window procedure can reach it.
///
/// On this thread and nowhere else. The window was made here and every message about it
/// arrives here, so there is exactly one thread that ever touches any of this, and it does
/// not need to be defended from the others.
struct Painter {
    surface: Surface,
    scene: Scene,
    font: HGDIOBJ,
    phase: u32,
    since: Instant,
}

thread_local! {
    static PAINTER: RefCell<Option<Painter>> = const { RefCell::new(None) };
}

unsafe fn boot_screen() {
    // The class and the window are registered to the same module, and both are told which
    // one. Left out, a window class belongs to nothing in particular and finding it again
    // is left to a rule about what null means that is easier to get right than to rely on.
    let module = GetModuleHandleW(std::ptr::null());
    let name = wide("FractalBootScreen");
    let class = WNDCLASSW {
        style: 0,
        lpfnWndProc: Some(handle),
        cbClsExtra: 0,
        cbWndExtra: 0,
        hInstance: module,
        hIcon: std::ptr::null_mut(),
        hCursor: LoadCursorW(std::ptr::null_mut(), IDC_ARROW),
        hbrBackground: std::ptr::null_mut(),
        lpszMenuName: std::ptr::null(),
        lpszClassName: name.as_ptr(),
    };
    if RegisterClassW(&class) == 0 {
        return;
    }

    let width = GetSystemMetrics(SM_CXSCREEN);
    let height = GetSystemMetrics(SM_CYSCREEN);
    let title = wide("FractalJDE");
    let window = CreateWindowExW(WS_EX_TOPMOST, name.as_ptr(), title.as_ptr(), WS_POPUP,
                                 0, 0, width, height, std::ptr::null_mut(),
                                 std::ptr::null_mut(), module, std::ptr::null_mut());
    if window.is_null() {
        return;
    }

    let mut surface = match Surface::new(width, height) {
        Some(surface) => surface,
        None => return,
    };
    let dpi = match GetDpiForWindow(window) {
        0 => 96,
        found => found,
    };
    let scene = Scene::laid_out(width, height, dpi);
    let font = scene.font();
    scene.paint_still(&mut surface);
    scene.paint_moving(&mut surface, 0, "", font);
    PAINTER.with(|painter| {
        *painter.borrow_mut() = Some(Painter {
            surface, scene, font, phase: 0, since: Instant::now(),
        });
    });

    ShowWindow(window, SW_SHOW);
    SetTimer(window, 1, TICK_MILLISECONDS, std::ptr::null());

    let mut message = MSG::default();
    while GetMessageW(&mut message, std::ptr::null_mut(), 0, 0) > 0 {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }

    PAINTER.with(|painter| {
        if let Some(painter) = painter.borrow_mut().take() {
            DeleteObject(painter.font);
        }
    });
}

unsafe extern "system" fn handle(window: HWND, message: u32, w: WPARAM, l: LPARAM) -> LRESULT {
    match message {
        WM_TIMER => {
            let (saying, done) = {
                let heard = HEARD.lock().unwrap();
                (heard.saying.clone(), heard.ready || heard.over)
            };
            let mut over = done;
            PAINTER.with(|painter| {
                if let Some(Painter { surface, scene, font, phase, since }) =
                        painter.borrow_mut().as_mut() {
                    if since.elapsed().as_secs() >= GIVE_UP_AFTER_SECONDS {
                        over = true;
                    }
                    if !over {
                        *phase = phase.wrapping_add(1);
                        scene.paint_moving(surface, *phase, &saying, *font);
                        InvalidateRect(window, &scene.busy, 0);
                    }
                }
            });
            if over {
                DestroyWindow(window);
            }
            0
        }
        WM_PAINT => {
            let mut paint = PAINTSTRUCT::default();
            let dc = BeginPaint(window, &mut paint);
            PAINTER.with(|painter| {
                if let Some(painter) = painter.borrow().as_ref() {
                    BitBlt(dc, 0, 0, painter.surface.width, painter.surface.height,
                           painter.surface.dc, 0, 0, SRCCOPY);
                }
            });
            EndPaint(window, &paint);
            0
        }
        // A boot screen covering everything, that something has gone wrong behind, is a
        // machine somebody cannot use. Escape always takes it down; what it was waiting
        // for carries on either way.
        WM_KEYDOWN if w == VK_ESCAPE => {
            DestroyWindow(window);
            0
        }
        WM_CLOSE => {
            DestroyWindow(window);
            0
        }
        WM_DESTROY => {
            PostQuitMessage(0);
            0
        }
        _ => DefWindowProcW(window, message, w, l),
    }
}

/* ------------------------------------------------------------------- as a file */

/// Draws one frame of the boot screen into a file instead of onto the screen.
///
/// This is a full screen window over everything else, so opening it to see whether it
/// looks right takes over the screen of whoever is looking. Drawn this way it is the same
/// picture, made by the same code, and nothing appears.
fn draw_to_file(into: &str, width: i32, height: i32) -> i32 {
    if into.is_empty() {
        complain("--draw wants a file to draw into.");
        return 2;
    }
    unsafe {
        let mut surface = match Surface::new(width, height) {
            Some(surface) => surface,
            None => {
                complain("there is nowhere to draw.");
                return 1;
            }
        };
        let scene = Scene::laid_out(width, height, 96);
        let font = scene.font();
        scene.paint_still(&mut surface);
        scene.paint_moving(&mut surface, 3, "building the desktop", font);
        DeleteObject(font);
        let bytes = screen::to_bitmap(&mut surface);
        match std::fs::write(into, bytes) {
            Ok(()) => 0,
            Err(why) => {
                complain(&format!("{} could not be written: {}", into, why));
                1
            }
        }
    }
}

/// Writes down what this launcher found, without starting anything.
///
/// To a file rather than to a terminal, because a program with a boot screen in it must
/// not have a console attached to it, and a program with no console has nowhere to print.
/// It is the answer to the only question a launcher is ever asked, which is what it is
/// looking at.
fn report(here: &Path, into: &str) -> i32 {
    if into.is_empty() {
        complain("--where wants a file to write to.");
        return 2;
    }
    let said = format!(
        "launcher   {}\njar        {}\njava       {}\nboot log   {}\n",
        std::env::current_exe().map(|at| at.display().to_string())
            .unwrap_or_else(|_| "?".into()),
        find_jar(here).map(|at| at.display().to_string())
            .unwrap_or_else(|| "not found".into()),
        find_java(here).map(|at| at.display().to_string())
            .unwrap_or_else(|| "not found".into()),
        log_file().display());
    match std::fs::write(into, said) {
        Ok(()) => 0,
        Err(why) => {
            complain(&format!("{} could not be written: {}", into, why));
            1
        }
    }
}

/// A size written the way a screen is talked about.
fn measure(said: &str) -> Option<(i32, i32)> {
    let (across, down) = said.split_once(['x', 'X'])?;
    Some((across.trim().parse().ok()?, down.trim().parse().ok()?))
}

/* ----------------------------------------------------------------- going wrong */

/// The one thing this program says in its own words, and only when it cannot go on.
fn complain(what: &str) {
    let text = wide(what);
    let title = wide("FractalJDE");
    unsafe {
        MessageBoxW(std::ptr::null_mut(), text.as_ptr(), title.as_ptr(),
                    MB_OK | MB_ICONERROR | MB_TOPMOST);
    }
}

/* --------------------------------------------------------------------- checking */

#[cfg(test)]
mod checks {
    use super::{measure, narration};

    /// The lines org.fractalmicro.core.Progress really writes, copied from a boot.
    ///
    /// This is the whole of the agreement between the two programs, and it is written in
    /// two languages, so it is the one thing here worth checking on its own. If the shape
    /// at the other end changes, a boot screen goes blank rather than failing, which is
    /// the kind of breakage nobody notices for a month.
    #[test]
    fn reads_what_the_system_says() {
        assert_eq!(narration("   0.2  kernel: reading the loader"),
                   Some("reading the loader"));
        assert_eq!(narration("   0.4  launchd: starting, as task 1"),
                   Some("starting, as task 1"));
        assert_eq!(narration("  11.2  loginwindow: ready"), Some("ready"));
        assert_eq!(narration(" 103.5  loginwindow: failed: no screen"),
                   Some("failed: no screen"));
    }

    /// And everything else a runtime prints, which must not be mistaken for it.
    #[test]
    fn ignores_everything_else() {
        assert_eq!(narration(""), None);
        assert_eq!(narration("Exception in thread \"main\" java.lang.Error: no"), None);
        assert_eq!(narration("\tat org.fractalmicro.Main.main(Main.java:1)"), None);
        assert_eq!(narration("WARNING: preview features are enabled"), None);
        assert_eq!(narration("wrote C:\\Users\\someone\\picture.png"), None);
        // Close, but the speaker is a sentence rather than a name.
        assert_eq!(narration("  1.0  two words: no"), None);
        // A number with nothing after it is not a time and a stage.
        assert_eq!(narration("  1.0"), None);
    }

    #[test]
    fn reads_a_size() {
        assert_eq!(measure("1440x900"), Some((1440, 900)));
        assert_eq!(measure("1920X1080"), Some((1920, 1080)));
        assert_eq!(measure("wide"), None);
    }
}
