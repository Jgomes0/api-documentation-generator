package com.docgen.ingest;

import com.docgen.core.ApiParameter;
import com.docgen.core.Endpoint;
import com.docgen.core.Schema;
import com.docgen.core.SchemaField;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Deixa só os schemas realmente usados pelos endpoints e junta os que têm o mesmo nome.
final class SchemaPruner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SchemaPruner() {
    }

    static List<Schema> prune(List<Endpoint> endpoints, List<Schema> schemas) {
        Map<String, Schema> byName = deduplicate(schemas);

        Set<String> used = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();

        // 1. Começa pelos tipos que os endpoints devolvem ou recebem.
        for (Endpoint endpoint : endpoints) {
            enqueue(endpoint.returnType(), byName, used, pending);
            for (ApiParameter parameter : endpoint.parameters()) {
                enqueue(parameter.type(), byName, used, pending);
            }
        }

        // 2. Segue as referências: os campos de um schema podem usar outros schemas.
        while (!pending.isEmpty()) {
            Schema schema = byName.get(pending.poll());
            for (SchemaField field : schema.fields()) {
                enqueue(field.type(), byName, used, pending);
            }
        }

        List<Schema> result = new ArrayList<>();
        for (Schema schema : byName.values()) {
            if (used.contains(schema.name())) {
                result.add(schema);
            }
        }
        return result;
    }

    // Se dois schemas têm o mesmo nome, fica o que tem mais campos (o primeiro, em caso de empate).
    private static Map<String, Schema> deduplicate(List<Schema> schemas) {
        Map<String, Schema> byName = new LinkedHashMap<>();
        for (Schema schema : schemas) {
            Schema existing = byName.get(schema.name());
            if (existing == null || schema.fields().size() > existing.fields().size()) {
                byName.put(schema.name(), schema);
            }
        }
        return byName;
    }

    // Procura nomes de schemas dentro de um tipo, como array<Owner> ou Page<BookDto>.
    private static void enqueue(String type, Map<String, Schema> byName, Set<String> used, Deque<String> pending) {
        Matcher matcher = IDENTIFIER.matcher(type);
        while (matcher.find()) {
            String name = matcher.group();
            if (byName.containsKey(name) && used.add(name)) {
                pending.add(name);
            }
        }
    }
}

/*

prune()
  |
  |-- deduplicate()
  |     Junta os schemas que têm o mesmo nome (por exemplo, um Owner vindo
  |     do OpenAPI e outro vindo do código Java). Fica o que tem mais campos.
  |
  |-- para cada endpoint
  |     |
  |     |-- enqueue(tipo de retorno)
  |     |-- enqueue(tipo de cada parâmetro)
  |           Procura, dentro do tipo (ex.: array<Owner>), nomes que sejam
  |           schemas conhecidos e os coloca na fila de "usados".
  |
  |-- enquanto houver schema na fila
  |     |
  |     |-- para cada campo do schema
  |           enqueue(tipo do campo)
  |           Segue as referências: se Owner tem pets: array<Pet>,
  |           o Pet também passa a ser usado.
  |           Cada schema entra na fila uma única vez, então referências
  |           circulares (Owner -> Pet -> Owner) não travam o programa.
  |
  |-- monta a lista final
  |     Guarda só os schemas usados, na ordem em que apareceram.
  |
  |-- return result
        Devolve a lista filtrada para o ProjectScanner montar o ApiSpec.


Exemplo:
  Um endpoint devolve array<Owner>.
  Owner tem o campo pets: array<Pet>.
  Pet tem o campo type: PetType.
  Existe também um schema Unused que ninguém referencia.
  Resultado: Owner, Pet e PetType. O Unused é descartado.

 */