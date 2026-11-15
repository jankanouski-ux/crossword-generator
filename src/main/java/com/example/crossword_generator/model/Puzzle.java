package com.example.crossword_generator.model;

import java.util.List;

public record Puzzle(int size, List<Entry> entries) {

    public Cell[][] buildGrid() {
        Cell[][] grid = new Cell[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                grid[r][c] = new Cell(CellType.EMPTY, null, null);
            }
        }

        for (Entry e : entries) {
            // 2а. Клетка с заданием должна быть свободной
            if (grid[e.clueRow()][e.clueCol()].type() != CellType.EMPTY) {
                throw new IllegalStateException(
                        "Клетка задания уже занята: " + e.clue());
            }
            grid[e.clueRow()][e.clueCol()] = new Cell(CellType.CLUE, null, e);

            // 2б. Буквы ответа
            for (int i = 0; i < e.answer().length(); i++) {
                int r = e.startRow() + (e.direction() == Direction.DOWN ? i : 0);
                int c = e.startCol() + (e.direction() == Direction.RIGHT ? i : 0);
                char ch = e.answer().charAt(i);

                Cell cur = grid[r][c];
                if (cur.isClueCell() || (cur.isLetterCell() && cur.letter() != ch)) {
                    throw new IllegalStateException(
                            "Конфликт в клетке (" + r + "," + c + ") у слова " + e.answer());
                }
                grid[r][c] = new Cell(CellType.LETTER, ch, null);
            }
        }
        return grid;
    }
}