// Runs in MAIN world at document_start.
(() => {
  try {
    const loc = window.location;
    if (!loc) return;
    const href = (loc.href || "").toLowerCase();
    const host = (loc.hostname || "").toLowerCase();

    // Do NOT run on about:blank or empty iframes
    if (!host || host === "about:blank" || href.startsWith("about:") || href.startsWith("chrome:")) {
      return;
    }

    // Never interfere with official authentication / social platforms
    const exempt = [
      "x.com", "twitter.com", "twimg.com", "t.co",
      "google.com", "google.co.id", "accounts.google.com", "gstatic.com",
      "apple.com", "icloud.com",
      "facebook.com", "instagram.com", "meta.com",
      "github.com", "microsoft.com", "live.com","t.co"
    ];
    for (let i = 0; i < exempt.length; i++) {
      if (host === exempt[i] || host.endsWith("." + exempt[i]) || href.includes(exempt[i])) {
        return;
      }
    }
  } catch (e) {
    return;
  }

  if (window.__videoPopupGuardInstalled) return;
  window.__videoPopupGuardInstalled = true;

  // Block programmatic popup spam (window.open without user intent)
  const blockOpen = function (url) {
    console.info("[Video Popup Guard] window.open popup otomatis diblokir: " + url);
    return null;
  };
  try {
    window.open = blockOpen;
  } catch (e) {}

  function isNewContext(target) {
    return !!target && !["_self", "_parent", "_top"].includes(target.toLowerCase());
  }

  function isSameDomain(linkHost, pageHost) {
    if (!linkHost || !pageHost) return true;
    linkHost = linkHost.toLowerCase().trim();
    pageHost = pageHost.toLowerCase().trim();
    if (linkHost === pageHost) return true;
    return linkHost.endsWith("." + pageHost) || pageHost.endsWith("." + linkHost);
  }

  // Intercept link clicks:
  // - If link is on DIFFERENT domain (ad overlay on video/page) -> BLOCK IT!
  // - If link is on SAME domain (legit page/episode link) -> ALLOW in current tab!
  function sanitizeLink(event) {
    try {
      const path = event.composedPath ? event.composedPath() : [];
      let link = null;
      for (let i = 0; i < path.length; i++) {
        const node = path[i];
        if (node instanceof HTMLAnchorElement || node instanceof HTMLAreaElement) {
          link = node;
          break;
        }
      }
      if (!link && event.target) {
        link = event.target.closest ? event.target.closest("a, area") : null;
      }
      if (link && link.href) {
        const linkHost = link.hostname;
        const pageHost = window.location.hostname;

        // Block external ad link overlays
        if (linkHost && !isSameDomain(linkHost, pageHost)) {
          console.info("[Video Popup Guard] Iklan overlay diblokir: " + link.href);
          event.preventDefault();
          event.stopImmediatePropagation();
          return;
        }

        // Open same-domain links safely in current tab
        const target = link.getAttribute("target");
        if (isNewContext(target)) {
          link.setAttribute("target", "_self");
        }
      }
    } catch (e) {}
  }

  window.addEventListener("click", sanitizeLink, true);
  window.addEventListener("auxclick", sanitizeLink, true);

  window.addEventListener("submit", event => {
    try {
      const form = event.target;
      if (form instanceof HTMLFormElement) {
        if (isNewContext(form.getAttribute("target"))) {
          form.setAttribute("target", "_self");
        }
      }
    } catch (e) {}
  }, true);
})();
