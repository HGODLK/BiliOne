(function () {
  'use strict';

  var RUNTIME_KEY = '__biliDemoVideoRuntime';
  var STYLE_ID = 'bili-web-demo-video-style';
  var ACTIVE_ATTRIBUTE = 'data-bili-demo-video-active';
  var PROTECTED_LABELS = {
    login: true,
    passport: true,
    pay: true,
    payment: true,
    captcha: true,
    account: true,
    security: true
  };
  var styles = window.__BILI_DEMO_VIDEO_STYLES__ || {};
  var runtime = window[RUNTIME_KEY];

  if (!runtime) {
    runtime = {
      applyTimer: 0,
      cleanup: null,
      domReadyHooked: false,
      viewportNode: null,
      viewportCreated: false,
      viewportOriginalContent: null
    };
    window[RUNTIME_KEY] = runtime;

    ['pushState', 'replaceState'].forEach(function (name) {
      var original = history[name];
      if (typeof original !== 'function') return;
      history[name] = function () {
        var result = original.apply(this, arguments);
        var activeRuntime = window[RUNTIME_KEY];
        if (activeRuntime && activeRuntime.scheduleApply) activeRuntime.scheduleApply();
        return result;
      };
    });

    addEventListener('popstate', function () {
      var activeRuntime = window[RUNTIME_KEY];
      if (activeRuntime && activeRuntime.scheduleApply) activeRuntime.scheduleApply();
    });
    addEventListener('hashchange', function () {
      var activeRuntime = window[RUNTIME_KEY];
      if (activeRuntime && activeRuntime.scheduleApply) activeRuntime.scheduleApply();
    });
    addEventListener('pageshow', function () {
      var activeRuntime = window[RUNTIME_KEY];
      if (activeRuntime && activeRuntime.scheduleApply) activeRuntime.scheduleApply();
    });
    addEventListener('pagehide', function () {
      var activeRuntime = window[RUNTIME_KEY];
      if (activeRuntime && activeRuntime.cleanup) activeRuntime.cleanup.dispose();
    });
  }

  function protectedPage() {
    var labels = location.hostname.toLowerCase().split('.');
    for (var index = 0; index < labels.length; index += 1) {
      if (PROTECTED_LABELS[labels[index]]) return true;
    }
    var path = location.pathname.toLowerCase();
    return /(^|\/)(login|passport|pay|payment|captcha|account|security)(\/|$)/.test(path);
  }

  function videoPage() {
    var host = location.hostname.toLowerCase().replace(/\.$/, '');
    var allowedHost = host === 'bilibili.com' || /\.bilibili\.com$/.test(host);
    if (!allowedHost) return false;
    return /^\/(video|bangumi\/play)\//.test(location.pathname.toLowerCase());
  }

  function eligiblePage() {
    return videoPage() && !protectedPage();
  }

  function ensureViewport() {
    var viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) {
      viewport = document.createElement('meta');
      viewport.name = 'viewport';
      document.head.appendChild(viewport);
      runtime.viewportCreated = true;
      runtime.viewportOriginalContent = null;
    } else if (runtime.viewportNode !== viewport) {
      runtime.viewportCreated = false;
      runtime.viewportOriginalContent = viewport.getAttribute('content');
    }
    runtime.viewportNode = viewport;
    viewport.content = 'width=device-width, initial-scale=1, viewport-fit=cover';
  }

  function restoreViewport() {
    var viewport = runtime.viewportNode;
    if (!viewport) return;
    if (runtime.viewportCreated) {
      if (viewport.parentNode) viewport.parentNode.removeChild(viewport);
    } else if (runtime.viewportOriginalContent === null) {
      viewport.removeAttribute('content');
    } else {
      viewport.setAttribute('content', runtime.viewportOriginalContent);
    }
    runtime.viewportNode = null;
    runtime.viewportCreated = false;
    runtime.viewportOriginalContent = null;
  }

  function deactivate() {
    var styleNode = document.getElementById(STYLE_ID);
    if (styleNode && styleNode.parentNode) styleNode.parentNode.removeChild(styleNode);
    if (document.documentElement) document.documentElement.removeAttribute(ACTIVE_ATTRIBUTE);
    restoreViewport();
    if (runtime.cleanup) runtime.cleanup.deactivate();
  }

  function apply() {
    if (!document.documentElement || !eligiblePage()) {
      deactivate();
      return;
    }
    if (!document.head) return;

    ensureViewport();
    document.documentElement.setAttribute(ACTIVE_ATTRIBUTE, 'true');
    var css = (styles.common || '') + '\n' + (styles.video || '');
    var styleNode = document.getElementById(STYLE_ID);
    if (!styleNode) {
      styleNode = document.createElement('style');
      styleNode.id = STYLE_ID;
      document.head.appendChild(styleNode);
    }
    if (styleNode.textContent !== css) styleNode.textContent = css;
    if (runtime.cleanup) runtime.cleanup.apply();
  }

  function scheduleApply() {
    if (runtime.applyTimer) clearTimeout(runtime.applyTimer);
    runtime.applyTimer = setTimeout(function () {
      runtime.applyTimer = 0;
      apply();
    }, 0);
  }

  runtime.isEligiblePage = eligiblePage;
  runtime.apply = apply;
  runtime.scheduleApply = scheduleApply;

  if (document.head) {
    scheduleApply();
  } else if (!runtime.domReadyHooked) {
    runtime.domReadyHooked = true;
    document.addEventListener('DOMContentLoaded', function () {
      runtime.domReadyHooked = false;
      scheduleApply();
    }, {once: true});
  }
}());
