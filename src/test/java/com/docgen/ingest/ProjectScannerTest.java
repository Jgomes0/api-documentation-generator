package com.docgen.ingest;
import com.docgen.core.ApiSpec;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectScannerTest {

    @Test
    void shouldScanWholeFixtureProject() throws Exception {
        Path root = Path.of(getClass().getResource("/fixtures/library").toURI());

        ApiSpec spec = new ProjectScanner().scan(root, "Library API");

        assertThat(spec.name()).isEqualTo("Library API");
        assertThat(spec.endpoints()).hasSize(4);
    }

    @Test
    void shouldUseEveryExtractorThatSupportsAFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.fake"), "conteudo");
        Files.writeString(dir.resolve("b.txt"), "conteudo");

        ApiSpec spec = new ProjectScanner(List.of(new FakeExtractor())).scan(dir, "Fake API");

        assertThat(spec.endpoints()).hasSize(1);
        assertThat(spec.endpoints().get(0).path()).isEqualTo("/fake");
    }

    @Test
    void shouldIgnoreBuildFolders(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("build"));
        Files.writeString(dir.resolve("build").resolve("generated.fake"), "conteudo");

        ApiSpec spec = new ProjectScanner(List.of(new FakeExtractor())).scan(dir, "Fake API");

        assertThat(spec.endpoints()).isEmpty();
    }

    // Leitor de mentira, só para provar que o scanner funciona com qualquer leitor.
    private static class FakeExtractor implements EndpointExtractor {

        @Override
        public String framework() {
            return "Fake";
        }

        @Override
        public boolean supports(Path file) {
            return file.getFileName().toString().endsWith(".fake");
        }

        @Override
        public List<Endpoint> extract(Path file) {
            return List.of(new Endpoint(
                    HttpMethod.GET, "/fake", "FakeController", "fake", List.of(), "void", null));
        }
    }
}