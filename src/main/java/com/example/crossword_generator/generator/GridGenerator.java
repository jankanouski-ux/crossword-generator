package com.example.crossword_generator.generator;

import com.example.crossword_generator.model.Direction;
import com.example.crossword_generator.model.Entry;
import com.example.crossword_generator.model.Puzzle;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GridGenerator {

    private static final int MIN_LEN = 3;
    private static final int MAX_LEN = 9;
    private static final int BRANCH = 25;        // сколько слов пробуем на каждом шаге
    private static final int NODE_LIMIT = 2000;  // шагов поиска на одну попытку
    private static final int RESTARTS = 3;       // попыток на один шаблон
    private static final long TIME_LIMIT_MS = 10000;
    private static final int ALPHABET = 32;      // А..Я

    private final Random random = new Random();

    // Окно под слово: отрезок клеток + клетка-задание
    private static class Slot {
        final int startRow, startCol, length;
        final Direction dir;
        int clueRow, clueCol;

        Slot(int startRow, int startCol, Direction dir, int length) {
            this.startRow = startRow;
            this.startCol = startCol;
            this.dir = dir;
            this.length = length;
        }

        int row(int i) {
            return startRow + (dir == Direction.DOWN ? i : 0);
        }

        int col(int i) {
            return startCol + (dir == Direction.RIGHT ? i : 0);
        }
    }

    // Слова одной длины + индекс «позиция, буква -> слова»
    private static class Bucket {
        final int length;
        final List<String> words = new ArrayList<>();
        final BitSet all = new BitSet();
        final BitSet used = new BitSet();
        BitSet[][] index;

        Bucket(int length) {
            this.length = length;
        }
    }

    // состояние поиска
    private Map<Integer, Bucket> buckets;
    private List<Slot> slots;
    private char[][] grid;
    private String[] chosen;
    private int nodes;
    private int doneCount;
    private long deadline;
    private int bestCount;
    private List<Entry> best;

    // synchronized: объект общий для всех запросов, а состояние хранится в полях
    public synchronized Puzzle generate(int size, List<String> words) {
        int maxLen = Math.min(MAX_LEN, size - 1);
        buckets = buildBuckets(words, maxLen);

        best = new ArrayList<>();
        bestCount = -1;
        deadline = System.currentTimeMillis() + TIME_LIMIT_MS;

        double p = 0.2;       // доля клеток-заданий, подстраивается сама
        int rejected = 0;
        int templates = 0;
        boolean complete = false;

        while (!complete && System.currentTimeMillis() < deadline) {
            // ЭТАП 1: шаблон
            boolean[][] clue = makeTemplate(size, maxLen, p);
            int clues = countTrue(clue);
            List<Slot> list = buildSlots(clue);

            // слов больше, чем заданий -> заданий нужно больше, и наоборот
            if (list.size() > clues) {
                p = Math.min(0.45, p + 0.01);
            } else if (list.size() < clues) {
                p = Math.max(0.10, p - 0.01);
            }

            int allowedExtra = rejected / 300; // лишние клетки-задания (будут пустыми)
            if (list.isEmpty() || clues - list.size() > allowedExtra
                    || !assignClues(clue, list)) {
                rejected++;
                continue;
            }
            slots = list;
            templates++;

            // ЭТАП 2: слова под готовый шаблон
            for (int attempt = 0; attempt < RESTARTS && !complete
                    && System.currentTimeMillis() < deadline; attempt++) {
                grid = new char[size][size];
                chosen = new String[slots.size()];
                for (Bucket b : buckets.values()) {
                    b.used.clear();
                }
                nodes = 0;
                doneCount = 0;
                complete = solve();
            }
        }

        System.out.println("Шаблонов: " + templates + ", слов в лучшем: "
                + best.size() + ", полностью: " + complete);
        return new Puzzle(size, best);
    }

    // ---------- ЭТАП 1: шаблон ----------

    // true в массиве = клетка с заданием
    private boolean[][] makeTemplate(int n, int maxLen, double p) {
        boolean[][] clue = new boolean[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                clue[r][c] = random.nextDouble() < p;
            }
        }
        clue[0][0] = true;

        // Чиним, пока есть нарушения. Задания только добавляются,
        // поэтому цикл обязательно закончится.
        boolean changed = true;
        while (changed) {
            changed = false;

            // цепочки букв по горизонтали: длина 2 или больше максимума нельзя
            for (int r = 0; r < n; r++) {
                int c = 0;
                while (c < n) {
                    if (clue[r][c]) {
                        c++;
                        continue;
                    }
                    int start = c;
                    while (c < n && !clue[r][c]) {
                        c++;
                    }
                    int len = c - start;
                    if (len == 2 || len > maxLen) {
                        clue[r][start + random.nextInt(len)] = true;
                        changed = true;
                    }
                }
            }
            // по вертикали
            for (int c = 0; c < n; c++) {
                int r = 0;
                while (r < n) {
                    if (clue[r][c]) {
                        r++;
                        continue;
                    }
                    int start = r;
                    while (r < n && !clue[r][c]) {
                        r++;
                    }
                    int len = r - start;
                    if (len == 2 || len > maxLen) {
                        clue[start + random.nextInt(len)][c] = true;
                        changed = true;
                    }
                }
            }
            // буква, не входящая ни в одно слово, становится заданием
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (!clue[r][c] && hLen(clue, r, c) < MIN_LEN && vLen(clue, r, c) < MIN_LEN) {
                        clue[r][c] = true;
                        changed = true;
                    }
                }
            }
        }
        return clue;
    }

    private int countTrue(boolean[][] a) {
        int count = 0;
        for (boolean[] row : a) {
            for (boolean v : row) {
                if (v) {
                    count++;
                }
            }
        }
        return count;
    }

    private int rightRun(boolean[][] clue, int r, int c) {
        int len = 0;
        while (c + len < clue.length && !clue[r][c + len]) {
            len++;
        }
        return len;
    }

    private int downRun(boolean[][] clue, int r, int c) {
        int len = 0;
        while (r + len < clue.length && !clue[r + len][c]) {
            len++;
        }
        return len;
    }

    // длина горизонтальной цепочки, в которой стоит клетка
    private int hLen(boolean[][] clue, int r, int c) {
        int left = c;
        while (left - 1 >= 0 && !clue[r][left - 1]) {
            left--;
        }
        return rightRun(clue, r, left);
    }

    // длина вертикальной цепочки, в которой стоит клетка
    private int vLen(boolean[][] clue, int r, int c) {
        int top = r;
        while (top - 1 >= 0 && !clue[top - 1][c]) {
            top--;
        }
        return downRun(clue, top, c);
    }

    // Каждая цепочка из 3+ букв закрепляется за одним словом
    private List<Slot> buildSlots(boolean[][] clue) {
        int n = clue.length;
        List<Slot> list = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            int c = 0;
            while (c < n) {
                if (clue[r][c]) {
                    c++;
                    continue;
                }
                int start = c;
                while (c < n && !clue[r][c]) {
                    c++;
                }
                if (c - start >= MIN_LEN) {
                    list.add(new Slot(r, start, Direction.RIGHT, c - start));
                }
            }
        }
        for (int c = 0; c < n; c++) {
            int r = 0;
            while (r < n) {
                if (clue[r][c]) {
                    r++;
                    continue;
                }
                int start = r;
                while (r < n && !clue[r][c]) {
                    r++;
                }
                if (r - start >= MIN_LEN) {
                    list.add(new Slot(start, c, Direction.DOWN, r - start));
                }
            }
        }
        return list;
    }

    // Назначаем каждому окну свою клетку-задание среди 8 соседей первой буквы.
    // false, если назначить не получилось.
    private boolean assignClues(boolean[][] clue, List<Slot> list) {
        int n = clue.length;
        int[][] id = new int[n][n];
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (clue[r][c]) {
                    id[r][c] = cells.size();
                    cells.add(new int[]{r, c});
                } else {
                    id[r][c] = -1;
                }
            }
        }

        List<List<Integer>> adj = new ArrayList<>();
        for (Slot s : list) {
            // сначала клетка прямо перед словом (так обычно и бывает), потом остальные
            int pr = s.startRow - (s.dir == Direction.DOWN ? 1 : 0);
            int pc = s.startCol - (s.dir == Direction.RIGHT ? 1 : 0);
            int first = -1;
            if (pr >= 0 && pc >= 0 && id[pr][pc] >= 0) {
                first = id[pr][pc];
            }
            List<Integer> others = new ArrayList<>();
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int r = s.startRow + dr;
                    int c = s.startCol + dc;
                    if ((dr != 0 || dc != 0) && r >= 0 && c >= 0 && r < n && c < n
                            && id[r][c] >= 0 && id[r][c] != first) {
                        others.add(id[r][c]);
                    }
                }
            }
            Collections.shuffle(others, random);
            if (first >= 0) {
                others.add(0, first);
            }
            adj.add(others);
        }

        int[] owner = new int[cells.size()];
        for (int k = 0; k < owner.length; k++) {
            owner[k] = -1;
        }
        for (int s = 0; s < list.size(); s++) {
            if (!augment(s, adj, owner, new boolean[owner.length])) {
                return false;
            }
        }
        for (int k = 0; k < owner.length; k++) {
            if (owner[k] >= 0) {
                list.get(owner[k]).clueRow = cells.get(k)[0];
                list.get(owner[k]).clueCol = cells.get(k)[1];
            }
        }
        return true;
    }

    // Поиск увеличивающей цепи (алгоритм Куна)
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

    // ---------- Словарь с индексом ----------

    private Map<Integer, Bucket> buildBuckets(List<String> words, int maxLen) {
        Map<Integer, Bucket> map = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String w : words) {
            int len = w.length();
            if (len < MIN_LEN || len > maxLen || !isRussian(w) || !seen.add(w)) {
                continue;
            }
            Bucket b = map.get(len);
            if (b == null) {
                b = new Bucket(len);
                map.put(len, b);
            }
            b.words.add(w);
        }
        for (Bucket b : map.values()) {
            b.index = new BitSet[b.length][ALPHABET];
            for (int p = 0; p < b.length; p++) {
                for (int l = 0; l < ALPHABET; l++) {
                    b.index[p][l] = new BitSet();
                }
            }
            for (int k = 0; k < b.words.size(); k++) {
                b.all.set(k);
                String w = b.words.get(k);
                for (int p = 0; p < b.length; p++) {
                    b.index[p][w.charAt(p) - 'А'].set(k);
                }
            }
        }
        return map;
    }

    private boolean isRussian(String w) {
        for (int i = 0; i < w.length(); i++) {
            char ch = w.charAt(i);
            if (ch < 'А' || ch > 'Я') {
                return false;
            }
        }
        return true;
    }

    // Слова, подходящие в окно с учётом уже стоящих букв
    private BitSet domain(Slot s) {
        Bucket b = buckets.get(s.length);
        if (b == null) {
            return new BitSet();
        }
        BitSet d = (BitSet) b.all.clone();
        d.andNot(b.used);
        for (int i = 0; i < s.length && !d.isEmpty(); i++) {
            char ch = grid[s.row(i)][s.col(i)];
            if (ch != 0) {
                d.and(b.index[i][ch - 'А']);
            }
        }
        return d;
    }

    // ---------- ЭТАП 2: подбор слов с откатом ----------

    // true = все окна заполнены
    private boolean solve() {
        nodes++;
        if (nodes > NODE_LIMIT || System.currentTimeMillis() > deadline) {
            return false;
        }

        // Окно с наименьшим числом вариантов.
        // Если хотя бы у одного окна вариантов нет, шаг назад.
        int pick = -1;
        BitSet pickDom = null;
        int pickCount = Integer.MAX_VALUE;
        for (int s = 0; s < slots.size(); s++) {
            if (chosen[s] != null) {
                continue;
            }
            BitSet d = domain(slots.get(s));
            int cnt = d.cardinality();
            if (cnt == 0) {
                return false;
            }
            if (cnt < pickCount) {
                pickCount = cnt;
                pick = s;
                pickDom = d;
            }
        }

        if (pick == -1) {
            saveBest();
            return true;
        }

        // варианты в случайном порядке
        int[] ids = new int[pickCount];
        int k = 0;
        for (int i = pickDom.nextSetBit(0); i >= 0; i = pickDom.nextSetBit(i + 1)) {
            ids[k++] = i;
        }
        int tries = Math.min(BRANCH, pickCount);
        for (int j = 0; j < tries; j++) {
            int swap = j + random.nextInt(pickCount - j);
            int tmp = ids[j];
            ids[j] = ids[swap];
            ids[swap] = tmp;
        }

        Slot slot = slots.get(pick);
        Bucket b = buckets.get(slot.length);

        for (int j = 0; j < tries; j++) {
            int id = ids[j];
            String w = b.words.get(id);

            // ставим слово, запоминая, какие клетки были пустыми
            boolean[] wasEmpty = new boolean[slot.length];
            for (int i = 0; i < slot.length; i++) {
                int r = slot.row(i);
                int c = slot.col(i);
                wasEmpty[i] = grid[r][c] == 0;
                grid[r][c] = w.charAt(i);
            }
            chosen[pick] = w;
            b.used.set(id);
            doneCount++;
            if (doneCount > bestCount) {
                saveBest();
            }

            if (solve()) {
                return true;
            }

            // шаг назад
            chosen[pick] = null;
            b.used.clear(id);
            doneCount--;
            for (int i = 0; i < slot.length; i++) {
                if (wasEmpty[i]) {
                    grid[slot.row(i)][slot.col(i)] = 0;
                }
            }
            if (nodes > NODE_LIMIT) {
                return false;
            }
        }
        return false;
    }

    private void saveBest() {
        bestCount = doneCount;
        List<Entry> list = new ArrayList<>();
        for (int s = 0; s < slots.size(); s++) {
            if (chosen[s] != null) {
                Slot slot = slots.get(s);
                list.add(new Entry(chosen[s], "Подсказка",
                        slot.clueRow, slot.clueCol,
                        slot.startRow, slot.startCol, slot.dir));
            }
        }
        best = list;
    }
}