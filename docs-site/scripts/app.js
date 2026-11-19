/**
 * SearchForge Portal Application Coordinator
 * Initializes all interactive visualizers and navigation listeners.
 */

document.addEventListener("DOMContentLoaded", () => {
  // 1. Initialize Pipeline Animator
  if (typeof PipelineAnimator !== "undefined") {
    window.pipelineAnimatorInstance = new PipelineAnimator();
  }

  // 2. Initialize BM25 Calculator & Dynamic Canvas Graph
  if (typeof Bm25Calculator !== "undefined") {
    window.bm25CalculatorInstance = new Bm25Calculator();
  }

  // 3. Initialize Inverted Index Explorer
  if (typeof InvertedIndexExplorer !== "undefined") {
    window.invertedIndexInstance = new InvertedIndexExplorer();
  }

  // 4. Initialize Crawler Explorer
  if (typeof CrawlerExplorer !== "undefined") {
    window.crawlerExplorerInstance = new CrawlerExplorer();
  }

  // 5. Initialize Top-K Min-Heap Explorer
  if (typeof TopkExplorer !== "undefined") {
    window.topkExplorerInstance = new TopkExplorer();
  }

  // 6. Initialize Architecture Explorer
  if (typeof ArchitectureExplorer !== "undefined") {
    window.architectureExplorerInstance = new ArchitectureExplorer();
  }

  // 7. Navigation Scrollspy
  const navLinks = document.querySelectorAll(".nav-link");
  const sections = document.querySelectorAll("section[id]");

  window.addEventListener("scroll", () => {
    let current = "";
    sections.forEach(section => {
      const sectionTop = section.offsetTop - 100;
      if (window.scrollY >= sectionTop) {
        current = section.getAttribute("id");
      }
    });

    navLinks.forEach(link => {
      link.classList.remove("active");
      if (link.getAttribute("href") === `#${current}`) {
        link.classList.add("active");
      }
    });
  });
});
