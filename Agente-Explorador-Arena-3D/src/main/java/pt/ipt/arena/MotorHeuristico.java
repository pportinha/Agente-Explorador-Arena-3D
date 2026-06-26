package pt.ipt.arena;

import com.google.gson.JsonObject;

import java.util.Random;

public class MotorHeuristico {

    private final Random random = new Random();

    private static final String[] ACOES = {
            "MOVER_NORTE",
            "MOVER_SUL",
            "MOVER_ESTE",
            "MOVER_OESTE"
    };

    public String decidirProximaAcao(JsonObject percecao) {
        // Por enquanto é uma heurística simples:
        // escolhe aleatoriamente uma das quatro direções.
        int indice = random.nextInt(ACOES.length);
        return ACOES[indice];
    }
}