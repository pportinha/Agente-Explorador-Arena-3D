package pt.ipt.arena;

/**
 * Resultado imutável de uma resolução RAG de um Terminal de Plasma.
 * Transporta exatamente os três campos que o endpoint /unlock da Arena espera:
 *  - codigo:        a chave alfanumérica extraída pelo LLM (parâmetro "code")
 *  - chunkRelevante: o parágrafo do manual escolhido por cosine similarity ("rag_chunk")
 *  - respostaBruta:  a resposta crua do LLM, antes da limpeza ("llm_raw")
 */
public record ResultadoRAG(String codigo, String chunkRelevante, String respostaBruta) {
}
