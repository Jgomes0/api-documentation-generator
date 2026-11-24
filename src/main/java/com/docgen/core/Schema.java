package com.docgen.core;

import java.util.List;
// Estrutura de um objeto utilizado pela API.
public record Schema(String name, List<SchemaField> fields) {
    public Schema {
        fields = List.copyOf(fields);
    }
}