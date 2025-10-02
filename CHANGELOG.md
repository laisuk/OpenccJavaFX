# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) and uses
the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format.

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

#### Before (v1.0.2)

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
- Supports Simplified <-> Traditional Chinese conversion
- Built using OpenccJava 1.0.0

---
