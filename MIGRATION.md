# Migration Guide

This document helps users migrate between versions of **OpenCC Java**.

---

## From v1.0.3 → v1.1.0

### 1. Static Dictionary

- In **v1.0.3**, each `OpenCC` instance could load its own dictionary.
- In **v1.1.0**, dictionaries are now **loaded once per JVM** (via `DictionaryHolder`) and shared by all instances.

#### Benefits

- Faster startup (dictionary parsing happens only once).
- Lower memory usage (one dictionary in memory instead of one per instance).
- Ideal for GUI applications (e.g. JavaFX) and helpers (e.g. `OfficeHelper`).

#### Logging

- **INFO** log: dictionary loaded from file system or embedded resource.
- **WARNING** log: dictionary fallback to plain-text sources.

⚠️ **Note:** The shared dictionary is effectively global.  
Any modifications will affect all `OpenCC` instances in the same JVM.

---

### 2. `zhoCheck` is now static

- In **v1.0.3**, `zhoCheck` was an instance method:
  ```java
  OpenCC cc = new OpenCC("s2t");
  int result = cc.zhoCheck("汉字");
    ```

- In **v1.1.0**, `zhoCheck` is a static method:

```java
int result = OpenCC.zhoCheck("汉字"); // preferred

```

- For backward compatibility, use` zhoCheckInstance`:

```java
OpenCC cc = new OpenCC("s2t");
int result = cc.zhoCheckInstance("汉字");
```

---

### 3. Recommended Usage

#### GUI apps (JavaFX, Swing)

Use the static dictionary for performance:

```java
OpenCC cc = new OpenCC("s2t"); // shares dictionary
String converted = cc.convert("汉字");
```

#### Office document helpers

Pass the `OpenCC` instance:

```java
OpenCC instance = new OpenCC("s2t");
OfficeHelper.Resault result = OfficeHelper.convert(inputPath, outputPath, "docx", instance, /* punctuation */ true, /* keepFont */ true);
```

### 4. When to Use Instance Dictionary

If you need to load a custom dictionary path, use the deprecated constructor:

```java
OpenCC custom = new OpenCC("s2t", Paths.get("my_dicts"));

```

This loads a private dictionary for that instance only.
It is slower and uses more memory but allows per-instance customization.

---

### Summary

- **Default**: use static dictionary + `OpenCC.zhoCheck()`.
- **Compatibility**: use `zhoCheckInstance()`.
- **Custom dictionary**: use the deprecated `(config, Path)` constructor.