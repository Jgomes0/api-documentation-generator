package com.docgen.ingest.spring;
import com.docgen.core.*;
import com.docgen.ingest.ExtractionResult;
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
        endpoints = new SpringControllerExtractor().extract(fixture).endpoints();
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
        assertThat(byHandler("list").returnType()).isEqualTo("array<BookDto>");
        assertThat(byHandler("findById").returnType()).isEqualTo("BookDto");
        assertThat(byHandler("delete").returnType()).isEqualTo("void");
    }

    @Test
    void shouldExtractOptionalQueryParameter() {
        ApiParameter author = byHandler("list").parameters().get(0);

        assertThat(author.name()).isEqualTo("author");
        assertThat(author.type()).isEqualTo("string");
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
    @Test
    void shouldExtractRecordSchemaWithValidationAndPrimitiveRequired() throws Exception {
        Path file = Path.of(getClass()
                .getResource("/fixtures/library/dto/CreateBookRequest.java").toURI());

        ExtractionResult result = new SpringControllerExtractor().extract(file);

        assertThat(result.endpoints()).isEmpty();
        assertThat(result.schemas()).hasSize(1);
        Schema schema = result.schemas().get(0);
        assertThat(schema.name()).isEqualTo("CreateBookRequest");
        assertThat(schema.fields())
                .extracting(SchemaField::name, SchemaField::type, SchemaField::required)
                .containsExactly(
                        tuple("title", "string", true),
                        tuple("author", "string", true),
                        tuple("year", "integer(int32)", true)
                );
    }

    @Test
    void shouldTreatNonPrimitiveFieldsWithoutValidationAsOptional() throws Exception {
        Path file = Path.of(getClass()
                .getResource("/fixtures/library/dto/BookDto.java").toURI());

        Schema schema = new SpringControllerExtractor().extract(file).schemas().get(0);

        assertThat(schema.fields())
                .extracting(SchemaField::name, SchemaField::required)
                .containsExactly(
                        tuple("id", false),
                        tuple("title", false),
                        tuple("author", false),
                        tuple("year", true)
                );
    }

    @Test
    void shouldNotCreateSchemaForControllers() throws Exception {
        Path file = Path.of(getClass()
                .getResource("/fixtures/library/controller/BookController.java").toURI());

        assertThat(new SpringControllerExtractor().extract(file).schemas()).isEmpty();
    }
}