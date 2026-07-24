package com.vaultik.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VaultikCliTest {
    @TempDir
    Path directory;

    @Test
    void setThenGetPersistsAcrossCliInvocations() throws Exception {
        ByteArrayOutputStream setOutput = new ByteArrayOutputStream();
        int setCode = VaultikCli.run(new String[]{
                "set", "project", "vaultik",
                "--data-dir", directory.toString()
        }, new PrintStream(setOutput), System.err);

        ByteArrayOutputStream getOutput = new ByteArrayOutputStream();
        int getCode = VaultikCli.run(new String[]{
                "get", "project",
                "--data-dir", directory.toString()
        }, new PrintStream(getOutput), System.err);

        assertThat(setCode).isZero();
        assertThat(getCode).isZero();
        assertThat(getOutput.toString()).contains("project = vaultik");
    }

    @Test
    void benchmarkReportCommandWritesReport() throws Exception {
        Path report = directory.resolve("report.md");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int code = VaultikCli.run(new String[]{
                "benchmark-report",
                "--operations", "20",
                "--threads", "1",
                "--output", report.toString()
        }, new PrintStream(output), System.err);

        assertThat(code).isZero();
        assertThat(output.toString()).contains("Generated");
        assertThat(report).exists();
    }

    @Test
    void walCommandPrintsWriteAheadLogRecords() throws Exception {
        VaultikCli.run(new String[]{
                "set", "user:1", "shlok",
                "--data-dir", directory.toString()
        }, new PrintStream(new ByteArrayOutputStream()), System.err);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = VaultikCli.run(new String[]{
                "wal",
                "--data-dir", directory.toString()
        }, new PrintStream(output), System.err);

        assertThat(code).isZero();
        assertThat(output.toString())
                .contains("Records: 1")
                .contains("SET")
                .contains("user:1")
                .contains("shlok");
    }

    @Test
    void engineReportCommandWritesMarkdownReport() throws Exception {
        VaultikCli.run(new String[]{
                "set", "language", "java",
                "--data-dir", directory.toString()
        }, new PrintStream(new ByteArrayOutputStream()), System.err);
        Path report = directory.resolve("engine-report.md");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int code = VaultikCli.run(new String[]{
                "engine-report",
                "--data-dir", directory.toString(),
                "--output", report.toString()
        }, new PrintStream(output), System.err);

        assertThat(code).isZero();
        assertThat(output.toString()).contains("Generated");
        assertThat(Files.readString(report))
                .contains("# Vaultik Engine Report")
                .contains("## WAL Tail")
                .contains("language");
    }

    @Test
    void shellExecutesCommandsUntilExit() throws Exception {
        String commands = """
                set city pune
                get city
                stats
                exit
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int code = VaultikCli.run(new String[]{
                        "shell",
                        "--data-dir", directory.toString()
                },
                new PrintStream(output),
                System.err,
                new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)));

        assertThat(code).isZero();
        assertThat(output.toString())
                .contains("Vaultik shell")
                .contains("SET city = pune")
                .contains("city = pune")
                .contains("size=1");
    }
}
