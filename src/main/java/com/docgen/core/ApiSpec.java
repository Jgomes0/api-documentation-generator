package com.docgen.core;

import java.util.List;

// reunir e representar tudo o que foi encontrado durante a análise de uma API, pasta que organiza as informações da API.

public record ApiSpec(String name, List<Endpoint> endpoints, List<Schema> schemas) {

    //  construtor compacto de records
    public ApiSpec {
        // impedir que as listas internas sejam modificadas depois da criação do objeto
        endpoints = List.copyOf(endpoints);
        schemas = List.copyOf(schemas);
    }
}