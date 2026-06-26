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

        try {
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

                Thread.sleep(400);
            }

        } catch (Exception e) {
            System.out.println("Erro no agente:");
            e.printStackTrace();
        }
    }
}