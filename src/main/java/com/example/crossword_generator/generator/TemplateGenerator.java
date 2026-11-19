package com.example.crossword_generator.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TemplateGenerator {

    public static final int MIN_LEN = 3;
    public static final int MAX_LEN = 7;

    private final Random random = new Random();

    // Слово: отрезок клеток + его клетка-задание
    public static class Slot {
        public final int row, col, length;
        public final boolean horizontal;
        public int clueRow = -1, clueCol = -1;

        Slot(int row, int col, int length, boolean horizontal) {
            this.row = row;
            this.col = col;
            this.length = length;
            this.horizontal = horizontal;
        }

        public int rowAt(int i) {
            return row + (horizontal ? 0 : i);
        }

        public int colAt(int i) {
            return col + (horizontal ? i : 0);
        }
    }

    // Результат: true в массиве clue = клетка-задание
    public static class Template {
        public final boolean[][] clue;
        public final List<Slot> slots;
        public final int clues;
        public final int cost; // 0 = ошибок нет

        Template(boolean[][] clue, List<Slot> slots, int clues, int cost) {
            this.clue = clue;
            this.slots = slots;
            this.clues = clues;
            this.cost = cost;
        }
    }

    public Template generate(int n) {
        long deadline = System.currentTimeMillis() + 5000;
        Template best = null;

        while (System.currentTimeMillis() < deadline) {
            boolean[][] clue = new boolean[n][n];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    clue[r][c] = random.nextDouble() < 0.15;
                }
            }
            Template cur = evaluate(clue);

            for (int it = 0; it < 30000 && cur.cost > 0; it++) {
                int r = random.nextInt(n);
                int c = random.nextInt(n);
                clue[r][c] = !clue[r][c];
                Template next = evaluate(clue);
                if (next.cost <= cur.cost
                        || random.nextDouble() < Math.exp((cur.cost - next.cost) / 0.4)) {
                    cur = next;
                } else {
                    clue[r][c] = !clue[r][c]; // откат
                }
            }

            if (best == null || cur.cost < best.cost) {
                best = copyOf(cur);
            }
            if (best.cost == 0) {
                break;
            }
        }
        if (best != null && best.cost == 0) tidy(best);
        return best;
    }

    private Template copyOf(Template t) {
        boolean[][] c = new boolean[t.clue.length][];
        for (int i = 0; i < c.length; i++) {
            c[i] = t.clue[i].clone();
        }
        return new Template(c, t.slots, t.clues, t.cost);
    }

    private boolean isClue(boolean[][] clue, boolean horizontal, int a, int b) {
        return horizontal ? clue[a][b] : clue[b][a];
    }

    // Находит слова, считает ошибки, назначает слову клетку-задание
    private Template evaluate(boolean[][] clue) {
        int n = clue.length;
        List<Slot> slots = new ArrayList<>();
        boolean[][] covered = new boolean[n][n];
        int penalty = 0;

        for (int d = 0; d < 2; d++) {
            boolean horizontal = d == 0;
            for (int a = 0; a < n; a++) {
                int b = 0;
                while (b < n) {
                    if (isClue(clue, horizontal, a, b)) {
                        b++;
                        continue;
                    }
                    int start = b;
                    while (b < n && !isClue(clue, horizontal, a, b)) {
                        b++;
                    }
                    int len = b - start;
                    if (len == 1) {
                        continue;
                    }
                    if (len == 2) {
                        penalty++;
                        continue;
                    }
                    if (len > MAX_LEN) {
                        penalty += len - MAX_LEN;
                        continue;
                    }
                    Slot s = horizontal ? new Slot(a, start, len, true)
                            : new Slot(start, a, len, false);
                    slots.add(s);
                    for (int i = 0; i < len; i++) {
                        covered[s.rowAt(i)][s.colAt(i)] = true;
                    }
                }
            }
        }

        int clues = 0;
        int[][] id = new int[n][n];
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                id[r][c] = -1;
                if (clue[r][c]) {
                    id[r][c] = cells.size();
                    cells.add(new int[]{r, c});
                    clues++;
                } else if (!covered[r][c]) {
                    penalty++; // буква вне слов
                }
            }
        }

        // для каждого слова: какие клетки-задания рядом с его первой буквой
        List<List<Integer>> adj = new ArrayList<>();
        for (Slot s : slots) {
            List<Integer> list = new ArrayList<>();
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int r = s.row + dr;
                    int c = s.col + dc;
                    if ((dr != 0 || dc != 0) && r >= 0 && c >= 0 && r < n && c < n
                            && id[r][c] >= 0) {
                        list.add(id[r][c]);
                    }
                }
            }
            adj.add(list);
        }

        int[] owner = new int[cells.size()];
        for (int k = 0; k < owner.length; k++) {
            owner[k] = -1;
        }
        int matched = 0;
        for (int s = 0; s < slots.size(); s++) {
            if (augment(s, adj, owner, new boolean[owner.length])) {
                matched++;
            }
        }
        for (int k = 0; k < owner.length; k++) {
            if (owner[k] >= 0) {
                slots.get(owner[k]).clueRow = cells.get(k)[0];
                slots.get(owner[k]).clueCol = cells.get(k)[1];
            }
        }

        int cost = penalty + (slots.size() - matched) + (clues - matched);
        return new Template(clue, slots, clues, cost);
    }

    // Алгоритм Куна: поиск паросочетания слово <-> клетка-задание
    private boolean augment(int s, List<List<Integer>> adj, int[] owner, boolean[] seen) {
        for (int k : adj.get(s)) {
            if (seen[k]) {
                continue;
            }
            seen[k] = true;
            if (owner[k] < 0 || augment(owner[k], adj, owner, seen)) {
                owner[k] = s;
                return true;
            }
        }
        return false;
    }
    // Меняем местами задания, если обоим словам от этого станет лучше
    private void tidy(Template t) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < t.slots.size(); i++) {
                for (int j = i + 1; j < t.slots.size(); j++) {
                    Slot a = t.slots.get(i);
                    Slot b = t.slots.get(j);
                    if (!near(a, b.clueRow, b.clueCol) || !near(b, a.clueRow, a.clueCol)) {
                        continue; // слову не подходит чужая клетка
                    }
                    int before = badness(a, a.clueRow, a.clueCol) + badness(b, b.clueRow, b.clueCol);
                    int after = badness(a, b.clueRow, b.clueCol) + badness(b, a.clueRow, a.clueCol);
                    if (after < before) {
                        int r = a.clueRow;
                        int c = a.clueCol;
                        a.clueRow = b.clueRow;
                        a.clueCol = b.clueCol;
                        b.clueRow = r;
                        b.clueCol = c;
                        changed = true;
                    }
                }
            }
        }
    }

    // Клетка (r,c) — одна из 8 соседних с первой буквой слова?
    private boolean near(Slot s, int r, int c) {
        return Math.abs(r - s.row) <= 1 && Math.abs(c - s.col) <= 1;
    }

    // 0 = задание стоит прямо перед словом (слева или сверху), 1 = иначе
    private int badness(Slot s, int r, int c) {
        int prevRow = s.row - (s.horizontal ? 0 : 1);
        int prevCol = s.col - (s.horizontal ? 1 : 0);
        return (r == prevRow && c == prevCol) ? 0 : 1;
    }
}
