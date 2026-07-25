(function () {
  'use strict';

  var runtime = window.__biliDemoVideoRuntime;
  if (!runtime) return;
  if (runtime.cleanup) {
    runtime.cleanup.apply();
    return;
  }

  // Keep every site-facing selector in this object. These are semantic elements/attributes only;
  // no generated class names are used.
  var SELECTORS = Object.freeze({
    main: 'main,[role="main"]',
    article: 'article,[role="article"]',
    semanticShell: 'header,[role="banner"],nav[role="navigation"],body > nav[aria-label]',
    promotionCandidate: 'a[href],button,[role="button"]',
    link: 'a[href]',
    protectedMedia: 'video,[data-video-player]',
    logoImage: 'img[alt],img[title],svg[aria-label]'
  });
  var HIDDEN_ATTRIBUTE = 'data-bili-demo-hidden';
  var MAIN_ATTRIBUTE = 'data-bili-demo-video-main';
  var ARTICLE_ATTRIBUTE = 'data-bili-demo-video-article';
  var RETRY_DELAYS = [250, 750, 1500, 3000];
  var MAX_PROMOTION_CANDIDATES = 500;
  var observer = null;
  var retryTimers = [];
  var mutationTimer = 0;

  function normalizedText(node) {
    var text = (node.textContent || '').replace(/\s+/g, ' ').trim();
    return text.length > 80 ? text.slice(0, 80) : text;
  }

  function documentTop(node) {
    return node.getBoundingClientRect().top + (window.scrollY || 0);
  }

  function containsProtectedMedia(node) {
    return Boolean(node.querySelector && node.querySelector(SELECTORS.protectedMedia));
  }

  function markHidden(node) {
    if (!node || node === document.body || node === document.documentElement) return;
    if (containsProtectedMedia(node)) return;
    node.setAttribute(HIDDEN_ATTRIBUTE, 'true');
  }

  function markMainContent() {
    var main = document.querySelector(SELECTORS.main);
    if (main) main.setAttribute(MAIN_ATTRIBUTE, 'true');
    Array.prototype.forEach.call(document.querySelectorAll(SELECTORS.article), function (article) {
      if (!containsProtectedMedia(article)) article.setAttribute(ARTICLE_ATTRIBUTE, 'true');
    });
  }

  function hideSemanticShells() {
    var topLimit = Math.max(360, window.innerHeight * 0.35);
    Array.prototype.forEach.call(document.querySelectorAll(SELECTORS.semanticShell), function (node) {
      if (node.closest && node.closest(SELECTORS.article)) return;
      if (documentTop(node) > topLimit) return;
      markHidden(node);
    });
  }

  function targetHref(node) {
    if (node.matches && node.matches(SELECTORS.link)) return node.getAttribute('href') || '';
    var link = node.querySelector && node.querySelector(SELECTORS.link);
    return link ? (link.getAttribute('href') || '') : '';
  }

  function appLink(href) {
    if (!href) return false;
    var lower = href.trim().toLowerCase();
    if (/^(bilibili|intent):/.test(lower)) return true;
    try {
      var parsed = new URL(href, location.href);
      var host = parsed.hostname.toLowerCase();
      var path = parsed.pathname.toLowerCase();
      return host === 'app.bilibili.com' ||
        host === 'd.bilibili.com' ||
        /(^|\/)(download|app)(\/|$)/.test(path);
    } catch (_) {
      return false;
    }
  }

  function appPromotionText(text) {
    if (!text || text.length > 32) return false;
    return /(下载\s*app|app\s*内打开|打开\s*app|立即打开)/i.test(text);
  }

  function fixedOrStickyContainer(node) {
    var current = node;
    for (var depth = 0; current && depth < 4; depth += 1) {
      var position = getComputedStyle(current).position;
      if (position === 'fixed' || position === 'sticky') return current;
      current = current.parentElement;
    }
    return null;
  }

  function safeEdgeOverlay(node) {
    var rect = node.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    if (rect.width > window.innerWidth || rect.height > window.innerHeight * 0.3) return false;
    return rect.top < window.innerHeight * 0.3 || rect.bottom > window.innerHeight * 0.7;
  }

  function rootBilibiliLink(href) {
    if (!href) return false;
    try {
      var parsed = new URL(href, location.href);
      var host = parsed.hostname.toLowerCase().replace(/\.$/, '');
      return parsed.protocol === 'https:' &&
        (host === 'bilibili.com' || /\.bilibili\.com$/.test(host)) &&
        (parsed.pathname === '' || parsed.pathname === '/');
    } catch (_) {
      return false;
    }
  }

  function logoIdentity(node) {
    var ownIdentity = [
      node.getAttribute('aria-label') || '',
      node.getAttribute('title') || '',
      normalizedText(node)
    ].join(' ').toLowerCase();
    if (/(bilibili|哔哩哔哩)/.test(ownIdentity)) return true;
    var image = node.querySelector && node.querySelector(SELECTORS.logoImage);
    if (!image) return false;
    return /(bilibili|哔哩哔哩)/i.test([
      image.getAttribute('alt') || '',
      image.getAttribute('title') || '',
      image.getAttribute('aria-label') || ''
    ].join(' '));
  }

  function hidePromotionsAndLogo() {
    var candidates = document.querySelectorAll(SELECTORS.promotionCandidate);
    var count = Math.min(candidates.length, MAX_PROMOTION_CANDIDATES);
    for (var index = 0; index < count; index += 1) {
      var candidate = candidates[index];
      var href = targetHref(candidate);
      var text = normalizedText(candidate);

      if (appPromotionText(text) && appLink(href)) {
        var overlay = fixedOrStickyContainer(candidate);
        if (overlay && safeEdgeOverlay(overlay)) {
          markHidden(overlay);
        } else if (documentTop(candidate) < 280) {
          markHidden(candidate);
        }
        continue;
      }

      if (
        candidate.matches &&
        candidate.matches(SELECTORS.link) &&
        documentTop(candidate) < 280 &&
        rootBilibiliLink(href) &&
        logoIdentity(candidate)
      ) {
        markHidden(candidate);
      }
    }
  }

  function runCleanup() {
    if (!runtime.isEligiblePage || !runtime.isEligiblePage() || !document.body) return;
    markMainContent();
    hideSemanticShells();
    hidePromotionsAndLogo();
  }

  function clearScheduledWork() {
    retryTimers.forEach(function (timer) { clearTimeout(timer); });
    retryTimers = [];
    if (mutationTimer) clearTimeout(mutationTimer);
    mutationTimer = 0;
  }

  function connectObserver() {
    if (observer || !document.body) return;
    observer = new MutationObserver(function (records) {
      var hasAddedTopLevelNode = records.some(function (record) {
        return record.addedNodes && record.addedNodes.length > 0;
      });
      if (!hasAddedTopLevelNode || mutationTimer) return;
      mutationTimer = setTimeout(function () {
        mutationTimer = 0;
        runCleanup();
      }, 100);
    });
    // Only direct body children are observed. We deliberately do not watch the whole DOM subtree
    // or any attributes; finite retries handle content rendered inside the application root.
    observer.observe(document.body, {childList: true, subtree: false});
  }

  function removeMarkers() {
    Array.prototype.forEach.call(
      document.querySelectorAll('[' + HIDDEN_ATTRIBUTE + ']'),
      function (node) { node.removeAttribute(HIDDEN_ATTRIBUTE); }
    );
    Array.prototype.forEach.call(
      document.querySelectorAll('[' + MAIN_ATTRIBUTE + ']'),
      function (node) { node.removeAttribute(MAIN_ATTRIBUTE); }
    );
    Array.prototype.forEach.call(
      document.querySelectorAll('[' + ARTICLE_ATTRIBUTE + ']'),
      function (node) { node.removeAttribute(ARTICLE_ATTRIBUTE); }
    );
  }

  function deactivate() {
    clearScheduledWork();
    if (observer) observer.disconnect();
    observer = null;
    removeMarkers();
  }

  function apply() {
    if (!runtime.isEligiblePage || !runtime.isEligiblePage()) {
      deactivate();
      return;
    }
    if (!document.body) return;
    clearScheduledWork();
    runCleanup();
    connectObserver();
    RETRY_DELAYS.forEach(function (delay) {
      retryTimers.push(setTimeout(runCleanup, delay));
    });
  }

  function dispose() {
    deactivate();
  }

  runtime.cleanup = {
    selectors: SELECTORS,
    apply: apply,
    deactivate: deactivate,
    dispose: dispose
  };
  apply();
}());
