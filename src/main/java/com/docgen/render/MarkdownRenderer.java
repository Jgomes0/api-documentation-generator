package com.docgen.render;
import com.docgen.core.ApiParameter;
import com.docgen.core.ApiSpec;
import com.docgen.core.Endpoint;
import com.docgen.core.Schema;
import com.docgen.core.SchemaField;

public class MarkdownRenderer {

    public String render(ApiSpec spec) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(spec.name()).append("\n\n");

        md.append("## Endpoints\n\n");
        if (spec.endpoints().isEmpty()) {
            md.append("_Nenhum endpoint encontrado._\n\n");
        }
        for (Endpoint endpoint : spec.endpoints()) {
            renderEndpoint(md, endpoint);
        }

        if (!spec.schemas().isEmpty()) {
            md.append("## Schemas\n\n");
            for (Schema schema : spec.schemas()) {
                renderSchema(md, schema);
            }
        }
        return md.toString();
    }

    private void renderEndpoint(StringBuilder md, Endpoint endpoint) {
        md.append("### ").append(endpoint.method()).append(" ").append(endpoint.path()).append("\n\n");
        md.append("`").append(endpoint.controller()).append("#").append(endpoint.handlerName()).append("`\n\n");

        if (!endpoint.description().isBlank()) {
            md.append(endpoint.description()).append("\n\n");
        }

        if (!endpoint.parameters().isEmpty()) {
            md.append("| Parâmetro | Tipo | Local | Obrigatório | Descrição |\n");
            md.append("|---|---|---|---|---|\n");
            for (ApiParameter p : endpoint.parameters()) {
                md.append("| `").append(p.name()).append("`")
                        .append(" | `").append(p.type()).append("`")
                        .append(" | ").append(p.location())
                        .append(" | ").append(p.required() ? "sim" : "não")
                        .append(" | ").append(p.description())
                        .append(" |\n");
            }
            md.append("\n");
        }

        md.append("**Retorno:** `").append(endpoint.returnType()).append("`\n\n");
    }

    private void renderSchema(StringBuilder md, Schema schema) {
        md.append("### ").append(schema.name()).append("\n\n");
        md.append("| Campo | Tipo | Obrigatório |\n");
        md.append("|---|---|---|\n");
        for (SchemaField f : schema.fields()) {
            md.append("| `").append(f.name()).append("`")
                    .append(" | `").append(f.type()).append("`")
                    .append(" | ").append(f.required() ? "sim" : "não")
                    .append(" |\n");
        }
        md.append("\n");
    }
}