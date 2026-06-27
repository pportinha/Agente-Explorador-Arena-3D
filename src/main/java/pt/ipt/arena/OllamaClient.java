package pt.ipt.arena;

import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public OllamaClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public double[] gerarEmbedding(String texto) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", "nomic-embed-text");
        body.addProperty("prompt", texto);

        String resposta = post("/api/embeddings", body);

        JsonObject json = gson.fromJson(resposta, JsonObject.class);
        JsonArray embedding = json.getAsJsonArray("embedding");

        double[] vetor = new double[embedding.size()];

        for (int i = 0; i < embedding.size(); i++) {
            vetor[i] = embedding.get(i).getAsDouble();
        }

        return vetor;
    }

    public String gerarResposta(String prompt) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", "qwen2.5-coder:0.5b-instruct-q4_K_M");
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.0);
        body.add("options", options);

        String resposta = post("/api/generate", body);

        JsonObject json = gson.fromJson(resposta, JsonObject.class);

        return json.get("response").getAsString().trim();
    }

    private String post(String endpoint, JsonObject body) throws IOException, InterruptedException {
        String url = baseUrl + endpoint;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erro Ollama HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }
}