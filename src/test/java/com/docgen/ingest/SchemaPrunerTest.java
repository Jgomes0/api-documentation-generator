package com.docgen.ingest;

import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.core.ParameterLocation;
import com.docgen.core.Schema;
import com.docgen.core.SchemaField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaPrunerTest {

    private Endpoint endpoint(String returnType, ApiParameter... parameters) {
        return new Endpoint(HttpMethod.GET, "/x", "Controller", "handler",
                List.of(parameters), returnType, null);
    }

    private Schema schema(String name, SchemaField... fields) {
        return new Schema(name, List.of(fields));
    }

    @Test
    void shouldKeepOnlySchemasUsedByEndpoints() {
        Schema book = schema("BookDto", new SchemaField("id", "integer(int64)", false));
        Schema unused = schema("Unused", new SchemaField("x", "string", false));

        List<Schema> result = SchemaPruner.prune(List.of(endpoint("array<BookDto>")), List.of(book, unused));

        assertThat(result).extracting(Schema::name).containsExactly("BookDto");
    }

    @Test
    void shouldFollowReferencesBetweenSchemas() {
        Schema owner = schema("Owner", new SchemaField("pets", "array<Pet>", false));
        Schema pet = schema("Pet", new SchemaField("type", "PetType", false));
        Schema petType = schema("PetType", new SchemaField("name", "string", true));
        Schema other = schema("Other", new SchemaField("x", "string", false));

        List<Schema> result = SchemaPruner.prune(List.of(endpoint("Owner")), List.of(owner, pet, petType, other));

        assertThat(result).extracting(Schema::name).containsExactlyInAnyOrder("Owner", "Pet", "PetType");
    }

    @Test
    void shouldUseParameterTypesToo() {
        ApiParameter body = new ApiParameter("body", "CreateBookRequest", ParameterLocation.BODY, true, null);
        Schema request = schema("CreateBookRequest", new SchemaField("title", "string", true));

        List<Schema> result = SchemaPruner.prune(List.of(endpoint("void", body)), List.of(request));

        assertThat(result).extracting(Schema::name).containsExactly("CreateBookRequest");
    }

    @Test
    void shouldKeepVersionWithMostFieldsWhenNamesRepeat() {
        Schema small = schema("Owner", new SchemaField("id", "integer(int32)", false));
        Schema big = schema("Owner",
                new SchemaField("id", "integer(int32)", false),
                new SchemaField("firstName", "string", true));

        List<Schema> result = SchemaPruner.prune(List.of(endpoint("Owner")), List.of(small, big));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fields()).hasSize(2);
    }

    @Test
    void shouldNotLoopWhenSchemasReferenceEachOther() {
        Schema owner = schema("Owner", new SchemaField("pets", "array<Pet>", false));
        Schema pet = schema("Pet", new SchemaField("owner", "Owner", false));

        List<Schema> result = SchemaPruner.prune(List.of(endpoint("Owner")), List.of(owner, pet));

        assertThat(result).extracting(Schema::name).containsExactlyInAnyOrder("Owner", "Pet");
    }
}