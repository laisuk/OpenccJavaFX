package openccjava;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Converts text-bearing content inside Office, OpenDocument, and EPUB packages.
 *
 * <p>The package layer is independent of any particular text-conversion engine.
 * Callers may provide an {@link TextConverter} that performs any
 * {@code String -> String} transformation. Convenience overloads accepting an
 * {@link OpenCC} instance are retained and adapt OpenCC conversion to the same
 * generic package-processing core.</p>
 *
 * <p>This class owns package mechanics only: ZIP streaming and reconstruction,
 * format-specific entry selection, XLSX inline-string handling, optional font
 * preservation, EPUB {@code mimetype} rules, ZIP-entry safety checks, completed
 * archive validation, and transactional file publication.</p>
 *
 * <p>Supported formats:</p>
 * <ul>
 *   <li>Microsoft Office Open XML: {@code docx}, {@code xlsx}, {@code pptx}</li>
 *   <li>OpenDocument: {@code odt}, {@code ods}, {@code odp}</li>
 *   <li>EPUB: {@code epub}</li>
 * </ul>
 *
 * <p>The implementation is compatible with Java 8.</p>
 */
public class OfficeHelper {

    /**
     * Supported logical Office/EPUB format names.
     */
    public static final List<String> OFFICE_FORMATS = Collections.unmodifiableList(
            Arrays.asList("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub")
    );

    private static final Logger LOGGER = Logger.getLogger(OfficeHelper.class.getName());

    /**
     * Matches an XLSX inline-string cell:
     * {@code <c ... t="inlineStr" ...>...</c>}.
     */
    private static final Pattern XLSX_INLINE_STRING_CELL_PATTERN = Pattern.compile(
            "<c\\b(?=[^>]*\\bt=(?:\"inlineStr\"|'inlineStr'))[^>]*>.*?</c>",
            Pattern.DOTALL
    );

    /**
     * Matches {@code <t>} text nodes inside XLSX inline-string cells.
     */
    private static final Pattern XLSX_TEXT_NODE_PATTERN = Pattern.compile(
            "(<t\\b[^>]*>)(.*?)(</t>)",
            Pattern.DOTALL
    );

    /**
     * Font declarations temporarily protected when {@code keepFont} is enabled.
     */
    private static final Map<String, Pattern> FONT_PATTERNS;

    static {
        Map<String, Pattern> map = new HashMap<>();

        map.put("docx", Pattern.compile("(w:(?:eastAsia|ascii|hAnsi|cs)=\")(.*?)(\")"));
        map.put("xlsx", Pattern.compile("(val=\")(.*?)(\")"));
        map.put("pptx", Pattern.compile("(typeface=\")(.*?)(\")"));

        Pattern odPattern = Pattern.compile(
                "((?:style:font-name(?:-asian|-complex)?|svg:font-family|style:name)=[\"'])([^\"']+)([\"'])"
        );
        map.put("odt", odPattern);
        map.put("ods", odPattern);
        map.put("odp", odPattern);

        map.put("epub", Pattern.compile("(font-family\\s*:\\s*)([^;\"']+)([;\"'])?"));

        FONT_PATTERNS = Collections.unmodifiableMap(map);
    }

    /**
     * Base type for Office/EPUB conversion results.
     */
    public abstract static class Result {

        /**
         * {@code true} when conversion completed successfully.
         */
        public final boolean success;

        /**
         * Human-readable result or failure message. Never {@code null}.
         */
        public final String message;

        /**
         * Creates a conversion result.
         *
         * @param success whether conversion succeeded
         * @param message result message; must not be {@code null}
         * @throws NullPointerException if {@code message} is {@code null}
         */
        protected Result(boolean success, String message) {
            this.success = success;
            this.message = Objects.requireNonNull(message, "message must not be null");
        }
    }

    /**
     * Result of a file-to-file conversion.
     */
    public static final class FileResult extends Result {

        /**
         * Creates a file conversion result.
         *
         * @param success whether conversion succeeded
         * @param message result message; must not be {@code null}
         */
        public FileResult(boolean success, String message) {
            super(success, message);
        }
    }

    /**
     * Result of an in-memory conversion.
     */
    public static final class MemoryResult extends Result {

        /**
         * Converted package bytes, or {@code null} when conversion failed.
         *
         * <p>The constructor defensively copies the supplied array. This public
         * array itself remains mutable; callers needing an independent snapshot
         * should clone it before sharing or modifying it.</p>
         */
        public final byte[] data;

        /**
         * Creates an in-memory conversion result.
         *
         * @param success whether conversion succeeded
         * @param message result message; must not be {@code null}
         * @param data    converted package bytes, or {@code null}
         */
        public MemoryResult(boolean success, String message, byte[] data) {
            super(success, message);
            this.data = data == null ? null : data.clone();
        }
    }

    /**
     * Constructs an {@code OfficeHelper}.
     *
     * <p>The class currently exposes only static operations; the public constructor
     * is retained for source and binary compatibility with existing callers.</p>
     */
    public OfficeHelper() {
        // Compatibility constructor.
    }

    /**
     * Converts an Office or EPUB package entirely in memory.
     *
     * <p>The source package is streamed from {@code inputBytes} into a rebuilt ZIP.
     * Unchanged entries are copied through the ZIP streams, while only selected
     * text-bearing XML/XHTML entries are materialized as UTF-8 strings and passed
     * to {@code textConverter}. No temporary filesystem package is created.</p>
     *
     * <p>For EPUB, {@code mimetype} is emitted first and stored without compression.
     * The rebuilt archive is validated before it is returned.</p>
     *
     * @param inputBytes    complete source package bytes
     * @param format        logical format name:
     *                      {@code docx/xlsx/pptx/odt/ods/odp/epub}
     * @param textConverter caller-supplied text transformation
     * @param keepFont      whether supported font declarations should be protected
     * @return conversion result containing rebuilt package bytes on success
     * @since 1.5.0
     */
    public static MemoryResult convert(
            byte[] inputBytes,
            String format,
            TextConverter textConverter,
            boolean keepFont
    ) {
        if (inputBytes == null || inputBytes.length == 0) {
            return new MemoryResult(false, "❌ Input bytes are empty.", null);
        }
        if (textConverter == null) {
            return new MemoryResult(false, "❌ Text converter must not be null.", null);
        }

        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return new MemoryResult(false, "❌ Unsupported or invalid format: " + format, null);
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    Math.max(8192, inputBytes.length)
            );

            int convertedCount;

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(output))) {
                if ("epub".equals(normalizedFormat)) {
                    byte[] mimetype = findEntryBytes(
                            new ByteArrayInputStream(inputBytes),
                            "mimetype"
                    );

                    if (mimetype == null) {
                        return new MemoryResult(
                                false,
                                "❌ 'mimetype' file is missing. EPUB requires this.",
                                null
                        );
                    }

                    writeStoredEntry(zos, "mimetype", mimetype);
                }

                try (ZipInputStream zis = new ZipInputStream(
                        new BufferedInputStream(new ByteArrayInputStream(inputBytes)))) {
                    convertedCount = convertArchive(
                            zis,
                            zos,
                            normalizedFormat,
                            textConverter,
                            keepFont,
                            "epub".equals(normalizedFormat)
                    );
                }
            }

            if (convertedCount == 0) {
                return new MemoryResult(
                        false,
                        "⚠️ No valid XML fragments found in format: " + normalizedFormat,
                        null
                );
            }

            byte[] rebuilt = output.toByteArray();
            validateZipBytes(rebuilt);

            return new MemoryResult(
                    true,
                    successMessage(convertedCount, normalizedFormat),
                    rebuilt
            );
        } catch (Exception ex) {
            return new MemoryResult(
                    false,
                    "❌ Conversion failed: " + safeMessage(ex),
                    null
            );
        }
    }

    /**
     * Converts an Office or EPUB package entirely in memory using an initialized
     * {@link OpenCC} instance.
     *
     * <p>This convenience overload preserves the established API and adapts
     * {@link OpenCC} to the generic {@link TextConverter} core.</p>
     *
     * @param inputBytes  complete source package bytes
     * @param format      logical format name
     * @param converter   initialized OpenCC converter
     * @param punctuation whether punctuation conversion is enabled
     * @param keepFont    whether supported font declarations should be protected
     * @return conversion result containing rebuilt package bytes on success
     */
    public static MemoryResult convert(
            byte[] inputBytes,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        if (converter == null) {
            return new MemoryResult(false, "❌ Converter must not be null.", null);
        }

        return convert(
                inputBytes,
                format,
                openCcTextConverter(converter, punctuation),
                keepFont
        );
    }

    /**
     * Converts an Office or EPUB package using a streaming file-to-file path.
     *
     * <p>The source package is not loaded into a single {@code byte[]}. Unchanged
     * entries stream from the source ZIP to a rebuilt package, while selected
     * text-bearing entries alone are buffered for conversion.</p>
     *
     * <p>The candidate package is written to a sibling temporary file, validated,
     * and only then published to {@code outputFile}. Existing output therefore
     * remains untouched if conversion or validation fails.</p>
     *
     * @param inputFile     source Office/EPUB package
     * @param outputFile    destination package
     * @param format        logical format name
     * @param textConverter caller-supplied text transformation
     * @param keepFont      whether supported font declarations should be protected
     * @return file conversion result
     * @since 1.5.0
     */
    public static FileResult convert(
            File inputFile,
            File outputFile,
            String format,
            TextConverter textConverter,
            boolean keepFont
    ) {
        if (inputFile == null || !inputFile.isFile()) {
            return new FileResult(
                    false,
                    "❌ Input file must exist and be a regular file."
            );
        }
        if (outputFile == null) {
            return new FileResult(false, "❌ Output file must not be null.");
        }
        if (textConverter == null) {
            return new FileResult(false, "❌ Text converter must not be null.");
        }

        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return new FileResult(
                    false,
                    "❌ Unsupported or invalid format: " + format
            );
        }

        Path outputPath = outputFile.toPath().toAbsolutePath();
        Path parent = outputPath.getParent();
        Path tempOutput = null;

        try {
            if (parent != null) {
                Files.createDirectories(parent);
                tempOutput = Files.createTempFile(
                        parent,
                        outputFile.getName() + ".",
                        ".tmp"
                );
            } else {
                tempOutput = Files.createTempFile(
                        outputFile.getName() + ".",
                        ".tmp"
                );
            }

            int convertedCount;

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempOutput)))) {

                if ("epub".equals(normalizedFormat)) {
                    byte[] mimetype;

                    try (InputStream mimeInput = new BufferedInputStream(
                            Files.newInputStream(inputFile.toPath()))) {
                        mimetype = findEntryBytes(mimeInput, "mimetype");
                    }

                    if (mimetype == null) {
                        return new FileResult(
                                false,
                                "❌ 'mimetype' file is missing. EPUB requires this."
                        );
                    }

                    writeStoredEntry(zos, "mimetype", mimetype);
                }

                try (ZipInputStream zis = new ZipInputStream(
                        new BufferedInputStream(Files.newInputStream(inputFile.toPath())))) {
                    convertedCount = convertArchive(
                            zis,
                            zos,
                            normalizedFormat,
                            textConverter,
                            keepFont,
                            "epub".equals(normalizedFormat)
                    );
                }
            }

            if (convertedCount == 0) {
                return new FileResult(
                        false,
                        "⚠️ No valid XML fragments found in format: " + normalizedFormat
                );
            }

            validateZipFile(tempOutput);
            publishTempFile(tempOutput, outputPath);
            tempOutput = null;

            return new FileResult(
                    true,
                    successMessage(convertedCount, normalizedFormat)
            );
        } catch (IOException ex) {
            return new FileResult(
                    false,
                    "❌ I/O error during conversion: " + safeMessage(ex)
            );
        } catch (Exception ex) {
            return new FileResult(
                    false,
                    "❌ Conversion failed: " + safeMessage(ex)
            );
        } finally {
            if (tempOutput != null) {
                try {
                    Files.deleteIfExists(tempOutput);
                } catch (IOException ex) {
                    LOGGER.log(
                            Level.WARNING,
                            "Failed to delete temporary output " + tempOutput,
                            ex
                    );
                }
            }
        }
    }

    /**
     * Converts an Office or EPUB package using an initialized {@link OpenCC}
     * instance and the streaming file-to-file path.
     *
     * <p>This convenience overload adapts OpenCC conversion to the generic
     * {@link TextConverter} package-processing core.</p>
     *
     * @param inputFile   source Office/EPUB package
     * @param outputFile  destination package
     * @param format      logical format name
     * @param converter   initialized OpenCC converter
     * @param punctuation whether punctuation conversion is enabled
     * @param keepFont    whether supported font declarations should be protected
     * @return file conversion result
     */
    public static FileResult convert(
            File inputFile,
            File outputFile,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        if (converter == null) {
            return new FileResult(false, "❌ Converter must not be null.");
        }

        return convert(
                inputFile,
                outputFile,
                format,
                openCcTextConverter(converter, punctuation),
                keepFont
        );
    }

    /**
     * Adapts an initialized {@link OpenCC} instance to the generic Office text
     * transformation contract.
     *
     * <p>OpenCC-specific conversion state and error handling remain outside the
     * package core.</p>
     */
    private static TextConverter openCcTextConverter(
            final OpenCC converter,
            final boolean punctuation
    ) {
        return text -> {
            String converted = converter.convert(text, punctuation);

            if (converted == null) {
                throw new IllegalStateException(
                        "native error: " + converter.getLastError()
                );
            }

            return converted;
        };
    }

    /**
     * Streams one ZIP package into another and converts selected entries.
     *
     * @param zis              source ZIP stream
     * @param zos              destination ZIP stream
     * @param format           normalized logical format
     * @param textConverter    text transformation
     * @param keepFont         whether supported font declarations should be protected
     * @param skipEpubMimetype whether a separately emitted EPUB {@code mimetype}
     *                         entry should be skipped
     * @return number of converted package entries
     * @throws IOException if ZIP reading or writing fails
     */
    private static int convertArchive(
            ZipInputStream zis,
            ZipOutputStream zos,
            String format,
            TextConverter textConverter,
            boolean keepFont,
            boolean skipEpubMimetype
    ) throws IOException {
        int convertedCount = 0;
        ZipEntry sourceEntry;

        while ((sourceEntry = zis.getNextEntry()) != null) {
            String rawEntryName = sourceEntry.getName();
            String entryName = normalizeEntryName(rawEntryName);

            if (isUnsafeZipEntryName(entryName)) {
                throw new IOException("Unsafe ZIP entry path: " + rawEntryName);
            }

            if (skipEpubMimetype && "mimetype".equals(entryName)) {
                zis.closeEntry();
                continue;
            }

            if (sourceEntry.isDirectory()) {
                String directoryName = entryName.endsWith("/")
                        ? entryName
                        : entryName + "/";

                ZipEntry outputEntry = new ZipEntry(directoryName);
                copyEntryMetadata(sourceEntry, outputEntry);

                zos.putNextEntry(outputEntry);
                zos.closeEntry();
                zis.closeEntry();
                continue;
            }

            boolean target = isTargetEntry(format, entryName);

            ZipEntry outputEntry = new ZipEntry(entryName);
            copyEntryMetadata(sourceEntry, outputEntry);
            zos.putNextEntry(outputEntry);

            if (target) {
                byte[] bytes = readCurrentEntry(zis);
                String xml = new String(bytes, StandardCharsets.UTF_8);

                String converted = convertTextEntry(
                        format,
                        entryName,
                        xml,
                        textConverter,
                        keepFont
                );

                zos.write(converted.getBytes(StandardCharsets.UTF_8));
                convertedCount++;
            } else {
                copy(zis, zos);
            }

            zos.closeEntry();
            zis.closeEntry();
        }

        return convertedCount;
    }

    /**
     * Applies optional font protection and format-specific conversion to one
     * selected text-bearing package entry.
     */
    private static String convertTextEntry(
            String format,
            String entryName,
            String xml,
            TextConverter textConverter,
            boolean keepFont
    ) {
        Map<String, String> fontMap = new HashMap<>();

        Path relativePath = Paths.get(entryName);

        if (keepFont && shouldMaskFonts(format, relativePath)) {
            Pattern pattern = getFontPattern(format);

            if (pattern != null) {
                Matcher matcher = pattern.matcher(xml);
                int counter = 0;
                StringBuffer masked = new StringBuffer();

                while (matcher.find()) {
                    String marker = "__F_O_N_T_" + counter++ + "__";
                    fontMap.put(marker, matcher.group(2));

                    String suffix = matcher.groupCount() >= 3
                            && matcher.group(3) != null
                            ? matcher.group(3)
                            : "";

                    matcher.appendReplacement(
                            masked,
                            Matcher.quoteReplacement(
                                    matcher.group(1) + marker + suffix
                            )
                    );
                }

                matcher.appendTail(masked);
                xml = masked.toString();
            }
        }

        String converted = convertXmlContent(
                format,
                relativePath,
                xml,
                textConverter
        );

        for (Map.Entry<String, String> entry : fontMap.entrySet()) {
            converted = converted.replace(entry.getKey(), entry.getValue());
        }

        return converted;
    }

    /**
     * Returns whether a package entry contains text that should be converted.
     */
    private static boolean isTargetEntry(String format, String entryName) {
        switch (format) {
            case "docx":
                return "word/document.xml".equals(entryName);

            case "xlsx":
                return "xl/sharedStrings.xml".equals(entryName)
                        || isXlsxWorksheetEntry(entryName);

            case "pptx":
                return isPptxTargetEntry(entryName);

            case "odt":
            case "ods":
            case "odp":
                return "content.xml".equals(entryName);

            case "epub":
                return isEpubTextEntry(entryName);

            default:
                return false;
        }
    }

    /**
     * Returns whether an XLSX package path is a worksheet XML part.
     */
    private static boolean isXlsxWorksheetEntry(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        return lower.startsWith("xl/worksheets/")
                && lower.endsWith(".xml");
    }

    /**
     * Returns whether a PPTX XML part is intended for text conversion.
     *
     * <p>Matching is based on normalized package-relative paths rather than broad
     * filename substring tests. This prevents unrelated XML parts from being
     * converted accidentally.</p>
     */
    private static boolean isPptxTargetEntry(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);

        if (!lower.endsWith(".xml")) {
            return false;
        }

        return lower.startsWith("ppt/slides/")
                || lower.startsWith("ppt/notesslides/")
                || lower.startsWith("ppt/slidemasters/")
                || lower.startsWith("ppt/slidelayouts/")
                || lower.startsWith("ppt/comments/")
                || "ppt/commentauthors.xml".equals(lower);
    }

    /**
     * Returns whether an EPUB package path contains text-bearing content.
     */
    private static boolean isEpubTextEntry(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);

        return lower.endsWith(".xhtml")
                || lower.endsWith(".html")
                || lower.endsWith(".opf")
                || lower.endsWith(".ncx");
    }

    /**
     * Normalizes and validates a logical format name.
     *
     * @return normalized format name, or {@code null} when unsupported
     */
    private static String normalizeFormat(String format) {
        if (format == null) {
            return null;
        }

        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return OFFICE_FORMATS.contains(normalized) ? normalized : null;
    }

    /**
     * Normalizes ZIP entry separators to forward slashes.
     */
    private static String normalizeEntryName(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }

    /**
     * Returns whether a ZIP entry name is unsafe to reproduce.
     *
     * <p>Absolute paths, Windows drive-qualified paths, empty names, and any
     * {@code ..} path component are rejected. Although conversion does not extract
     * package entries to arbitrary filesystem paths, validating names avoids
     * propagating traversal-style entries into rebuilt archives.</p>
     */
    private static boolean isUnsafeZipEntryName(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return true;
        }

        if (entryName.charAt(0) == '/' || entryName.charAt(0) == '\\') {
            return true;
        }

        if (entryName.length() >= 3
                && entryName.charAt(1) == ':'
                && (entryName.charAt(2) == '/'
                || entryName.charAt(2) == '\\')) {
            return true;
        }

        String normalized = entryName.replace('\\', '/');
        String[] parts = normalized.split("/");

        for (String part : parts) {
            if ("..".equals(part)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Copies ZIP metadata that does not constrain the rebuilt entry's compressed
     * size, uncompressed size, CRC, or compression method.
     */
    private static void copyEntryMetadata(ZipEntry source, ZipEntry target) {
        if (source.getTime() >= 0) {
            target.setTime(source.getTime());
        }

        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }

        byte[] extra = source.getExtra();
        if (extra != null) {
            target.setExtra(extra);
        }
    }

    /**
     * Finds one ZIP entry and returns its bytes.
     *
     * <p>The supplied package stream is consumed and closed by this method.</p>
     */
    private static byte[] findEntryBytes(
            InputStream packageInput,
            String wantedName
    ) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(packageInput)) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeEntryName(entry.getName());

                if (!entry.isDirectory() && wantedName.equals(entryName)) {
                    return readCurrentEntry(zis);
                }

                zis.closeEntry();
            }
        }

        return null;
    }

    /**
     * Writes an uncompressed ZIP entry.
     *
     * <p>ZIP STORED entries require size and CRC values before the entry is
     * opened.</p>
     */
    private static void writeStoredEntry(
            ZipOutputStream zos,
            String entryName,
            byte[] data
    ) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);

        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());

        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * Reads the current ZIP entry completely.
     */
    private static byte[] readCurrentEntry(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    /**
     * Copies all bytes from {@code input} to {@code output}.
     */
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;

        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    /**
     * Drains an input stream fully without retaining its contents.
     */
    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];

        while (input.read(buffer) != -1) {
            // Intentionally discard.
        }
    }

    /**
     * Validates an in-memory rebuilt ZIP archive.
     *
     * <p>Every entry is read fully so malformed compressed data or CRC failures are
     * detected before the result is returned to the caller.</p>
     */
    private static void validateZipBytes(byte[] data) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data)))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                drain(zis);
                zis.closeEntry();
            }
        }
    }

    /**
     * Validates a completed filesystem ZIP archive before publication.
     *
     * <p>Every non-directory entry is read fully so malformed data and CRC failures
     * are detected while the candidate file is still temporary.</p>
     */
    private static void validateZipFile(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                try (InputStream input = zipFile.getInputStream(entry)) {
                    drain(input);
                }
            }
        }
    }

    /**
     * Publishes a validated sibling temporary file to its final destination.
     *
     * <p>An atomic replacement is attempted first. Filesystems that do not support
     * {@link StandardCopyOption#ATOMIC_MOVE} fall back to a normal replacement.</p>
     */
    private static void publishTempFile(
            Path tempOutput,
            Path outputPath
    ) throws IOException {
        try {
            Files.move(
                    tempOutput,
                    outputPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException atomicMoveFailure) {
            Files.move(
                    tempOutput,
                    outputPath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    /**
     * Builds the standard success message.
     */
    private static String successMessage(
            int convertedCount,
            String format
    ) {
        return "✅ Successfully converted "
                + convertedCount
                + " fragment(s) in "
                + format
                + " document.";
    }

    /**
     * Returns a useful exception message even when {@link Throwable#getMessage()}
     * is {@code null}.
     */
    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }

    /**
     * Creates a ZIP archive from a file or directory.
     *
     * <p>This public utility is retained for backward compatibility. Office/EPUB
     * conversion itself uses the dedicated streaming package pipeline above.</p>
     *
     * @param sourcePath  file or directory to archive
     * @param zipFilePath destination ZIP file
     * @throws IOException              if ZIP creation fails
     * @throws IllegalArgumentException if {@code sourcePath} is neither a regular
     *                                  file nor a directory
     */
    public static void zip(
            Path sourcePath,
            Path zipFilePath
    ) throws IOException {
        Path parentDir = zipFilePath.getParent();

        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (OutputStream output = Files.newOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(output)) {

            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> paths = Files.walk(sourcePath)) {
                    Iterator<Path> iterator = paths
                            .filter(path -> !Files.isDirectory(path))
                            .iterator();

                    while (iterator.hasNext()) {
                        Path path = iterator.next();
                        Path relativePath = sourcePath.relativize(path);

                        ZipEntry zipEntry = new ZipEntry(
                                relativePath.toString().replace('\\', '/')
                        );

                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                }
            } else if (Files.isRegularFile(sourcePath)) {
                ZipEntry zipEntry = new ZipEntry(
                        sourcePath.getFileName().toString()
                );

                zos.putNextEntry(zipEntry);
                Files.copy(sourcePath, zos);
                zos.closeEntry();
            } else {
                throw new IllegalArgumentException(
                        "Source path must be a file or a directory: " + sourcePath
                );
            }
        }
    }

    /**
     * Returns whether font masking should be applied to a selected package part.
     *
     * <p>For XLSX, broad {@code val="..."} masking is restricted to
     * {@code xl/sharedStrings.xml}; worksheet XML contains many unrelated
     * structural {@code val} attributes.</p>
     */
    private static boolean shouldMaskFonts(
            String format,
            Path relativePath
    ) {
        if (!"xlsx".equals(format)) {
            return true;
        }

        String normalized = relativePath.toString().replace('\\', '/');
        return "xl/sharedStrings.xml".equalsIgnoreCase(normalized);
    }

    /**
     * Returns the format-specific font declaration pattern.
     */
    private static Pattern getFontPattern(String format) {
        return FONT_PATTERNS.get(format);
    }

    /**
     * Converts one selected XML/XHTML fragment.
     *
     * <p>XLSX worksheets are handled narrowly: only cells whose type is
     * {@code inlineStr} are rewritten, and within those cells only {@code <t>}
     * text nodes are passed to the text converter. Shared strings and other
     * supported package parts use whole-fragment conversion.</p>
     */
    private static String convertXmlContent(
            String format,
            Path relativePath,
            String xml,
            TextConverter textConverter
    ) {
        if ("xlsx".equals(format) && isWorksheetPath(relativePath)) {
            return convertXlsxInlineStrings(xml, textConverter);
        }

        return applyTextConverter(textConverter, xml);
    }

    /**
     * Applies the caller-supplied text transformation and enforces its non-null
     * return contract.
     *
     * @throws NullPointerException  if {@code textConverter} is {@code null}
     * @throws IllegalStateException if the converter returns {@code null}
     */
    private static String applyTextConverter(
            TextConverter textConverter,
            String text
    ) {
        Objects.requireNonNull(
                textConverter,
                "textConverter must not be null"
        );

        String converted = textConverter.convert(text);

        if (converted == null) {
            throw new IllegalStateException(
                    "Office text converter returned null."
            );
        }

        return converted;
    }

    /**
     * Returns whether the relative package path identifies an XLSX worksheet XML
     * part.
     */
    private static boolean isWorksheetPath(Path relativePath) {
        String normalized = relativePath.toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);

        return normalized.startsWith("xl/worksheets/")
                && normalized.endsWith(".xml");
    }

    /**
     * Converts only inline-string cells in one XLSX worksheet XML document.
     */
    private static String convertXlsxInlineStrings(
            String xml,
            TextConverter textConverter
    ) {
        Matcher cellMatcher = XLSX_INLINE_STRING_CELL_PATTERN.matcher(xml);
        StringBuffer xmlOut = new StringBuffer();

        while (cellMatcher.find()) {
            String convertedCell = convertXlsxInlineStringCell(
                    cellMatcher.group(),
                    textConverter
            );

            cellMatcher.appendReplacement(
                    xmlOut,
                    Matcher.quoteReplacement(convertedCell)
            );
        }

        cellMatcher.appendTail(xmlOut);
        return xmlOut.toString();
    }

    /**
     * Converts only {@code <t>} nodes inside one XLSX inline-string cell.
     */
    private static String convertXlsxInlineStringCell(
            String cellXml,
            TextConverter textConverter
    ) {
        Matcher textMatcher = XLSX_TEXT_NODE_PATTERN.matcher(cellXml);
        StringBuffer cellOut = new StringBuffer();

        while (textMatcher.find()) {
            String convertedText = applyTextConverter(
                    textConverter,
                    textMatcher.group(2)
            );

            String replacement = textMatcher.group(1)
                    + convertedText
                    + textMatcher.group(3);

            textMatcher.appendReplacement(
                    cellOut,
                    Matcher.quoteReplacement(replacement)
            );
        }

        textMatcher.appendTail(cellOut);
        return cellOut.toString();
    }
}
