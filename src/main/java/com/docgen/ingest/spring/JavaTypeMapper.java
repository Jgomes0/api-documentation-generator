package com.docgen.ingest.spring;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.Map;
import java.util.Set;

// Traduz tipos Java (String, Long, List<BookDto>...) para o formato neutro usado
// pelo projeto (string, integer(int64), array<BookDto>...), igual ao do leitor de OpenAPI.
final class JavaTypeMapper {

    private static final Map<String, String> SIMPLE_TYPES = Map.ofEntries(
            Map.entry("String", "string"),
            Map.entry("char", "string"),
            Map.entry("Character", "string"),
            Map.entry("int", "integer(int32)"),
            Map.entry("Integer", "integer(int32)"),
            Map.entry("short", "integer(int32)"),
            Map.entry("Short", "integer(int32)"),
            Map.entry("long", "integer(int64)"),
            Map.entry("Long", "integer(int64)"),
            Map.entry("BigInteger", "integer"),
            Map.entry("float", "number(float)"),
            Map.entry("Float", "number(float)"),
            Map.entry("double", "number(double)"),
            Map.entry("Double", "number(double)"),
            Map.entry("BigDecimal", "number"),
            Map.entry("boolean", "boolean"),
            Map.entry("Boolean", "boolean"),
            Map.entry("LocalDate", "string(date)"),
            Map.entry("LocalDateTime", "string(date-time)"),
            Map.entry("OffsetDateTime", "string(date-time)"),
            Map.entry("ZonedDateTime", "string(date-time)"),
            Map.entry("Instant", "string(date-time)"),
            Map.entry("Date", "string(date-time)"),
            Map.entry("UUID", "string(uuid)"),
            Map.entry("Object", "object"),
            Map.entry("Map", "object"),
            Map.entry("HashMap", "object"),
            Map.entry("LinkedHashMap", "object"),
            Map.entry("Void", "void")
    );

    private static final Set<String> COLLECTIONS = Set.of(
            "List", "Set", "Collection", "Iterable", "ArrayList", "LinkedList", "HashSet", "LinkedHashSet"
    );

    // Tipos que só "embrulham" o tipo de verdade (ResponseEntity<BookDto> é só um BookDto).
    private static final Set<String> WRAPPERS = Set.of(
            "ResponseEntity", "Optional", "Mono", "CompletableFuture", "Callable", "DeferredResult"
    );

    private JavaTypeMapper() {
    }

    static String toNeutral(Type type) {
        if (type.isVoidType()) {
            return "void";
        }
        if (type.isArrayType()) {
            return "array<" + toNeutral(type.asArrayType().getComponentType()) + ">";
        }
        if (type.isPrimitiveType()) {
            return SIMPLE_TYPES.getOrDefault(type.asString(), type.asString());
        }
        if (type.isClassOrInterfaceType()) {
            return fromClassType(type.asClassOrInterfaceType());
        }
        if (type.isWildcardType()) {
            return "object";
        }
        return type.asString();
    }

    private static String fromClassType(ClassOrInterfaceType type) {
        String name = type.getNameAsString();
        NodeList<Type> args = type.getTypeArguments().orElse(new NodeList<>());

        if (WRAPPERS.contains(name)) {
            return args.isEmpty() ? "object" : toNeutral(args.get(0));
        }
        if (COLLECTIONS.contains(name)) {
            return "array<" + (args.isEmpty() ? "object" : toNeutral(args.get(0))) + ">";
        }
        if (SIMPLE_TYPES.containsKey(name)) {
            return SIMPLE_TYPES.get(name);
        }
        if (args.isEmpty()) {
            return name; // tipo do próprio projeto, como BookDto
        }
        StringBuilder generic = new StringBuilder(name).append("<");
        for (int i = 0; i < args.size(); i++) {
            generic.append(i > 0 ? ", " : "").append(toNeutral(args.get(i)));
        }
        return generic.append(">").toString(); // ex.: Page<BookDto>
    }
}