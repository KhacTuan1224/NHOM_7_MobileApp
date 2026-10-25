(function () {
  'use strict';
  if (window.rescueFarmInlineCaptcha) return;
  window.rescueFarmInlineCaptcha = true;
  // Adapt the existing hosted widget. No key, credentials or token are embedded here.
  var style = document.createElement('style');
  style.textContent = 'html,body{margin:0!important;padding:0!important;background:transparent!important}' +
    'body>p:not(#status){display:none!important}#captcha{display:flex;justify-content:center;padding:4px 0}' +
    '#status{font:13px sans-serif;text-align:center;margin:6px;display:none}';
  document.head.appendChild(style);
  var pending = false;
  function measure() {
    pending = false;
    var captcha = document.getElementById('captcha');
    if (!captcha || !window.RescueFarmCaptcha) return;
    var bottom = captcha.getBoundingClientRect().bottom + window.scrollY;
    var ready = captcha.querySelectorAll('iframe').length > 0, expanded = false;
    document.querySelectorAll('iframe').forEach(function (frame) {
      // Google may append the bframe inside OR outside the widget container.
      // Skip only the anchor, never all frames nested within #captcha.
      var src = frame.getAttribute('src') || '', title = frame.getAttribute('title') || '';
      var challenge = /\/bframe(?:\?|$)/.test(src) || /challenge/i.test(title) || !captcha.contains(frame);
      if (!challenge) return;
      var rect = frame.getBoundingClientRect(), css = window.getComputedStyle(frame);
      if (rect.width > 0 && rect.height > 0 && css.visibility !== 'hidden' && css.display !== 'none') {
        expanded = true;bottom = Math.max(bottom, rect.height + 16, rect.bottom + window.scrollY + 12);
      }
    });
    var status = document.getElementById('status'), notice = '';
    if (status) {
      var message = status.textContent || '';
      if (/Không|hết hạn/i.test(message)) notice = message;
      var display = notice ? 'block' : 'none';
      if (status.style.display !== display) status.style.display = display;
      if (notice) bottom = Math.max(bottom, status.getBoundingClientRect().bottom + window.scrollY);
    }
    window.RescueFarmCaptcha.layout(Math.ceil(bottom + 8), expanded, ready, notice);
  }
  function schedule() { if (!pending) { pending = true; window.requestAnimationFrame(measure); } }
  new MutationObserver(schedule).observe(document.body, {subtree:true,childList:true,attributes:true,characterData:true});
  window.addEventListener('resize',schedule);
  window.setInterval(schedule,500);
  window.rescueFarmResetCaptcha = function () {
    if (window.grecaptcha && typeof window.grecaptcha.reset === 'function') window.grecaptcha.reset();
    window.scrollTo(0,0);schedule();
  };
  schedule();
}());
