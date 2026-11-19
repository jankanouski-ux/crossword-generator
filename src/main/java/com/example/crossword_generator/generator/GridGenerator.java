package com.example.crossword_generator.generator;

import com.example.crossword_generator.generator.TemplateGenerator.Slot;
import com.example.crossword_generator.model.Direction;
import com.example.crossword_generator.model.Entry;
import com.example.crossword_generator.model.Puzzle;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GridGenerator {

    private static final int NODE_LIMIT = 3000;   // шагов поиска на одну попытку
    private static final int RESTARTS = 3;        // попыток на один шаблон
    private static final int BRANCH = 40;         // сколько слов пробуем на каждом шаге
    private static final long TIME_LIMIT_MS = 20000;
    private static final int ALPHABET = 32;       // А..Я

    private final Random random = new Random();
    private final TemplateGenerator templateGenerator = new TemplateGenerator();

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
        buckets = buildBuckets(words);
        best = new ArrayList<>();
        bestCount = -1;
        deadline = System.currentTimeMillis() + TIME_LIMIT_MS;

        boolean complete = false;
        int templates = 0;

        while (!complete && System.currentTimeMillis() < deadline) {
            // ЭТАП 1: готовая конфигурация без слов
            TemplateGenerator.Template t = templateGenerator.generate(size);
            if (t == null || t.cost != 0 || t.slots.isEmpty()) {
                continue;
            }
            slots = t.slots;
            templates++;

            // ЭТАП 2: слова под эту конфигурацию
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

        System.out.println("Шаблонов: " + templates + ", слов в результате: "
                + best.size() + ", полностью: " + complete);
        return new Puzzle(size, best);
    }

    // ---------- Словарь с индексом ----------

    private Map<Integer, Bucket> buildBuckets(List<String> words) {
        Map<Integer, Bucket> map = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String w : words) {
            int len = w.length();
            if (len < TemplateGenerator.MIN_LEN || len > TemplateGenerator.MAX_LEN
                    || !isRussian(w) || !seen.add(w)) {
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

    // Слова, подходящие в отрезок с учётом уже стоящих букв
    private BitSet domain(Slot s) {
        Bucket b = buckets.get(s.length);
        if (b == null) {
            return new BitSet();
        }
        BitSet d = (BitSet) b.all.clone();
        d.andNot(b.used);
        for (int i = 0; i < s.length && !d.isEmpty(); i++) {
            char ch = grid[s.rowAt(i)][s.colAt(i)];
            if (ch != 0) {
                d.and(b.index[i][ch - 'А']);
            }
        }
        return d;
    }

    // ---------- ЭТАП 2: подбор слов с откатом ----------

    // true = все отрезки заполнены
    private boolean solve() {
        nodes++;
        if (nodes > NODE_LIMIT || System.currentTimeMillis() > deadline) {
            return false;
        }

        // Отрезок с наименьшим числом вариантов.
        // Если хотя бы у одного вариантов нет, шаг назад.
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
                int r = slot.rowAt(i);
                int c = slot.colAt(i);
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
                    grid[slot.rowAt(i)][slot.colAt(i)] = 0;
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
                        slot.row, slot.col,
                        slot.horizontal ? Direction.RIGHT : Direction.DOWN));
            }
        }
        best = list;
    }
}