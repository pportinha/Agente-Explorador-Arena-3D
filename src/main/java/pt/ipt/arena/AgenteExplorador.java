package pt.ipt.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.swing.*;

public class AgenteExplorador {

    private static final String OLLAMA_URL = "http://localhost:11434";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JanelaConfiguracao janela = new JanelaConfiguracao();
            janela.setVisible(true);

            if (!janela.isConfirmado()) {
                System.out.println("Arranque cancelado pelo utilizador.");
                return;
            }

            iniciarAgente(
                    janela.getServidor(),
                    janela.getRoomId(),
                    janela.getRobotId(),
                    janela.isModoHeuristicaPura()
            );
        });
    }

    private static void iniciarAgente(String servidor, String roomId, String robotId, boolean modoHeuristicaPura) {
        PainelMapaCalor painelMapaCalor = criarJanelaMapaCalor();
        ArenaClient arenaClient = new ArenaClient(servidor);
        MotorHeuristico motorHeuristico = new MotorHeuristico(painelMapaCalor);
        OllamaClient ollamaClient = new OllamaClient(OLLAMA_URL);
        MotorRAG motorRAG = new MotorRAG(ollamaClient);

        Thread agenteThread = new Thread(() -> executarCicloAgente(
                arenaClient,
                motorHeuristico,
                motorRAG,
                servidor,
                roomId,
                robotId,
                modoHeuristicaPura
        ), "agente-explorador-loop");
        agenteThread.start();
    }

    private static PainelMapaCalor criarJanelaMapaCalor() {
        PainelMapaCalor painel = new PainelMapaCalor();
        painel.setPreferredSize(new java.awt.Dimension(760, 620));

        JFrame frame = new JFrame("Telemetria - Mapa de Calor");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(new JScrollPane(painel));
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        return painel;
    }

    private static void executarCicloAgente(
            ArenaClient arenaClient,
            MotorHeuristico motorHeuristico,
            MotorRAG motorRAG,
            String servidor,
            String roomId,
            String robotId,
            boolean modoHeuristicaPura
    ) {
        try {
            System.out.println("=====================================");
            System.out.println("      AGENTE EXPLORADOR INICIADO");
            System.out.println("=====================================");
            System.out.println("Servidor: " + servidor);
            System.out.println("Sala: " + roomId);
            System.out.println("Robô: " + robotId);
            System.out.println("Modo heurística pura: " + modoHeuristicaPura);

            JsonObject respostaRegisto = arenaClient.registarRobo(roomId, robotId);
            System.out.println("Registo:");
            System.out.println(respostaRegisto);

            Thread.sleep(400);

            // Após o registo, descarrega e indexa o manual técnico (fase de Retrieval).
            // Em "modo heurística pura" não usamos LLM, por isso saltamos esta etapa.
            if (!modoHeuristicaPura) {
                try {
                    System.out.println("A descarregar manual técnico...");
                    String manual = arenaClient.descarregarManual(roomId);
                    motorRAG.ingerirManual(manual);
                    if (motorRAG.temConhecimento()) {
                        System.out.println("[RAG] PRONTO: base vetorial carregada. Os baús vão ser resolvidos com LLM.");
                    } else {
                        System.out.println("[RAG] ATENCAO: base vetorial VAZIA. Os baús vão ser IGNORADOS!");
                    }
                } catch (Exception e) {
                    System.out.println("Aviso: falha ao preparar a base RAG. O agente segue só com heurística.");
                    e.printStackTrace();
                }
            }

            while (true) {
                JsonObject percecao;
                try {
                    percecao = arenaClient.perceber(roomId, robotId);
                } catch (Exception e) {
                    System.out.println("Falha temporaria na percecao. A recuperar sem encerrar...");
                    e.printStackTrace();
                    Thread.sleep(1000);
                    continue;
                }

                System.out.println("Perceção:");
                System.out.println(percecao);

                // Perceção inválida (sala fechada / erro do servidor): não há estado para decidir.
                if (percecao.has("error") || !percecao.has("o_meu_estado")
                        || percecao.get("o_meu_estado").isJsonNull()) {
                    System.out.println("Perceção sem estado válido. A aguardar...");
                    Thread.sleep(400);
                    continue;
                }

                if (percecao.has("game_over") && percecao.get("game_over").getAsBoolean()) {
                    System.out.println("Jogo terminado.");
                    break;
                }

                if (percecao.has("game_started") && !percecao.get("game_started").getAsBoolean()) {
                    System.out.println("A aguardar início da operação...");
                    Thread.sleep(400);
                    continue;
                }

                if (temDesafioTerminal(percecao)) {
                    tratarTerminal(arenaClient, motorHeuristico, motorRAG, percecao,
                            roomId, robotId, modoHeuristicaPura);
                    Thread.sleep(400);
                    continue;
                }

                String acao = motorHeuristico.decidirProximaAcao(percecao);

                JsonObject respostaAcao;
                try {
                    respostaAcao = arenaClient.enviarAcao(roomId, robotId, acao);
                } catch (Exception e) {
                    System.out.println("Falha temporaria ao enviar acao. A tentar no proximo ciclo...");
                    e.printStackTrace();
                    Thread.sleep(1000);
                    continue;
                }

                System.out.println("Ação escolhida: " + acao);
                System.out.println("Resposta da ação:");
                System.out.println(respostaAcao);

                if (estaEliminadoOuTerminou(respostaAcao)) {
                    System.out.println("Agente eliminado ou jogo terminado segundo a resposta da acao.");
                    break;
                }
                if (estaBloqueado(respostaAcao)) {
                    System.out.println("Acao bloqueada/castigo detetado. A aguardar para evitar anti-flood...");
                    Thread.sleep(1200);
                    continue;
                }

                Thread.sleep(400);
            }

        } catch (Exception e) {
            System.out.println("Erro no agente:");
            e.printStackTrace();
        }
    }

    /**
     * Trata um Terminal de Plasma sobre o qual o robô está posicionado.
     * Decide entre: fugir (modo heurístico/cofre já falhado) ou correr o
     * pipeline RAG e submeter a chave ao /unlock.
     */
    private static void tratarTerminal(
            ArenaClient arenaClient,
            MotorHeuristico motorHeuristico,
            MotorRAG motorRAG,
            JsonObject percecao,
            String roomId,
            String robotId,
            boolean modoHeuristicaPura
    ) throws Exception {

        JsonObject estado = percecao.getAsJsonObject("o_meu_estado");
        int x = lerCoordenada(estado, "x");
        int y = lerCoordenada(estado, "y");

        // Razões para NÃO tentar abrir (com diagnóstico explícito de qual disparou).
        if (modoHeuristicaPura) {
            System.out.println("[SKIP] Modo heurística pura ativo: terminal ignorado de propósito.");
            afastar(arenaClient, motorHeuristico, percecao, roomId, robotId, x, y);
            return;
        }
        if (!motorRAG.temConhecimento()) {
            System.out.println("[SKIP] Base RAG VAZIA (manual não indexado / Ollama em baixo). "
                    + "Verifica o Ollama e o download do manual. Terminal ignorado.");
            afastar(arenaClient, motorHeuristico, percecao, roomId, robotId, x, y);
            return;
        }
        if (motorHeuristico.cofreEstaNaListaNegra(x, y)) {
            System.out.println("[SKIP] Cofre (" + x + "," + y + ") já está na Lista Negra (falhou antes).");
            String fuga = motorHeuristico.decidirProximaAcao(percecao);
            arenaClient.enviarAcao(roomId, robotId, fuga);
            return;
        }

        String desafio = obterDesafioTerminal(percecao);

        System.out.println("=====================================");
        System.out.println(" TERMINAL DE PLASMA DETETADO");
        System.out.println("=====================================");
        System.out.println("Desafio: " + desafio);

        ResultadoRAG resultado = motorRAG.resolverDesafio(desafio);

        if (resultado == null || resultado.codigo().isBlank()
                || "DESCONHECIDO".equalsIgnoreCase(resultado.codigo())) {
            System.out.println("RAG não produziu chave. A marcar cofre como falhado.");
            motorHeuristico.marcarCofreFalhado(x, y);
            String fuga = motorHeuristico.decidirProximaAcao(percecao);
            arenaClient.enviarAcao(roomId, robotId, fuga);
            return;
        }

        JsonObject respostaUnlock = arenaClient.desbloquearCofre(
                roomId,
                robotId,
                resultado.codigo(),
                resultado.chunkRelevante(),
                resultado.respostaBruta()
        );

        System.out.println("Resposta do /unlock:");
        System.out.println(respostaUnlock);

        if (foiSucesso(respostaUnlock)) {
            System.out.println(">>> COFRE ABERTO! Chave: " + resultado.codigo() + " (+100 HP)");
            motorHeuristico.marcarCofreResolvido(x, y);
            // Reflexo tático: dar um passo para libertar o terminal.
            String saida = motorHeuristico.decidirProximaAcao(percecao);
            arenaClient.enviarAcao(roomId, robotId, saida);
        } else {
            System.out.println(">>> FALHA no desbloqueio (-10 HP). A adicionar à Lista Negra.");
            motorHeuristico.marcarCofreFalhado(x, y);
            String fuga = motorHeuristico.decidirProximaAcao(percecao);
            arenaClient.enviarAcao(roomId, robotId, fuga);
        }
    }

    /** Marca o cofre como falhado e dá um passo para sair de cima do terminal. */
    private static void afastar(ArenaClient arenaClient, MotorHeuristico motorHeuristico,
                                JsonObject percecao, String roomId, String robotId,
                                int x, int y) throws Exception {
        motorHeuristico.marcarCofreFalhado(x, y);
        String fuga = motorHeuristico.decidirProximaAcao(percecao);
        arenaClient.enviarAcao(roomId, robotId, fuga);
        System.out.println("A afastar-me do terminal com: " + fuga);
    }

    private static boolean foiSucesso(JsonObject respostaUnlock) {
        if (respostaUnlock == null || !respostaUnlock.has("status")
                || respostaUnlock.get("status").isJsonNull()) {
            return false;
        }
        return "sucesso".equalsIgnoreCase(respostaUnlock.get("status").getAsString());
    }

    private static boolean estaBloqueado(JsonObject resposta) {
        return contemTexto(resposta, "bloqueado")
                || contemTexto(resposta, "blocked")
                || contemTexto(resposta, "anti-flood")
                || contemTexto(resposta, "tarpit");
    }

    private static boolean estaEliminadoOuTerminou(JsonObject resposta) {
        return contemTexto(resposta, "eliminado")
                || contemTexto(resposta, "eliminated")
                || contemTexto(resposta, "game_over")
                || contemTexto(resposta, "rip");
    }

    private static boolean contemTexto(JsonObject objeto, String texto) {
        return objeto != null && objeto.toString().toLowerCase().contains(texto.toLowerCase());
    }

    private static boolean temDesafioTerminal(JsonObject percecao) {
        return obterDesafioTerminal(percecao) != null;
    }

    /**
     * O enigma vem DENTRO de cada objeto do array "cofres_no_mundo" e só está
     * preenchido quando o robô partilha as coordenadas do cofre. Comparamos a
     * nossa posição com a de cada cofre e devolvemos o desafio correspondente.
     */
    private static String obterDesafioTerminal(JsonObject percecao) {
        if (!percecao.has("o_meu_estado") || percecao.get("o_meu_estado").isJsonNull()
                || !percecao.has("cofres_no_mundo")
                || !percecao.get("cofres_no_mundo").isJsonArray()) {
            return null;
        }

        JsonObject estado = percecao.getAsJsonObject("o_meu_estado");
        int meuX = lerCoordenada(estado, "x");
        int meuY = lerCoordenada(estado, "y");

        JsonArray cofres = percecao.getAsJsonArray("cofres_no_mundo");

        for (int i = 0; i < cofres.size(); i++) {
            JsonObject cofre = cofres.get(i).getAsJsonObject();

            if (!cofre.has("x") || !cofre.has("y")) {
                continue;
            }

            int cofreX = lerCoordenada(cofre, "x");
            int cofreY = lerCoordenada(cofre, "y");

            boolean emCima = (cofreX == meuX && cofreY == meuY);
            boolean temDesafio = cofre.has("terminal_desafio")
                    && !cofre.get("terminal_desafio").isJsonNull()
                    && !cofre.get("terminal_desafio").getAsString().isBlank();

            if (emCima && temDesafio) {
                return cofre.get("terminal_desafio").getAsString();
            }
        }

        return null;
    }

    /**
     * As coordenadas do servidor vêm como decimais (ex.: 2.0). Lê como double
     * e arredonda para inteiro, evitando exceções do getAsInt() e erros de grelha.
     */
    private static int lerCoordenada(JsonObject objeto, String chave) {
        return (int) Math.round(objeto.get(chave).getAsDouble());
    }
}
