package openccjavacli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void convertMissingConfigReturnsUsage() {
        CommandResult result = execute("convert");
        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        // Picocli default message
        assertTrue(result.stderr.contains("Missing required option"), result.stderr);
        // Option name should appear
        assertTrue(result.stderr.contains("--config"), result.stderr);
        // Keep this
        assertFalse(result.stderr.contains("--list-configs"), result.stderr);
    }

    @Test
    void convertInvalidConfigReturnsUsageAndListsSupportedConfigs() {
        CommandResult result = execute("convert", "-c", "not-a-config");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        assertTrue(result.stderr.contains("Invalid config: not-a-config"), result.stderr);
        assertTrue(result.stderr.contains("Supported configs:"), result.stderr);
        assertTrue(result.stderr.contains("t2s"), result.stderr);
        assertTrue(result.stderr.contains("t2hkp"), result.stderr);
        assertTrue(result.stderr.contains("hk2tp"), result.stderr);
    }

    @Test
    void pdfInvalidConfigReturnsUsage() {
        Path samplePdf = Paths.get("sample.pdf").toAbsolutePath().normalize();

        CommandResult result = execute("pdf", "-i", samplePdf.toString(), "-c", "not-a-config");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode);
        assertTrue(result.stderr.contains("Missing or invalid config: not-a-config"), result.stderr);
        assertTrue(result.stderr.contains("Supported configs:"), result.stderr);
    }

    @Test
    void pdfHelpListsSupportedConfigs() {
        CommandResult result = execute("pdf", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode, result.stderr);
        assertTrue(result.stdout.contains("t2hkp"), result.stdout);
        assertTrue(result.stdout.contains("hk2tp"), result.stdout);
    }

    @Test
    void officeHelpListsSupportedConfigs() {
        CommandResult result = execute("office", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode, result.stderr);
        assertTrue(result.stdout.contains("t2hkp"), result.stdout);
        assertTrue(result.stdout.contains("hk2tp"), result.stdout);
    }

    @Test
    void convertSupportsDirectHongKongPhraseConfigs() throws Exception {
        Path input = tempDir.resolve("traditional.txt");
        Path hongKong = tempDir.resolve("hong-kong.txt");
        Files.write(input, "光標".getBytes(StandardCharsets.UTF_8));

        CommandResult forward = execute("convert", "-c", "t2hkp", "-i", input.toString(), "-o", hongKong.toString());
        assertEquals(CommandLine.ExitCode.OK, forward.exitCode, forward.stderr);
        assertEquals("游標", new String(Files.readAllBytes(hongKong), StandardCharsets.UTF_8));

        Path traditional = tempDir.resolve("traditional-roundtrip.txt");
        CommandResult reverse = execute(
                "convert", "-c", "hk2tp", "-i", hongKong.toString(), "-o", traditional.toString()
        );
        assertEquals(CommandLine.ExitCode.OK, reverse.exitCode, reverse.stderr);
        assertEquals("光標", new String(Files.readAllBytes(traditional), StandardCharsets.UTF_8));
    }

    @Test
    void convertPreservesMultipleCustomDictionaryOptionOrder() throws Exception {
        Path input = tempDir.resolve("input.txt");
        Path firstDictionary = tempDir.resolve("first.txt");
        Path secondDictionary = tempDir.resolve("second.txt");
        Path output = tempDir.resolve("output.txt");

        Files.write(input, "甲乙".getBytes(StandardCharsets.UTF_8));
        Files.write(firstDictionary, "甲\t一\n乙\t二\n".getBytes(StandardCharsets.UTF_8));
        Files.write(secondDictionary, "甲\t三\n".getBytes(StandardCharsets.UTF_8));

        CommandResult result = execute(
                "convert", "-c", "s2t", "-i", input.toString(), "-o", output.toString(),
                "-D", "STPhrases:override:" + firstDictionary,
                "-D", "STPhrases:append:" + secondDictionary
        );

        assertEquals(CommandLine.ExitCode.OK, result.exitCode, result.stderr);
        assertEquals("三二", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    private static CommandResult execute(String... args) {
        try {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            CommandLine commandLine = new CommandLine(new Main())
                    .setOut(new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true))
                    .setErr(new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8), true));

            int exitCode = commandLine.execute(args);
            return new CommandResult(
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8.name()),
                    stderr.toString(StandardCharsets.UTF_8.name())
            );
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 should always be supported", e);
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
