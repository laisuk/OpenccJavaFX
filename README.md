# OpenccJavaFX

[![GitHub Release](https://img.shields.io/github/v/release/laisuk/OpenccJavaFX?display_name=tag&sort=semver)](https://github.com/laisuk/OpenccJavaFX/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/laisuk/openccjavafx/total.svg)](https://github.com/laisuk/openccjavafx/releases)
[![Latest Downloads](https://img.shields.io/github/downloads/laisuk/openccjavafx/latest/total.svg)](https://github.com/laisuk/openccjavafx/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**OpenccJavaFX** is a Chinese text conversion application built with JavaFX and the FXML design pattern. It leverages
the [OpenccJava](https://github.com/laisuk/OpenccJava) library to provide simplified and traditional Chinese conversion.

---

## 🚀 Download

Download the latest version of **OpenccJavaFX** for your platform
at [Release](https://github.com/laisuk/OpenccJavaFX/releases) section.

> 📦 These are **Java builds**, targeting `Java 17+`.  
> You must have [Java Runtime 17+](https://www.azul.com/downloads/?package=jdk) installed to run them.

---

## Features

- **Chinese Conversion**: Convert between simplified and traditional Chinese text.
- **Regional Variant Phrases**: Taiwan and Hong Kong forward variant conversion applies phrase-level exceptions before
  character-level variants.
- **Single/Batch Conversion**: Perform Chinese text conversion in single or batch mode.
- Designed to convert most **text based file types** and **Office documents** (`.docx`, `.xlsx`, `.pptx`, `.odt`)

---

## Dependencies

- [JavaFX](https://openjfx.io/): Cross-platform Java UI framework.
- [RichTextFX](https://github.com/FXMisc/RichTextFX): Text editor for JavaFX with virtualization support.
- [OpenccJava](https://github.com/laisuk/OpenccJava): Pure Java library for conversions between Traditional and
  Simplified Chinese.
- [picocli](https://github.com/remkop/picocli): A modern framework for building powerful, user-friendly command line
  apps with ease.

---

## Getting Started

**Clone the repository**:

```bash
git clone https://github.com/laisuk/OpenccJavaFX.git
```

**Navigate to the project directory**:

```bash
cd OpenccJavaFX
```

**Build the project**:

```bash
./gradlew build
```

**Run the application**:

```bash
./gradlew run
```

---

## Usage

### Embedded OpenccJava custom dictionaries

The mirrored Java API and command-line tools use one portable custom-dictionary token grammar:

```text
<slot>:<append|override>:<path>
```

Slot names are case-insensitive canonical names supplied by `DictSlot`, for example `STPhrases` and
`HKVariantsRev`. Canonical names—not enum ordinals—are the stable external contract. Deprecated enum constants remain
available so older Java source still compiles, but they are not active and are rejected by parsing and dictionary
operations. Hyphen and underscore aliases are not accepted.

Use `CustomDictSpec.parse(...)` for portable string tokens and `CustomDictSpec.fromFile(...)` when the slot, path, and
mode are already strongly typed. Parsing validates syntax only; it does not test whether the file exists. Files are
opened when a spec is applied through `DictionaryMaxlength.fromDicts(...)`, `withCustomDicts(...)`, or an `OpenCC`
constructor.

```java
CustomDictSpec parsed = CustomDictSpec.parse(
        "STPhrases:append:C:\\dicts\\terms.txt"
);
CustomDictSpec typed = CustomDictSpec.fromFile(
        DictSlot.HKVariantsRev,
        Paths.get("dicts/hk-reverse.txt"),
        CustomDictMode.Override
);
DictionaryMaxlength customized = DictionaryMaxlength.fromDicts(
        Arrays.asList(parsed, typed)
);
```

The CLI accepts the same grammar and permits repeated options:

```shell
openccjavacli convert -c s2t \
  -D STPhrases:override:dicts/base.txt \
  -D STPhrases:append:dicts/project.txt
```

This token format is intentionally unified across the C#, Java, Rust, and Python OpenccJava ecosystem. The Java API
names above describe the implementation embedded in this project; equivalent language packages may expose the shared
format through language-appropriate APIs.

### Single Mode

![image01](./assets/image01.png)

Support most **text base** file types.

1. Paste the text or open a file you wish to convert (file/text drag and drop are supported).
2. Select the desired conversion configuration (e.g., Simplified to Traditional).
3. Click the **Start** button to see the results.

---

### Batch Mode

![image02](./assets/image02.png)
![image03](./assets/image03.png)

Support most **text base** file types, **Office documents** (`.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`) and
EPUB (`.epub`).

1. Select or drag file(s) into the source list box.
2. Select the desired conversion configuration.
3. Set the output folder.
4. Click the **Start** button to begin batch conversion.

---

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request for any enhancements or bug fixes.

---

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---

## Acknowledgements

- [OpenCC](https://github.com/BYVoid/OpenCC) for the Chinese text conversion lexicon.
- [OpenccJava](https://github.com/laisuk/OpenccJava) for the Java Chinese conversion library.
- [JavaFX](https://openjfx.io/) for the cross-platform UI framework.
- [RichTextFX](https://github.com/FXMisc/RichTextFX) for the text editor with virtualization.
