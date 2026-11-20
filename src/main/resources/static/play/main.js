import { BoardView } from './board-view.js';
import { Game, Phase } from './game.js';
import { bindInput } from './input.js';
import { PanelView } from './panel-view.js';
import { Puzzle } from './puzzle.js';

const byId = (id) => document.getElementById(id);

const boardEl = byId('board');
const winDialog = byId('win');

const puzzle = Puzzle.fromBoard(boardEl);
const game = new Game(puzzle);

const board = new BoardView(boardEl, puzzle, (row, col) => game.selectCell(row, col));
const panel = new PanelView(
  {
    clueCount: byId('clue-count'),
    clueText: byId('clue-text'),
    prev: byId('prev'),
    next: byId('next'),
    answer: byId('answer'),
  },
  (delta) => game.step(delta),
);

game.subscribe((state) => {
  board.render(state);
  panel.render(state);
  if (state.phase === Phase.COMPLETE && !winDialog.open) winDialog.showModal();
});

bindInput(game, document.querySelector('.keyboard'));

board.render(game);
panel.render(game);
