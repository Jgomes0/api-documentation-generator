package com.docgen.ingest;

import com.docgen.core.Endpoint;
import com.docgen.core.Schema;
import java.util.List;

public record ExtractionResult(List<Endpoint> endpoints, List<Schema> schemas) {

    public ExtractionResult {
        endpoints = List.copyOf(endpoints);
        schemas = List.copyOf(schemas);
    }

    // Atalho para leitores que só encontram endpoints (como o do Spring, por enquanto).
    public static ExtractionResult ofEndpoints(List<Endpoint> endpoints) {
        return new ExtractionResult(endpoints, List.of());
    }
}