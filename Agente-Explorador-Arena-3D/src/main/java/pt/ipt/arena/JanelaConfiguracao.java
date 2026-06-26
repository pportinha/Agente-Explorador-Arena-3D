package pt.ipt.arena;

import javax.swing.*;
import java.awt.*;

public class JanelaConfiguracao extends JDialog {

    private JTextField campoRobotId;
    private JTextField campoRoomId;
    private JTextField campoServidor;
    private JCheckBox modoHeuristicaPura;

    private boolean confirmado = false;

    public JanelaConfiguracao() {
        setTitle("Configuração do Agente - Arena SaaS 2026");
        setModal(true);
        setSize(500, 260);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        criarInterface();
    }

    private void criarInterface() {
        JPanel painel = new JPanel(new GridLayout(5, 2, 10, 10));
        painel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        campoRobotId = new JTextField("Gondro");
        campoRoomId = new JTextField();
        campoServidor = new JTextField("https://arena.pmonteiro.ovh");
        modoHeuristicaPura = new JCheckBox("Modo Heurística Pura (Sem LLM)");

        painel.add(new JLabel("Identificador do Robô:"));
        painel.add(campoRobotId);

        painel.add(new JLabel("Código da Sala:"));
        painel.add(campoRoomId);

        painel.add(new JLabel("Servidor Alvo:"));
        painel.add(campoServidor);

        painel.add(new JLabel("Desempenho:"));
        painel.add(modoHeuristicaPura);

        JButton botaoOk = new JButton("OK");
        JButton botaoCancelar = new JButton("Cancelar");

        botaoOk.addActionListener(e -> confirmar());
        botaoCancelar.addActionListener(e -> cancelar());

        painel.add(botaoOk);
        painel.add(botaoCancelar);

        add(painel);
    }

    private void confirmar() {
        if (getRobotId().isBlank() || getRoomId().isBlank() || getServidor().isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Preenche o identificador do robô, o código da sala e o servidor.",
                    "Campos obrigatórios",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        confirmado = true;
        dispose();
    }

    private void cancelar() {
        confirmado = false;
        dispose();
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    public String getRobotId() {
        return campoRobotId.getText().trim();
    }

    public String getRoomId() {
        return campoRoomId.getText().trim();
    }

    public String getServidor() {
        return campoServidor.getText().trim();
    }

    public boolean isModoHeuristicaPura() {
        return modoHeuristicaPura.isSelected();
    }
}