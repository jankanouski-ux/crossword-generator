package com.example.crossword_generator.service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class StubClueProvider implements ClueProvider {

    @Override
    public String getClue(String word) {
        return "Подсказка к слову " + word;
    }
    @Override
    public Map<String, String> getClues(List<String> words) {
        Map<String, String> result = new HashMap<>();
        for (String w : words) {
            result.put(w, getClue(w));
        }
        return result;
    }
}