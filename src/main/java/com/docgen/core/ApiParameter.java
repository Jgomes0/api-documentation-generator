package com.docgen.core;


// é bom usar record quando for armazenar muitas informações de maneira simples e organizada
// PARAMETRO É A INFORMAÇÃO COMPLEMENTAR QUE VEM JUNTO DE UMA REQUISIÇÃO



// FUNÇÃO DA CLASSE: define como o gerador vai representar e armazenar as informações de cada parâmetro que encontrar
public record ApiParameter(
        String name,
        String type,
        ParameterLocation location,
        boolean required,
        String description
) {

    // construtor compacto de records
    public ApiParameter {
        description = description == null ? "" : description; // evitar que a descrição fique nula aqui dentro
    }
}