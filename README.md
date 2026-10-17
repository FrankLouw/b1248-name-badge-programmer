# B1248 Name Badge Programmer

Cross-platform GUI for programming the **B1248** LED name badge
(USB `0483:5750`, 11×44, USB-HID).

See **[SPEC.md](SPEC.md)** for the full project specification, including the
validated device protocol, application requirements, and build/packaging plan.

## Stack

- Java 25 (LTS)
- JavaFX 25
- Maven
- [hid4java](https://github.com/gary-rowe/hid4java) (bundles hidapi for Windows/Linux/macOS)

## Usage

```
mvn javafx:run
```

Select a slot, enter text (or load an image), set the effect/speed/blink/frame,
watch the live preview, then click **Write to badge** with the device connected.

## Status

Functional MVP. Note the device's write buffer caps total content at **23 byte-columns
(184 px)** — about 3 full-width messages — see [SPEC.md §9](SPEC.md).

## License

- Code: [MIT](LICENSE)
- Bundled [DejaVu Sans](https://dejavu-fonts.github.io/) font: Bitstream Vera License
  (see `src/main/resources/fonts/DejaVuSans-LICENSE.txt`)
