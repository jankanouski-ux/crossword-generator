package com.example.crossword_generator.service;

import com.example.crossword_generator.model.Difficulty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WordDictionary {

    private final Map<Difficulty, List<String>> words = new HashMap<>();

    public WordDictionary() throws IOException {
        words.put(Difficulty.EASY, readFile("dictionaries/easy.txt"));
        words.put(Difficulty.MEDIUM, readFile("dictionaries/medium.txt"));
        words.put(Difficulty.HARD, readFile("dictionaries/hard.txt"));
    }

    private List<String> readFile(String path) throws IOException {
        List<String> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(path);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            String word = line.trim().toUpperCase().replace('Ё', 'Е');
            if (!word.isEmpty() && !result.contains(word)) {
                result.add(word);
            }
        }
        reader.close();
        return result;
    }

    public List<String> find(Difficulty difficulty, int length) {
        List<String> result = new ArrayList<>();
        for (String word : words.get(difficulty)) {
            if (word.length() == length) {
                result.add(word);
            }
        }
        return result;
    }
}