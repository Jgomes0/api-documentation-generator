package com.docgen.core;

import java.util.List;
// representar informações de um endpoint q foi encontrado na api
public record Endpoint(
        HttpMethod method,
        String path,
        String controller,
        String handlerName,
        List<ApiParameter> parameters,
        String returnType,
        String description
) {
    public Endpoint {
        // impede modificações na lista de parâmetros., evita que a descrição seja nula.
        parameters = List.copyOf(parameters);
        description = description == null ? "" : description;
    }
}