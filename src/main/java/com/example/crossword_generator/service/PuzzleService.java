package com.example.crossword_generator.service;

import com.example.crossword_generator.generator.GridGenerator;
import com.example.crossword_generator.model.Difficulty;
import com.example.crossword_generator.model.Entry;
import com.example.crossword_generator.model.Puzzle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PuzzleService {

    private final WordDictionary dictionary;
    private final ClueProvider clueProvider;
    private final GridGenerator generator = new GridGenerator();

    public PuzzleService(WordDictionary dictionary, ClueProvider clueProvider) {
        this.dictionary = dictionary;
        this.clueProvider = clueProvider;
    }

    public Puzzle create(Difficulty difficulty, int size) {
        // 1. Слова нужной сложности, длиной от 3 до size-1
        List<String> words = new ArrayList<>();
        for (int len = 3; len < size; len++) {
            words.addAll(dictionary.find(difficulty, len));
        }

        // 2. Расставляем слова (пока с подсказкой-заглушкой)
        Puzzle puzzle = generator.generate(size, words);

        // 3. Подсказки только для слов, которые попали в сетку
        List<Entry> result = new ArrayList<>();
        for (Entry e : puzzle.entries()) {
            result.add(e.withClue(clueProvider.getClue(e.answer())));
        }
        return new Puzzle(size, result);
    }
}