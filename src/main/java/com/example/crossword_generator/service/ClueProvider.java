package com.example.crossword_generator.service;

import java.util.List;
import java.util.Map;

public interface ClueProvider {

    String getClue(String word);

    // Подсказки ко всем словам сразу: слово -> подсказка
    Map<String, String> getClues(List<String> words);
}