(function () {
  var DIFFICULTIES = {
    easy: { label: 'Лёгкий', timeFactor: 1, clueDensity: 0.26 },
    medium: { label: 'Средний', timeFactor: 1.5, clueDensity: 0.22 },
    hard: { label: 'Сложный', timeFactor: 2.2, clueDensity: 0.18 },
  };

  var SIZES = {
    9: { words: 18, minutes: 10 },
    12: { words: 34, minutes: 20 },
    15: { words: 52, minutes: 35 },
  };

  var form = document.getElementById('generator');
  var grid = document.getElementById('grid');
  var summary = document.getElementById('summary');
  var words = document.getElementById('words');
  var minutes = document.getElementById('minutes');
  var status = document.getElementById('status');
  var submit = document.getElementById('submit');
  var submitLabel = document.getElementById('submit-label');
  var timer = null;

  function isClueCell(row, col, size, density) {
    if (row === 0 || col === 0) return true;
    var hash = (row * 31 + col * 17 + row * col * 7 + size * 5) % 100;
    return hash < density * 100;
  }

  function renderGrid(size, density) {
    var html = '';
    for (var row = 0; row < size; row++) {
      for (var col = 0; col < size; col++) {
        var clue = isClueCell(row, col, size, density) ? ' class="clue"' : '';
        html += '<span' + clue + ' style="animation-delay:' + (row + col) * 14 + 'ms"></span>';
      }
    }
    grid.style.gridTemplateColumns = 'repeat(' + size + ', minmax(0, 1fr))';
    grid.setAttribute('aria-label', 'Схема сетки ' + size + ' на ' + size);
    grid.innerHTML = html;
  }

  function update() {
    var data = new FormData(form);
    var level = DIFFICULTIES[data.get('difficulty')];
    var size = Number(data.get('size'));
    var format = SIZES[size];
    var time = Math.round((format.minutes * level.timeFactor) / 5) * 5;

    renderGrid(size, level.clueDensity);
    summary.textContent = 'Сканворд ' + size + '×' + size + ', сложность — ' + level.label.toLowerCase() + '.';
    words.textContent = '≈ ' + format.words + ' слов';
    minutes.textContent = '≈ ' + time + ' минут';
  }

  form.addEventListener('change', function () {
    clearTimeout(timer);
    status.textContent = '';
    submit.disabled = false;
    submit.classList.remove('is-loading');
    submitLabel.textContent = 'Сгенерировать';
    update();
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var data = new FormData(form);
    var level = DIFFICULTIES[data.get('difficulty')];
    var size = data.get('size');

    submit.disabled = true;
    submit.classList.add('is-loading');
    submitLabel.textContent = 'Расставляем слова…';
    status.textContent = '';

    timer = setTimeout(function () {
      submit.disabled = false;
      submit.classList.remove('is-loading');
      submitLabel.textContent = 'Сгенерировать';
      status.textContent =
        'Заготовка ' + size + '×' + size + ' (' + level.label.toLowerCase() + ') готова. Сборку слов подключим следующим шагом.';
    }, 1400);
  });

  document.querySelectorAll('label[role="button"]').forEach(function (el) {
    el.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        document.getElementById('menu-toggle').click();
      }
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') document.getElementById('menu-toggle').checked = false;
  });

  update();
})();
