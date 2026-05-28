package com.ergpos.app.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * GroqService — cliente HTTP para la API de Groq (compatible con OpenAI).
 *
 * Funciona solo si app.groq.api-key está configurado.
 * Si la clave está vacía, lanza GroqNoConfiguredException para que el
 * AsistenteChatController devuelva el mensaje de fallback adecuado.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String baseUrl;

    @Value("${app.groq.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${app.groq.fallback-models:llama-3.3-70b-versatile,openai/gpt-oss-20b,openai/gpt-oss-120b}")
    private String fallbackModels;

    @Value("${app.groq.max-tokens:1024}")
    private int maxTokens;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Llama a la API de Groq enviando un system prompt (contexto del negocio)
     * y la pregunta del usuario.
     *
     * @param systemPrompt Contexto del negocio inyectado dinámicamente.
     * @param userMessage  Pregunta del usuario.
     * @return Respuesta de la IA en texto plano.
     */
    public String preguntar(String systemPrompt, String userMessage) throws GroqException {

        if (apiKey == null || apiKey.isBlank()) {
            throw new GroqNoConfiguredException(
                    "La integración con IA no está configurada. " +
                    "Configura la variable GROQ_API_KEY en el servidor para activarla.");
        }

        List<String> modelsToTry = getModelsToTry();
        List<String> deniedModels = new ArrayList<>();
        try {
            for (String currentModel : modelsToTry) {
                String requestBody = buildRequestJson(currentModel, systemPrompt, userMessage);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return extractContent(response.body());
                } else if (response.statusCode() == 401) {
                    log.error("Groq API: clave inválida o expirada. Status: {}", response.statusCode());
                    throw new GroqException("La clave de API de Groq es inválida o expiró. Por favor reconfigúrala.");
                } else if (response.statusCode() == 403) {
                    deniedModels.add(currentModel);
                    log.warn("Groq API: acceso denegado para el modelo {}. Probando siguiente fallback.", currentModel);
                    continue;
                } else if (response.statusCode() == 429) {
                    log.warn("Groq API: límite de requests alcanzado. Status: {}", response.statusCode());
                    throw new GroqException("Límite de consultas alcanzado. Intenta en unos segundos.");
                } else {
                    log.error("Groq API error con modelo {}. Status: {} Body: {}", currentModel, response.statusCode(), response.body());
                    throw new GroqException("Error al contactar el servicio de IA. Código: " + response.statusCode());
                }
            }

            throw new GroqException("Groq rechazó todos los modelos configurados: " + String.join(", ", deniedModels)
                    + ". Revisa Model Permissions en Groq o actualiza app.groq.model/app.groq.fallback-models.");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error de red al llamar a Groq: {}", e.getMessage());
            throw new GroqException("No se pudo conectar con el servicio de IA. Verifica la conexión a internet.");
        }
    }

    /** Devuelve true si la integración con Groq está activa. */
    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── Construcción manual del JSON de request ──────────────────────────────

    private String buildRequestJson(String currentModel, String systemPrompt, String userMessage) {
        String escapedSystem = escapeJson(systemPrompt);
        String escapedUser   = escapeJson(userMessage);

        return """
            {
              "model": "%s",
              "max_tokens": %d,
              "temperature": 0.3,
              "messages": [
                { "role": "system", "content": "%s" },
                { "role": "user",   "content": "%s" }
              ]
            }
            """.formatted(escapeJson(currentModel), maxTokens, escapedSystem, escapedUser);
    }

    private List<String> getModelsToTry() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        addModel(models, model);
        for (String fallback : fallbackModels.split(",")) {
            addModel(models, fallback);
        }
        return new ArrayList<>(models);
    }

    private void addModel(LinkedHashSet<String> models, String value) {
        if (value != null && !value.isBlank()) {
            models.add(value.trim());
        }
    }

    private String extractContent(String json) throws GroqException {
        try {
            JsonNode content = objectMapper.readTree(json).at("/choices/0/message/content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new GroqException("Respuesta inesperada del servicio de IA.");
            }
            return content.asText();
        } catch (Exception e) {
            throw new GroqException("No se pudo interpretar la respuesta de la IA.");
        }
    }

    /** Escapa caracteres especiales para JSON. */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    // ── Excepciones internas ─────────────────────────────────────────────────

    public static class GroqException extends Exception {
        public GroqException(String message) { super(message); }
    }

    public static class GroqNoConfiguredException extends GroqException {
        public GroqNoConfiguredException(String message) { super(message); }
    }
}
