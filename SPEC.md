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
header. If the total width across all messages exceeds 64 byte-columns, the row is
segmented across multiple reports (not yet validated — see §9).

### 3.5 Opening the device

- Enumerate HID devices and select `0483:5750`.
- On Linux this appears as a `/dev/hidraw*` node (requires a udev rule or the node to be writable; the node is often already `0666`).
- On Windows the device is a standard HID device and requires no driver.

## 4. Application Requirements

### 4.1 Functional

- Create up to **8 messages**; each message is text and/or an image.
- Per message: choose **effect**, **speed** (1..8), **blink** on/off, **frame** (border) on/off.
- **Live preview** of each message as an 11×44 pixel grid (scaled up), so the user sees exactly what will be sent.
- **Write** the current configuration to the device.
- **Clear** the device (write a header with all slots disabled).
- **Auto-detect** the device and report a clear error if it is not found.

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
   - Converts text (via Java 2D `BufferedImage`/`Graphics2D` with system fonts at 11 px) and images (via `ImageIO`) into the column-major, MSB-first bitmaps described in §3.4.

4. **UI layer** (JavaFX)
   - Message list + editor + controls + live preview + write/clear actions.

## 6. UI Specification

- **Message list** (left): 8 slots; click to select; each shows a thumbnail or label.
- **Editor** (center/right): text field and/or image loader for the selected message.
- **Controls**: effect dropdown, speed slider/spinner (1–8), blink and frame checkboxes.
- **Preview**: an 11×44 grid rendered at an enlarged scale (e.g. 8–12×) that mirrors the exact bitmap; updates live.
- **Actions**: "Write to badge" and "Clear badge" buttons with progress/error feedback.

## 7. Cross-platform & Packaging

- **Language/stack**: Java 25 (LTS), JavaFX 25, Maven, hid4java.
- **HID**: hid4java bundles hidapi native libraries for Windows/Linux/macOS, so no system libs are needed at build or runtime.
- **Text rendering**: Java 2D with system fonts (no freetype/fontconfig dev packages).
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

1. **Four-slot drop (unresolved):** a 4-message write rendered the 4th slot (offset 18)
   as all-dark while 1- and 2-message writes rendered correctly. Header and data were
   byte-verified correct, so the cause is likely a device-firmware detail (or an
   interaction with the total 24-column width). Must be reproduced and root-caused
   before shipping 8-message support.
2. **Segmentation > 64 byte-columns:** sending a row wider than 64 columns requires
   splitting across reports; not yet validated. The MVP may limit total content width.
3. **Laser effect / brightness:** not supported (see §3.3).

## 10. Roadmap

1. **MVP**: render text + images, single and multiple messages, effects/speed/blink/frame,
   live preview, write + clear, on Linux and Windows.
2. Resolve the 4-slot quirk and validate 8-message writes.
3. Packaging with `jpackage` + CI for Windows/Linux installers.
4. Optional: animations, brightness (if a device variant supports it), macOS.
