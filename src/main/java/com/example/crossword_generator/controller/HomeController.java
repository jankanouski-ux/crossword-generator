package com.example.crossword_generator.controller;

import com.example.crossword_generator.generator.TemplateGenerator;
import com.example.crossword_generator.model.Difficulty;
import com.example.crossword_generator.model.Puzzle;
import com.example.crossword_generator.service.PuzzleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    private final PuzzleService puzzleService;

    public HomeController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam Difficulty difficulty,
                           @RequestParam int size,
                           Model model) {
        Puzzle puzzle = puzzleService.create(difficulty, size);
        model.addAttribute("puzzle", puzzle);
        model.addAttribute("grid", puzzle.buildGrid());
        return "gameboard";
    }




    @GetMapping(value = "/template", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String template(@RequestParam(defaultValue = "9") int size) {
        TemplateGenerator.Template t = new TemplateGenerator().generate(size);

        int[][] h = new int[size][size];   // номер горизонтального слова в клетке
        int[][] v = new int[size][size];   // номер вертикального слова в клетке
        int[][] clueOf = new int[size][size]; // какому слову принадлежит задание
        String[][] arrows = {{"↖", "↑", "↗"}, {"←", "·", "→"}, {"↙", "↓", "↘"}};
        String[][] arrowText = new String[size][size];

        for (int i = 0; i < t.slots.size(); i++) {
            TemplateGenerator.Slot s = t.slots.get(i);
            for (int k = 0; k < s.length; k++) {
                if (s.horizontal) {
                    h[s.rowAt(k)][s.colAt(k)] = i + 1;
                } else {
                    v[s.rowAt(k)][s.colAt(k)] = i + 1;
                }
            }
            if (s.clueRow >= 0) {
                clueOf[s.clueRow][s.clueCol] = i + 1;
                arrowText[s.clueRow][s.clueCol] =
                        arrows[Integer.signum(s.row - s.clueRow) + 1]
                                [Integer.signum(s.col - s.clueCol) + 1]
                                + (s.horizontal ? "→" : "↓");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<p>Слов: ").append(t.slots.size())
                .append(", заданий: ").append(t.clues)
                .append(", ошибок: ").append(t.cost).append("</p>");
        sb.append("<table style='border-collapse:collapse;font:14px sans-serif'>");
        for (int r = 0; r < size; r++) {
            sb.append("<tr>");
            for (int c = 0; c < size; c++) {
                String style = "width:48px;height:48px;text-align:center;border:1px solid #999;";
                sb.append("<td style='").append(style);
                if (t.clue[r][c]) {
                    sb.append("background:#f5c542'>");
                    if (clueOf[r][c] > 0) {
                        sb.append("№").append(clueOf[r][c]).append("<br>").append(arrowText[r][c]);
                    }
                } else {
                    sb.append("'>");
                    if (h[r][c] > 0 && v[r][c] > 0) {
                        sb.append(h[r][c]).append("/").append(v[r][c]);
                    } else {
                        sb.append(Math.max(h[r][c], v[r][c]));
                    }
                }
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}