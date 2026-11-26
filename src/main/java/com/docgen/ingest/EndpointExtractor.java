package com.docgen.ingest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface EndpointExtractor {
    /** Nome do framework que este leitor entende (ex.: "Spring"). */
    String framework();

    /** Diz se este leitor sabe analisar o arquivo informado. */
    boolean supports(Path file);

    /**
     * Prepara o leitor com os arquivos disponíveis no projeto.
     * Implementações que não precisam dessa preparação podem ignorar.
     */
    default void prepare(List<Path> files) throws IOException {
    }

    /** Lê o arquivo e devolve os endpoints encontrados. */
    ExtractionResult extract(Path file) throws IOException;

    /** Prioridade dos schemas deste leitor quando dois leitores encontram o mesmo nome. Maior vence. */
    default int priority() {
        return 0;
    }
}

