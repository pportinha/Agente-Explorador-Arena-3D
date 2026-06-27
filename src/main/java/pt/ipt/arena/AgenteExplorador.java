package pt.ipt.arena;

import com.google.gson.JsonObject;

import javax.swing.*;

public class AgenteExplorador {

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
        ArenaClient arenaClient = new ArenaClient(servidor);
        MotorHeuristico motorHeuristico = new MotorHeuristico();

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

            while (true) {
                JsonObject percecao = arenaClient.perceber(roomId, robotId);

                System.out.println("Perceção:");
                System.out.println(percecao);

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
                    String desafio = obterDesafioTerminal(percecao);

                    System.out.println("=====================================");
                    System.out.println(" TERMINAL DE PLASMA DETETADO");
                    System.out.println("=====================================");
                    System.out.println("Desafio:");
                    System.out.println(desafio);
                    System.out.println("Aqui será chamado o Ollama no próximo passo.");
                    System.out.println();

                    Thread.sleep(400);
                    continue;
                }

                String acao = motorHeuristico.decidirProximaAcao(percecao);

                JsonObject respostaAcao = arenaClient.enviarAcao(roomId, robotId, acao);

                System.out.println("Ação escolhida: " + acao);
                System.out.println("Resposta da ação:");
                System.out.println(respostaAcao);

                Thread.sleep(400);
            }

        } catch (Exception e) {
            System.out.println("Erro no agente:");
            e.printStackTrace();
        }
    }

    private static boolean temDesafioTerminal(JsonObject percecao) {
        return percecao.has("terminal_desafio")
                && !percecao.get("terminal_desafio").isJsonNull()
                && !percecao.get("terminal_desafio").getAsString().isBlank();
    }

    private static String obterDesafioTerminal(JsonObject percecao) {
        return percecao.get("terminal_desafio").getAsString();
    }
}