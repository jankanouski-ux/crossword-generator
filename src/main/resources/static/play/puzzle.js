/**
 * Неизменяемое описание сканворда. Читается один раз из разметки, которую
 * отрисовал Thymeleaf (gameboard.html): сервер остаётся единственным
 * источником данных, а JS только играет.
 */

const STEP = {
  RIGHT: { row: 0, col: 1 },
  DOWN: { row: 1, col: 0 },
};

export const cellKey = (row, col) => `${row},${col}`;

/** В словарях нет «Ё», на клавиатуре её тоже нет. */
export const normalizeLetter = (text) => text.toUpperCase().replaceAll('Ё', 'Е');

export class Puzzle {
  #entryByClueKey;
  #entriesByCellKey = new Map();

  /** @param {HTMLElement} boardEl контейнер `.board` с клетками `[data-row][data-col]` */
  static fromBoard(boardEl) {
    // querySelectorAll отдаёт клетки построчно, поэтому id заданий идут в порядке чтения.
    const entries = [...boardEl.querySelectorAll('.board__clue[data-answer]')].map((clueEl, id) => {
      const cellEl = clueEl.closest('[data-row]');
      const data = clueEl.dataset;
      const answer = normalizeLetter(data.answer);
      const step = STEP[data.direction];
      const startRow = Number(data.startRow);
      const startCol = Number(data.startCol);

      const cells = [...answer].map((_, i) => {
        const row = startRow + step.row * i;
        const col = startCol + step.col * i;
        return { row, col, key: cellKey(row, col) };
      });

      return {
        id,
        answer,
        clue: data.clue,
        clueRow: Number(cellEl.dataset.row),
        clueCol: Number(cellEl.dataset.col),
        clueKey: cellKey(Number(cellEl.dataset.row), Number(cellEl.dataset.col)),
        step,
        cells,
      };
    });

    return new Puzzle(entries);
  }

  /** @param {Array<{id:number, answer:string, clue:string, clueKey:string, cells:Array}>} entries */
  constructor(entries) {
    this.entries = entries;
    this.#entryByClueKey = new Map(entries.map((entry) => [entry.clueKey, entry]));

    for (const entry of entries) {
      for (const { key } of entry.cells) {
        const through = this.#entriesByCellKey.get(key) ?? [];
        through.push(entry);
        this.#entriesByCellKey.set(key, through);
      }
    }
  }

  get size() {
    return this.entries.length;
  }

  /** Слово, чьё задание лежит в этой клетке (или undefined). */
  entryAtClue(row, col) {
    return this.#entryByClueKey.get(cellKey(row, col));
  }

  /** Все слова, проходящие через клетку с буквой (одно или два). */
  entriesThrough(key) {
    return this.#entriesByCellKey.get(key) ?? [];
  }
}
