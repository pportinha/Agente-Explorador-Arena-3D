package pt.ipt.arena;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ArenaClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public ArenaClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public JsonObject registarRobo(String roomId, String robotId) throws IOException, InterruptedException {
        String url = baseUrl
                + "/arena/" + encode(roomId)
                + "/register"
                + "?robot_id=" + encode(robotId);

        return postJson(url, new JsonObject());
    }

    public JsonObject perceber(String roomId, String robotId) throws IOException, InterruptedException {
        String url = baseUrl
                + "/arena/" + encode(roomId)
                + "/perceive/"
                + encode(robotId);

        return getJson(url);
    }

    public JsonObject enviarAcao(String roomId, String robotId, String action) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("room_id", roomId);
        body.addProperty("robot_id", robotId);
        body.addProperty("action", action);

        String url = baseUrl + "/arena/action";

        return postJson(url, body);
    }

    public JsonObject desbloquearCofre(
            String roomId,
            String robotId,
            String code,
            String ragChunk,
            String llmRaw
    ) throws IOException, InterruptedException {

        String url = baseUrl
                + "/arena/" + encode(roomId)
                + "/unlock"
                + "?robot_id=" + encode(robotId)
                + "&code=" + encode(code)
                + "&rag_chunk=" + encode(ragChunk)
                + "&llm_raw=" + encode(llmRaw);

        return postJson(url, new JsonObject());
    }

    public String descarregarManual(String roomId) throws IOException, InterruptedException {
        String url = baseUrl
                + "/arena/"
                + encode(roomId)
                + "/download_manual";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Accept", "text/plain")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        verificarResposta(response);

        return response.body();
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        verificarResposta(response);

        return gson.fromJson(response.body(), JsonObject.class);
    }

    private JsonObject postJson(String url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        verificarResposta(response);

        return gson.fromJson(response.body(), JsonObject.class);
    }

    private void verificarResposta(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erro HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String encode(String valor) {
        if (valor == null) {
            return "";
        }
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}