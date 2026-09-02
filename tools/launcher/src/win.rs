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

//! What this launcher asks of Windows, declared rather than wrapped.
//!
//! No crate is used for any of it. A launcher exists to be the one thing on the machine
//! that is certainly there and certainly works, and every dependency is a way for that to
//! stop being true. The system's own Windows layer declares its calls the same way, so
//! this is the arrangement the rest of the program already uses, in another language.
//!
//! Only the drawing is here. Starting a program, reading what it prints and waiting for it
//! to finish are all in the standard library, which does them correctly on this platform
//! and is not a dependency.

#![allow(non_snake_case, non_camel_case_types)]

use std::ffi::c_void;

pub type HANDLE = *mut c_void;
pub type HWND = *mut c_void;
pub type HDC = *mut c_void;
pub type HGDIOBJ = *mut c_void;
pub type LRESULT = isize;
pub type WPARAM = usize;
pub type LPARAM = isize;

/* ------------------------------------------------------------------- windows */

#[repr(C)]
pub struct WNDCLASSW {
    pub style: u32,
    pub lpfnWndProc: Option<unsafe extern "system" fn(HWND, u32, WPARAM, LPARAM) -> LRESULT>,
    pub cbClsExtra: i32,
    pub cbWndExtra: i32,
    pub hInstance: HANDLE,
    pub hIcon: HANDLE,
    pub hCursor: HANDLE,
    pub hbrBackground: HANDLE,
    pub lpszMenuName: *const u16,
    pub lpszClassName: *const u16,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct POINT {
    pub x: i32,
    pub y: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct RECT {
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct MSG {
    pub hwnd: HWND,
    pub message: u32,
    pub wParam: WPARAM,
    pub lParam: LPARAM,
    pub time: u32,
    pub pt: POINT,
}

#[repr(C)]
pub struct PAINTSTRUCT {
    pub hdc: HDC,
    pub fErase: i32,
    pub rcPaint: RECT,
    pub fRestore: i32,
    pub fIncUpdate: i32,
    pub rgbReserved: [u8; 32],
}

impl Default for PAINTSTRUCT {
    fn default() -> PAINTSTRUCT {
        PAINTSTRUCT {
            hdc: std::ptr::null_mut(),
            fErase: 0,
            rcPaint: RECT::default(),
            fRestore: 0,
            fIncUpdate: 0,
            rgbReserved: [0; 32],
        }
    }
}

pub const WS_POPUP: u32 = 0x8000_0000;
pub const WS_EX_TOPMOST: u32 = 0x0000_0008;
pub const SW_SHOW: i32 = 5;
pub const WM_DESTROY: u32 = 0x0002;
pub const WM_PAINT: u32 = 0x000F;
pub const WM_CLOSE: u32 = 0x0010;
pub const WM_TIMER: u32 = 0x0113;
pub const WM_KEYDOWN: u32 = 0x0100;
pub const VK_ESCAPE: usize = 0x1B;
pub const SM_CXSCREEN: i32 = 0;
pub const SM_CYSCREEN: i32 = 1;
pub const IDC_ARROW: *const u16 = 32512 as *const u16;
pub const MB_ICONERROR: u32 = 0x0000_0010;
pub const MB_OK: u32 = 0;
pub const MB_TOPMOST: u32 = 0x0004_0000;

/** Per monitor, version 2: what a program says when it means to draw in real pixels. */
pub const DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: isize = -4;

/* --------------------------------------------------------------------- drawing */

#[repr(C)]
#[derive(Default)]
pub struct BITMAPINFOHEADER {
    pub biSize: u32,
    pub biWidth: i32,
    pub biHeight: i32,
    pub biPlanes: u16,
    pub biBitCount: u16,
    pub biCompression: u32,
    pub biSizeImage: u32,
    pub biXPelsPerMeter: i32,
    pub biYPelsPerMeter: i32,
    pub biClrUsed: u32,
    pub biClrImportant: u32,
}

pub const BI_RGB: u32 = 0;
pub const DIB_RGB_COLORS: u32 = 0;
pub const SRCCOPY: u32 = 0x00CC_0020;
pub const TRANSPARENT: i32 = 1;
pub const DT_CENTER: u32 = 0x0000_0001;
pub const DT_VCENTER: u32 = 0x0000_0004;
pub const DT_SINGLELINE: u32 = 0x0000_0020;
pub const DT_END_ELLIPSIS: u32 = 0x0000_8000;
pub const FW_NORMAL: i32 = 400;
pub const DEFAULT_CHARSET: u32 = 1;
pub const OUT_DEFAULT_PRECIS: u32 = 0;
pub const CLIP_DEFAULT_PRECIS: u32 = 0;
pub const ANTIALIASED_QUALITY: u32 = 4;
pub const DEFAULT_PITCH: u32 = 0;

/* ------------------------------------------------------------------- registry */

pub const HKEY_LOCAL_MACHINE: HANDLE = 0x8000_0002u32 as usize as HANDLE;
pub const RRF_RT_REG_SZ: u32 = 0x0000_0002;
pub const ERROR_SUCCESS: i32 = 0;

/* ----------------------------------------------------------------- the calls */

#[link(name = "user32")]
extern "system" {
    pub fn RegisterClassW(class: *const WNDCLASSW) -> u16;
    pub fn CreateWindowExW(exStyle: u32, class: *const u16, title: *const u16, style: u32,
                           x: i32, y: i32, width: i32, height: i32, parent: HWND,
                           menu: HANDLE, instance: HANDLE, parameter: *mut c_void) -> HWND;
    pub fn DefWindowProcW(window: HWND, message: u32, w: WPARAM, l: LPARAM) -> LRESULT;
    pub fn ShowWindow(window: HWND, command: i32) -> i32;
    pub fn DestroyWindow(window: HWND) -> i32;
    pub fn GetMessageW(message: *mut MSG, window: HWND, first: u32, last: u32) -> i32;
    pub fn TranslateMessage(message: *const MSG) -> i32;
    pub fn DispatchMessageW(message: *const MSG) -> LRESULT;
    pub fn PostQuitMessage(code: i32);
    pub fn BeginPaint(window: HWND, paint: *mut PAINTSTRUCT) -> HDC;
    pub fn EndPaint(window: HWND, paint: *const PAINTSTRUCT) -> i32;
    pub fn InvalidateRect(window: HWND, rectangle: *const RECT, erase: i32) -> i32;
    pub fn SetTimer(window: HWND, id: usize, milliseconds: u32, called: *const c_void) -> usize;
    pub fn GetSystemMetrics(index: i32) -> i32;
    pub fn LoadCursorW(instance: HANDLE, name: *const u16) -> HANDLE;
    pub fn MessageBoxW(owner: HWND, text: *const u16, caption: *const u16, kind: u32) -> i32;
    pub fn DrawTextW(dc: HDC, text: *const u16, count: i32, rectangle: *mut RECT,
                     format: u32) -> i32;
    pub fn GetDpiForWindow(window: HWND) -> u32;
    pub fn SetProcessDpiAwarenessContext(context: isize) -> i32;
}

#[link(name = "gdi32")]
extern "system" {
    pub fn CreateCompatibleDC(dc: HDC) -> HDC;
    pub fn CreateDIBSection(dc: HDC, information: *const BITMAPINFOHEADER, use_: u32,
                            bits: *mut *mut c_void, section: HANDLE, offset: u32) -> HGDIOBJ;
    pub fn SelectObject(dc: HDC, object: HGDIOBJ) -> HGDIOBJ;
    pub fn DeleteObject(object: HGDIOBJ) -> i32;
    pub fn DeleteDC(dc: HDC) -> i32;
    pub fn BitBlt(destination: HDC, x: i32, y: i32, width: i32, height: i32,
                  source: HDC, sourceX: i32, sourceY: i32, operation: u32) -> i32;
    pub fn CreateFontW(height: i32, width: i32, escapement: i32, orientation: i32,
                       weight: i32, italic: u32, underline: u32, strikeOut: u32,
                       charSet: u32, outputPrecision: u32, clipPrecision: u32,
                       quality: u32, pitchAndFamily: u32, face: *const u16) -> HGDIOBJ;
    pub fn SetTextColor(dc: HDC, colour: u32) -> u32;
    pub fn SetBkMode(dc: HDC, mode: i32) -> i32;
}

#[link(name = "advapi32")]
extern "system" {
    pub fn RegGetValueW(key: HANDLE, subKey: *const u16, value: *const u16, flags: u32,
                        kind: *mut u32, data: *mut c_void, size: *mut u32) -> i32;
}

/* ------------------------------------------------------------------ small help */

/// A Rust string as Windows wants one: sixteen bit units with a nothing on the end.
pub fn wide(text: &str) -> Vec<u16> {
    text.encode_utf16().chain(std::iter::once(0)).collect()
}

/// And back again, up to the first nothing.
pub fn from_wide(buffer: &[u16]) -> String {
    let end = buffer.iter().position(|&c| c == 0).unwrap_or(buffer.len());
    String::from_utf16_lossy(&buffer[..end])
}

/// A string from under a registry key, or nothing when the key is not there.
pub fn registry_string(key: HANDLE, path: &str, name: &str) -> Option<String> {
    let path = wide(path);
    let name = wide(name);
    let mut buffer = [0u16; 512];
    let mut size = (buffer.len() * 2) as u32;
    let answered = unsafe {
        RegGetValueW(key, path.as_ptr(), name.as_ptr(), RRF_RT_REG_SZ,
                     std::ptr::null_mut(), buffer.as_mut_ptr() as *mut c_void, &mut size)
    };
    if answered != ERROR_SUCCESS {
        return None;
    }
    let found = from_wide(&buffer);
    if found.is_empty() { None } else { Some(found) }
}
