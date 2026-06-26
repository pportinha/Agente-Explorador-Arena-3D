package pt.ipt.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

public class MotorHeuristico {

    private final Random random = new Random();
    private final Map<String, Integer> historicoVisitas = new HashMap<>();

    public String decidirProximaAcao(JsonObject percecao) {
        JsonObject estado = percecao.getAsJsonObject("o_meu_estado");

        int x = estado.get("x").getAsInt();
        int y = estado.get("y").getAsInt();
        int energia = estado.get("energia").getAsInt();

        registarVisita(x, y);

        List<String> acoesPossiveis = obterAcoesPossiveis(percecao, x, y);

        if (acoesPossiveis.isEmpty()) {
            return "MOVER_NORTE";
        }

        String fuga = tentarFugirDeInimigo(percecao, x, y, energia, acoesPossiveis);
        if (fuga != null) {
            return fuga;
        }

        String ataque = tentarAtacarInimigo(percecao, x, y, energia, acoesPossiveis);
        if (ataque != null) {
            return ataque;
        }

        String recurso = irParaAlvoMaisProximo(percecao, x, y, "recursos_no_mundo", acoesPossiveis);
        if (energia < 120 && recurso != null) {
            return recurso;
        }

        String cofre = irParaAlvoMaisProximo(percecao, x, y, "cofres_no_mundo", acoesPossiveis);
        if (cofre != null) {
            return cofre;
        }

        if (recurso != null) {
            return recurso;
        }

        return escolherCasaMenosVisitada(x, y, acoesPossiveis);
    }

    private List<String> obterAcoesPossiveis(JsonObject percecao, int x, int y) {
        List<String> acoes = new ArrayList<>();

        if (!temParede(percecao, x, y - 1)) {
            acoes.add("MOVER_NORTE");
        }

        if (!temParede(percecao, x, y + 1)) {
            acoes.add("MOVER_SUL");
        }

        if (!temParede(percecao, x + 1, y)) {
            acoes.add("MOVER_ESTE");
        }

        if (!temParede(percecao, x - 1, y)) {
            acoes.add("MOVER_OESTE");
        }

        return acoes;
    }

    private boolean temParede(JsonObject percecao, int destinoX, int destinoY) {
        if (!percecao.has("objetos_fixos") || !percecao.get("objetos_fixos").isJsonArray()) {
            return false;
        }

        JsonArray objetosFixos = percecao.getAsJsonArray("objetos_fixos");

        for (int i = 0; i < objetosFixos.size(); i++) {
            JsonObject objeto = objetosFixos.get(i).getAsJsonObject();

            if (!objeto.has("x") || !objeto.has("y")) {
                continue;
            }

            int paredeX = objeto.get("x").getAsInt();
            int paredeY = objeto.get("y").getAsInt();

            if (paredeX == destinoX && paredeY == destinoY) {
                return true;
            }
        }

        return false;
    }

    private void registarVisita(int x, int y) {
        String chave = chaveCoordenada(x, y);
        historicoVisitas.put(chave, historicoVisitas.getOrDefault(chave, 0) + 1);
    }

    private String escolherCasaMenosVisitada(int x, int y, List<String> acoesPossiveis) {
        String melhorAcao = null;
        int menorVisitas = Integer.MAX_VALUE;

        for (String acao : acoesPossiveis) {
            int[] destino = calcularDestino(x, y, acao);
            int visitas = historicoVisitas.getOrDefault(chaveCoordenada(destino[0], destino[1]), 0);

            if (visitas < menorVisitas) {
                menorVisitas = visitas;
                melhorAcao = acao;
            }
        }

        if (melhorAcao == null) {
            return acoesPossiveis.get(random.nextInt(acoesPossiveis.size()));
        }

        return melhorAcao;
    }

    private String irParaAlvoMaisProximo(JsonObject percecao, int x, int y, String nomeArray, List<String> acoesPossiveis) {
        if (!percecao.has(nomeArray) || !percecao.get(nomeArray).isJsonArray()) {
            return null;
        }

        JsonArray alvos = percecao.getAsJsonArray(nomeArray);

        if (alvos.isEmpty()) {
            return null;
        }

        JsonObject melhorAlvo = null;
        int melhorDistancia = Integer.MAX_VALUE;

        for (int i = 0; i < alvos.size(); i++) {
            JsonObject alvo = alvos.get(i).getAsJsonObject();

            if (!alvo.has("x") || !alvo.has("y")) {
                continue;
            }

            int alvoX = alvo.get("x").getAsInt();
            int alvoY = alvo.get("y").getAsInt();

            int distancia = Math.abs(alvoX - x) + Math.abs(alvoY - y);

            if (distancia < melhorDistancia) {
                melhorDistancia = distancia;
                melhorAlvo = alvo;
            }
        }

        if (melhorAlvo == null) {
            return null;
        }

        int alvoX = melhorAlvo.get("x").getAsInt();
        int alvoY = melhorAlvo.get("y").getAsInt();

        return escolherDirecaoParaAlvo(x, y, alvoX, alvoY, acoesPossiveis);
    }

    private String escolherDirecaoParaAlvo(int x, int y, int alvoX, int alvoY, List<String> acoesPossiveis) {
        List<String> prioridades = new ArrayList<>();

        if (alvoX > x) prioridades.add("MOVER_ESTE");
        if (alvoX < x) prioridades.add("MOVER_OESTE");
        if (alvoY > y) prioridades.add("MOVER_SUL");
        if (alvoY < y) prioridades.add("MOVER_NORTE");

        for (String acao : prioridades) {
            if (acoesPossiveis.contains(acao)) {
                return acao;
            }
        }

        return null;
    }

    private String tentarFugirDeInimigo(JsonObject percecao, int x, int y, int energia, List<String> acoesPossiveis) {
        JsonObject inimigo = inimigoMaisProximo(percecao, x, y);

        if (inimigo == null) {
            return null;
        }

        int inimigoX = inimigo.get("x").getAsInt();
        int inimigoY = inimigo.get("y").getAsInt();
        int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 999;

        int distancia = Math.abs(inimigoX - x) + Math.abs(inimigoY - y);

        if (distancia <= 2 && energia < energiaInimigo) {
            return escolherDirecaoOposta(x, y, inimigoX, inimigoY, acoesPossiveis);
        }

        return null;
    }

    private String tentarAtacarInimigo(JsonObject percecao, int x, int y, int energia, List<String> acoesPossiveis) {
        JsonObject inimigo = inimigoMaisProximo(percecao, x, y);

        if (inimigo == null) {
            return null;
        }

        int inimigoX = inimigo.get("x").getAsInt();
        int inimigoY = inimigo.get("y").getAsInt();
        int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 0;

        int distancia = Math.abs(inimigoX - x) + Math.abs(inimigoY - y);

        if (distancia <= 2 && energia > energiaInimigo + 20) {
            return escolherDirecaoParaAlvo(x, y, inimigoX, inimigoY, acoesPossiveis);
        }

        return null;
    }

    private JsonObject inimigoMaisProximo(JsonObject percecao, int x, int y) {
        if (!percecao.has("outros_robots") || !percecao.get("outros_robots").isJsonArray()) {
            return null;
        }

        JsonArray inimigos = percecao.getAsJsonArray("outros_robots");

        JsonObject melhorInimigo = null;
        int melhorDistancia = Integer.MAX_VALUE;

        for (int i = 0; i < inimigos.size(); i++) {
            JsonObject inimigo = inimigos.get(i).getAsJsonObject();

            if (!inimigo.has("x") || !inimigo.has("y")) {
                continue;
            }

            int inimigoX = inimigo.get("x").getAsInt();
            int inimigoY = inimigo.get("y").getAsInt();

            int distancia = Math.abs(inimigoX - x) + Math.abs(inimigoY - y);

            if (distancia < melhorDistancia) {
                melhorDistancia = distancia;
                melhorInimigo = inimigo;
            }
        }

        return melhorInimigo;
    }

    private String escolherDirecaoOposta(int x, int y, int inimigoX, int inimigoY, List<String> acoesPossiveis) {
        List<String> prioridades = new ArrayList<>();

        if (inimigoX > x) prioridades.add("MOVER_OESTE");
        if (inimigoX < x) prioridades.add("MOVER_ESTE");
        if (inimigoY > y) prioridades.add("MOVER_NORTE");
        if (inimigoY < y) prioridades.add("MOVER_SUL");

        for (String acao : prioridades) {
            if (acoesPossiveis.contains(acao)) {
                return acao;
            }
        }

        return escolherCasaMenosVisitada(x, y, acoesPossiveis);
    }

    private int[] calcularDestino(int x, int y, String acao) {
        return switch (acao) {
            case "MOVER_NORTE" -> new int[]{x, y - 1};
            case "MOVER_SUL" -> new int[]{x, y + 1};
            case "MOVER_ESTE" -> new int[]{x + 1, y};
            case "MOVER_OESTE" -> new int[]{x - 1, y};
            default -> new int[]{x, y};
        };
    }

    private String chaveCoordenada(int x, int y) {
        return x + "," + y;
    }
}