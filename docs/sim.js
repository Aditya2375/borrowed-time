(function () {
  var LIMIT = 20, MAX_ASKS = 3, ASK = 10;
  var used = 0, extra = 0, asks = 0, pending = false;
  var $ = function (id) { return document.getElementById(id); };
  var logEl = $('log');
  function say(t) { logEl.textContent = t; }
  function render() {
    var total = LIMIT + extra, left = Math.max(0, total - used);
    $('left').textContent = left;
    $('bar').style.width = Math.min(100, (used / total) * 100) + '%';
    $('barwrap').setAttribute('aria-valuenow', String(Math.round(Math.min(100, (used / total) * 100))));
    $('meta').textContent = used + ' of ' + total + ' min used';
    var blocked = left === 0;
    $('pause').hidden = !blocked;
    $('use5').disabled = blocked;
    $('ask').disabled = pending || asks >= MAX_ASKS;
    $('asks').textContent = pending ? 'Waiting for a friend...' : (MAX_ASKS - asks) + ' of ' + MAX_ASKS + ' asks left today';
    $('req').hidden = !pending;
    $('idle').hidden = pending;
  }
  $('use5').addEventListener('click', function () {
    used = Math.min(LIMIT + extra, used + 5);
    say(used >= LIMIT + extra ? 'Time is up. The apps are paused.' : '');
    render();
  });
  $('ask').addEventListener('click', function () {
    if (pending || asks >= MAX_ASKS) return;
    asks += 1; pending = true;
    say('Ask sent. One friend saying yes is enough.');
    render();
  });
  $('give').addEventListener('click', function () {
    if (!pending) return;
    pending = false; extra += ASK;
    say('Sana gave ' + ASK + ' minutes. When they run out, the apps pause again.');
    render();
  });
  $('no').addEventListener('click', function () {
    if (!pending) return;
    pending = false;
    say('Not now. That ask still counts toward your three.');
    render();
  });
  $('reset').addEventListener('click', function () {
    used = 0; extra = 0; asks = 0; pending = false;
    say('New day. Limit and asks reset.');
    render();
  });
  render();
})();
