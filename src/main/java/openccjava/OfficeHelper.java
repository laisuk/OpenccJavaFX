package openccjava;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class OfficeHelper {
    public record ConversionResult(boolean success, String message) {
    }

    public static final Set<String> OFFICE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".docx", ".xlsx", ".pptx",  // Office Open XML
            ".odt", ".ods", ".odp",     // OpenDocument formats
            ".epub"                     // E-book archive format
    ));

    public static ConversionResult convertOfficeDoc(
            String inputPath,
            String outputPath,
            String format,
            OpenCC openccHelper,
            boolean punctuation,
            boolean keepFont
    ) {
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + format + "_temp_" + UUID.randomUUID();
        File tempDirectory = new File(tempDir);

        try {
            unzip(new File(inputPath), tempDirectory);

            List<String> targetXmlPaths;

            try {
                targetXmlPaths = switch (format) {
                    case "docx" -> List.of("word/document.xml");
                    case "xlsx" -> List.of("xl/sharedStrings.xml");
                    case "pptx" -> {
                        File pptFolder = new File(tempDirectory, "ppt");
                        if (!pptFolder.exists()) yield List.of();
                        try (Stream<Path> stream = Files.walk(pptFolder.toPath())) {
                            yield stream
                                    .filter(p -> p.toString().endsWith(".xml"))
                                    .map(p -> tempDirectory.toPath().relativize(p).toString())
                                    .filter(p -> p.contains("slide") || p.contains("notesSlide")
                                            || p.contains("slideMaster") || p.contains("slideLayout")
                                            || p.contains("comment"))
                                    .collect(Collectors.toList());
                        }
                    }
                    case "odt", "ods", "odp" -> List.of("content.xml");
                    case "epub" -> {
                        try (Stream<Path> stream = Files.walk(Path.of(tempDir))) {
                            yield stream
                                    .filter(Files::isRegularFile)
                                    .map(p -> tempDirectory.toPath().relativize(p).toString())
                                    .filter(p -> p.endsWith(".xhtml") || p.endsWith(".opf") || p.endsWith(".ncx"))
                                    .collect(Collectors.toList());
                        }
                    }
                    default -> null;
                };
            } catch (IOException e) {
                return new ConversionResult(false, "❌ Failed to scan files for format '" + format + "': " + e.getMessage());
            }

            if (targetXmlPaths == null || targetXmlPaths.isEmpty()) {
                return new ConversionResult(false, "❌ Unsupported or invalid format: " + format);
            }

            int convertedCount = 0;

            for (String relativePath : targetXmlPaths) {
                File fullPath = new File(tempDirectory, relativePath);
                if (!fullPath.exists()) continue;

                String xmlContent = Files.readString(fullPath.toPath(), StandardCharsets.UTF_8);
                Map<String, String> fontMap = new HashMap<>();

                if (keepFont) {
                    String pattern = switch (format) {
                        case "docx" -> "(w:eastAsia=\"|w:ascii=\"|w:hAnsi=\"|w:cs=\")(.*?)(\")";
                        case "xlsx" -> "(val=\")(.*?)(\")";
                        case "pptx" -> "(typeface=\")(.*?)(\")";
                        case "odt", "ods", "odp" ->
                                "((?:style:font-name(?:-asian|-complex)?|svg:font-family|style:name)=[\"'])([^\"']+)([\"'])";
                        case "epub" -> "(font-family\\s*:\\s*)([^;\"']+)";
                        default -> null;
                    };

                    Matcher matcher = Pattern.compile(pattern).matcher(xmlContent);
                    StringBuilder sb = new StringBuilder();
                    int fontCounter = 0;
                    while (matcher.find()) {
                        String originalFont = matcher.group(2);
                        String marker = "__F_O_N_T_" + fontCounter++ + "__";
                        fontMap.put(marker, originalFont);
                        String replacement;
                        if ("epub".equals(format)) {
                            replacement = matcher.group(1) + marker;
                        } else {
                            replacement = matcher.group(1) + marker + matcher.group(3);
                        }
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    }
                    matcher.appendTail(sb);
                    xmlContent = sb.toString();
                }

                String convertedXml = openccHelper.convert(xmlContent, punctuation);

                if (keepFont) {
                    for (Map.Entry<String, String> entry : fontMap.entrySet()) {
                        convertedXml = convertedXml.replace(entry.getKey(), entry.getValue());
                    }
                }

                Files.writeString(fullPath.toPath(), convertedXml, StandardCharsets.UTF_8);
                convertedCount++;
            }

            if (convertedCount == 0) {
                return new ConversionResult(false, "⚠️ No valid XML fragments were found for conversion.");
            }

            Path outputFullPath = Path.of(outputPath);
            if (Files.exists(outputFullPath)) Files.delete(outputFullPath);
            if ("epub".equals(format)) {
                return createEpubZipWithSpec(tempDir, outputPath);
            } else {
                zip(tempDirectory.toPath(), outputFullPath);
            }

            return new ConversionResult(true, "✅ Successfully converted " + convertedCount + " fragment(s).");

        } catch (Exception ex) {
            return new ConversionResult(false, "❌ Conversion failed: " + ex.getMessage());
        } finally {
            deleteRecursive(tempDirectory);
        }
    }

    private static ConversionResult createEpubZipWithSpec(String sourceDir, String outputPath) {
        Path mimePath = Path.of(sourceDir, "mimetype");

        try (FileOutputStream fos = new FileOutputStream(outputPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            if (!Files.exists(mimePath)) {
                return new ConversionResult(false, "❌ 'mimetype' file is missing. EPUB requires this.");
            }

            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = Files.readAllBytes(mimePath);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            try (Stream<Path> stream = Files.walk(Path.of(sourceDir))) {
                stream
                        .filter(p -> Files.isRegularFile(p) && !p.equals(mimePath))
                        .forEach(p -> {
                            try {
                                String entryName = Path.of(sourceDir).relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                // You could log the error here if needed
                            }
                        });
            } catch (IOException e) {
                return new ConversionResult(false, "❌ Failed to add EPUB files: " + e.getMessage());
            }


            return new ConversionResult(true, "✅ EPUB archive created successfully.");
        } catch (Exception e) {
            return new ConversionResult(false, "❌ Failed to create EPUB: " + e.getMessage());
        }
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    if (!newFile.mkdirs() && !newFile.exists()) {
                        throw new IOException("Failed to create directory: " + newFile.getAbsolutePath());
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.mkdirs() && !parent.exists()) {
                        throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        zis.transferTo(fos);
                    }
                }
            }
        }
    }


    private static void zip(Path sourceDir, Path outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                stream
                        .filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                String entryName = sourceDir.relativize(p).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                // Log or handle error if needed
                            }
                        });
            } catch (IOException e) {
                throw new IOException("Failed to zip files from: " + sourceDir, e);
            }

        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        if (!file.delete()) {
            System.err.println("⚠️ Failed to delete: " + file.getAbsolutePath());
        }
    }

}
