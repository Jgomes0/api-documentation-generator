package com.docgen.render;
import com.docgen.core.ApiParameter;
import com.docgen.core.ApiSpec;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.core.ParameterLocation;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void shouldRenderEndpointWithParametersTable() {
        Endpoint endpoint = new Endpoint(
                HttpMethod.GET,
                "/api/books/{id}",
                "BookController",
                "findById",
                List.of(new ApiParameter("id", "Long", ParameterLocation.PATH, true, null)),
                "BookDto",
                null
        );
        ApiSpec spec = new ApiSpec("Library API", List.of(endpoint), List.of());

        String markdown = renderer.render(spec);

        assertThat(markdown)
                .contains("# Library API")
                .contains("### GET /api/books/{id}")
                .contains("`BookController#findById`")
                .contains("| `id` | `Long` | PATH | sim |")
                .contains("**Retorno:** `BookDto`");
    }

    @Test
    void shouldRenderDescriptionWhenPresent() {
        Endpoint endpoint = new Endpoint(
                HttpMethod.DELETE, "/api/books/{id}", "BookController", "delete",
                List.of(), "void", "Remove um livro pelo id."
        );

        String markdown = renderer.render(new ApiSpec("Library API", List.of(endpoint), List.of()));

        assertThat(markdown).contains("Remove um livro pelo id.");
    }

    @Test
    void shouldSkipParametersTableWhenThereAreNone() {
        Endpoint endpoint = new Endpoint(
                HttpMethod.GET, "/api/books", "BookController", "list",
                List.of(), "List<BookDto>", null
        );

        String markdown = renderer.render(new ApiSpec("Library API", List.of(endpoint), List.of()));

        assertThat(markdown).doesNotContain("| Parâmetro |");
    }

    @Test
    void shouldWarnWhenThereAreNoEndpoints() {
        String markdown = renderer.render(new ApiSpec("Empty API", List.of(), List.of()));

        assertThat(markdown).contains("_Nenhum endpoint encontrado._");
    }
}