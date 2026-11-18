package com.example.crossword_generator.model;

/**
 * Одно слово. Задание лежит в клетке (clueRow, clueCol),
 * ответ начинается в клетке (startRow, startCol) и идёт вправо или вниз.
 */
public record Entry(String answer, String clue,
                    int clueRow, int clueCol,
                    int startRow, int startCol,
                    Direction direction) {

    private static final String[][] ARROWS = {
            {"↖", "↑", "↗"},
            {"←", "", "→"},
            {"↙", "↓", "↘"}
    };

    public Entry withClue(String newClue) {
        return new Entry(answer, newClue, clueRow, clueCol, startRow, startCol, direction);
    }

    /** Первая стрелка: где начинается слово. Вторая (если нужна): куда оно идёт. */
    public String arrow() {
        String where = ARROWS[Integer.signum(startRow - clueRow) + 1]
                [Integer.signum(startCol - clueCol) + 1];
        String dir = direction == Direction.RIGHT ? "→" : "↓";
        return where.equals(dir) ? where : where + dir;
    }
}