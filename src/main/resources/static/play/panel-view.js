/**
 * Панель под полем: текущее задание со стрелками и слово над клавиатурой.
 */

import { EntryStatus, Phase } from './game.js';

const PLURAL_RULES = new Intl.PluralRules('ru');
const LETTER_FORMS = { one: 'буква', few: 'буквы', many: 'букв', other: 'буквы' };

const letters = (count) => `${count} ${LETTER_FORMS[PLURAL_RULES.select(count)]}`;

export class PanelView {
  #clueCount;
  #clueText;
  #prev;
  #next;
  #answer;

  /**
   * @param {{clueCount: HTMLElement, clueText: HTMLElement, prev: HTMLButtonElement,
   *          next: HTMLButtonElement, answer: HTMLElement}} elements
   * @param {(delta: number) => void} onStep
   */
  constructor(elements, onStep) {
    this.#clueCount = elements.clueCount;
    this.#clueText = elements.clueText;
    this.#prev = elements.prev;
    this.#next = elements.next;
    this.#answer = elements.answer;

    this.#prev.addEventListener('click', () => onStep(-1));
    this.#next.addEventListener('click', () => onStep(1));
  }

  render(game) {
    const entry = game.activeEntry;
    if (entry === null) return;

    this.#clueText.textContent = entry.clue;
    this.#clueCount.textContent =
      `${letters(entry.cells.length)} · разгадано ${game.solvedCount} из ${game.totalCount}`;

    this.#prev.disabled = !game.canStep;
    this.#next.disabled = !game.canStep;

    this.#renderAnswer(game, entry);
  }

  #renderAnswer(game, entry) {
    const status = game.statusOf(entry);
    const slots = game.slotsOf(entry);
    const nextIndex = game.phase === Phase.PLAYING ? slots.findIndex((slot) => !slot.char) : -1;

    this.#answer.classList.toggle('is-success', status === EntryStatus.CORRECT);
    this.#answer.classList.toggle('is-error', status === EntryStatus.WRONG);

    this.#answer.replaceChildren(
      ...slots.map((slot, index) => {
        const el = document.createElement('span');
        el.className = 'answer__slot';
        el.textContent = slot.char;
        el.classList.toggle('is-locked', slot.locked);
        el.classList.toggle('is-next', index === nextIndex);
        return el;
      }),
    );
  }
}
