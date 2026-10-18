# AI Keyboard — Phase 1

This is the reliable Android IME foundation for the larger keyboard project. It currently
provides English QWERTY typing, space, backspace, enter, shift/double-tap caps lock, punctuation,
number/symbol switching, and long-press backspace repeat.

## Architecture

- `ime/KeyboardInputMethodService.kt` owns Android IME lifecycle and the current `InputConnection`.
- `core/` contains reusable keyboard state, cached layouts, key definitions, and the synchronous
  `TypingEngine`.
- `ui/KeyboardView.kt` is a dependency-free canvas keyboard. It dispatches keys on touch down and
  keeps the entire visible key cell tappable.
- `settings/` is the persistence boundary; preferences are not read during typing.
- `extensions/` contains future feature contracts only. No prediction, AI, network, database,
  voice, glide typing, theme, or personal-learning code is active in Phase 1.

Normal characters use direct `InputConnection.commitText` calls. Backspace uses the code-point-aware
delete API where available, and enter performs the editor action when one is requested by the field.

## Build

Open the project in Android Studio with an Android SDK installed, or run:

```text
gradle :app:assembleDebug
```

The project targets SDK 35, supports API 24+, and intentionally has no runtime dependencies.
