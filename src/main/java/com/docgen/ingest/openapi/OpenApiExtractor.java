package com.docgen.ingest.openapi;

import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.core.ParameterLocation;
import com.docgen.core.SchemaField;
import com.docgen.ingest.EndpointExtractor;
import com.docgen.ingest.ExtractionResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Lê arquivos de especificação OpenAPI (openapi.yml, swagger.json...) e os converte em Endpoints e Schemas.
public class OpenApiExtractor implements EndpointExtractor {

    private static final Set<String> FILE_NAMES = Set.of(
            "openapi.yml", "openapi.yaml", "openapi.json",
            "swagger.yml", "swagger.yaml", "swagger.json"
    );

    @Override
    public String framework() {
        return "OpenAPI";
    }

    // Uma especificação OpenAPI é o contrato declarado da API, então vence o código Java.
    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(Path file) {
        return FILE_NAMES.contains(file.getFileName().toString().toLowerCase());
    }

    @Override
    public ExtractionResult extract(Path file) throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(file.toAbsolutePath().toString(), null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new IllegalArgumentException("Falha ao ler " + file + ": " + result.getMessages());
        }
        if (openApi.getPaths() == null) {
            return new ExtractionResult(List.of(), toSchemas(openApi));
        }

        List<Endpoint> endpoints = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> operation : pathItem.readOperationsMap().entrySet()) {
                toHttpMethod(operation.getKey()).ifPresent(method ->
                        endpoints.add(toEndpoint(method, path, pathItem, operation.getValue())));
            }
        }
        return new ExtractionResult(endpoints, toSchemas(openApi));
    }

    private Optional<HttpMethod> toHttpMethod(PathItem.HttpMethod method) {
        try {
            return Optional.of(HttpMethod.valueOf(method.name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // métodos que não usamos (ex.: TRACE)
        }
    }

    private Endpoint toEndpoint(HttpMethod method, String path, PathItem pathItem, Operation operation) {
        List<ApiParameter> parameters = new ArrayList<>();
        parameters.addAll(toApiParameters(pathItem.getParameters()));
        parameters.addAll(toApiParameters(operation.getParameters()));
        toBodyParameter(operation.getRequestBody()).ifPresent(parameters::add);

        String group = operation.getTags() == null || operation.getTags().isEmpty()
                ? "default"
                : operation.getTags().get(0);
        String handler = operation.getOperationId() != null
                ? operation.getOperationId()
                : method.name().toLowerCase() + path;

        return new Endpoint(method, path, group, handler, parameters, returnType(operation), describe(operation));
    }

    private List<ApiParameter> toApiParameters(List<Parameter> parameters) {
        List<ApiParameter> result = new ArrayList<>();
        if (parameters == null) {
            return result;
        }
        for (Parameter parameter : parameters) {
            ParameterLocation location = toLocation(parameter.getIn());
            if (location == null) {
                continue; // cookies ainda não são suportados
            }
            boolean required = location == ParameterLocation.PATH || Boolean.TRUE.equals(parameter.getRequired());
            result.add(new ApiParameter(
                    parameter.getName(),
                    typeOf(parameter.getSchema()),
                    location,
                    required,
                    parameter.getDescription()
            ));
        }
        return result;
    }

    private ParameterLocation toLocation(String in) {
        if (in == null) {
            return null;
        }
        return switch (in) {
            case "path" -> ParameterLocation.PATH;
            case "query" -> ParameterLocation.QUERY;
            case "header" -> ParameterLocation.HEADER;
            default -> null;
        };
    }

    private Optional<ApiParameter> toBodyParameter(RequestBody body) {
        if (body == null) {
            return Optional.empty();
        }
        return Optional.of(new ApiParameter(
                "body",
                typeOf(firstSchema(body.getContent())),
                ParameterLocation.BODY,
                Boolean.TRUE.equals(body.getRequired()),
                body.getDescription()
        ));
    }

    private String returnType(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return "void";
        }
        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            if (entry.getKey().startsWith("2")) {
                Schema<?> schema = firstSchema(entry.getValue().getContent());
                return schema == null ? "void" : typeOf(schema);
            }
        }
        return "void";
    }

    private Schema<?> firstSchema(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return content.values().iterator().next().getSchema();
    }

    // Lê a seção components/schemas e converte cada objeto numa ficha Schema.
    // O nosso Schema é escrito com o caminho completo para não confundir com o da biblioteca.
    private List<com.docgen.core.Schema> toSchemas(OpenAPI openApi) {
        List<com.docgen.core.Schema> result = new ArrayList<>();
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return result;
        }
        Map<String, Schema> components = openApi.getComponents().getSchemas();
        for (Map.Entry<String, Schema> entry : components.entrySet()) {
            result.add(new com.docgen.core.Schema(entry.getKey(), toFields(entry.getValue(), components)));
        }
        return result;
    }

    private List<SchemaField> toFields(Schema<?> schema, Map<String, Schema> components) {
        Map<String, SchemaField> fields = new LinkedHashMap<>();
        collectFields(schema, components, fields, new HashSet<>());
        return new ArrayList<>(fields.values());
    }

    // Reúne os campos de um objeto, seguindo referências ($ref) e composições (allOf).
    private void collectFields(Schema<?> schema,
                               Map<String, Schema> components,
                               Map<String, SchemaField> fields,
                               Set<String> visited) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            String name = refName(schema.get$ref());
            if (visited.add(name)) { // o controle evita loop em objetos que se referenciam
                collectFields(components.get(name), components, fields, visited);
            }
            return;
        }
        if (schema.getAllOf() != null) {
            for (Schema<?> part : schema.getAllOf()) {
                collectFields(part, components, fields, visited);
            }
        }
        if (schema.getProperties() != null) {
            List<String> required = schema.getRequired() == null ? List.of() : schema.getRequired();
            for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                fields.put(property.getKey(), new SchemaField(
                        property.getKey(),
                        typeOf(property.getValue()),
                        required.contains(property.getKey())
                ));
            }
        }
    }

    private String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    // Escreve o tipo em texto simples: BookDto, array<BookDto>, integer(int64)...
    private String typeOf(Schema<?> schema) {
        if (schema == null) {
            return "object";
        }
        if (schema.get$ref() != null) {
            return refName(schema.get$ref());
        }
        if (schema instanceof ArraySchema array) {
            return "array<" + typeOf(array.getItems()) + ">";
        }
        String type = schema.getType() == null ? "object" : schema.getType();
        return schema.getFormat() == null ? type : type + "(" + schema.getFormat() + ")";
    }

    private String describe(Operation operation) {
        if (operation.getSummary() != null && !operation.getSummary().isBlank()) {
            return operation.getSummary();
        }
        return operation.getDescription();
    }
}

/*

extract()
  |
  |-- OpenAPIV3Parser.readLocation()
  |     A biblioteca swagger-parser lê o arquivo e já resolve as referências
  |     internas ($ref), devolvendo a especificação inteira organizada.
  |
  |-- para cada caminho (path) da especificação
  |     |
  |     |-- para cada operação (GET, POST...) desse caminho
  |           |
  |           |-- toHttpMethod()
  |           |     Converte para o nosso HttpMethod (ignora os que não usamos).
  |           |
  |           |-- toApiParameters()
  |           |     Lê os parâmetros de path, query e header.
  |           |
  |           |-- toBodyParameter()
  |           |     Lê o corpo do pedido (requestBody) como parâmetro BODY.
  |           |
  |           |-- returnType()
  |           |     Descobre o tipo devolvido na resposta de sucesso (2xx).
  |           |
  |           |-- describe()
  |           |     Usa o resumo (summary) da operação como descrição.
  |           |
  |           |-- new Endpoint(...)
  |                 Monta a ficha. A tag da operação vira o grupo (controller)
  |                 e o operationId vira o nome do método (handler).
  |
  |-- toSchemas()
  |     Lê a seção components/schemas e monta uma ficha Schema por objeto.
  |     |
  |     |-- toFields()
  |           Lê os campos (properties) de cada objeto, com o tipo e a
  |           obrigatoriedade (lista required) de cada um.
  |
  |-- return new ExtractionResult(endpoints, schemas)
        Devolve endpoints e schemas juntos para o ProjectScanner.

 */