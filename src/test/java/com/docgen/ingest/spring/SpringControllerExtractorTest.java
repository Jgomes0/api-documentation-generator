package com.docgen.ingest.spring;
import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.core.ParameterLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SpringControllerExtractorTest {

    private List<Endpoint> endpoints;

    @BeforeEach
    void setUp() throws Exception {
        Path fixture = Path.of(getClass()
                .getResource("/fixtures/library/controller/BookController.java")
                .toURI());
        endpoints = new SpringControllerExtractor().extract(fixture);
    }

    private Endpoint byHandler(String handlerName) {
        return endpoints.stream()
                .filter(e -> e.handlerName().equals(handlerName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void shouldExtractAllEndpointsWithFullPaths() {
        assertThat(endpoints)
                .extracting(Endpoint::method, Endpoint::path, Endpoint::handlerName)
                .containsExactly(
                        tuple(HttpMethod.GET, "/api/books", "list"),
                        tuple(HttpMethod.GET, "/api/books/{id}", "findById"),
                        tuple(HttpMethod.POST, "/api/books", "create"),
                        tuple(HttpMethod.DELETE, "/api/books/{id}", "delete")
                );
    }

    @Test
    void shouldExtractReturnTypes() {
        assertThat(byHandler("list").returnType()).isEqualTo("List<BookDto>");
        assertThat(byHandler("findById").returnType()).isEqualTo("BookDto");
        assertThat(byHandler("delete").returnType()).isEqualTo("void");
    }

    @Test
    void shouldExtractOptionalQueryParameter() {
        ApiParameter author = byHandler("list").parameters().get(0);

        assertThat(author.name()).isEqualTo("author");
        assertThat(author.type()).isEqualTo("String");
        assertThat(author.location()).isEqualTo(ParameterLocation.QUERY);
        assertThat(author.required()).isFalse();
    }

    @Test
    void shouldExtractPathVariableAndBody() {
        ApiParameter id = byHandler("findById").parameters().get(0);
        assertThat(id.location()).isEqualTo(ParameterLocation.PATH);
        assertThat(id.required()).isTrue();

        ApiParameter body = byHandler("create").parameters().get(0);
        assertThat(body.location()).isEqualTo(ParameterLocation.BODY);
        assertThat(body.type()).isEqualTo("CreateBookRequest");
    }
    @Test
    void shouldOnlySupportJavaFiles() {
        SpringControllerExtractor extractor = new SpringControllerExtractor();

        assertThat(extractor.supports(Path.of("BookController.java"))).isTrue();
        assertThat(extractor.supports(Path.of("users.py"))).isFalse();
        assertThat(extractor.framework()).isEqualTo("Spring");
    }
}