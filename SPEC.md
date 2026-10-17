# B1248 Name Badge Programmer — Project Specification

## 1. Purpose

A cross-platform desktop application for creating and writing messages (text and
images) to the **B1248 LED name badge**. The goal is an easy-to-use GUI that works
identically on **Windows** and **Linux** (and ideally macOS), without requiring users
to install drivers or system libraries.

This document is the authoritative reference for what the application does, how it
interacts with the device, and how it is built and distributed.

## 2. Target Device

| Property | Value |
|---|---|
| Model | B1248 (also sold as "LED Mini Board" / "LED name badge") |
| USB VID:PID | `0483:5750` |
| USB product string | `STMicroelectronics LED badge -- mini LED display -- 11x44` |
| HID name | `HELLO USB! ... MM32 Custm HID` |
| Interface | USB HID (no serial port, no vendor driver) |
| Controller | MindMotion MM32 |
| Display | 11 rows × 44 columns, monochrome (1 bit per pixel) |
| HID report size | 64 bytes (input and output); no report ID |

**Important:** there are many look-alike badges with incompatible protocols. This
project targets *only* the `0483:5750` B1248 HID badge. The older serial (Prolific
`067b:2303`) badges and the LS32 (`0416:5020`) HID badges use different protocols and
are explicitly out of scope.

## 3. Device Protocol

The protocol below has been **validated** against a real device (see §9 for the one
remaining open quirk). All writes use the HID *output* report (64 bytes). When writing
through hidapi, a leading report-ID byte `0x00` is prepended and stripped by hidapi, so
each write is `0x00` + 64 payload bytes.

### 3.1 Write sequence

A complete write consists of the following reports, in order:

1. **Handshake** — 64 bytes: `48 65 6c 6c 6f` (`"Hello"`) followed by 59× `0x00`.
2. **Header** — 64 bytes describing up to 8 message slots (see §3.2).
3. **Data** — 11 reports, one per display row, top → bottom (see §3.4).
4. **Dummy** — 1 report of all `0x00`.

The badge accepts and displays the message immediately while USB-connected, and the
message **persists across a power cycle**.

### 3.2 Header layout (64 bytes)

| Byte(s) | Meaning |
|---|---|
| 0 | `0x00` (leading/reserved) |
| 1–8 | One **effect byte** per message slot (8 slots) |
| 9 | `0x00` (reserved) |
| 10–41 | Eight 4-byte **offset/length** entries, one per slot |
| 42–63 | `0x00` (padding) |

**Effect byte** (per slot):

```
bit 7    frame        (0 = no border, 1 = border)
bits 6-4 speed - 1    (scrolling speed, 0..7 → user speed 1..8)
bit 3    blink        (0 = normal, 1 = blink)
bits 2-0 effect id    (0..7, see §3.3)
```

**Offset/length entry** (4 bytes per slot):

```
[ 0x08, offset, 0x00, length ]
```

- `0x08` is a fixed marker.
- `offset` = starting position of this message's data, in **byte-columns** (1 byte-column = 8 pixels wide). It is the running total of the preceding messages' lengths.
- `length` = this message's width in **byte-columns** (0 = slot disabled/empty).
- The `0x08` marker and a correct `offset` **must** be written for *all eight* slots, including disabled ones (length 0). Omitting this causes the badge to misparse the header.

### 3.3 Effects, speed, blink, frame

Effect ids:

| id | name |
|---|---|
| 0 | left (scroll left) |
| 1 | right |
| 2 | up |
| 3 | down |
| 4 | freeze (static) |
| 5 | animation |
| 6 | snow |
| 7 | volume |

- Speed is stored as `speed - 1`, so user-facing speed is `1..8`.
- All effect ids 0–7 have been **validated on the device** (left/right/up/down scroll, freeze,
  animation, snow, volume).
- **Animation (5)** expects the message data as `N` frames, each 6 byte-columns (48 px) wide:
  the message is a single wide bitmap `N × 6` byte-columns, and the badge cycles through the
  frames. (3 frames validated.)
- **Snow (6)** and **volume (7)** are built-in effects and do not require special data.
- A "laser" effect (id 8) exists in some references but is truncated by the 3-bit field; treat it as unsupported.
- Brightness is **not** part of the B1248 header and is not supported by this device.

### 3.4 Data layout and transmission

**Bitmap encoding** (per message): column-major. Each 8-pixel-wide column is stored as
11 bytes (one per row), and within each byte the **MSB is the leftmost pixel**. A `1`
bit means "lit".

- A full-width 44-px image = 6 byte-columns (48 px); the low 4 bits of the 6th column are unused/padding.

**Transmission** (row-major): the 11 data reports carry one horizontal row each. For row
`j` (0..10), the report contains row `j` of every message, concatenated in slot order,
each message's bytes placed at the byte offset given by its cumulative `offset` in the
header. The badge reads exactly `W` bytes from each of the 11 data reports (where `W` is
the summed width), then ignores the dummy report.

**Hard capacity limit:** the device's write buffer is **256 bytes**, i.e. `11 × W ≤ 256`.
This caps total content at **23 byte-columns (184 px)** — see §9 for the full investigation
and the consequence that the device cannot actually hold 8 full-width messages via this
protocol.

### 3.5 Opening the device

- Enumerate HID devices and select `0483:5750`.
- On Linux this appears as a `/dev/hidraw*` node (requires a udev rule or the node to be writable; the node is often already `0666`).
- On Windows the device is a standard HID device and requires no driver.

## 4. Application Requirements

### 4.1 Functional

- Create up to **8 messages**; each message is text and/or an image. **MVP limit: total
  content ≤ 23 byte-columns (184 px)** — i.e. up to 3 full-width (44 px) messages, or more
  shorter ones (see §9).
- Per message: choose **effect**, **speed** (1..8), **blink** on/off, **frame** (border) on/off.
- **Live preview** of each message as an 11×44 pixel grid (scaled up), so the user sees exactly what will be sent.
- **Write** the current configuration to the device.
- **Clear** the device (write a header with all slots disabled).
- **Auto-detect** the device and report a clear error if it is not found.
- **Enforce/warn on the width cap**: warn (or block) when the summed message width would
  exceed 23 byte-columns, since the device drops or corrupts the overflow.

### 4.2 Cross-platform

- Windows is the primary target; Linux is the development platform and must work too.
- Identical behavior and byte output on both platforms (the protocol layer must be platform-independent, with only the HID transport differing).
- No driver installation on Windows (native HID); on Linux a udev rule may be required but must be documented/automated.

### 4.3 Ease of use

- Single window: message list, per-message editor, controls, preview, and a prominent "Write to badge" button.
- Helpful error messages (device not found, no messages, write failure).
- Bundled runtime so users do not install Java separately (see §7).

## 5. Architecture

Four layers, each independently testable:

1. **Device / protocol layer** (`BadgeDriver`)
   - Opens the HID device (hid4java), builds the handshake/header/data reports from a model, and writes them.
   - No UI dependencies; unit-testable by capturing the byte stream.

2. **Model layer**
   - `BadgeMessage` (effect, speed, blink, frame, bitmap/columns).
   - `Badge` (a fixed array of 8 `BadgeMessage` plus the assembled payload).

3. **Renderer layer**
   - Converts text and images into the column-major, MSB-first bitmaps described in §3.4.
   - **Text**: Java 2D `BufferedImage`/`Graphics2D` with **anti-aliasing off** (crisp 1-bit),
     using the bundled **DejaVu Sans** font (Bitstream Vera License) at 11 px height. Text may
     be wider than 44 px — it will scroll (scroll effects) or be clipped (freeze) — see §6.
   - **Images**: `ImageIO`, scaled to 11 px height (aspect preserved), grayscale, thresholded
     (≥ 128 = lit).
   - Shared encoder: column-major, MSB = leftmost pixel, width rounded up to byte-columns.

4. **UI layer** (JavaFX)
   - Message list + editor + controls + live preview + write/clear actions.

## 6. UI Specification

- **Message list** (left): 8 slots; click to select; each shows a thumbnail or label.
- **Editor** (center/right): text field and/or image loader for the selected message.
- **Controls**: effect dropdown, speed slider/spinner (1–8), blink and frame checkboxes.
- **Preview**: an 11×44 grid rendered at an enlarged scale (e.g. 8–12×) that mirrors the exact bitmap; updates live.
- **Overflow communication**: when a message is wider than 44 px, the preview draws the
  44-px display "window" over the full bitmap and labels the result — "scrolling" (scroll
  effects 0–3) or "clipped" (freeze/animation) — so the motion/truncation is not unexpected.
- **Actions**: "Write to badge" and "Clear badge" buttons with progress/error feedback.

## 7. Cross-platform & Packaging

- **Language/stack**: Java 25 (LTS), JavaFX 25, Maven, hid4java.
- **HID**: hid4java bundles hidapi native libraries for Windows/Linux/macOS, so no system libs are needed at build or runtime.
- **Text rendering**: Java 2D with the **bundled DejaVu Sans** font (Bitstream Vera License) — no freetype/fontconfig dev packages, and consistent glyphs on every platform.
- **Packaging**: `jpackage` produces a bundled JRE:
  - Windows: `.msi` / `.exe`
  - Linux: `.deb` / `.rpm` / AppImage
  - macOS: `.dmg` (stretch goal)
- **CI**: GitHub Actions matrix building Windows + Linux artifacts on tag.

## 8. Build & Tooling

- Maven project, `maven.compiler.release=25`.
- Dependencies: `org.openjfx:javafx-controls` (25.0.4), `org.hid4java:hid4java` (0.8.0).
- Run during development: `mvn javafx:run`.
- Package: jpackage (add a jpackage Maven plugin when the UI is functional).

## 9. Known Issues & Open Questions

1. **256-byte write-buffer limit (device-side, confirmed).** The device's single write is
   capped at 256 bytes of image data, i.e. `11 rows × total byte-columns ≤ 256`, which
   means **total content ≤ 23 byte-columns (184 px)**. Empirically (all byte-identical
   across an independent Python harness and the Java implementation):
   - totals of 12, 16, 20, 21, 22, 23 columns render correctly;
   - 24 columns (4 × 44 px) drops the last message ("M4 blank", M1–M3 correct);
   - 25+ columns renders unpredictably (blank / all-solid / transient garble).
   
   The failure mode — the last message(s) being dropped — is consistent with a 256-byte
   buffer that fills in message order (3 full messages = 198 bytes fit; a 4th pushes it
   over). This is a **firmware/device behavior, not a code bug**.
   
   **Ruled out by experiment** (none unlock > 23 columns):
   - per-message data blocks (only the first message renders);
   - byte-stream transmission, row-major and column-major (misaligned — the badge reads
     `W` bytes per report, not 64);
   - timing (longer delays / a 0.5 s pause at the 256-byte boundary);
   - a page/address field in the unused header bytes (42–63) or the "Hello" padding.
   
   **Sibling badge differs:** the LS32 (`0416:5020`, "wang" protocol) stores messages with
   2-byte big-endian lengths and column-major "stuffed" data, confirming the B1248's
   protocol is distinct and that its multi-message (> 256-byte) capability is *not*
   documented in any public reference. Each write (`Hello → header → data`) **replaces**
   the flash, so simple multi-write paging does not work.
   
   **Path to full 8-message support:** capture the vendor Windows software's USB traffic
   (QEMU/VirtualBox + USBPcap) to observe the real command sequence for > 256 bytes.
   Until then the app enforces the 23-column cap.

2. **Write timing is marginal:** a transient "all lit" result was observed on one run
   that later rendered correctly with identical bytes. Delays after the handshake/header
   (0.5 s) and between data lines (0.1 s) are used; reliability under load should be
   validated before release.
3. **Laser effect / brightness:** not supported (see §3.3).

## 10. Roadmap

1. **MVP** (current scope): render text + images, up to 3 full-width (44 px) messages
   (or more shorter ones, total ≤ 23 byte-columns), effects/speed/blink/frame,
   live preview, write + clear, with a width-cap warning, on Linux and Windows.
2. **Full 8-message support**: capture vendor Windows USB traffic to reverse-engineer the
   > 256-byte protocol, then lift the cap.
3. **Packaging** with `jpackage` + CI for Windows/Linux installers.
4. Optional: animations, brightness (if a device variant supports it), macOS.
