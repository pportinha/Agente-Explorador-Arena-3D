package pt.ipt.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.swing.SwingUtilities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Motor deterministico de navegacao.
 *
 * Mantem memoria das paredes, visitas, alvos e cofres falhados. Quando existe
 * um alvo visivel, calcula um caminho com custo ponderado pelo mapa de calor.
 * Quando nao existe alvo, procura uma fronteira fria para continuar a revelar o
 * mapa em vez de oscilar no mesmo corredor.
 */
public class MotorHeuristico {

    private static final int MARGEM_BFS = 8;
    private static final int MAX_NOS_BUSCA = 4000;
    private static final int ENERGIA_CRITICA = 65;
    private static final int ENERGIA_CONFORTAVEL = 150;
    private static final int MEMORIA_POSICOES_RECENTES = 8;
    private static final int RAIO_EXPLORACAO = 10;
    private static final int RAIO_COMBATE = 5;
    private static final int RAIO_PERIGO = 4;
    private static final int VANTAGEM_ATAQUE = 20;
    private static final int ENERGIA_MINIMA_ATAQUE = 90;
    private static final int PENALIZACAO_PING_PONG = 60;

    private final Random random = new Random();
    private final Map<String, Integer> historicoVisitas = new HashMap<>();
    private final Set<String> cofresFalhados = new HashSet<>();
    private final Set<String> cofresResolvidos = new HashSet<>();
    private final Set<String> paredesConhecidas = new HashSet<>();
    private final Set<String> cofresConhecidos = new HashSet<>();
    private final Set<String> recursosConhecidos = new HashSet<>();
    private final Set<String> inimigosVisiveisMapa = new HashSet<>();
    private final Deque<String> filaAcoesPlaneadas = new ArrayDeque<>();
    private final Deque<String> posicoesRecentes = new ArrayDeque<>();
    private final PainelMapaCalor painelMapaCalor;

    public MotorHeuristico() {
        this(null);
    }

    public MotorHeuristico(PainelMapaCalor painelMapaCalor) {
        this.painelMapaCalor = painelMapaCalor;
    }

    public void marcarCofreFalhado(int x, int y) {
        String chave = chaveCoordenada(x, y);
        cofresFalhados.add(chave);
        cofresConhecidos.remove(chave);
    }

    public void marcarCofreResolvido(int x, int y) {
        String chave = chaveCoordenada(x, y);
        cofresResolvidos.add(chave);
        cofresConhecidos.remove(chave);
    }

    public boolean cofreEstaNaListaNegra(int x, int y) {
        return cofresFalhados.contains(chaveCoordenada(x, y));
    }

    public String decidirProximaAcao(JsonObject percecao) {
        JsonObject estado = percecao.getAsJsonObject("o_meu_estado");

        int x = arredondar(estado, "x");
        int y = arredondar(estado, "y");
        int energia = estado.has("energia") ? estado.get("energia").getAsInt() : 200;

        memorizarParedes(percecao);
        memorizarAlvos(percecao);
        memorizarInimigos(percecao);
        registarVisita(x, y);
        registarPosicaoRecente(x, y);
        atualizarPainel(x, y);

        List<String> acoesPossiveis = obterAcoesPossiveis(x, y);
        if (acoesPossiveis.isEmpty()) {
            return "MOVER_NORTE";
        }

        String reflexo = obterAcaoPlaneadaValida(x, y, acoesPossiveis);
        if (reflexo != null) {
            return reflexo;
        }

        String fuga = tentarFugirDeInimigo(percecao, x, y, energia, acoesPossiveis);
        if (fuga != null) {
            planearPassoExtraSeguro(x, y, fuga);
            return fuga;
        }

        String ataqueImediato = tentarAtacarInimigo(percecao, x, y, energia, 2);
        if (ataqueImediato != null) {
            return ataqueImediato;
        }

        if (energia <= ENERGIA_CRITICA) {
            String passoRecurso = passoDiretoParaAlvo(percecao, x, y, "recursos_no_mundo");
            if (passoRecurso != null) {
                return passoRecurso;
            }
        }

        String passoCofre = passoDiretoParaCofre(x, y, percecao);
        if (passoCofre != null) {
            return passoCofre;
        }

        String ataque = tentarAtacarInimigo(percecao, x, y, energia, RAIO_COMBATE);
        if (ataque != null) {
            return ataque;
        }

        String passoRecurso = passoDiretoParaAlvo(percecao, x, y, "recursos_no_mundo");
        if (passoRecurso != null) {
            return passoRecurso;
        }

        String exploracao = passoParaFronteiraFria(x, y);
        if (exploracao != null && acoesPossiveis.contains(exploracao)) {
            return aplicarAntiOscilacao(x, y, exploracao, acoesPossiveis);
        }

        return aplicarAntiOscilacao(x, y, escolherCasaMenosVisitada(x, y, acoesPossiveis), acoesPossiveis);
    }

    private String passoDiretoParaAlvo(JsonObject percecao, int x, int y, String nomeArray) {
        List<int[]> alvos = obterAlvos(percecao, nomeArray);
        if (alvos.isEmpty()) {
            return null;
        }
        return primeiroPassoDireto(x, y, alvos);
    }

    private String passoDiretoParaCofre(int x, int y, JsonObject percecao) {
        List<int[]> alvos = obterAlvos(percecao, "cofres_no_mundo");
        for (String chave : cofresConhecidos) {
            if (cofresFalhados.contains(chave) || cofresResolvidos.contains(chave)) {
                continue;
            }
            int[] coord = descodificarChave(chave);
            alvos.add(coord);
        }
        if (alvos.isEmpty()) {
            return null;
        }
        return primeiroPassoDireto(x, y, alvos);
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
            String chave = chaveCoordenada(ax, ay);
            if (ehCofre && (cofresFalhados.contains(chave) || cofresResolvidos.contains(chave))) {
                continue;
            }
            alvos.add(new int[]{ax, ay});
        }
        return alvos;
    }

    private String primeiroPassoDireto(int inicioX, int inicioY, List<int[]> alvos) {
        Set<String> conjuntoAlvos = new HashSet<>();
        int minX = inicioX;
        int maxX = inicioX;
        int minY = inicioY;
        int maxY = inicioY;

        for (int[] alvo : alvos) {
            conjuntoAlvos.add(chaveCoordenada(alvo[0], alvo[1]));
            minX = Math.min(minX, alvo[0]);
            maxX = Math.max(maxX, alvo[0]);
            minY = Math.min(minY, alvo[1]);
            maxY = Math.max(maxY, alvo[1]);
        }

        minX -= MARGEM_BFS;
        maxX += MARGEM_BFS;
        minY -= MARGEM_BFS;
        maxY += MARGEM_BFS;

        Map<String, String> pai = new HashMap<>();
        Deque<int[]> fila = new ArrayDeque<>();
        String chaveInicio = chaveCoordenada(inicioX, inicioY);
        pai.put(chaveInicio, null);
        fila.add(new int[]{inicioX, inicioY});

        int expandidos = 0;

        while (!fila.isEmpty() && expandidos < MAX_NOS_BUSCA) {
            int[] atual = fila.poll();
            String chaveAtual = chaveCoordenada(atual[0], atual[1]);
            expandidos++;

            if (conjuntoAlvos.contains(chaveAtual) && !chaveAtual.equals(chaveInicio)) {
                return reconstruirPrimeiroPasso(pai, chaveInicio, chaveAtual, inicioX, inicioY);
            }

            for (int[] d : direcoesOrdenadasParaAlvo(atual[0], atual[1], alvos)) {
                int nx = atual[0] + d[0];
                int ny = atual[1] + d[1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY || temParede(nx, ny)) {
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

        return null;
    }

    private String passoParaFronteiraFria(int inicioX, int inicioY) {
        String chaveInicio = chaveCoordenada(inicioX, inicioY);
        Map<String, String> pai = new HashMap<>();
        Deque<int[]> fila = new ArrayDeque<>();
        pai.put(chaveInicio, null);
        fila.add(new int[]{inicioX, inicioY});

        String melhor = null;
        int melhorPontuacao = Integer.MAX_VALUE;
        int expandidos = 0;

        while (!fila.isEmpty() && expandidos < MAX_NOS_BUSCA) {
            int[] atual = fila.poll();
            expandidos++;
            int cx = atual[0];
            int cy = atual[1];
            int distancia = Math.abs(cx - inicioX) + Math.abs(cy - inicioY);
            if (distancia > RAIO_EXPLORACAO) {
                continue;
            }

            String chaveAtual = chaveCoordenada(cx, cy);
            if (!chaveAtual.equals(chaveInicio)) {
                int pontuacao = custoCasa(cx, cy) + distancia;
                pontuacao -= contarVizinhosDesconhecidos(cx, cy) * 8;
                if (cx == inicioX) {
                    pontuacao += 6;
                }
                if (pontuacao < melhorPontuacao) {
                    melhorPontuacao = pontuacao;
                    melhor = chaveAtual;
                }
            }

            for (int[] d : direcoesOrdenadasPorCalor(cx, cy)) {
                int nx = cx + d[0];
                int ny = cy + d[1];
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

        if (melhor == null) {
            return null;
        }
        return reconstruirPrimeiroPasso(pai, chaveInicio, melhor, inicioX, inicioY);
    }

    private String reconstruirPrimeiroPasso(Map<String, String> pai, String chaveInicio,
                                            String chaveAlvo, int inicioX, int inicioY) {
        String atual = chaveAlvo;
        String anterior = pai.get(atual);
        while (anterior != null && !anterior.equals(chaveInicio)) {
            atual = anterior;
            anterior = pai.get(atual);
        }

        int[] passo = descodificarChave(atual);
        return direcaoEntre(inicioX, inicioY, passo[0], passo[1]);
    }

    private String direcaoEntre(int x, int y, int nx, int ny) {
        if (nx == x + 1 && ny == y) {
            return "MOVER_ESTE";
        }
        if (nx == x - 1 && ny == y) {
            return "MOVER_OESTE";
        }
        if (ny == y + 1 && nx == x) {
            return "MOVER_SUL";
        }
        if (ny == y - 1 && nx == x) {
            return "MOVER_NORTE";
        }
        return "MOVER_NORTE";
    }

    private void memorizarParedes(JsonObject percecao) {
        if (!percecao.has("objetos_fixos") || !percecao.get("objetos_fixos").isJsonArray()) {
            return;
        }
        JsonArray objetos = percecao.getAsJsonArray("objetos_fixos");
        for (int i = 0; i < objetos.size(); i++) {
            JsonObject o = objetos.get(i).getAsJsonObject();
            if (o.has("x") && o.has("y")) {
                paredesConhecidas.add(chaveCoordenada(arredondar(o, "x"), arredondar(o, "y")));
            }
        }
    }

    private void memorizarAlvos(JsonObject percecao) {
        memorizarCoordenadas(percecao, "cofres_no_mundo", cofresConhecidos);
        memorizarCoordenadas(percecao, "recursos_no_mundo", recursosConhecidos);
        cofresConhecidos.removeAll(cofresFalhados);
        cofresConhecidos.removeAll(cofresResolvidos);
    }

    private void memorizarInimigos(JsonObject percecao) {
        inimigosVisiveisMapa.clear();
        for (JsonObject inimigo : inimigosVisiveis(percecao)) {
            inimigosVisiveisMapa.add(chaveCoordenada(arredondar(inimigo, "x"), arredondar(inimigo, "y")));
        }
    }

    private void memorizarCoordenadas(JsonObject percecao, String nomeArray, Set<String> destino) {
        if (!percecao.has(nomeArray) || !percecao.get(nomeArray).isJsonArray()) {
            return;
        }
        JsonArray objetos = percecao.getAsJsonArray(nomeArray);
        for (int i = 0; i < objetos.size(); i++) {
            JsonObject o = objetos.get(i).getAsJsonObject();
            if (o.has("x") && o.has("y")) {
                destino.add(chaveCoordenada(arredondar(o, "x"), arredondar(o, "y")));
            }
        }
    }

    private boolean temParede(int x, int y) {
        return paredesConhecidas.contains(chaveCoordenada(x, y));
    }

    private List<String> obterAcoesPossiveis(int x, int y) {
        List<String> acoes = new ArrayList<>();
        if (!temParede(x, y - 1)) {
            acoes.add("MOVER_NORTE");
        }
        if (!temParede(x, y + 1)) {
            acoes.add("MOVER_SUL");
        }
        if (!temParede(x + 1, y)) {
            acoes.add("MOVER_ESTE");
        }
        if (!temParede(x - 1, y)) {
            acoes.add("MOVER_OESTE");
        }
        return acoes;
    }

    private void registarVisita(int x, int y) {
        String chave = chaveCoordenada(x, y);
        historicoVisitas.put(chave, historicoVisitas.getOrDefault(chave, 0) + 1);
    }

    private void registarPosicaoRecente(int x, int y) {
        posicoesRecentes.addLast(chaveCoordenada(x, y));
        while (posicoesRecentes.size() > MEMORIA_POSICOES_RECENTES) {
            posicoesRecentes.removeFirst();
        }
    }

    private void atualizarPainel(int x, int y) {
        if (painelMapaCalor == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> painelMapaCalor.atualizarMapa(
                new HashMap<>(historicoVisitas),
                new HashSet<>(paredesConhecidas),
                new HashSet<>(cofresConhecidos),
                new HashSet<>(recursosConhecidos),
                new HashSet<>(inimigosVisiveisMapa),
                x,
                y
        ));
    }

    private String escolherCasaMenosVisitada(int x, int y, List<String> acoesPossiveis) {
        String melhorAcao = null;
        int melhorPontuacao = Integer.MAX_VALUE;

        for (String acao : acoesPossiveis) {
            int[] destino = calcularDestino(x, y, acao);
            int pontuacao = custoCasa(destino[0], destino[1]);
            pontuacao -= contarVizinhosDesconhecidos(destino[0], destino[1]) * 3;
            if (acao.equals("MOVER_ESTE") || acao.equals("MOVER_OESTE")) {
                pontuacao -= 2;
            }
            if (estaEmPingPong() && voltaParaCasaAnterior(x, y, acao)) {
                pontuacao += PENALIZACAO_PING_PONG;
            }
            if (pontuacao < melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhorAcao = acao;
            }
        }

        if (melhorAcao == null) {
            return acoesPossiveis.get(random.nextInt(acoesPossiveis.size()));
        }
        return melhorAcao;
    }

    private String aplicarAntiOscilacao(int x, int y, String acaoEscolhida, List<String> acoesPossiveis) {
        if (!estaEmPingPong() || !voltaParaCasaAnterior(x, y, acaoEscolhida)) {
            return acaoEscolhida;
        }

        String alternativa = null;
        int melhorPontuacao = Integer.MAX_VALUE;

        for (String acao : acoesPossiveis) {
            if (voltaParaCasaAnterior(x, y, acao)) {
                continue;
            }
            int[] destino = calcularDestino(x, y, acao);
            int pontuacao = custoCasa(destino[0], destino[1])
                    - contarVizinhosDesconhecidos(destino[0], destino[1]) * 6;
            if (pontuacao < melhorPontuacao) {
                melhorPontuacao = pontuacao;
                alternativa = acao;
            }
        }

        return alternativa != null ? alternativa : acaoEscolhida;
    }

    private boolean estaEmPingPong() {
        if (posicoesRecentes.size() < 4) {
            return false;
        }

        List<String> ultimas = new ArrayList<>(posicoesRecentes);
        int n = ultimas.size();
        String a = ultimas.get(n - 4);
        String b = ultimas.get(n - 3);
        String c = ultimas.get(n - 2);
        String d = ultimas.get(n - 1);

        return a.equals(c) && b.equals(d) && !a.equals(b);
    }

    private boolean voltaParaCasaAnterior(int x, int y, String acao) {
        String anterior = casaAnterior();
        if (anterior == null) {
            return false;
        }

        int[] destino = calcularDestino(x, y, acao);
        return anterior.equals(chaveCoordenada(destino[0], destino[1]));
    }

    private String casaAnterior() {
        if (posicoesRecentes.size() < 2) {
            return null;
        }

        List<String> ultimas = new ArrayList<>(posicoesRecentes);
        return ultimas.get(ultimas.size() - 2);
    }

    private int custoCasa(int x, int y) {
        String chave = chaveCoordenada(x, y);
        int visitas = historicoVisitas.getOrDefault(chave, 0);
        int repeticoesRecentes = 0;
        for (String posicao : posicoesRecentes) {
            if (posicao.equals(chave)) {
                repeticoesRecentes++;
            }
        }
        int bonusFronteira = temVizinhoDesconhecido(x, y) ? -2 : 0;
        return 1 + visitas * 4 + repeticoesRecentes * 5 + bonusFronteira;
    }

    private boolean temVizinhoDesconhecido(int x, int y) {
        return contarVizinhosDesconhecidos(x, y) > 0;
    }

    private int contarVizinhosDesconhecidos(int x, int y) {
        int desconhecidos = 0;
        for (int[] d : direcoesBase()) {
            int nx = x + d[0];
            int ny = y + d[1];
            String chave = chaveCoordenada(nx, ny);
            if (!paredesConhecidas.contains(chave) && !historicoVisitas.containsKey(chave)) {
                desconhecidos++;
            }
        }
        return desconhecidos;
    }

    private List<int[]> direcoesOrdenadasPorCalor(int x, int y) {
        List<int[]> direcoes = direcoesBase();
        direcoes.sort(Comparator.comparingInt(d -> custoCasa(x + d[0], y + d[1])));
        return direcoes;
    }

    private List<int[]> direcoesOrdenadasParaAlvo(int x, int y, List<int[]> alvos) {
        List<int[]> direcoes = direcoesBase();
        direcoes.sort(Comparator.comparingInt(d -> menorDistanciaAAlvos(x + d[0], y + d[1], alvos)));
        return direcoes;
    }

    private int menorDistanciaAAlvos(int x, int y, List<int[]> alvos) {
        int melhor = Integer.MAX_VALUE;
        for (int[] alvo : alvos) {
            int distancia = Math.abs(alvo[0] - x) + Math.abs(alvo[1] - y);
            melhor = Math.min(melhor, distancia);
        }
        return melhor;
    }

    private List<int[]> direcoesBase() {
        List<int[]> direcoes = new ArrayList<>();
        direcoes.add(new int[]{1, 0});
        direcoes.add(new int[]{-1, 0});
        direcoes.add(new int[]{0, -1});
        direcoes.add(new int[]{0, 1});
        return direcoes;
    }

    private String obterAcaoPlaneadaValida(int x, int y, List<String> acoesPossiveis) {
        while (!filaAcoesPlaneadas.isEmpty()) {
            String acao = filaAcoesPlaneadas.pollFirst();
            int[] destino = calcularDestino(x, y, acao);
            if (acoesPossiveis.contains(acao) && !temParede(destino[0], destino[1])) {
                return acao;
            }
        }
        return null;
    }

    private void planearPassoExtraSeguro(int x, int y, String primeiroPasso) {
        int[] destino = calcularDestino(x, y, primeiroPasso);
        List<String> proximas = obterAcoesPossiveis(destino[0], destino[1]);
        if (proximas.isEmpty()) {
            return;
        }

        String extra = escolherCasaMenosVisitada(destino[0], destino[1], proximas);
        if (extra != null) {
            filaAcoesPlaneadas.addLast(extra);
        }
    }

    private String tentarFugirDeInimigo(JsonObject percecao, int x, int y,
                                        int energia, List<String> acoesPossiveis) {
        List<JsonObject> inimigos = inimigosVisiveis(percecao);
        JsonObject inimigo = inimigoMaisProximo(inimigos, x, y);
        if (inimigo == null) {
            return null;
        }

        int ix = arredondar(inimigo, "x");
        int iy = arredondar(inimigo, "y");
        int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 999;
        int distancia = Math.abs(ix - x) + Math.abs(iy - y);
        boolean ameacaForte = energia < energiaInimigo + 15;
        boolean energiaBaixa = energia < ENERGIA_CONFORTAVEL && energia <= energiaInimigo;

        if (distancia <= RAIO_PERIGO && (ameacaForte || energiaBaixa)) {
            return escolherMelhorFuga(x, y, inimigos, acoesPossiveis);
        }
        return null;
    }

    private String tentarAtacarInimigo(JsonObject percecao, int x, int y, int energia, int raio) {
        List<JsonObject> inimigos = inimigosVisiveis(percecao);
        JsonObject inimigo = melhorInimigoParaAtacar(inimigos, x, y, energia, raio);
        if (inimigo == null) {
            return null;
        }

        int ix = arredondar(inimigo, "x");
        int iy = arredondar(inimigo, "y");

        List<int[]> alvos = new ArrayList<>();
        alvos.add(new int[]{ix, iy});
        return primeiroPassoDireto(x, y, alvos);
    }

    private List<JsonObject> inimigosVisiveis(JsonObject percecao) {
        List<JsonObject> inimigos = new ArrayList<>();
        if (!percecao.has("outros_robots") || !percecao.get("outros_robots").isJsonArray()) {
            return inimigos;
        }

        JsonArray arr = percecao.getAsJsonArray("outros_robots");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject inimigo = arr.get(i).getAsJsonObject();
            if (inimigo.has("x") && inimigo.has("y")) {
                inimigos.add(inimigo);
            }
        }
        return inimigos;
    }

    private JsonObject inimigoMaisProximo(List<JsonObject> inimigos, int x, int y) {
        JsonObject melhor = null;
        int melhorDist = Integer.MAX_VALUE;

        for (JsonObject inimigo : inimigos) {
            int ix = arredondar(inimigo, "x");
            int iy = arredondar(inimigo, "y");
            int dist = Math.abs(ix - x) + Math.abs(iy - y);
            if (dist < melhorDist) {
                melhorDist = dist;
                melhor = inimigo;
            }
        }
        return melhor;
    }

    private JsonObject melhorInimigoParaAtacar(List<JsonObject> inimigos, int x, int y,
                                               int energia, int raio) {
        if (energia < ENERGIA_MINIMA_ATAQUE) {
            return null;
        }

        JsonObject melhor = null;
        int melhorPontuacao = Integer.MIN_VALUE;

        for (JsonObject inimigo : inimigos) {
            int ix = arredondar(inimigo, "x");
            int iy = arredondar(inimigo, "y");
            int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 0;
            int distancia = Math.abs(ix - x) + Math.abs(iy - y);
            int vantagem = energia - energiaInimigo;

            if (distancia > raio || vantagem < VANTAGEM_ATAQUE) {
                continue;
            }

            int pontuacao = vantagem * 3 - distancia * 12 - energiaInimigo;
            if (pontuacao > melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhor = inimigo;
            }
        }

        return melhor;
    }

    private String escolherMelhorFuga(int x, int y, List<JsonObject> inimigos,
                                      List<String> acoesPossiveis) {
        String melhorAcao = null;
        int melhorPontuacao = Integer.MIN_VALUE;

        for (String acao : acoesPossiveis) {
            int[] destino = calcularDestino(x, y, acao);
            int menorDistancia = Integer.MAX_VALUE;
            int maiorPerigo = 0;

            for (JsonObject inimigo : inimigos) {
                int ix = arredondar(inimigo, "x");
                int iy = arredondar(inimigo, "y");
                int distancia = Math.abs(ix - destino[0]) + Math.abs(iy - destino[1]);
                int energiaInimigo = inimigo.has("energia") ? inimigo.get("energia").getAsInt() : 100;
                menorDistancia = Math.min(menorDistancia, distancia);
                maiorPerigo = Math.max(maiorPerigo, Math.max(0, energiaInimigo - distancia * 10));
                if (distancia == 0) {
                    maiorPerigo += 1000;
                } else if (distancia == 1) {
                    maiorPerigo += 120;
                }
            }

            int pontuacao = menorDistancia * 20 - maiorPerigo - custoCasa(destino[0], destino[1]);
            if (pontuacao > melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhorAcao = acao;
            }
        }

        if (melhorAcao == null) {
            return escolherCasaMenosVisitada(x, y, acoesPossiveis);
        }
        return melhorAcao;
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

    private int arredondar(JsonObject objeto, String chave) {
        return (int) Math.round(objeto.get(chave).getAsDouble());
    }

    private String chaveCoordenada(int x, int y) {
        return x + "," + y;
    }

    private int[] descodificarChave(String chave) {
        int separador = chave.indexOf(',');
        int x = Integer.parseInt(chave.substring(0, separador));
        int y = Integer.parseInt(chave.substring(separador + 1));
        return new int[]{x, y};
    }

}
