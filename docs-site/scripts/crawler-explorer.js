/**
 * SearchForge Crawler Explorer
 * Interactive visualization of the Breadth-First Search (BFS) crawler loop.
 */

const CRAWLER_STEPS = [
  {
    step: 1,
    action: "Frontier Dequeue",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 3,
    status: "Dequeued task from lock-free ConcurrentLinkedQueue in BFS order."
  },
  {
    step: 2,
    action: "Robots.txt Check",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 2,
    status: "Evaluated against cached RFC 9309 rules: '/docs/current/' is PERMITTED (200 OK)."
  },
  {
    step: 3,
    action: "Politeness Throttling",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 2,
    status: "Checked domain lock for 'www.postgresql.org': minimum 1000ms delay satisfied."
  },
  {
    step: 4,
    action: "HTTP Fetch (Jsoup)",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 2,
    status: "Downloaded HTML payload (42 KB, status 200 OK). Content-type verified text/html."
  },
  {
    step: 5,
    action: "DOM Noise Stripping",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 2,
    status: "Removed <script>, <style>, <nav>, <footer> boilerplate. Extracted clean text body."
  },
  {
    step: 6,
    action: "SHA-256 Deduplication",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 2,
    status: "Fingerprint 'b84a9f...': unique! Persisted to database as CrawledDocument #9."
  },
  {
    step: 7,
    action: "Outbound Links Enqueued",
    url: "https://www.postgresql.org/docs/current/indexes.html",
    depth: 0,
    queueSize: 18,
    status: "Discovered 16 outbound links (e.g. indexes-types.html). Enqueued at Depth=1."
  }
];

class CrawlerExplorer {
  constructor() {
    this.currentStep = 0;
    this.initDOM();
    this.attachEvents();
    this.render();
  }

  initDOM() {
    this.stepTitle = document.getElementById("crawlerStepTitle");
    this.stepAction = document.getElementById("crawlerStepAction");
    this.stepUrl = document.getElementById("crawlerStepUrl");
    this.stepDepth = document.getElementById("crawlerStepDepth");
    this.stepQueue = document.getElementById("crawlerStepQueue");
    this.stepStatus = document.getElementById("crawlerStepStatus");

    this.nextBtn = document.getElementById("crawlerNextBtn");
    this.prevBtn = document.getElementById("crawlerPrevBtn");
    this.resetBtn = document.getElementById("crawlerResetBtn");
  }

  attachEvents() {
    if (this.nextBtn) {
      this.nextBtn.addEventListener("click", () => {
        if (this.currentStep < CRAWLER_STEPS.length - 1) {
          this.currentStep++;
        } else {
          this.currentStep = 0;
        }
        this.render();
      });
    }

    if (this.prevBtn) {
      this.prevBtn.addEventListener("click", () => {
        if (this.currentStep > 0) {
          this.currentStep--;
        } else {
          this.currentStep = CRAWLER_STEPS.length - 1;
        }
        this.render();
      });
    }

    if (this.resetBtn) {
      this.resetBtn.addEventListener("click", () => {
        this.currentStep = 0;
        this.render();
      });
    }
  }

  render() {
    const data = CRAWLER_STEPS[this.currentStep];
    if (!data) return;

    if (this.stepTitle) this.stepTitle.textContent = `Crawl Lifecycle Step ${data.step} of ${CRAWLER_STEPS.length}`;
    if (this.stepAction) this.stepAction.textContent = data.action;
    if (this.stepUrl) this.stepUrl.textContent = data.url;
    if (this.stepDepth) this.stepDepth.textContent = `Depth: ${data.depth}`;
    if (this.stepQueue) this.stepQueue.textContent = `Pending Queue: ${data.queueSize} URLs`;
    if (this.stepStatus) this.stepStatus.textContent = data.status;
  }
}

window.CrawlerExplorer = CrawlerExplorer;
