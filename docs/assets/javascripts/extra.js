/* Microservices Enterprise Handbook — small client-side enhancements.
 * Material re-renders pages on instant navigation, so everything hangs off document$.
 * Mermaid rendering is handled natively by Material (superfences custom fence). */
(function () {
  const WORDS_PER_MINUTE = 220;

  function addReadingTime() {
    const article = document.querySelector("article.md-content__inner");
    if (!article || article.querySelector(".mh-meta")) return;
    if (document.querySelector(".mh-hero")) return; // skip landing page
    const h1 = article.querySelector("h1");
    if (!h1) return;
    const prose = article.cloneNode(true);
    prose.querySelectorAll("pre, .mermaid, .md-source-file").forEach((n) => n.remove());
    const words = (prose.textContent || "").trim().split(/\s+/).length;
    const codeBlocks = article.querySelectorAll("pre").length;
    if (words < 200) return;
    const minutes = Math.max(1, Math.round(words / WORDS_PER_MINUTE + codeBlocks * 0.3));
    const meta = document.createElement("div");
    meta.className = "mh-meta";
    meta.innerHTML =
      `<span>⏱ ~${minutes} min read</span>` +
      `<span>📝 ${words.toLocaleString()} words</span>` +
      (codeBlocks ? `<span>💻 ${codeBlocks} code blocks</span>` : "");
    h1.insertAdjacentElement("afterend", meta);
  }

  function externalLinksInNewTab() {
    document.querySelectorAll('.md-content a[href^="http"]').forEach((a) => {
      if (a.host !== window.location.host) {
        a.setAttribute("target", "_blank");
        a.setAttribute("rel", "noopener");
      }
    });
  }

  if (typeof document$ !== "undefined") {
    document$.subscribe(() => {
      addReadingTime();
      externalLinksInNewTab();
    });
  } else {
    document.addEventListener("DOMContentLoaded", () => {
      addReadingTime();
      externalLinksInNewTab();
    });
  }
})();
