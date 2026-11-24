package com.docgen.ingest.spring;
import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.HttpMethod;
import com.docgen.ingest.EndpointExtractor;
import com.docgen.core.ParameterLocation;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


 // responsável por coordenar a extração dos endpoints.
public class SpringControllerExtractor implements EndpointExtractor {

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

    // javaparser analisar arquivos
    private final JavaParser parser;
    // executado quando criar um objeto do tipo controllextractor, prepara a ferramenta que será utilizada pela classe para analisar os arquivos Java
    public SpringControllerExtractor() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(configuration);
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
    public List<Endpoint> extract(Path file) throws IOException {
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
        return endpoints;
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
                    method.getType().asString(),
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
                        parameter.getType().asString(),
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
}

/*

extract()
  |
  |-- isController()
  |     Verifica se a classe possui @RestController ou @Controller.
  |
  |-- extractPath()
  |     Extrai o path definido na classe, geralmente em @RequestMapping.
  |
  |-- toEndpoint()
        Identifica se um metodo da classe representa um endpoint HTTP.
        |
        |-- extractPath()
        |     Extrai o path definido no metodo, como @GetMapping("/{id}").
        |
        |-- toApiParameter()
        |     Identifica parâmetros e suas localizações:
        |     PATH, QUERY, HEADER e BODY.
        |     |
        |     |-- isRequired()
        |           Verifica se o parâmetro é obrigatório.
        |
        |-- new Endpoint(...)
              Cria um objeto Endpoint com as informações extraídas.
  |
  |-- adiciona Endpoint na lista
  |     Armazena o novo endpoint na lista.
  |
  |-- return endpoints
        Retorna a lista de endpoints encontrados.


 */
