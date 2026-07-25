(function () {
  "use strict";

  var MAX_ITEMS = 40;
  var BILIBILI_BASE = "https://www.bilibili.com/";
  var stats = {
    videoLinksFound: 0,
    parsedItems: 0,
    uniqueItems: 0,
    duplicateItems: 0,
    filteredInvalidVideoUrl: 0,
    filteredMissingTitle: 0,
    filteredMissingCover: 0,
  };

  function result(status, items, error) {
    return JSON.stringify({
      status: status,
      items: items,
      stats: stats,
      error: error || null,
    });
  }

  function cleanText(value) {
    return String(value || "")
      .replace(/\s+/g, " ")
      .trim();
  }

  function isBilibiliHost(hostname) {
    var host = cleanText(hostname).toLowerCase().replace(/\.$/, "");
    return host === "bilibili.com" || host.endsWith(".bilibili.com");
  }

  function resolutionBase() {
    try {
      var page = new URL(document.baseURI);
      if (page.protocol === "https:" && isBilibiliHost(page.hostname)) return page.href;
    } catch (_) {
      // Local fixtures and incomplete documents intentionally fall back to the public site base.
    }
    return BILIBILI_BASE;
  }

  function looksLikeVideoPath(rawValue) {
    return /(^|\/)video\//i.test(cleanText(rawValue));
  }

  function normalizeVideoUrl(rawValue) {
    var value = cleanText(rawValue);
    if (!value) return null;
    try {
      var url = new URL(value, resolutionBase());
      if (url.protocol !== "https:" || !isBilibiliHost(url.hostname)) return null;
      if (url.port && url.port !== "443") return null;
      if (!url.pathname.toLowerCase().startsWith("/video/") || url.pathname.length <= 7) {
        return null;
      }
      url.search = "";
      url.hash = "";
      return url.href;
    } catch (_) {
      return null;
    }
  }

  function normalizeImageUrl(rawValue) {
    var value = cleanText(rawValue);
    if (!value) return null;
    try {
      var url = new URL(value, resolutionBase());
      if (url.protocol !== "https:" || !url.hostname || url.username || url.password) return null;
      url.hash = "";
      return url.href;
    } catch (_) {
      return null;
    }
  }

  function firstSrcSetUrl(value) {
    var first = cleanText(value).split(",")[0];
    return cleanText(first).split(/\s+/)[0];
  }

  function readCover(image) {
    if (!image) return null;
    var values = [
      image.currentSrc,
      image.getAttribute("src"),
      image.getAttribute("data-src"),
      image.getAttribute("data-lazy-src"),
      image.getAttribute("data-original"),
      image.getAttribute("data-url"),
      firstSrcSetUrl(image.getAttribute("srcset")),
      firstSrcSetUrl(image.getAttribute("data-srcset")),
    ];
    for (var index = 0; index < values.length; index += 1) {
      var normalized = normalizeImageUrl(values[index]);
      if (normalized) return normalized;
    }
    return null;
  }

  function isMetadataText(value) {
    var text = cleanText(value);
    if (!text || text.length < 2 || text.length > 180) return true;
    if (/^(?:\d{1,2}:)?\d{1,2}:\d{2}$/.test(text)) return true;
    if (/^[\d.]+\s*(?:万|亿)?\s*(?:播放|观看|点赞|弹幕)?$/.test(text)) return true;
    return /^(?:封面|图片|视频|播放视频|观看视频|作者|UP主)$/i.test(text);
  }

  function addCandidate(candidates, value) {
    var text = cleanText(value);
    if (!isMetadataText(text) && candidates.indexOf(text) < 0) candidates.push(text);
  }

  function readTitle(anchor, container, image, videoUrl) {
    var candidates = [];
    addCandidate(candidates, anchor.getAttribute("title"));
    addCandidate(candidates, anchor.getAttribute("aria-label"));
    addCandidate(candidates, anchor.textContent);
    if (image) {
      addCandidate(candidates, image.getAttribute("alt"));
      addCandidate(candidates, image.getAttribute("title"));
    }

    var linked = container.querySelectorAll("a[href]");
    for (var linkIndex = 0; linkIndex < linked.length; linkIndex += 1) {
      var link = linked[linkIndex];
      if (normalizeVideoUrl(link.getAttribute("href")) !== videoUrl) continue;
      addCandidate(candidates, link.getAttribute("title"));
      addCandidate(candidates, link.getAttribute("aria-label"));
      addCandidate(candidates, link.textContent);
    }

    var semantic = container.querySelectorAll(
      'h1,h2,h3,h4,h5,h6,figcaption,[itemprop="name"],[title],[aria-label]'
    );
    for (var semanticIndex = 0; semanticIndex < semantic.length; semanticIndex += 1) {
      var node = semantic[semanticIndex];
      addCandidate(candidates, node.getAttribute("title"));
      addCandidate(candidates, node.getAttribute("aria-label"));
      addCandidate(candidates, node.textContent);
    }

    var visibleText = String(container.innerText || container.textContent || "").split(/\n+/);
    for (var textIndex = 0; textIndex < visibleText.length; textIndex += 1) {
      addCandidate(candidates, visibleText[textIndex]);
    }
    candidates.sort(function (first, second) {
      return second.length - first.length;
    });
    return candidates.length ? candidates[0].slice(0, 300) : null;
  }

  function containsDifferentVideo(container, expectedUrl) {
    var links = container.querySelectorAll("a[href]");
    for (var index = 0; index < links.length; index += 1) {
      var normalized = normalizeVideoUrl(links[index].getAttribute("href"));
      if (normalized && normalized !== expectedUrl) return true;
    }
    return false;
  }

  function findImage(container) {
    if (container.tagName === "IMG") return container;
    var images = container.querySelectorAll("img");
    for (var index = 0; index < images.length; index += 1) {
      if (readCover(images[index])) return images[index];
    }
    return images.length ? images[0] : null;
  }

  function findCard(anchor, videoUrl) {
    var node = anchor;
    var fallback = { container: anchor, image: findImage(anchor), title: null };
    for (var depth = 0; node && depth < 7; depth += 1) {
      if (node === document.body || node === document.documentElement) break;
      if (node !== anchor && containsDifferentVideo(node, videoUrl)) break;
      var image = findImage(node);
      var title = readTitle(anchor, node, image, videoUrl);
      if (image || title) fallback = { container: node, image: image, title: title };
      if (image && title) return fallback;
      node = node.parentElement;
    }
    return fallback;
  }

  function readDuration(container) {
    var explicit = container.querySelector("[data-duration]");
    if (explicit) {
      var attribute = cleanText(explicit.getAttribute("data-duration"));
      if (attribute) return attribute.slice(0, 40);
    }
    var match = cleanText(container.innerText || container.textContent).match(
      /(?:^|\s)((?:\d{1,2}:)?\d{1,2}:\d{2})(?:\s|$)/
    );
    return match ? match[1] : null;
  }

  function readUploader(container) {
    var explicit = container.querySelector('[rel="author"],[itemprop="author"],[itemprop="creator"],[data-uploader]');
    if (explicit) {
      var explicitValue = cleanText(explicit.getAttribute("data-uploader") || explicit.textContent);
      if (explicitValue) return explicitValue.slice(0, 120);
    }
    var links = container.querySelectorAll("a[href]");
    for (var index = 0; index < links.length; index += 1) {
      try {
        var url = new URL(links[index].getAttribute("href"), resolutionBase());
        if (isBilibiliHost(url.hostname) && url.pathname.toLowerCase().startsWith("/space/")) {
          var value = cleanText(links[index].textContent);
          if (value) return value.slice(0, 120);
        }
      } catch (_) {
        // Ignore malformed nearby links without aborting the card.
      }
    }
    return null;
  }

  function readPlayCount(container) {
    var explicit = container.querySelector('[data-play-count],[itemprop="interactionCount"]');
    if (explicit) {
      var value = cleanText(
        explicit.getAttribute("data-play-count") || explicit.getAttribute("content") || explicit.textContent
      );
      if (value) return value.slice(0, 80);
    }
    var text = cleanText(container.innerText || container.textContent);
    var match = text.match(/(?:播放|观看)[：:]?\s*([\d.]+\s*(?:万|亿)?)/);
    if (!match) match = text.match(/([\d.]+\s*(?:万|亿)?)\s*(?:播放|观看)/);
    return match ? match[1] : null;
  }

  function idFromUrl(videoUrl) {
    try {
      var parts = new URL(videoUrl).pathname.split("/").filter(Boolean);
      return parts.length ? parts[parts.length - 1] : videoUrl;
    } catch (_) {
      return videoUrl;
    }
  }

  try {
    var items = [];
    var seen = Object.create(null);
    var anchors = document.querySelectorAll("a[href]");
    for (var index = 0; index < anchors.length; index += 1) {
      var anchor = anchors[index];
      var rawHref = anchor.getAttribute("href");
      var videoUrl = normalizeVideoUrl(rawHref);
      if (!videoUrl) {
        if (looksLikeVideoPath(rawHref)) stats.filteredInvalidVideoUrl += 1;
        continue;
      }

      stats.videoLinksFound += 1;
      var card = findCard(anchor, videoUrl);
      var title = card.title;
      var coverUrl = readCover(card.image);
      if (!title) {
        stats.filteredMissingTitle += 1;
        continue;
      }
      if (!coverUrl) {
        stats.filteredMissingCover += 1;
        continue;
      }

      stats.parsedItems += 1;
      if (seen[videoUrl]) {
        stats.duplicateItems += 1;
        continue;
      }
      seen[videoUrl] = true;
      if (items.length >= MAX_ITEMS) continue;
      items.push({
        id: idFromUrl(videoUrl),
        title: title,
        videoUrl: videoUrl,
        coverUrl: coverUrl,
        uploader: readUploader(card.container),
        playCount: readPlayCount(card.container),
        duration: readDuration(card.container),
      });
    }

    stats.uniqueItems = items.length;
    if (items.length) return result("success", items, null);
    if (!stats.videoLinksFound) {
      return result("empty", [], {
        code: "NO_VIDEO_LINKS",
        message: "页面尚未出现视频卡片",
        retryable: true,
      });
    }
    return result("error", [], {
      code: "NO_VALID_ITEMS",
      message: "找到视频链接，但没有包含有效标题和封面的卡片",
      retryable: true,
    });
  } catch (_) {
    return result("error", [], {
      code: "EXTRACTOR_EXCEPTION",
      message: "推荐页面结构无法安全解析",
      retryable: false,
    });
  }
})();
