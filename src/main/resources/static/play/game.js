/**
 * Состояние и правила игры. Ничего не знает о DOM: представления подписываются
 * через subscribe() и перерисовываются по каждому изменению.
 *
 * Буквы живут в клетках, а не в словах, поэтому слово на пересечении
 * автоматически видит буквы соседнего слова.
 *   draft  — введена игроком, ещё не подтверждена (можно стереть);
 *   locked — принадлежит разгаданному слову (стереть нельзя).
 */

import { cellKey, normalizeLetter } from './puzzle.js';

export const FLASH_MS = 1000;

export const Phase = Object.freeze({
  PLAYING: 'playing',
  /** Слово угадано: 1 с показываем зелёный, ввод заблокирован. */
  CELEBRATING: 'celebrating',
  COMPLETE: 'complete',
});

/** Состояние слова в панели ответа. */
export const EntryStatus = Object.freeze({
  EDITING: 'editing',
  WRONG: 'wrong',
  CORRECT: 'correct',
  SOLVED: 'solved',
});

export class Game {
  #puzzle;
  #flashMs;
  #letters = new Map();
  #solved = new Set();
  #listeners = new Set();
  #flashId = null;

  phase = Phase.PLAYING;
  activeId;

  constructor(puzzle, { flashMs = FLASH_MS } = {}) {
    this.#puzzle = puzzle;
    this.#flashMs = flashMs;
    this.activeId = puzzle.entries[0]?.id ?? null;
  }

  subscribe(listener) {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  }

  // ---------- Запросы ----------

  get puzzle() {
    return this.#puzzle;
  }

  get activeEntry() {
    return this.activeId === null ? null : this.#puzzle.entries[this.activeId];
  }

  get solvedCount() {
    return this.#solved.size;
  }

  get totalCount() {
    return this.#puzzle.size;
  }

  isSolved(id) {
    return this.#solved.has(id);
  }

  /** @returns {{char: string, locked: boolean} | undefined} */
  letterAt(key) {
    return this.#letters.get(key);
  }

  /** Клетки слова вместе с буквами, которые в них уже стоят. */
  slotsOf(entry) {
    return entry.cells.map(({ key }) => {
      const letter = this.#letters.get(key);
      return { key, char: letter?.char ?? '', locked: letter?.locked ?? false };
    });
  }

  statusOf(entry) {
    if (this.#flashId === entry.id) return EntryStatus.CORRECT;
    if (this.isSolved(entry.id)) return EntryStatus.SOLVED;
    // Совпавшее слово сразу становится решённым, так что «полное, но нерешённое» всегда неверно.
    return this.#isFull(entry) ? EntryStatus.WRONG : EntryStatus.EDITING;
  }

  /** Стрелки «назад/вперёд» нужны, только если есть куда переходить. */
  get canStep() {
    return this.phase === Phase.PLAYING && this.totalCount - this.solvedCount > 1;
  }

  // ---------- Навигация ----------

  step(delta) {
    if (!this.canStep) return;
    this.#activate(this.#nextUnsolved(this.activeId, delta));
  }

  /**
   * Нажатие на клетку поля. Клетка с заданием выбирает своё слово.
   * Клетка на пересечении при повторном нажатии переключает слово.
   * Решённые слова не выбираются.
   */
  selectCell(row, col) {
    if (this.phase !== Phase.PLAYING) return;

    const clueEntry = this.#puzzle.entryAtClue(row, col);
    if (clueEntry) {
      if (!this.isSolved(clueEntry.id)) this.#activate(clueEntry.id);
      return;
    }

    const candidates = this.#puzzle
      .entriesThrough(cellKey(row, col))
      .filter((entry) => !this.isSolved(entry.id));
    if (candidates.length === 0) return;

    const current = candidates.findIndex((entry) => entry.id === this.activeId);
    // current === -1 даёт индекс 0, то есть первое слово клетки.
    this.#activate(candidates[(current + 1) % candidates.length].id);
  }

  // ---------- Ввод ----------

  typeLetter(char) {
    if (!this.#canEdit()) return;

    const slot = this.activeEntry.cells.find(({ key }) => !this.#letters.has(key));
    if (!slot) return;

    this.#letters.set(slot.key, { char: normalizeLetter(char), locked: false });
    this.#settle(slot.key);
  }

  erase() {
    if (!this.#canEdit()) return;

    const slot = this.activeEntry.cells.findLast(({ key }) => {
      const letter = this.#letters.get(key);
      return letter && !letter.locked;
    });
    if (!slot) return;

    this.#letters.delete(slot.key);
    this.#emit();
  }

  // ---------- Внутреннее ----------

  #canEdit() {
    return this.phase === Phase.PLAYING && this.activeEntry !== null;
  }

  #isFull(entry) {
    return entry.cells.every(({ key }) => this.#letters.has(key));
  }

  #matchesAnswer(entry) {
    return entry.cells.every(({ key }, i) => this.#letters.get(key)?.char === entry.answer[i]);
  }

  /** После новой буквы проверяем все слова через клетку, не только активное. */
  #settle(key) {
    let activeSolved = false;

    for (const entry of this.#puzzle.entriesThrough(key)) {
      if (this.isSolved(entry.id) || !this.#isFull(entry) || !this.#matchesAnswer(entry)) continue;

      this.#solved.add(entry.id);
      for (const cell of entry.cells) {
        this.#letters.get(cell.key).locked = true;
      }
      if (entry.id === this.activeId) activeSolved = true;
    }

    if (activeSolved) {
      this.phase = Phase.CELEBRATING;
      this.#flashId = this.activeId;
      setTimeout(() => this.#finishFlash(), this.#flashMs);
    }
    this.#emit();
  }

  #finishFlash() {
    this.#flashId = null;

    if (this.solvedCount === this.totalCount) {
      this.phase = Phase.COMPLETE;
    } else {
      this.phase = Phase.PLAYING;
      this.activeId = this.#nextUnsolved(this.activeId, 1);
    }
    this.#emit();
  }

  /** Ближайшее нерешённое слово по кругу; решённые пропускаются. */
  #nextUnsolved(fromId, delta) {
    const total = this.totalCount;
    for (let offset = 1; offset <= total; offset++) {
      const id = (((fromId + delta * offset) % total) + total) % total;
      if (!this.isSolved(id)) return id;
    }
    return null;
  }

  #activate(id) {
    if (id === null || id === this.activeId) return;
    this.activeId = id;
    this.#emit();
  }

  #emit() {
    for (const listener of this.#listeners) listener(this);
  }
}
