/**
 * Игровое поле: подсветка выбранного слова, буквы, решённые задания.
 * Состояние не хранит, только рисует по Game и сообщает о нажатиях.
 */

import { buildArrow } from './arrow.js';
import { cellKey } from './puzzle.js';

export class BoardView {
  #cells = new Map();

  /**
   * @param {HTMLElement} boardEl
   * @param {import('./puzzle.js').Puzzle} puzzle
   * @param {(row: number, col: number) => void} onCellPress
   */
  constructor(boardEl, puzzle, onCellPress) {
    for (const el of boardEl.querySelectorAll('[data-row]')) {
      this.#cells.set(cellKey(el.dataset.row, el.dataset.col), el);
    }

    for (const entry of puzzle.entries) {
      this.#cells.get(entry.clueKey).append(buildArrow(entry));
    }

    boardEl.addEventListener('click', (event) => {
      const el = event.target.closest('[data-row]');
      if (el) onCellPress(Number(el.dataset.row), Number(el.dataset.col));
    });
  }

  render(game) {
    const active = game.activeEntry;
    const activeKeys = new Set(active?.cells.map((cell) => cell.key));

    for (const [key, el] of this.#cells) {
      const letter = game.letterAt(key);
      const text = letter?.char ?? '';
      if (el.textContent !== text && el.classList.contains('board__cell--letter')) {
        el.textContent = text;
      }

      el.classList.toggle('is-active', activeKeys.has(key));
      el.classList.toggle('is-locked', letter?.locked === true);
      el.classList.toggle('is-draft', letter !== undefined && !letter.locked);
    }

    for (const entry of game.puzzle.entries) {
      const el = this.#cells.get(entry.clueKey);
      el.classList.toggle('is-active', entry.id === game.activeId);
      el.classList.toggle('is-solved', game.isSolved(entry.id));
    }
  }
}
