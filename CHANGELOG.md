# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) and uses
the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format.

---

## [1.3.0] - Unreleased

### Added

- Added Find and Replace for the main source and destination editors with `Ctrl`/`Command`+`F` and
  `Ctrl`/`Command`+`H`, next/previous navigation, search wrapping, match-case and regular-expression options.
    - Supports literal replacement, Java regular-expression capture-group replacement, Replace Current, and single-edit
      Replace All in the editable source editor.
    - Includes inline match, wrap, validation, and replacement-count feedback with light and dark theme styling.
    - Keeps the read-only destination searchable while disabling replacement controls; the batch preview remains
      read-only and outside editor search targeting.
- Added Go to Line for the active source or destination editor with `Ctrl`/`Command`+`G`.
    - Validates 1-based logical line numbers, centers the requested RichTextFX paragraph, and reports the current line
      and total line count inline.
    - Keeps the panel open after navigation with the line field focused and selected for rapid repeated jumps; Escape
      and Close restore focus to the active editor.
- Added a Dialog Quotes Fixer for the editable source editor.
    - Normalizes ASCII dialog quotes to matching curly or Traditional Chinese corner quote families while preserving
      existing CJK quotation marks and Latin apostrophes inside words.
    - Applies normalization as one undoable RichTextFX document edit, refreshes source metadata, and does not
      automatically convert or modify the destination editor.
- Added Dialog Quotes Validators for both source and destination editors.
    - Conservatively reports reversed, mixed-family, and mixed-level completed quote pairs at logical line boundaries
      without attempting speculative document-wide quote balancing.
    - Presents up to five structured issues in a reusable light/dark-themed dialog, with clipped long-line previews and
      a target-aware **Go to First Suspicious Line** action that returns to the editor that was validated.
    - Reuses shared editor navigation with Go to Line and includes pure backend tests for normalization, validation,
      newline handling, issue ordering, summaries, and immutable results.
    - Localizes the complete feature UI, including tooltips, status messages, dialog titles, summaries, hints, line
      labels, counts, and actions, in English, Simplified Chinese, and Traditional Chinese while preserving original
      document excerpts verbatim.

### Changed

- Replaced the embedded legacy CLI commands with the current OpenccJava CLI implementations, including current
  validation, exit-code, help, conversion, Office, PDF, dictionary-generation, and progress behavior.
- Synced the mirrored OpenccJava custom-dictionary API around `DictSlot` canonical names and
  `CustomDictSpec.parse(<slot>:<append|override>:<path>)`, with strict active-slot validation, CLI delegation, and a
  deprecated `withCustomDictFiles(...)` forwarding method.
- Synced core parity fixes for conversion-plan cache retention, one-shot `OpenCC.convert(...)`, DeToFu level aliases,
  and defensive `StarterUnion` state handling.
- Update and optimize dictionary data to reduce ambiguity.
- Updated the mirrored `openccjava` package with forward Taiwan/Hong Kong regional variant phrase slots:
    - Added `DictSlot.TWVariantsPhrases` and `DictSlot.HKVariantsPhrases`.
    - Loads and serializes `tw_variants_phrases` / `hk_variants_phrases` from `TWVariantsPhrases.txt` /
      `HKVariantsPhrases.txt`.
    - Applies forward regional phrase dictionaries before character variants for TW/HK conversions.
    - Renamed internal union cache keys from `TwVariantsOnly` / `HkVariantsOnly` to `TwVariantsPair` /
      `HkVariantsPair`.
- Updated dictionary JSON serialization and `dictgen` to use stable field order, optional sorted keys, and the complete
  dictionary slot list including `TWVariantsPhrases.txt` and `HKVariantsPhrases.txt`.

---

## [1.2.2] - 2026-04-26

### Changed

- Update dictionary data.
- UI retouch.
- Optimized CJK text paragraph reflow.
- Optimized Mouse Hover hints.
- Added `ConversionComboBoxHelper` class and further optimize i18n.

### Fixed

- Fixed `Manual Config` label update dynamically when UI language selection changed.
- Fixed total chars status per UI language.

---

## [1.2.1] - 2026-04-07

### Changed

- Update `NumberingContext` for Word document open as text.
- Update openccjava dictionary.
- Optimized `Reflow` to handle unclosed dialog and certain quote symbol typo.
- Update UI to `Fluent 2` theme.
- Added `Settings` tab.
- Update conversion core to `OpenccJava v1.2.1`.
- Release build with Java 21 for better performance.

---

## [1.1.1] - 2025-11-25

### Changed

- Refactored `OfficeHelper` to include a core `byte[]`-based `convert()` API for in-memory document processing.
- Updated conversion result handling: introduced unified abstract `Result` base class with concrete `FileResult` and
  `MemoryResult` subtypes.
- Ensured backward compatibility: legacy `Result` return type remains valid and unchanged for existing users.

---

## [1.1.0] - 2025-10.02

### Added

- Add drag-and-drop text support to `TextBoxSource`.
- Introduced `StarterIndex` and `UnionCache` to speed up conversion.

### Changed

- **Static dictionary implementation**:  
  Dictionaries are now loaded once per JVM (lazy-loaded via `DictionaryHolder`) and shared by all `OpenCC` instances.
    - Improves startup performance and reduces memory usage for GUI apps (e.g. JavaFX) and helper classes (e.g.
      `OfficeHelper`).
    - Log messages are emitted on first load:
        - **INFO** when loaded from file system or embedded resource.
        - **WARNING** when falling back to plain-text dictionary sources.

- `OpenCC.zhoCheck(String)` is now a **static method** for clarity and consistency.  
  Existing instance calls `myOpenCC.zhoCheck(text)` will no longer compile.  
  Use one of:
    - `OpenCC.zhoCheck(text)` – preferred static usage.
    - `myOpenCC.zhoCheckInstance(text)` – for backward-compatible instance style.

- Add Starter Length Mask for faster dictionary lookup

### Migration Notes

#### Before (v1.0.3)

```java
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheck("汉字");
```

### After (v1.1.0)

```java
// Preferred static usage
int result = OpenCC.zhoCheck("汉字");

// Or for compatibility with old instance style
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheckInstance("汉字");

```

> ⚠️ Note: The dictionary is now shared across all OpenCC instances.  
> Any modification to the dictionary object will affect all instances in the JVM.

---

## [1.0.0] – 2025-07-30

### Added

- Initial public release of OpenccJavaFX
- Cross-platform JavaFX GUI
- Supports Simplified ↔ Traditional Chinese conversion
- Built using OpenccJava 1.0.0

---
