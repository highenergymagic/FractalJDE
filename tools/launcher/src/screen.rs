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

//! The boot screen: grey, the company mark, a turning indicator, and one line saying
//! where the system has got to.
//!
//! It is drawn into a surface rather than onto a window, and the window is one thing that
//! can be given the surface. The other is a file, which is how it gets looked at: this is
//! a full screen window over everything, and checking it by opening it is checking it by
//! taking over the screen of whoever is checking. `Fractal.exe --draw picture.bmp` draws
//! exactly what would have been shown and writes it out instead.

use crate::win::*;
use std::ffi::c_void;

/* -------------------------------------------------------------------- the look */

/// Behind everything. A light grey, which is what a machine of this era comes up in.
const BACKGROUND: u32 = 0x00C1C1C1;
/// The mark, nearly black rather than black, so it sits on the grey instead of cutting it.
const MARK: u32 = 0x00262626;
/// The turning indicator at its darkest. Every spoke behind the leading one fades to grey.
const SPOKE: u32 = 0x00606060;
/// What the system is saying, in the grey a caption is set in.
const CAPTION: u32 = 0x004F4F4F;

/// The face the desktop itself settles on when there is no Lucida Grande, which on
/// Windows there never is. It ships with the system, so this cannot fall back to
/// something arbitrary chosen for us.
const FACE: &str = "Lucida Sans Unicode";

/// The mark, as coverage: how much of each pixel it covers, in runs. Made by tools/Logo.java.
const MASK: &[u8] = include_bytes!("mark.mask");

/// How many spokes the indicator has, and how long a full turn takes.
const SPOKES: u32 = 12;
pub const TICK_MILLISECONDS: u32 = 1000 / SPOKES as u32;

/* ------------------------------------------------------------------ the surface */

/// Somewhere to draw: pixels this program writes, and a device context Windows can use
/// on the same pixels, which is how text gets drawn without a font renderer in here.
pub struct Surface {
    pub dc: HDC,
    bitmap: HGDIOBJ,
    previous: HGDIOBJ,
    pixels: *mut u32,
    pub width: i32,
    pub height: i32,
}

impl Surface {
    pub unsafe fn new(width: i32, height: i32) -> Option<Surface> {
        let dc = CreateCompatibleDC(std::ptr::null_mut());
        if dc.is_null() {
            return None;
        }
        let header = BITMAPINFOHEADER {
            biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
            biWidth: width,
            // Upside down, which is the right way up: a negative height is how a bitmap
            // says its first row is the top one, and every other way of holding a picture
            // agrees with that.
            biHeight: -height,
            biPlanes: 1,
            biBitCount: 32,
            biCompression: BI_RGB,
            ..Default::default()
        };
        let mut bits: *mut c_void = std::ptr::null_mut();
        let bitmap = CreateDIBSection(dc, &header, DIB_RGB_COLORS, &mut bits,
                                      std::ptr::null_mut(), 0);
        if bitmap.is_null() || bits.is_null() {
            DeleteDC(dc);
            return None;
        }
        let previous = SelectObject(dc, bitmap);
        Some(Surface { dc, bitmap, previous, pixels: bits as *mut u32, width, height })
    }

    pub fn pixels(&mut self) -> &mut [u32] {
        unsafe {
            std::slice::from_raw_parts_mut(self.pixels, (self.width * self.height) as usize)
        }
    }
}

impl Drop for Surface {
    fn drop(&mut self) {
        unsafe {
            SelectObject(self.dc, self.previous);
            DeleteObject(self.bitmap);
            DeleteDC(self.dc);
        }
    }
}

/* -------------------------------------------------------------------- the scene */

/// Where everything sits, worked out once for a given size and resolution.
///
/// Every measurement is in points and multiplied up, so the screen is the same screen on
/// a doubled display rather than the same number of pixels a quarter of the size.
pub struct Scene {
    pub height: i32,
    scale: f32,
    mark: RECT,
    spinner: POINT,
    caption: RECT,
    /// The part that changes as it turns, and the only part redrawn.
    pub busy: RECT,
}

impl Scene {
    pub fn laid_out(width: i32, height: i32, dpi: u32) -> Scene {
        let scale = dpi as f32 / 96.0;
        let at = |points: f32| (points * scale).round() as i32;

        let (mark_width, mark_height) = mark_size();
        let tall = at(150.0);
        let wide = (tall as f32 * mark_width as f32 / mark_height as f32).round() as i32;
        let middle = width / 2;
        let mark_middle = (height as f32 * 0.40).round() as i32;
        let mark = RECT {
            left: middle - wide / 2,
            top: mark_middle - tall / 2,
            right: middle - wide / 2 + wide,
            bottom: mark_middle - tall / 2 + tall,
        };

        let spinner = POINT { x: middle, y: mark.bottom + at(72.0) };
        let caption = RECT {
            left: middle - at(320.0),
            top: spinner.y + at(40.0),
            right: middle + at(320.0),
            bottom: spinner.y + at(40.0) + at(22.0),
        };
        let busy = RECT {
            left: 0,
            top: spinner.y - at(26.0),
            right: width,
            bottom: caption.bottom + at(4.0),
        };
        Scene { height, scale, mark, spinner, caption, busy }
    }

    /// The part that is drawn once: the grey, and the mark on it.
    pub fn paint_still(&self, surface: &mut Surface) {
        let width = surface.width;
        let pixels = surface.pixels();
        pixels.fill(BACKGROUND);
        paint_mark(pixels, width, &self.mark);
    }

    /// The part that is drawn again every tick: the indicator, and what it is saying.
    pub unsafe fn paint_moving(&self, surface: &mut Surface, phase: u32, saying: &str,
                               font: HGDIOBJ) {
        let width = surface.width;
        let busy = self.busy;
        let spinner = self.spinner;
        let scale = self.scale;
        let pixels = surface.pixels();
        for y in busy.top.max(0)..busy.bottom.min(self.height) {
            let row = (y * width) as usize;
            pixels[row + busy.left.max(0) as usize..row + busy.right.min(width) as usize]
                .fill(BACKGROUND);
        }
        paint_spinner(pixels, width, self.height, spinner, scale, phase);

        let previous = SelectObject(surface.dc, font);
        SetBkMode(surface.dc, TRANSPARENT);
        SetTextColor(surface.dc, CAPTION);
        let text = wide(saying);
        let mut where_ = self.caption;
        DrawTextW(surface.dc, text.as_ptr(), -1, &mut where_,
                  DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        SelectObject(surface.dc, previous);
    }

    pub unsafe fn font(&self) -> HGDIOBJ {
        let face = wide(FACE);
        // Smoothed in grey rather than in colour. Subpixel smoothing is right on a screen
        // whose subpixels are where Windows thinks they are, and this picture also gets
        // drawn into a file and looked at, where the coloured fringes are all anyone sees.
        CreateFontW(-((13.0 * self.scale).round() as i32), 0, 0, 0, FW_NORMAL, 0, 0, 0,
                    DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                    ANTIALIASED_QUALITY, DEFAULT_PITCH, face.as_ptr())
    }
}

/* --------------------------------------------------------------------- the mark */

/// The size the mark was stored at, from the front of the coverage.
fn mark_size() -> (i32, i32) {
    let read = |at: usize| {
        MASK[at] as i32 | (MASK[at + 1] as i32) << 8
            | (MASK[at + 2] as i32) << 16 | (MASK[at + 3] as i32) << 24
    };
    (read(0), read(4))
}

/// The coverage, run by run, laid back out as one value per pixel.
fn mark_coverage() -> Vec<u8> {
    let (width, height) = mark_size();
    let mut out = Vec::with_capacity((width * height) as usize);
    let mut at = 8;
    while at + 1 < MASK.len() {
        let run = MASK[at] as usize;
        let value = MASK[at + 1];
        at += 2;
        for _ in 0..run {
            out.push(value);
        }
    }
    out.resize((width * height) as usize, 0);
    out
}

/// Draws the mark into a rectangle, sampling the stored coverage.
///
/// Between the four nearest stored values rather than the nearest one, because the mark is
/// nearly always being made smaller and taking one pixel in three would eat the thin parts
/// of the letters and leave the rest looking chewed.
fn paint_mark(pixels: &mut [u32], width: i32, into: &RECT) {
    let (mark_width, mark_height) = mark_size();
    let coverage = mark_coverage();
    let across = into.right - into.left;
    let down = into.bottom - into.top;
    if across <= 0 || down <= 0 {
        return;
    }
    for y in 0..down {
        let from_y = (y as f32 + 0.5) * mark_height as f32 / down as f32 - 0.5;
        for x in 0..across {
            let from_x = (x as f32 + 0.5) * mark_width as f32 / across as f32 - 0.5;
            let covered = sample(&coverage, mark_width, mark_height, from_x, from_y);
            if covered == 0.0 {
                continue;
            }
            let at = ((into.top + y) * width + into.left + x) as usize;
            if at < pixels.len() {
                pixels[at] = mix(BACKGROUND, MARK, covered);
            }
        }
    }
}

fn sample(coverage: &[u8], width: i32, height: i32, x: f32, y: f32) -> f32 {
    let x0 = x.floor() as i32;
    let y0 = y.floor() as i32;
    let fx = x - x0 as f32;
    let fy = y - y0 as f32;
    let mut total = 0.0;
    for (dy, wy) in [(0, 1.0 - fy), (1, fy)] {
        for (dx, wx) in [(0, 1.0 - fx), (1, fx)] {
            let sx = (x0 + dx).clamp(0, width - 1);
            let sy = (y0 + dy).clamp(0, height - 1);
            total += coverage[(sy * width + sx) as usize] as f32 / 255.0 * wx * wy;
        }
    }
    total
}

/* ----------------------------------------------------------------- the indicator */

/// Twelve spokes around a middle, the leading one darkest and the rest fading behind it.
///
/// Drawn here rather than by Windows because Windows draws a line with hard edges, and a
/// spoke a few pixels wide at an angle with hard edges is a staircase. Each pixel is
/// sampled at four points against the spoke's shape, which is enough to look drawn.
fn paint_spinner(pixels: &mut [u32], width: i32, height: i32, middle: POINT, scale: f32,
                 phase: u32) {
    let inner = 8.0 * scale;
    let outer = 18.0 * scale;
    let half = 1.8 * scale;
    let reach = (outer + half).ceil() as i32 + 1;

    for y in (middle.y - reach).max(0)..(middle.y + reach).min(height) {
        for x in (middle.x - reach).max(0)..(middle.x + reach).min(width) {
            let mut darkest: f32 = 0.0;
            for spoke in 0..SPOKES {
                // How far behind the leading spoke this one is, and so how faded. Behind
                // it and not in front: the head turns one spoke each tick and what trails
                // it is where it has been.
                let behind = (phase % SPOKES + SPOKES - spoke) % SPOKES;
                let strength = 1.0 - behind as f32 / SPOKES as f32;
                if strength <= 0.0 {
                    continue;
                }
                let angle = -std::f32::consts::FRAC_PI_2
                    + spoke as f32 * std::f32::consts::TAU / SPOKES as f32;
                let covered = spoke_coverage(x as f32 - middle.x as f32,
                                             y as f32 - middle.y as f32,
                                             angle, inner, outer, half);
                darkest = darkest.max(covered * strength);
            }
            if darkest > 0.0 {
                let at = (y * width + x) as usize;
                if at < pixels.len() {
                    pixels[at] = mix(BACKGROUND, SPOKE, darkest.min(1.0));
                }
            }
        }
    }
}

/// How much of a pixel one spoke covers, by trying four points inside it.
fn spoke_coverage(x: f32, y: f32, angle: f32, inner: f32, outer: f32, half: f32) -> f32 {
    let (sin, cos) = angle.sin_cos();
    let mut inside = 0;
    for (dx, dy) in [(-0.25, -0.25), (0.25, -0.25), (-0.25, 0.25), (0.25, 0.25)] {
        // Turned so the spoke lies along one axis, where being inside it is two comparisons.
        let along = (x + dx) * cos + (y + dy) * sin;
        let across = -(x + dx) * sin + (y + dy) * cos;
        if across.abs() > half {
            continue;
        }
        // Rounded ends: past the end, how far past counts against the half width too.
        let past = if along < inner { inner - along } else if along > outer { along - outer }
                   else { 0.0 };
        if past * past + across * across <= half * half {
            inside += 1;
        }
    }
    inside as f32 / 4.0
}

/* ---------------------------------------------------------------------- mixing */

/// One colour over another, by how much of it there is.
fn mix(under: u32, over: u32, amount: f32) -> u32 {
    let amount = amount.clamp(0.0, 1.0);
    let blend = |shift: u32| {
        let a = ((under >> shift) & 0xFF) as f32;
        let b = ((over >> shift) & 0xFF) as f32;
        ((a + (b - a) * amount).round() as u32).min(255) << shift
    };
    blend(0) | blend(8) | blend(16)
}

/* ------------------------------------------------------------------- as a file */

/// The surface as a bitmap file, for looking at what would have been shown.
pub fn to_bitmap(surface: &mut Surface) -> Vec<u8> {
    let width = surface.width;
    let height = surface.height;
    let stride = ((width * 3 + 3) / 4) * 4;
    let pixels = (stride * height) as usize;
    let mut out = Vec::with_capacity(54 + pixels);
    out.extend_from_slice(b"BM");
    out.extend_from_slice(&((54 + pixels) as u32).to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());
    out.extend_from_slice(&54u32.to_le_bytes());
    out.extend_from_slice(&40u32.to_le_bytes());
    out.extend_from_slice(&width.to_le_bytes());
    out.extend_from_slice(&height.to_le_bytes());
    out.extend_from_slice(&1u16.to_le_bytes());
    out.extend_from_slice(&24u16.to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());
    out.extend_from_slice(&(pixels as u32).to_le_bytes());
    out.extend_from_slice(&2835i32.to_le_bytes());
    out.extend_from_slice(&2835i32.to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());

    let source = surface.pixels();
    // Bitmaps are written from the bottom up, whatever the picture in memory does.
    for y in (0..height).rev() {
        let row = (y * width) as usize;
        for x in 0..width as usize {
            let colour = source[row + x];
            out.push((colour & 0xFF) as u8);
            out.push(((colour >> 8) & 0xFF) as u8);
            out.push(((colour >> 16) & 0xFF) as u8);
        }
        for _ in 0..(stride - width * 3) {
            out.push(0);
        }
    }
    out
}
