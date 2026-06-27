package pt.ipt.arena;

/**
 * Modelo de dados (POJO) que representa, em memória RAM, um pedaço (chunk) do
 * manual técnico e o seu respetivo vetor matemático (embedding).
 *
 * É a unidade base da nossa "base de dados vetorial" usada na fase de Retrieval
 * do pipeline RAG.
 */
public class DocumentoVetorial {

    private final String texto;
    private final double[] vetor;

    public DocumentoVetorial(String texto, double[] vetor) {
        this.texto = texto;
        this.vetor = vetor;
    }

    public String getTexto() {
        return texto;
    }

    public double[] getVetor() {
        return vetor;
    }

    /**
     * Calcula a Semelhança de Cossenos entre o vetor deste documento e outro
     * vetor (tipicamente o vetor do enigma do terminal).
     *
     * Quanto mais próximo de 1.0, mais semanticamente parecidos são os textos.
     */
    public double similaridadeCosseno(double[] outro) {
        if (outro == null || outro.length != vetor.length) {
            return -1.0;
        }

        double produtoInterno = 0.0;
        double normaA = 0.0;
        double normaB = 0.0;

        for (int i = 0; i < vetor.length; i++) {
            produtoInterno += vetor[i] * outro[i];
            normaA += vetor[i] * vetor[i];
            normaB += outro[i] * outro[i];
        }

        if (normaA == 0.0 || normaB == 0.0) {
            return -1.0;
        }

        return produtoInterno / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}