/**
 * Выносные стрелки, как в печатных сканвордах: рисуются поверх границы клетки
 * с заданием и указывают на клетку, с которой начинается слово. Если слово
 * идёт не в сторону стрелки, она делает «колено» по направлению слова.
 *
 * Геометрия считается в единицах клетки: сама клетка с заданием занимает
 * квадрат 0..1, соседние клетки лежат вокруг неё.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';

// Размеры подобраны по печатным сканвордам: стрелка занимает четверть клетки.
const STROKE = 0.022;
const HEAD = 0.11;
const STRAIGHT_REACH = 0.27;
const SHIFT = 0.2;
const STUB = 0.15;
const RUN = 0.32;

const DIAGONAL_STEP = 0.2;
const DIAGONAL_RUN_FORWARD = 0.3;
const DIAGONAL_RUN_BACK = 0.2;

const point = (x, y) => ({ x, y });
const move = (p, v, k) => point(p.x + v.x * k, p.y + v.y * k);

/**
 * Стрелка к диагональному соседу: короткая косая черта из угла клетки с
 * заданием в клетку начала и затем отрезок с остриём по направлению слова.
 * Если слово идёт обратно к клетке задания, отрезок короче и не выходит
 * за пределы клетки начала.
 */
function diagonalPoints(toStart, along, dot) {
  const corner = point((1 + toStart.x) / 2, (1 + toStart.y) / 2);
  const bend = move(move(corner, point(toStart.x, 0), DIAGONAL_STEP), point(0, toStart.y), DIAGONAL_STEP);
  const run = dot > 0 ? DIAGONAL_RUN_FORWARD : DIAGONAL_RUN_BACK;
  return [corner, bend, move(bend, along, run)];
}

/**
 * Ломаная стрелки; последняя точка — остриё.
 * @param {{x: number, y: number}} toStart  шаг от клетки задания к клетке начала слова
 * @param {{x: number, y: number}} along    направление слова
 */
export function arrowPoints(toStart, along) {
  const orthogonal = toStart.x === 0 || toStart.y === 0;
  const dot = toStart.x * along.x + toStart.y * along.y;

  if (!orthogonal) return diagonalPoints(toStart, along, dot);

  // Середина общей стороны. Каждая стрелка начинается ровно на контуре клетки
  // с заданием и целиком лежит снаружи неё.
  const edge = move(point(0.5, 0.5), toStart, 0.5);

  if (dot === 0) {
    // Начало ближе к началу слова: короткая ножка внутрь клетки и поворот.
    const base = move(edge, along, -SHIFT);
    const turn = move(base, toStart, STUB);
    return [base, turn, move(turn, along, RUN)];
  }

  return [edge, move(edge, toStart, STRAIGHT_REACH)];
}

function svg(name, attributes) {
  const el = document.createElementNS(SVG_NS, name);
  for (const [key, value] of Object.entries(attributes)) el.setAttribute(key, value);
  return el;
}

/** Линия и наконечник по ломаной. */
function arrowShapes(points) {
  const tip = points.at(-1);
  const prev = points.at(-2);
  const length = Math.hypot(tip.x - prev.x, tip.y - prev.y);
  const dir = point((tip.x - prev.x) / length, (tip.y - prev.y) / length);
  const normal = point(-dir.y, dir.x);

  const headBase = move(tip, dir, -HEAD);
  const wingA = move(headBase, normal, HEAD * 0.5);
  const wingB = move(headBase, normal, -HEAD * 0.5);

  // Линия заканчивается внутри наконечника, чтобы остриё не затупилось.
  const line = [...points.slice(0, -1), move(tip, dir, -HEAD * 0.5)];
  const format = (p) => `${p.x.toFixed(3)},${p.y.toFixed(3)}`;

  return [
    svg('polyline', {
      points: line.map(format).join(' '),
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': STROKE,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
    }),
    svg('polygon', {
      points: [tip, wingA, wingB].map(format).join(' '),
      fill: 'currentColor',
    }),
  ];
}

const sign = (value) => Math.sign(value);

/**
 * SVG-слой для клетки с заданием. Его рамка в три раза больше клетки,
 * поэтому стрелка свободно заходит в соседние клетки.
 * @param {{clueRow: number, clueCol: number, cells: Array<{row: number, col: number}>,
 *          step: {row: number, col: number}}} entry
 */
export function buildArrow(entry) {
  const [first] = entry.cells;
  const toStart = point(sign(first.col - entry.clueCol), sign(first.row - entry.clueRow));
  const along = point(entry.step.col, entry.step.row);

  const root = svg('svg', { viewBox: '-1 -1 3 3', 'aria-hidden': 'true', class: 'board__arrow' });
  root.append(...arrowShapes(arrowPoints(toStart, along)));
  return root;
}
