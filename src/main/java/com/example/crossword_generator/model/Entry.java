package com.example.crossword_generator.model;

public record Entry(String answer, String clue,
                    int clueRow, int clueCol, Direction direction) {

    /** Строка, где стоит первая буква ответа. */
    public int startRow() {
        return direction == Direction.DOWN ? clueRow + 1 : clueRow;
    }

    /** Колонка, где стоит первая буква ответа. */
    public int startCol() {
        return direction == Direction.RIGHT ? clueCol + 1 : clueCol;
    }

    /** Стрелка для показа в клетке. */
    public String arrow() {
        return direction == Direction.RIGHT ? "→" : "↓";
    }
}