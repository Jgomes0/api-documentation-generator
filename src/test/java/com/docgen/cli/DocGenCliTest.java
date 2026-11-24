package com.docgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocGenCliTest {

    @Test
    void shouldGenerateMarkdownFile(@TempDir Path out) throws Exception {
        Path source = Path.of(getClass().getResource("/fixtures/library").toURI());

        int exitCode = new CommandLine(new DocGenCli()).execute(
                "--source", source.toString(),
                "--out", out.toString(),
                "--name", "Library API");

        assertThat(exitCode).isZero();
        String markdown = Files.readString(out.resolve("api-reference.md"));
        assertThat(markdown)
                .contains("# Library API")
                .contains("### GET /api/books/{id}")
                .contains("### POST /api/books");
    }

    @Test
    void shouldFailWhenSourceFolderDoesNotExist(@TempDir Path out) {
        int exitCode = new CommandLine(new DocGenCli()).execute(
                "--source", out.resolve("nao-existe").toString(),
                "--out", out.toString());

        assertThat(exitCode).isEqualTo(1);
    }
}