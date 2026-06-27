package pt.ipt.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Motor Determinístico (Heurística de navegação).
 *
 * Constrói um mapa das paredes que vai vendo (memória persistente) e usa
 * pesquisa em largura (BFS) para traçar o caminho mais curto, contornando
 * obstáculos, até ao cofre ou recurso mais próximo. Quando não há alvo
 * alcançável, explora pelo mapa de calor (escolhe a casa menos visitada).
 */
public class MotorHeuristico {

    private static final int MARGEM_BFS = 8;          // expande a área de busca à volta de início/alvo
    private static final int MAX_NOS_BFS = 4000;      // teto de segurança da pesquisa
    private static final int ENERGIA_PROCURA_RECURSO = 120;

    private final Random random = new Random();
    private final Map<String, Integer> historicoVisitas = new HashMap<>();
    private final Set<String> cofresFalhados = new HashSet<>();
    private final Set<String> paredesConhecidas = new HashSet<>(); // memória do mapa (Fig. 2 do enunciado)

    // ---- Lista Negra de cofres ----

    public void marcarCofreFalhado(int x, int y) {
        cofresFalhados.add(chaveCoordenada(x, y));
    }

    public boolean cofreEstaNaListaNegra(int x, int y) {
        return cofresFalhados.contains(chaveCoordenada(x, y));
    }

    // ---- Decisão principal ----

    public String decidirProximaAcao(JsonObject percecao) {
        JsonObject estado = percecao.getAsJsonObject("o_meu_estado");

        int x = arredondar(estado, "x");
        int y = arredondar(estado, "y");
        int energia = estado.has("energia") ? estado.get("energia").getAsInt() : 200;

        memorizarParedes(percecao);
        registarVisita(x, y);

        List<String> acoesPossiveis = obterAcoesPossiveis(x, y);
        if (acoesPossiveis.isEmpty()) {
            return "MOVER_NORTE"; // encurralado: tenta algo
        }

        // Combate (em modo Missão o array vem vazio e estes métodos não fazem nada).
        String fuga = tentarFugirDeInimigo(percecao, x, y, energia, acoesPossiveis);
        if (fuga != null) {
            return fuga;
        }
        String ataque = tentarAtacarInimigo(percecao, x, y, energia, acoesPossiveis);
        if (ataque != null) {
            return ataque;
        }

        // Com pouca energia, prioriza recursos.
        if (energia < ENERGIA_PROCURA_RECURSO) {
            String passoRecurso = passoBFSparaAlvo(percecao, x, y, "recursos_no_mundo");
            if (passoRecurso != null) {
                return passoRecurso;
            }
        }

        // Objetivo principal: o cofre alcançável mais próximo (ignorando a Lista Negra).
        String passoCofre = passoBFSparaAlvo(percecao, x, y, "cofres_no_mundo");
        if (passoCofre != null) {
            return passoCofre;
        }

        // Recurso, mesmo com energia razoável.
        String passoRecurso = passoBFSparaAlvo(percecao, x, y, "recursos_no_mundo");
        if (passoRecurso != null) {
            return passoRecurso;
        }

        // Sem alvo alcançável conhecido: explora a casa mais "fria".
        return escolherCasaMenosVisitada(x, y, acoesPossiveis);
    }

    // ---- Pesquisa em largura (BFS) ----

    /**
     * Devolve o primeiro passo do caminho mais curto até ao alvo alcançável mais
     * próximo do tipo pedido, contornando as paredes conhecidas. null se não
     * houver alvos ou se nenhum for alcançável pelo mapa atual.
     */
    private String passoBFSparaAlvo(JsonObject percecao, int x, int y, String nomeArray) {
        List<int[]> alvos = obterAlvos(percecao, nomeArray);
        if (alvos.isEmpty()) {
            return null;
        }
        return primeiroPassoBFS(x, y, alvos);
    }

    private List<int[]> obterAlvos(JsonObject percecao, String nomeArray) {
        List<int[]> alvos = new ArrayList<>();
        if (!percecao.has(nomeArray) || !percecao.get(nomeArray).isJsonArray()) {
            return alvos;
        }

        JsonArray arr = percecao.getAsJsonArray(nomeArray);
        boolean ehCofre = nomeArray.equals("cofres_no_mundo");

        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            if (!o.has("x") || !o.has("y")) {
                continue;
            }
            int ax = arredondar(o, "x");
            int ay = arredondar(o, "y");

            if (ehCofre && cofresFalhados.contains(chaveCoordenada(ax, ay))) {
                continue; // não persegue cofres já falhados
            }
            alvos.add(new int[]{ax, ay});
        }
        return alvos;
    }

    private String primeiroPassoBFS(int inicioX, int inicioY, List<int[]> alvos) {
        Set<String> conjuntoAlvos = new HashSet<>();
        int minX = inicioX, maxX = inicioX, minY = inicioY, maxY = inicioY;
        for (int[] a : alvos) {
            conjuntoAlvos.add(chaveCoordenada(a[0], a[1]));
            minX = Math.min(minX, a[0]); maxX = Math.max(maxX, a[0]);
            minY = Math.min(minY, a[1]); maxY = Math.max(maxY, a[1]);
        }
        minX -= MARGEM_BFS; maxX += MARGEM_BFS;
        minY -= MARGEM_BFS; maxY += MARGEM_BFS;

        Map<String, String> pai = new HashMap<>(); // filho -> pai
        Deque<int[]> fila = new ArrayDeque<>();
        String chaveInicio = chaveCoordenada(inicioX, inicioY);
        pai.put(chaveInicio, null);
        fila.add(new int[]{inicioX, inicioY});

        int[][] direcoes = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}}; // N, S, E, O
        int expandidos = 0;

        while (!fila.isEmpty() && expandidos < MAX_NOS_BFS) {
            int[] atual = fila.poll();
            expandidos++;
            int cx = atual[0], cy = atual[1];
            String chaveAtual = chaveCoordenada(cx, cy);

            // Chegámos a um alvo (e não estamos já em cima dele): reconstrói o caminho.
            if (conjuntoAlvos.contains(chaveAtual) && !chaveAtual.equals(chaveInicio)) {
                return reconstruirPrimeiroPasso(pai, chaveInicio, chaveAtual, inicioX, inicioY);
            }

            for (int[] d : direcoes) {
                int nx = cx + d[0], ny = cy + d[1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) {
                    continue;
                }
                if (temParede(nx, ny)) {
                    continue;
                }
                String chaveViz = chaveCoordenada(nx, ny);
                if (pai.containsKey(chaveViz)) {
                    continue;
                }
                pai.put(chaveViz, chaveAtual);
                fila.add(new int[]{nx, ny});
            }
        }
        return null; // alvo inalcançável com o mapa atual
    }

    /**
     * Sobe pela cadeia de pais desde o alvo até ao início e devolve a direção
     * do primeiro passo (a casa adjacente ao início no caminho encontrado).
     */
    private String reconstruirPrimeiroPasso(Map<String, String> pai, String chaveInicio,
                                            String chaveAlvo, int inicioX, int inicioY) {
        String atual = chaveAlvo;
        String anterior = pai.get(atual);
        while (anterior != null && !anterior.equals(chaveInicio)) {
            atual = anterior;
            anterior = pai.get(atual);
        }
        // 'atual' é agora a casa adjacente ao início.
        int[] passo = descodificarChave(atual);
        return direcaoEntre(inicioX, inicioY, passo[0], passo[1]);
    }

    private String direcaoEntre(int x, int y, int nx, int ny) {
        if (nx == x + 1 && ny == y) return "MOVER_ESTE";
        if (nx == x - 1 && ny == y) return "MOVER_OESTE";
        if (ny == y + 1 && nx == x) return "MOVER_SUL";
        if (ny == y - 1 && nx == x) return "MOVER_NORTE";
        return "MOVER_NORTE";
    }

    // ---- Mapa, paredes e exploração ----

    private void memorizarParedes(JsonObject percecao) {
        if (!percecao.has("objetos_fixos") || !percecao.get("objetos_fixos").isJsonArray()) {
            return;
        }
        JsonArray objetos = percecao.getAsJsonArray("objetos_fixos");
        for (int i = 0; i < objetos.size(); i++) {
            JsonObject o = objetos.get(i).getAsJsonObject();
            if (!o.has("x") || !o.has("y")) {
                continue;
            }
            paredesConhecidas.add(chaveCoordenada(arredondar(o, "x"), arredondar(o, "y")));
        }
    }

    private boolean temParede(int x, int y) {
        return paredesConhecidas.contains(chaveCoordenada(x, y));
    }

    private List<String> obterAcoesPossiveis(int x, int y) {
        List<String> acoes = new ArrayList<>();
        if (!temParede(x, y - 1)) acoes.add("MOVER_NORTE");
        if (!temParede(x, y + 1)) acoes.add("MOVER_SUL");
        if (!temParede(x + 1, y)) acoes.add("MOVER_ESTE");
        if (!temParede(x - 1, y)) acoes.add("MOVER_OESTE");
        return acoes;
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

    // ---- Combate (Battle Royale). Inativo em modo Missão. ----

    private String tentarFugirDeInimigo(JsonObject percecao, int x, int y, int energia, List<String> acoesPossiveis) {
        JsonObject inimigo = inimigoMaisProximo(percecao, x, y);
        if (inimigo == null) {
            return null;
        }
        int ix = inimigo.get("x").getAsInt();
        int iy = inimigo.get("y").getAsInt();
        int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 999;
        int distancia = Math.abs(ix - x) + Math.abs(iy - y);

        if (distancia <= 2 && energia < energiaInimigo) {
            return escolherDirecaoOposta(x, y, ix, iy, acoesPossiveis);
        }
        return null;
    }

    private String tentarAtacarInimigo(JsonObject percecao, int x, int y, int energia, List<String> acoesPossiveis) {
        JsonObject inimigo = inimigoMaisProximo(percecao, x, y);
        if (inimigo == null) {
            return null;
        }
        int ix = inimigo.get("x").getAsInt();
        int iy = inimigo.get("y").getAsInt();
        int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 0;
        int distancia = Math.abs(ix - x) + Math.abs(iy - y);

        if (distancia <= 2 && energia > energiaInimigo + 20) {
            return escolherDirecaoParaAlvo(x, y, ix, iy, acoesPossiveis);
        }
        return null;
    }

    private JsonObject inimigoMaisProximo(JsonObject percecao, int x, int y) {
        if (!percecao.has("outros_robots") || !percecao.get("outros_robots").isJsonArray()) {
            return null; // no JSON desta arena vem como {} (objeto vazio): sem inimigos
        }
        JsonArray inimigos = percecao.getAsJsonArray("outros_robots");
        JsonObject melhor = null;
        int melhorDist = Integer.MAX_VALUE;

        for (int i = 0; i < inimigos.size(); i++) {
            JsonObject inimigo = inimigos.get(i).getAsJsonObject();
            if (!inimigo.has("x") || !inimigo.has("y")) {
                continue;
            }
            int ix = inimigo.get("x").getAsInt();
            int iy = inimigo.get("y").getAsInt();
            int dist = Math.abs(ix - x) + Math.abs(iy - y);
            if (dist < melhorDist) {
                melhorDist = dist;
                melhor = inimigo;
            }
        }
        return melhor;
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

    // ---- Utilitários ----

    private int[] calcularDestino(int x, int y, String acao) {
        return switch (acao) {
            case "MOVER_NORTE" -> new int[]{x, y - 1};
            case "MOVER_SUL" -> new int[]{x, y + 1};
            case "MOVER_ESTE" -> new int[]{x + 1, y};
            case "MOVER_OESTE" -> new int[]{x - 1, y};
            default -> new int[]{x, y};
        };
    }

    /** Coordenadas vêm como decimais (ex.: 2.0): lê como double e arredonda. */
    private int arredondar(JsonObject objeto, String chave) {
        return (int) Math.round(objeto.get(chave).getAsDouble());
    }

    private String chaveCoordenada(int x, int y) {
        return x + "," + y;
    }

    private int[] descodificarChave(String chave) {
        int v = chave.indexOf(',');
        int x = Integer.parseInt(chave.substring(0, v));
        int y = Integer.parseInt(chave.substring(v + 1));
        return new int[]{x, y};
    }
}