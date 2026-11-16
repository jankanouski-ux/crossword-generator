package com.example.crossword_generator.controller;

import com.example.crossword_generator.model.Difficulty;
import com.example.crossword_generator.model.Direction;
import com.example.crossword_generator.model.Entry;
import com.example.crossword_generator.model.Puzzle;
import com.example.crossword_generator.service.WordDictionary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {
    private final WordDictionary dictionary;

    public HomeController(WordDictionary dictionary) {
        this.dictionary = dictionary;
    }

    @GetMapping("/")
    public String index() {
        return "index";   // Spring найдёт templates/index.html
    }

    @PostMapping("/generate")
    public String generate(@RequestParam Difficulty difficulty, @RequestParam int size, Model model){
        List<Entry> entries = List.of(
                new Entry("СОК",  "Напиток из фруктов",         1, 0, Direction.RIGHT),
                new Entry("НОС",  "Орган обоняния",             4, 0, Direction.RIGHT),
                new Entry("СЛОН", "Большое животное с хоботом", 0, 1, Direction.DOWN),
                new Entry("КВАС", "Напиток из хлеба",           0, 3, Direction.DOWN),
                new Entry("ЛУК", "Овощ, от которого плачут", 6, 0, Direction.RIGHT)
        );
        Puzzle puzzle = new Puzzle(size, entries);
        model.addAttribute("puzzle", puzzle);
        model.addAttribute("grid", puzzle.buildGrid());
        return "gameboard";
    }
}