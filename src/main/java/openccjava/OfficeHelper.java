package openccjava;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.function.Predicate;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for converting Office-based document formats using OpenCC logic.
 *
 * <p>Supported formats include:
 * <ul>
 *   <li>Microsoft Office XML formats: {@code .docx}, {@code .xlsx}, {@code .pptx}</li>
 *   <li>OpenDocument formats: {@code .odt}, {@code .ods}, {@code .odp}</li>
 *   <li>EPUB eBooks: {@code .epub}</li>
 * </ul>
 *
 * <p>Internally, the class handles these formats as ZIP archives, extracts and processes
 * their XML/XHTML content, applies OpenCC transformations, and repackages the result.
 *
 * <p>This class is designed for use in batch or CLI applications.
 */
public class OfficeHelper {
    /**
     * List of supported file extensions for Office and EPUB documents.
     */
    public static final List<String> OFFICE_FORMATS = Arrays.asList(
            "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub"
    );

    /**
     * Logger instance used for reporting non-fatal processing errors.
     */
    private static final Logger LOGGER = Logger.getLogger(OfficeHelper.class.getName());

    /**
     * Precompiled regular expression patterns for extracting font declarations
     * across supported document formats.
     *
     * <p>Each pattern provides three capturing groups:
     * <ol>
     *   <li>Prefix (e.g., attribute or CSS property start)</li>
     *   <li>The actual font value</li>
     *   <li>Suffix (e.g., closing quote, semicolon, or delimiter)</li>
     * </ol>
     *
     * <p>Supported formats and their corresponding attributes:
     * <ul>
     *   <li><b>docx</b>: {@code w:eastAsia}, {@code w:ascii}, {@code w:hAnsi}, {@code w:cs}</li>
     *   <li><b>xlsx</b>: {@code val}</li>
     *   <li><b>pptx</b>: {@code typeface}</li>
     *   <li><b>odt/ods/odp</b>: {@code style:font-name}, {@code style:font-name-asian},
     *       {@code style:font-name-complex}, {@code svg:font-family}, {@code style:name}</li>
     *   <li><b>epub</b>: CSS {@code font-family}</li>
     * </ul>
     *
     * <p>These patterns are used when {@code --keep-font} is enabled to temporarily
     * replace font declarations with markers during OpenCC text conversion,
     * and then restore them afterward.
     */
    private static final Map<String, Pattern> FONT_PATTERNS;

    static {
        Map<String, Pattern> map = new HashMap<>();
        map.put("docx", Pattern.compile("(w:(?:eastAsia|ascii|hAnsi|cs)=\")(.*?)(\")"));
        map.put("xlsx", Pattern.compile("(val=\")(.*?)(\")"));
        map.put("pptx", Pattern.compile("(typeface=\")(.*?)(\")"));

        Pattern odPattern = Pattern.compile("((?:style:font-name(?:-asian|-complex)?|svg:font-family|style:name)=[\"'])([^\"']+)([\"'])");
        map.put("odt", odPattern);
        map.put("ods", odPattern);
        map.put("odp", odPattern);

        map.put("epub", Pattern.compile("(font-family\\s*:\\s*)([^;\"']+)([;\"'])?"));

        FONT_PATTERNS = Collections.unmodifiableMap(map);
    }

    /**
     * Represents the result of an Office document conversion.
     */
    public static class Result {
        /**
         * Whether the conversion succeeded
         */
        public boolean success;

        /**
         * Message describing success, warning, or failure details
         */
        public String message;

        /**
         * Constructs a {@code Result} with success status and message.
         *
         * @param success true if successful, false otherwise
         * @param message the result message or error description
         */
        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Constructs an instance of {@code OfficeHelper}.
     */
    public OfficeHelper() {
        // No initialization required
    }

    /**
     * Converts an Office or EPUB document using the given OpenCC converter.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Unzips the document to a temporary directory</li>
     *   <li>Locates content XML/XHTML files based on the format</li>
     *   <li>Optionally preserves font markup using pattern markers</li>
     *   <li>Applies OpenCC conversion to the relevant fragments</li>
     *   <li>Replaces font markers if needed</li>
     *   <li>Zips the result back into the output file</li>
     * </ol>
     *
     * @param inputFile   the input Office or EPUB file
     * @param outputFile  the destination file to write the converted result
     * @param format      the file format (e.g., {@code docx}, {@code odt}, {@code epub})
     * @param converter   the {@link OpenCC} instance to use for conversion
     * @param punctuation whether to convert punctuation characters
     * @param keepFont    whether to preserve font tags/markup during conversion
     * @return a {@link Result} object indicating success or failure
     */
    public static Result convert(
            File inputFile,
            File outputFile,
            String format,
            OpenCC converter,
            boolean punctuation,
            boolean keepFont
    ) {
        Path tempDir = null;

        try {
            tempDir = Files.createTempDirectory(format + "_temp_");
            unzip(inputFile.toPath(), tempDir);

            List<Path> targets = getTargetXmlPaths(format, tempDir);
            if (targets == null || targets.isEmpty()) {
                return new Result(false, "❌ Unsupported or invalid format: " + format);
            }

            int convertedCount = 0;
            for (Path relativePath : targets) {
                Path fullPath = tempDir.resolve(relativePath);
                if (!Files.isRegularFile(fullPath)) continue;

//                String xml = Files.readString(fullPath);
                // Java 8: no Files.readString, use readAllBytes
                byte[] bytes = Files.readAllBytes(fullPath);
                String xml = new String(bytes, StandardCharsets.UTF_8);
                Map<String, String> fontMap = new HashMap<>();

                if (keepFont) {
                    Pattern pattern = getFontPattern(format);
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(xml);
                        int counter = 0;
                        StringBuffer sb = new StringBuffer();

                        while (matcher.find()) {
                            String marker = "__F_O_N_T_" + counter++ + "__";
                            fontMap.put(marker, matcher.group(2));
                            matcher.appendReplacement(sb, matcher.group(1) + marker + matcher.group(3));
                        }
                        matcher.appendTail(sb);
                        xml = sb.toString();
                    }
                }

                String converted = converter.convert(xml, punctuation);
                if (converted == null) {
                    // Fail fast with a clear error message
                    throw new RuntimeException("native error: " + converter.getLastError());
                }

                if (keepFont) {
                    for (Map.Entry<String, String> entry : fontMap.entrySet()) {
                        converted = converted.replace(entry.getKey(), entry.getValue());
                    }
                }

                // Files.writeString(fullPath, converted);
                // Java 8: no Files.writeString, use Files.write
                // ✅ portable: always UTF-8
                Files.write(fullPath, converted.getBytes(StandardCharsets.UTF_8));
                convertedCount++;
            }

            if (convertedCount == 0) {
                return new Result(false, "⚠️ No valid XML fragments found in format: " + format);
            }

            if (Files.exists(outputFile.toPath())) Files.delete(outputFile.toPath());

            if ("epub".equals(format)) {
                return createEpubZip(tempDir, outputFile.toPath());
            } else {
                zip(tempDir, outputFile.toPath());
            }

            return new Result(true, "✅ Successfully converted " + convertedCount + " fragment(s) in " + format + " document.");
        } catch (Exception ex) {
            return new Result(false, "❌ Conversion failed: " + ex.getMessage());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    /**
     * Extracts the contents of a ZIP file to a target directory.
     *
     * <p>This method creates the target directory if it does not exist.
     * It preserves the relative file structure and avoids ZIP slip attacks by verifying path normalization.
     *
     * @param zipFile   the path to the source ZIP file
     * @param targetDir the directory to extract files into
     * @throws IOException if an I/O error occurs during extraction
     */
    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) continue; // Prevent ZIP slip

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Creates a ZIP archive from a file or directory.
     *
     * <p>If the source path is a directory, it recursively adds all files under that path
     * using forward-slash (UNIX-style) entry names. If the source is a single file,
     * only that file is zipped.
     *
     * @param sourcePath  the path to a file or directory to archive
     * @param zipFilePath the destination ZIP file path
     * @throws IOException if an error occurs during zipping
     */
    public static void zip(Path sourcePath, Path zipFilePath) throws IOException {
        Path parentDir = zipFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (OutputStream fos = Files.newOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> paths = Files.walk(sourcePath)) {
                    paths
                            .filter(path -> !Files.isDirectory(path))
                            .forEach(path -> {
                                Path relativePath = sourcePath.relativize(path);
                                try {
                                    ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                                    zos.putNextEntry(zipEntry);
                                    Files.copy(path, zos);
                                    zos.closeEntry();
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Error zipping file " + path, e);
                                }
                            });
                }
            } else if (Files.isRegularFile(sourcePath)) {
                ZipEntry zipEntry = new ZipEntry(sourcePath.getFileName().toString());
                zos.putNextEntry(zipEntry);
                Files.copy(sourcePath, zos);
                zos.closeEntry();
            } else {
                throw new IllegalArgumentException("Source path must be a file or a directory: " + sourcePath);
            }
        }
    }

    /**
     * Creates a valid EPUB ZIP archive from the extracted source directory.
     *
     * <p>According to the EPUB specification, the {@code mimetype} file:
     * <ul>
     *   <li>Must be the first entry in the ZIP archive</li>
     *   <li>Must be stored uncompressed</li>
     * </ul>
     *
     * <p>This method first adds the {@code mimetype} file, then recursively adds the remaining files
     * in the directory. If the mimetype file is missing, an error {@link Result} is returned.
     *
     * @param sourceDir the base directory containing the EPUB structure
     * @param outputZip the path to write the resulting EPUB ZIP file
     * @return a {@link Result} indicating success or failure
     */
    private static Result createEpubZip(Path sourceDir, Path outputZip) {
        Path mimePath = sourceDir.resolve("mimetype");

        if (!Files.exists(mimePath)) {
            return new Result(false, "❌ 'mimetype' file is missing. EPUB requires this.");
        }

        try (FileOutputStream fos = new FileOutputStream(outputZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add mimetype file first, uncompressed
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = Files.readAllBytes(mimePath);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);

            CRC32 crc = new CRC32();
//            crc.update(mimeBytes);
            // Java 8 zip checksum
            crc.update(mimeBytes, 0, mimeBytes.length);
            mimeEntry.setCrc(crc.getValue());

            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            // Add the rest of the EPUB files
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                stream
                        .filter(p -> Files.isRegularFile(p) && !p.equals(mimePath))
                        .forEach(p -> {
                            try {
                                String entryName = sourceDir.relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to add file to zip: " + p.getFileName(), e);
                            }
                        });
            }

            return new Result(true, "✅ EPUB archive created successfully.");
        } catch (Exception e) {
            return new Result(false, "❌ Failed to create EPUB: " + e.getMessage());
        }
    }

    /**
     * Returns a list of XML or XHTML file paths inside a document structure that should be converted.
     *
     * <p>This method identifies the key text-containing XML components based on the input format.
     * For most formats, these are well-defined single paths. For formats like {@code pptx} and
     * {@code epub}, this method uses recursive file discovery.
     *
     * @param format  the file format (e.g., {@code docx}, {@code epub}, {@code odt})
     * @param baseDir the extracted root directory of the document
     * @return a list of relative {@link Path} entries to be converted, or {@code null} if unsupported
     */
    private static List<Path> getTargetXmlPaths(String format, Path baseDir) {
        switch (format) {
            case "docx":
                return Collections.singletonList(Paths.get("word/document.xml"));

            case "xlsx":
                return Collections.singletonList(Paths.get("xl/sharedStrings.xml"));

            case "pptx": {
                Path pptDir = baseDir.resolve("ppt");
                if (!Files.isDirectory(pptDir)) {
                    return Collections.emptyList();
                }

                Predicate<Path> isTarget = p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".xml") && (
                            name.startsWith("slide") ||
                                    name.contains("notesSlide") ||
                                    name.contains("slideMaster") ||
                                    name.contains("slideLayout") ||
                                    name.contains("comment")
                    );
                };

                try (Stream<Path> stream = Files.walk(pptDir)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(isTarget)
                            .map(baseDir::relativize)
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to collect pptx targets", e);
                    return Collections.emptyList();
                }
            }

            case "odt":
            case "ods":
            case "odp":
                return Collections.singletonList(Paths.get("content.xml"));

            case "epub": {
                Predicate<Path> isTarget = p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".xhtml") ||
                            name.endsWith(".html") ||
                            name.endsWith(".opf") ||
                            name.endsWith(".ncx");
                };

                try (Stream<Path> stream = Files.walk(baseDir)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(isTarget)
                            .map(baseDir::relativize)
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to collect epub targets", e);
                    return Collections.emptyList();
                }
            }

            default:
                return null;
        }
    }

    /**
     * Returns a regular expression {@link Pattern} for extracting font declarations
     * in the specified document format.
     *
     * <p>See {@link #FONT_PATTERNS} for the supported formats and attributes.</p>
     *
     * @param format the document format key (e.g., {@code docx}, {@code xlsx}, {@code pptx},
     *               {@code odt}, {@code ods}, {@code odp}, {@code epub})
     * @return the format-specific font extraction {@link Pattern}, or {@code null} if unsupported
     */
    private static Pattern getFontPattern(String format) {
        return FONT_PATTERNS.get(format);
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * <p>This method is typically used to clean up temporary extraction folders after document processing.
     * It walks the file tree in reverse order (files first, then directories) to ensure successful deletion.
     *
     * <p>Any deletion failures (e.g. due to file locks) are logged but do not halt execution.
     *
     * @param dirPath the root directory to delete
     */
    private static void deleteRecursive(Path dirPath) {
        if (dirPath == null || !Files.exists(dirPath)) return;

        try {
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths
                        .sorted(Comparator.reverseOrder()) // Delete children before parents
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to delete " + p, e);
                            }
                        });
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error walking directory for cleanup at " + dirPath, e);
        }
    }
}
