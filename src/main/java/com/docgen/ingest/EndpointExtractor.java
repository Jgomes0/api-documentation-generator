package com.docgen.ingest;
import com.docgen.core.Endpoint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface EndpointExtractor {
    /** Nome do framework que este leitor entende (ex.: "Spring"). */
    String framework();

    /** Diz se este leitor sabe analisar o arquivo informado. */
    boolean supports(Path file);

    /** Lê o arquivo e devolve os endpoints encontrados. */
    List<Endpoint> extract(Path file) throws IOException;
}

