package com.docgen.cli;
import com.docgen.core.ApiSpec;
import com.docgen.ingest.ProjectScanner;
import com.docgen.render.MarkdownRenderer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "docgen",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Gera documentação de APIs a partir do código-fonte."
)

// É o botão do programa: recebe os dados digitados no terminal e liga as peças
// (ProjectScanner → MarkdownRenderer) para gerar o arquivo da documentação.
// Não sabe ler código nem escrever Markdown, só coordena quem sabe.
public class DocGenCli implements Callable<Integer> {

    @Option(names = {"-s", "--source"}, required = true,
            description = "Pasta com o código-fonte do projeto")
    private Path source;

    @Option(names = {"-o", "--out"}, defaultValue = "docs",
            description = "Pasta de saída (padrão: ${DEFAULT-VALUE})")
    private Path out;

    @Option(names = {"-n", "--name"}, defaultValue = "API",
            description = "Nome da API na documentação (padrão: ${DEFAULT-VALUE})")
    private String name;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(source)) {
            System.err.println("Pasta de código-fonte não encontrada: " + source);
            return 1;
        }

        ApiSpec spec = new ProjectScanner().scan(source, name);
        String markdown = new MarkdownRenderer().render(spec);

        Files.createDirectories(out);
        Path output = out.resolve("api-reference.md");
        Files.writeString(output, markdown);

        System.out.println(spec.endpoints().size() + " endpoint(s) documentado(s) em " + output.toAbsolutePath());
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new DocGenCli()).execute(args));
    }
}

/*

main()
  |
  |-- new CommandLine(new DocGenCli())
  |     O Picocli lê o que foi digitado no terminal (--source, --out, --name)
  |     e preenche os campos da classe com esses valores.
  |
  |-- execute(args)
  |     Chama o método call() abaixo e devolve o código de saída.
  |
  |-- System.exit(...)
        Encerra o programa com esse código (0 = sucesso, 1 = erro).


call()
  |
  |-- Files.isDirectory(source)
  |     Confere se a pasta de código-fonte existe.
  |     Se não existir, mostra um erro e termina com código 1.
  |
  |-- new ProjectScanner().scan()
  |     Percorre o projeto e monta o ApiSpec com todos os endpoints.
  |
  |-- new MarkdownRenderer().render()
  |     Transforma o ApiSpec em texto Markdown.
  |
  |-- Files.createDirectories(out)
  |     Cria a pasta de saída, caso ainda não exista.
  |
  |-- Files.writeString()
  |     Grava o texto no arquivo api-reference.md.
  |
  |-- System.out.println()
  |     Mostra quantos endpoints foram documentados e onde ficou o arquivo.
  |
  |-- return 0
        Informa que tudo deu certo.


Opções aceitas na linha de comando:

--source (-s)   Pasta com o código do projeto. Obrigatória.
--out (-o)      Pasta onde a documentação será gravada. Padrão: docs
--name (-n)     Nome da API que aparece no título. Padrão: API

Exemplo:
docgen --source ./meu-projeto --out ./docs --name MinhaAPI

 */