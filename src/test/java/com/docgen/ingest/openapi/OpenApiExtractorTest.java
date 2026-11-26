package com.docgen.ingest.openapi;

import com.docgen.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OpenApiExtractorTest {

    private final OpenApiExtractor extractor = new OpenApiExtractor();
    private List<Endpoint> endpoints;

    @BeforeEach
    void setUp() throws Exception {
        Path fixture = Path.of(getClass().getResource("/fixtures/openapi/openapi.yml").toURI());
        endpoints = extractor.extract(fixture).endpoints();
    }

    private Endpoint byHandler(String handlerName) {
        return endpoints.stream()
                .filter(e -> e.handlerName().equals(handlerName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void shouldExtractAllEndpoints() {
        assertThat(endpoints)
                .extracting(Endpoint::method, Endpoint::path, Endpoint::handlerName)
                .containsExactlyInAnyOrder(
                        tuple(HttpMethod.GET, "/api/books", "listBooks"),
                        tuple(HttpMethod.POST, "/api/books", "createBook"),
                        tuple(HttpMethod.GET, "/api/books/{id}", "getBook"),
                        tuple(HttpMethod.DELETE, "/api/books/{id}", "deleteBook")
                );
    }

    @Test
    void shouldUseFirstTagAsGroupAndSummaryAsDescription() {
        Endpoint endpoint = byHandler("getBook");

        assertThat(endpoint.controller()).isEqualTo("Books");
        assertThat(endpoint.description()).isEqualTo("Busca um livro pelo id");
    }

    @Test
    void shouldExtractReturnTypes() {
        assertThat(byHandler("listBooks").returnType()).isEqualTo("array<BookDto>");
        assertThat(byHandler("getBook").returnType()).isEqualTo("BookDto");
        assertThat(byHandler("deleteBook").returnType()).isEqualTo("void");
    }

    @Test
    void shouldExtractQueryPathAndBodyParameters() {
        ApiParameter author = byHandler("listBooks").parameters().get(0);
        assertThat(author.name()).isEqualTo("author");
        assertThat(author.location()).isEqualTo(ParameterLocation.QUERY);
        assertThat(author.required()).isFalse();

        ApiParameter id = byHandler("getBook").parameters().get(0);
        assertThat(id.location()).isEqualTo(ParameterLocation.PATH);
        assertThat(id.required()).isTrue();
        assertThat(id.type()).isEqualTo("integer(int64)");

        ApiParameter body = byHandler("createBook").parameters().get(0);
        assertThat(body.location()).isEqualTo(ParameterLocation.BODY);
        assertThat(body.type()).isEqualTo("CreateBookRequest");
        assertThat(body.required()).isTrue();
    }

    @Test
    void shouldOnlySupportOpenApiFiles() {
        assertThat(extractor.supports(Path.of("openapi.yml"))).isTrue();
        assertThat(extractor.supports(Path.of("swagger.json"))).isTrue();
        assertThat(extractor.supports(Path.of("BookController.java"))).isFalse();
        assertThat(extractor.framework()).isEqualTo("OpenAPI");
    }

    @Test
    void shouldMergeFieldsFromAllOf() throws Exception {
        Path fixture = Path.of(getClass().getResource("/fixtures/openapi-allof/openapi.yml").toURI());

        Schema person = extractor.extract(fixture).schemas().stream()
                .filter(s -> s.name().equals("Person"))
                .findFirst()
                .orElseThrow();

        assertThat(person.fields())
                .extracting(SchemaField::name, SchemaField::type, SchemaField::required)
                .containsExactlyInAnyOrder(
                        tuple("name", "string", true),
                        tuple("id", "integer(int32)", true),
                        tuple("nickname", "string", false)
                );

    }
}