package com.example.crossword_generator.model;

import java.util.List;

public record Cell(CellType type, Character letter, Entry entry) {

    public boolean isClueCell() {
        return type == CellType.CLUE;
    }
    public boolean isLetterCell() {
        return type == CellType.LETTER;
    }
    public boolean isEmptyCell(){
        return type == CellType.EMPTY;
    }
    public String cssClass() {
        return switch (type) {
            case CLUE   -> "board__cell--clue";
            case LETTER -> "board__cell--letter";
            case EMPTY  -> "board__cell--empty";
        };
    }

}