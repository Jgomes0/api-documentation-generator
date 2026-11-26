package com.docgen.ingest.spring;

import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.core.ParameterLocation;
import com.docgen.core.Schema;
import com.docgen.core.SchemaField;
import com.docgen.ingest.EndpointExtractor;
import com.docgen.ingest.ExtractionResult;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.Type;


import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

// Responsável por coordenar a extração dos endpoints e dos schemas (objetos de dados) do código Spring.
public class SpringControllerExtractor implements EndpointExtractor {

    // Guarda a declaração da classe e a CompilationUnit à qual ela pertence.
    private record IndexedClass(
            CompilationUnit unit,
            ClassOrInterfaceDeclaration declaration
    ) {}

    // Map que relaciona o nome de uma anotação do Spring com um método HTTP do nosso projeto
    private static final Map<String, HttpMethod> MAPPING_ANNOTATIONS = Map.of(
            "GetMapping", HttpMethod.GET,
            "PostMapping", HttpMethod.POST,
            "PutMapping", HttpMethod.PUT,
            "PatchMapping", HttpMethod.PATCH,
            "DeleteMapping", HttpMethod.DELETE
    );

    // identificando onde os parâmetros de um endpoint estão localizados
    private static final Map<String, ParameterLocation> PARAMETER_ANNOTATIONS = Map.of(
            "PathVariable", ParameterLocation.PATH,
            "RequestParam", ParameterLocation.QUERY,
            "RequestHeader", ParameterLocation.HEADER,
            "RequestBody", ParameterLocation.BODY
    );

    // Classes do Spring que não são objetos de dados (não geram schema).
    private static final Set<String> SPRING_COMPONENTS = Set.of(
            "RestController", "Controller", "Service", "Repository", "Component",
            "Configuration", "SpringBootApplication", "ControllerAdvice", "RestControllerAdvice"
    );


    // Anotações de validação que tornam um campo obrigatório.
    private static final Set<String> REQUIRED_ANNOTATIONS = Set.of("NotNull", "NotBlank", "NotEmpty");

    // javaparser analisar arquivos
    private final JavaParser parser;

    // Índice das classes Java encontradas no projeto.
   // A chave será o nome completo da classe (pacote + classe).
    private final Map<String, IndexedClass> classIndex = new HashMap<>();

    // executado quando criar um objeto do tipo SpringControllerExtractor, prepara a ferramenta que será utilizada pela classe para analisar os arquivos Java
    public SpringControllerExtractor() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(configuration);
    }
    @Override
    public void prepare(List<Path> files) throws IOException {

        // Limpa o índice antes de iniciar uma nova análise.
        classIndex.clear();

        // Percorre todos os arquivos recebidos pelo ProjectScanner.
        for (Path file : files) {

            // Considera somente arquivos Java.
            if (!file.getFileName().toString().endsWith(".java")) {
                continue;
            }

            // Faz o parsing do arquivo para obter sua estrutura Java.
            ParseResult<CompilationUnit> result = parser.parse(file);

            // Se o arquivo não puder ser analisado, passa para o próximo.
            if (result.getResult().isEmpty()) {
                continue;
            }

            CompilationUnit unit = result.getResult().get();

            // Obtém o pacote declarado no arquivo.
            String packageName = unit.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Percorre as declarações de classes de nível superior.
            for (var type : unit.getTypes()) {

                // Ignora interfaces, enums e outros tipos que não sejam classes.
                if (!(type instanceof ClassOrInterfaceDeclaration clazz) || clazz.isInterface()) {
                    continue;
                }

                // Monta o nome completo da classe: pacote + nome.
                String className = packageName.isEmpty()
                        ? clazz.getNameAsString()
                        : packageName + "." + clazz.getNameAsString();

                // Armazena a classe e o arquivo ao qual ela pertence.
                classIndex.put(className, new IndexedClass(unit, clazz));
            }
        }
    }

    @Override
    public String framework() {
        return "Spring";
    }

    @Override
    public boolean supports(Path file) {
        return file.getFileName().toString().endsWith(".java");
    }

    // metodo principal
    @Override
    public ExtractionResult extract(Path file) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);
        CompilationUnit unit = result.getResult().orElseThrow(() ->
                new IllegalArgumentException("Falha ao ler " + file + ": " + result.getProblems()));

        List<Endpoint> endpoints = new ArrayList<>();
        for (ClassOrInterfaceDeclaration clazz : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!isController(clazz)) {
                continue;
            }
            String basePath = clazz.getAnnotationByName("RequestMapping")
                    .map(this::extractPath)
                    .orElse("");
            for (MethodDeclaration method : clazz.getMethods()) {
                toEndpoint(clazz, method, basePath).ifPresent(endpoints::add);
            }
        }
        return new ExtractionResult(endpoints, extractSchemas(unit));
    }

    private boolean isController(ClassOrInterfaceDeclaration clazz) {
        return clazz.isAnnotationPresent("RestController") || clazz.isAnnotationPresent("Controller");
    }

    private Optional<Endpoint> toEndpoint(ClassOrInterfaceDeclaration clazz,
                                          MethodDeclaration method,
                                          String basePath) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            HttpMethod httpMethod = MAPPING_ANNOTATIONS.get(annotation.getNameAsString());
            if (httpMethod == null) {
                continue;
            }
            String path = joinPaths(basePath, extractPath(annotation));

            List<ApiParameter> parameters = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                toApiParameter(parameter).ifPresent(parameters::add);
            }

            return Optional.of(new Endpoint(
                    httpMethod,
                    path,
                    clazz.getNameAsString(),
                    method.getNameAsString(),
                    parameters,
                    JavaTypeMapper.toNeutral(method.getType()),
                    null
            ));
        }
        return Optional.empty();
    }

    private Optional<ApiParameter> toApiParameter(Parameter parameter) {
        for (Map.Entry<String, ParameterLocation> entry : PARAMETER_ANNOTATIONS.entrySet()) {
            Optional<AnnotationExpr> annotation = parameter.getAnnotationByName(entry.getKey());
            if (annotation.isPresent()) {
                return Optional.of(new ApiParameter(
                        parameter.getNameAsString(),
                        JavaTypeMapper.toNeutral(parameter.getType()),
                        entry.getValue(),
                        isRequired(annotation.get()),
                        null
                ));
            }
        }
        return Optional.empty();
    }

    private boolean isRequired(AnnotationExpr annotation) {
        if (!annotation.isNormalAnnotationExpr()) {
            return true;
        }
        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
            String name = pair.getNameAsString();
            if (name.equals("defaultValue")) {
                return false;
            }
            if (name.equals("required")
                    && pair.getValue().isBooleanLiteralExpr()
                    && !pair.getValue().asBooleanLiteralExpr().getValue()) {
                return false;
            }
        }
        return true;
    }

    private String extractPath(AnnotationExpr annotation) {
        Expression value = null;
        if (annotation.isSingleMemberAnnotationExpr()) {
            value = annotation.asSingleMemberAnnotationExpr().getMemberValue();
        } else if (annotation.isNormalAnnotationExpr()) {
            value = annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .map(MemberValuePair::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (value == null) {
            return "";
        }
        if (value.isArrayInitializerExpr()) {
            NodeList<Expression> values = value.asArrayInitializerExpr().getValues();
            value = values.isEmpty() ? null : values.get(0);
        }
        return value != null && value.isStringLiteralExpr()
                ? value.asStringLiteralExpr().asString()
                : "";
    }

    private String joinPaths(String base, String sub) {
        String joined = ("/" + base + "/" + sub).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/")
                ? joined.substring(0, joined.length() - 1)
                : joined;
    }

    // Procura records e classes de dados no arquivo e monta um Schema para cada um.
    private List<Schema> extractSchemas(CompilationUnit unit) {
        List<Schema> schemas = new ArrayList<>();

        for (RecordDeclaration record : unit.findAll(RecordDeclaration.class)) {
            if (isSpringComponent(record)) {
                continue;
            }
            List<SchemaField> fields = new ArrayList<>();
            for (Parameter component : record.getParameters()) {
                fields.add(toSchemaField(component.getNameAsString(), component.getType(), component.getAnnotations()));
            }
            addIfNotEmpty(schemas, record.getNameAsString(), fields);
        }

        for (ClassOrInterfaceDeclaration clazz : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (clazz.isInterface() || isSpringComponent(clazz)) {
                continue;
            }
            List<SchemaField> fields = new ArrayList<>();
            fields.addAll(collectFields(clazz, unit, Collections.newSetFromMap(new IdentityHashMap<>())).values());
            addIfNotEmpty(schemas, clazz.getNameAsString(), fields);
        }
        return schemas;
    }

    private void addIfNotEmpty(List<Schema> schemas, String name, List<SchemaField> fields) {
        if (!fields.isEmpty()) {
            schemas.add(new Schema(name, fields));
        }
    }

    private boolean isSpringComponent(NodeWithAnnotations<?> node) {
        return SPRING_COMPONENTS.stream().anyMatch(name -> node.isAnnotationPresent(name));
    }

    // Um campo é obrigatório se for tipo primitivo (int, long...) ou tiver @NotNull, @NotBlank ou @NotEmpty.
    private SchemaField toSchemaField(String name, Type type, NodeList<AnnotationExpr> annotations) {
        boolean required = type.isPrimitiveType()
                || annotations.stream().anyMatch(a -> REQUIRED_ANNOTATIONS.contains(a.getNameAsString()));
        return new SchemaField(name, JavaTypeMapper.toNeutral(type), required);
    }
    // Reúne os campos da classe e das classes que ela herda. Os da classe-pai vêm primeiro.
    private Map<String, SchemaField> collectFields(ClassOrInterfaceDeclaration clazz,
                                                   CompilationUnit unit,
                                                   Set<ClassOrInterfaceDeclaration> visited) {
        Map<String, SchemaField> fields = new LinkedHashMap<>();
        if (!visited.add(clazz)) {
            return fields; // evita loop se uma herança apontar para si mesma
        }
        if (!clazz.getExtendedTypes().isEmpty()) {
            String parentName = clazz.getExtendedTypes(0).getNameAsString();
            findClass(parentName, unit).ifPresent(parent ->
                    fields.putAll(collectFields(parent.declaration(), parent.unit(), visited)));
        }
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.isStatic()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                fields.put(variable.getNameAsString(),
                        toSchemaField(variable.getNameAsString(), variable.getType(), field.getAnnotations()));
            }
        }
        return fields;
    }

    // Procura no índice a classe com esse nome: primeiro no mesmo pacote, depois nos imports.
    private Optional<IndexedClass> findClass(String name, CompilationUnit from) {
        String samePackage = from.getPackageDeclaration()
                .map(pd -> pd.getNameAsString() + ".")
                .orElse("") + name;
        if (classIndex.containsKey(samePackage)) {
            return Optional.of(classIndex.get(samePackage));
        }
        for (ImportDeclaration imported : from.getImports()) {
            String importedName = imported.getNameAsString();
            String candidate = imported.isAsterisk() ? importedName + "." + name : importedName;
            if (candidate.endsWith("." + name) && classIndex.containsKey(candidate)) {
                return Optional.of(classIndex.get(candidate));
            }
        }
        return Optional.empty();
    }
}

/*
prepare()
  |
  |-- para cada arquivo Java do projeto
        Lê o arquivo e guarda cada classe num índice (nome completo -> classe).
        O índice é usado depois para achar classes-pai (herança).
extract()
  |
  |-- isController()
  |     Verifica se a classe possui @RestController ou @Controller.
  |
  |-- extractPath()
  |     Extrai o path definido na classe, geralmente em @RequestMapping.
  |
  |-- toEndpoint()
  |     Identifica se um metodo da classe representa um endpoint HTTP.
  |     |
  |     |-- extractPath()
  |     |     Extrai o path definido no metodo, como @GetMapping("/{id}").
  |     |
  |     |-- toApiParameter()
  |     |     Identifica parâmetros e suas localizações:
  |     |     PATH, QUERY, HEADER e BODY.
  |     |     |
  |     |     |-- isRequired()
  |     |           Verifica se o parâmetro é obrigatório.
  |     |
  |     |-- new Endpoint(...)
  |           Cria um objeto Endpoint com as informações extraídas.
  |
  |-- adiciona Endpoint na lista
  |     Armazena o novo endpoint na lista.
  |
  |-- extractSchemas()
  |     Procura objetos de dados no arquivo (records e classes).
  |     |
  |     |-- isSpringComponent()
  |     |     Ignora controllers, services, repositories e outras
  |     |     classes do Spring que não são objetos de dados.
  |     |
  |     |-- toSchemaField()
  |     |     Lê cada campo: nome, tipo e se é obrigatório
  |     |       O tipo é traduzido pelo JavaTypeMapper (String vira string,
  |     |      List<X> vira array<X>, Long vira integer(int64)...).
  |     |    |-- collectFields()
  |     |     Reúne os campos da classe e das classes-pai (herança).
  |     |     Acha a classe-pai pelo índice (mesmo pacote ou imports)
  |     |     e sobe até o fim da cadeia. Os campos da classe-pai vêm primeiro.
  |     |
  |     |-- addIfNotEmpty()
  |           Só guarda o schema se ele tiver pelo menos um campo.
  |
  |-- return new ExtractionResult(endpoints, schemas)
        Devolve endpoints e schemas juntos para o ProjectScanner.


 */