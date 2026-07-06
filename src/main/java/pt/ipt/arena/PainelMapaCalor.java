package pt.ipt.arena;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PainelMapaCalor extends JPanel {

    private final Map<String, Integer> visitas = new HashMap<>();
    private final Set<String> paredes = new HashSet<>();
    private final Set<String> cofres = new HashSet<>();
    private final Set<String> recursos = new HashSet<>();
    private final Set<String> inimigos = new HashSet<>();
    private String posicaoAtual;

    public void registarVisita(int x, int y) {
        String chave = x + "," + y;
        visitas.put(chave, visitas.getOrDefault(chave, 0) + 1);
        posicaoAtual = chave;
        repaint();
    }

    public void atualizarMapa(Map<String, Integer> novasVisitas,
                              Set<String> novasParedes,
                              Set<String> novosCofres,
                              Set<String> novosRecursos,
                              Set<String> novosInimigos,
                              int atualX,
                              int atualY) {
        visitas.clear();
        visitas.putAll(novasVisitas);

        paredes.clear();
        paredes.addAll(novasParedes);

        cofres.clear();
        cofres.addAll(novosCofres);

        recursos.clear();
        recursos.addAll(novosRecursos);

        inimigos.clear();
        inimigos.addAll(novosInimigos);

        posicaoAtual = atualX + "," + atualY;
        repaint();
    }

    public Map<String, Integer> getVisitas() {
        return Collections.unmodifiableMap(visitas);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(32, 38, 46));
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString("Mapa de Calor do Agente", 20, 24);

        int tamanhoCelula = 25;
        int origemX = 20;
        int origemY = 46;

        Rectangle limites = calcularLimites();
        if (limites == null) {
            g2.setFont(getFont().deriveFont(12f));
            g2.drawString("A aguardar telemetria da arena...", origemX, origemY + 24);
            g2.dispose();
            return;
        }

        int minX = limites.x;
        int minY = limites.y;

        desenharConjunto(g2, paredes, origemX, origemY, minX, minY, tamanhoCelula,
                new Color(57, 62, 70), null);
        desenharConjunto(g2, recursos, origemX, origemY, minX, minY, tamanhoCelula,
                new Color(44, 156, 111), "E");
        desenharConjunto(g2, cofres, origemX, origemY, minX, minY, tamanhoCelula,
                new Color(111, 89, 176), "C");
        desenharConjunto(g2, inimigos, origemX, origemY, minX, minY, tamanhoCelula,
                new Color(194, 54, 54), "I");

        for (Map.Entry<String, Integer> entrada : visitas.entrySet()) {
            int[] coord = descodificar(entrada.getKey());
            int valor = entrada.getValue();
            int intensidade = Math.min(220, 35 + valor * 28);
            int px = origemX + (coord[0] - minX) * tamanhoCelula;
            int py = origemY + (coord[1] - minY) * tamanhoCelula;

            g2.setColor(new Color(255, Math.max(80, 230 - intensidade), 80));
            g2.fillRect(px, py, tamanhoCelula, tamanhoCelula);
            g2.setColor(new Color(120, 54, 40));
            g2.drawRect(px, py, tamanhoCelula, tamanhoCelula);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.drawString(String.valueOf(valor), px + 8, py + 17);
        }

        if (posicaoAtual != null) {
            int[] coord = descodificar(posicaoAtual);
            int px = origemX + (coord[0] - minX) * tamanhoCelula;
            int py = origemY + (coord[1] - minY) * tamanhoCelula;
            g2.setColor(new Color(28, 97, 209));
            g2.fillOval(px + 5, py + 5, tamanhoCelula - 10, tamanhoCelula - 10);
            g2.setColor(Color.WHITE);
            g2.drawString("R", px + 9, py + 17);
        }

        g2.setColor(new Color(88, 96, 105));
        g2.setFont(getFont().deriveFont(11f));
        g2.drawString("R robo  C cofre  E energia  I inimigo  cinzento parede", 20, getHeight() - 14);
        g2.dispose();
    }

    private void desenharConjunto(Graphics2D g2, Set<String> conjunto, int origemX, int origemY,
                                  int minX, int minY, int tamanhoCelula, Color cor, String etiqueta) {
        for (String chave : conjunto) {
            int[] coord = descodificar(chave);
            int px = origemX + (coord[0] - minX) * tamanhoCelula;
            int py = origemY + (coord[1] - minY) * tamanhoCelula;
            g2.setColor(cor);
            g2.fillRect(px, py, tamanhoCelula, tamanhoCelula);
            g2.setColor(new Color(45, 48, 54));
            g2.drawRect(px, py, tamanhoCelula, tamanhoCelula);
            if (etiqueta != null) {
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                g2.drawString(etiqueta, px + 8, py + 17);
            }
        }
    }

    private Rectangle calcularLimites() {
        Set<String> todas = new HashSet<>();
        todas.addAll(visitas.keySet());
        todas.addAll(paredes);
        todas.addAll(cofres);
        todas.addAll(recursos);
        todas.addAll(inimigos);
        if (posicaoAtual != null) {
            todas.add(posicaoAtual);
        }
        if (todas.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (String chave : todas) {
            int[] coord = descodificar(chave);
            minX = Math.min(minX, coord[0]);
            minY = Math.min(minY, coord[1]);
            maxX = Math.max(maxX, coord[0]);
            maxY = Math.max(maxY, coord[1]);
        }

        return new Rectangle(minX - 1, minY - 1, maxX - minX + 3, maxY - minY + 3);
    }

    private int[] descodificar(String chave) {
        int separador = chave.indexOf(',');
        int x = Integer.parseInt(chave.substring(0, separador));
        int y = Integer.parseInt(chave.substring(separador + 1));
        return new int[]{x, y};
    }
}
