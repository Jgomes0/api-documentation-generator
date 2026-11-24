package com.docgen.ingest;

import com.docgen.core.ApiSpec;
import com.docgen.core.Endpoint;
import com.docgen.ingest.spring.SpringControllerExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// Percorre o projeto inteiro e entrega cada arquivo ao leitor que o entende.
// Não conhece nenhuma linguagem: só conversa com o contrato EndpointExtractor.
public class ProjectScanner {

    // Pastas que não contêm código-fonte útil e podem ser ignoradas.
    private static final Set<String> IGNORED_DIRS =
            Set.of(".git", ".gradle", ".idea", "build", "target", "node_modules", "out");

    private final List<EndpointExtractor> extractors;

    public ProjectScanner() {
        this(List.of(new SpringControllerExtractor()));
    }

    public ProjectScanner(List<EndpointExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public ApiSpec scan(Path sourceDir, String apiName) throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(file -> !isInIgnoredDir(sourceDir, file))
                    .sorted()
                    .toList();
        }

        List<Endpoint> endpoints = new ArrayList<>();
        for (Path file : files) {
            for (EndpointExtractor extractor : extractors) {
                if (!extractor.supports(file)) {
                    continue;
                }
                try {
                    endpoints.addAll(extractor.extract(file));
                } catch (IllegalArgumentException e) {
                    System.err.println("Ignorando " + file + ": " + e.getMessage());
                }
            }
        }
        return new ApiSpec(apiName, endpoints, List.of());
    }

    private boolean isInIgnoredDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (IGNORED_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }
}
/*

scan()
  |
  |-- Files.walk()
  |     Percorre todas as pastas e subpastas a partir da pasta informada.
  |
  |-- Files::isRegularFile
  |     Mantém só arquivos, descartando as pastas em si.
  |
  |-- isInIgnoredDir()
  |     Descarta arquivos dentro de pastas sem código útil,
  |     como .git, build, target e node_modules.
  |
  |-- sorted()
  |     Coloca os arquivos em ordem, para o resultado ser sempre igual.
  |
  |-- para cada arquivo encontrado
  |     |
  |     |-- para cada leitor (EndpointExtractor) da lista
  |           |
  |           |-- supports()
  |           |     Pergunta ao leitor se ele entende esse arquivo.
  |           |     Se não entende, passa para o próximo leitor.
  |           |
  |           |-- extract()
  |           |     O leitor lê o arquivo e devolve os endpoints dele.
  |           |
  |           |-- adiciona os endpoints na lista geral
  |           |
  |           |-- se o arquivo não puder ser lido
  |                 Mostra um aviso e segue para o próximo,
  |                 sem interromper a análise do projeto.
  |
  |-- new ApiSpec(...)
  |     Junta todos os endpoints encontrados em um único ApiSpec.
  |
  |-- return ApiSpec
        Devolve o resultado, pronto para o MarkdownRenderer.


Construtores:

ProjectScanner()
  Usa só o leitor do Spring. É o caso comum.

ProjectScanner(List<EndpointExtractor>)
  Aceita uma lista de leitores. É por aqui que entram novas linguagens
  e frameworks, sem precisar alterar o scanner.

 */