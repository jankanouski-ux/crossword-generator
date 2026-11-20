package com.example.crossword_generator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary   // из двух реализаций ClueProvider Spring выберет эту
public class AiaiClueProvider implements ClueProvider {

    private static final String SYSTEM_PROMPT =
            "Ты составитель сканвордов. Напиши определение (3-4 слова) для каждого слова из списка. "
                    + "Не используй однокоренные слова. "
                    + "Формат ответа строго: на каждой строке СЛОВО: определение. "
                    + "Никаких вступлений, нумерации и пояснений.";

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final StubClueProvider fallback;

    public AiaiClueProvider(@Value("${aiai.url}") String url,
                            @Value("${aiai.api-key}") String apiKey,
                            @Value("${aiai.model}") String model,
                            StubClueProvider fallback) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public String getClue(String word) {
        List<String> one = new ArrayList<>();
        one.add(word);
        return getClues(one).get(word);
    }

    @Override
    public Map<String, String> getClues(List<String> words) {
        // Сначала заглушки: если ИИ не ответит, сканворд всё равно откроется
        Map<String, String> result = fallback.getClues(words);

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("AIAI: ключ не задан (переменная AIAI_API_KEY), берём заглушки");
            return result;
        }

        try {
            String content = sendRequest(String.join(", ", words));
            parseInto(content, result);
        } catch (Exception e) {
            System.out.println("AIAI: ошибка запроса: " + e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String sendRequest(String userWords) {
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userWords));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("messages", messages);

        Map<String, Object> response = client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Object> choices = (List<Object>) response.get("choices");
        Map<String, Object> first = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        return (String) message.get("content");
    }

    // Строки вида "СЛОН: Африканский великан" -> в таблицу слово -> подсказка
    private void parseInto(String content, Map<String, String> result) {
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String word = line.substring(0, colon)
                    .replaceAll("[^А-Яа-яЁё]", "")
                    .toUpperCase()
                    .replace('Ё', 'Е');
            String clue = line.substring(colon + 1).replace("\"", "").trim();
            if (result.containsKey(word) && !clue.isEmpty()) {
                result.put(word, clue);
            }
        }
    }
}