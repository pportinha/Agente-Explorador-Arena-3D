package pt.ipt.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor Semântico (RAG - Retrieval-Augmented Generation).

 * Responsabilidade única: dado o enigma de um Terminal de Plasma, encontrar a
 * secção certa do manual técnico e usar o LLM local (Ollama) para extrair a
 * chave de desbloqueio.

 * Não fala com a Arena nem desenha ecrãs — apenas raciocina sobre texto.
 */
public class MotorRAG {

    private final OllamaClient ollamaClient;
    private final List<DocumentoVetorial> baseConhecimento = new ArrayList<>();

    public MotorRAG(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    /**
     * Fase de Retrieval (offline): parte o manual em chunks, vetoriza cada um
     * com o modelo de embeddings e guarda tudo em memória (RAM).
     */
    public void ingerirManual(String manual) throws Exception {
        baseConhecimento.clear();

        if (manual == null || manual.isBlank()) {
            System.out.println("[RAG] Manual vazio. Nada para indexar.");
            return;
        }

        String[] blocos = manual.split("\\r?\\n");
        System.out.println("[RAG] A vetorizar manual técnico (" + blocos.length + " linhas)...");

        int indexados = 0;
        for (String bloco : blocos) {
            String texto = bloco.trim();

            // Ignora linhas vazias ou demasiado curtas para terem significado.
            if (texto.length() < 8) {
                continue;
            }

            double[] vetor = ollamaClient.gerarEmbedding(texto);
            baseConhecimento.add(new DocumentoVetorial(texto, vetor));
            indexados++;
        }

        System.out.println("[RAG] Base vetorial pronta: " + indexados + " chunks em memória.");
    }

    public boolean temConhecimento() {
        return !baseConhecimento.isEmpty();
    }

    /**
     * Fase de Retrieval (online) + Generation:
     *  1. Vetoriza o enigma.
     *  2. Encontra o chunk do manual com maior cosine similarity.
     *  3. Constrói um prompt ChatML rígido e pede a chave ao LLM.
     *  4. Devolve código + chunk + resposta bruta para submeter ao /unlock.
     */
    public ResultadoRAG resolverDesafio(String enigma) throws Exception {
        if (!temConhecimento()) {
            System.out.println("[RAG] Sem base de conhecimento. Impossível resolver.");
            return null;
        }

        double[] vetorEnigma = ollamaClient.gerarEmbedding(enigma);

        DocumentoVetorial melhorChunk = null;
        double melhorScore = -2.0;

        for (DocumentoVetorial doc : baseConhecimento) {
            double score = doc.similaridadeCosseno(vetorEnigma);
            if (score > melhorScore) {
                melhorScore = score;
                melhorChunk = doc;
            }
        }

        if (melhorChunk == null) {
            return null;
        }

        System.out.printf("[RAG] Chunk mais relevante (score=%.4f): %s%n",
                melhorScore, melhorChunk.getTexto());

        String prompt = construirPromptChatML(melhorChunk.getTexto(), enigma);
        String respostaBruta = ollamaClient.gerarResposta(prompt);
        String codigo = limparChave(respostaBruta);

        System.out.println("[RAG] Resposta bruta do LLM: " + respostaBruta);
        System.out.println("[RAG] Chave extraída: " + codigo);

        return new ResultadoRAG(codigo, melhorChunk.getTexto(), respostaBruta);
    }

    /**
     * Prompt Engineering em ChatML, com restrições negativas no system e o
     * assistant deixado em aberto para forçar o modelo a começar pela chave,
     * sem introduções nem alucinações. (Temperatura 0.0 é fixada no OllamaClient.)
     */
    private String construirPromptChatML(String seccaoManual, String enigma) {
        return """
                <|im_start|>system
                És um extrator de dados técnicos. Lês a SECÇÃO DO MANUAL e devolves
                EXCLUSIVAMENTE a chave/código de desbloqueio pedido pelo ENIGMA.
                Regras absolutas:
                - Responde apenas com a palavra-chave, em maiúsculas.
                - Não escrevas frases, explicações nem pontuação.
                - Não inventes. Se a chave não estiver no manual, responde DESCONHECIDO.
                <|im_end|>
                <|im_start|>user
                SECÇÃO DO MANUAL:
                %s

                ENIGMA:
                %s
                <|im_end|>
                <|im_start|>assistant
                """.formatted(seccaoManual, enigma);
    }

    /**
     * Mesmo com ChatML, modelos pequenos por vezes acrescentam ruído.
     * Esta limpeza garante uma chave isolada para enviar à Arena.
     */
    private String limparChave(String respostaBruta) {
        if (respostaBruta == null) {
            return "";
        }

        String limpo = respostaBruta.trim();

        // Fica só com a primeira linha não vazia.
        for (String linha : limpo.split("\\r?\\n")) {
            if (!linha.isBlank()) {
                limpo = linha.trim();
                break;
            }
        }

        // Remove cercas de código, aspas e tags ChatML residuais.
        limpo = limpo.replace("```", "")
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("<|im_end|>", "")
                .replace("<|im_start|>", "")
                .trim();

        return limpo;
    }
}