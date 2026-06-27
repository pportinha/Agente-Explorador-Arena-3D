package pt.ipt.arena;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class PainelMapaCalor extends JPanel {

    private final Map<String, Integer> visitas = new HashMap<>();

    public void registarVisita(int x, int y) {
        String chave = x + "," + y;
        visitas.put(chave, visitas.getOrDefault(chave, 0) + 1);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawString("Mapa de Calor do Agente", 20, 20);

        int tamanhoCelula = 25;
        int origemX = 20;
        int origemY = 40;

        for (Map.Entry<String, Integer> entrada : visitas.entrySet()) {
            String[] partes = entrada.getKey().split(",");
            int x = Integer.parseInt(partes[0]);
            int y = Integer.parseInt(partes[1]);
            int valor = entrada.getValue();

            int intensidade = Math.min(255, valor * 40);

            g.drawRect(origemX + x * tamanhoCelula, origemY + y * tamanhoCelula, tamanhoCelula, tamanhoCelula);
            g.drawString(String.valueOf(valor),
                    origemX + x * tamanhoCelula + 8,
                    origemY + y * tamanhoCelula + 17);
        }
    }
}