package com.example.crossword_generator.service;

import org.springframework.stereotype.Component;

@Component
public class StubClueProvider implements ClueProvider {

    @Override
    public String getClue(String word) {
        return "Подсказка к слову " + word;
    }
}