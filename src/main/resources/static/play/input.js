/**
 * Ввод: экранная клавиатура и физическая. Только переводит нажатия в
 * команды Game, правил здесь нет.
 */

const RUSSIAN_LETTER = /^[а-яё]$/i;

/**
 * @param {import('./game.js').Game} game
 * @param {HTMLElement} keyboardEl
 */
export function bindInput(game, keyboardEl) {
  keyboardEl.addEventListener('click', (event) => {
    const key = event.target.closest('.key');
    if (!key) return;

    if (key.hasAttribute('data-erase')) game.erase();
    else game.typeLetter(key.dataset.key);
  });

  document.addEventListener('keydown', (event) => {
    if (event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return;
    if (document.getElementById('menu-toggle')?.checked) return;

    if (RUSSIAN_LETTER.test(event.key)) {
      game.typeLetter(event.key);
    } else if (event.key === 'Backspace') {
      event.preventDefault();
      game.erase();
    } else if (event.key === 'ArrowLeft') {
      game.step(-1);
    } else if (event.key === 'ArrowRight') {
      game.step(1);
    }
  });
}
