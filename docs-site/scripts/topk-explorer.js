/**
 * SearchForge Top-K Min-Heap Explorer
 * Visualizes how bounded priority queues select top-K documents in O(C log K).
 */

const SAMPLE_CANDIDATES = [
  { docId: 16, title: "The jcmd Command", score: 1.0375, note: "Inserted into heap (Heap size = 1)" },
  { docId: 22, title: "SimpleDateFormat", score: 3.4641, note: "Inserted into heap (Heap size = 2)" },
  { docId: 3,  title: "About PostgreSQL", score: 4.3790, note: "Inserted into heap (Heap size = 3, Root = Doc 16 @ 1.0375)" },
  { docId: 18, title: "Semaphore", score: 0.8520, note: "Rejected: Score (0.85) < Heap Root (1.0375). Heap unaffected." },
  { docId: 9,  title: "PostgreSQL Chapter 11. Indexes", score: 8.2070, note: "Evicts Root Doc 16! New Root is Doc 22 @ 3.4641." }
];

class TopkExplorer {
  constructor() {
    this.step = 0;
    this.initDOM();
    this.attachEvents();
    this.render();
  }

  initDOM() {
    this.candidateList = document.getElementById("topkCandidateList");
    this.heapContainer = document.getElementById("topkHeapContainer");
    this.explainText = document.getElementById("topkExplainText");

    this.nextBtn = document.getElementById("topkNextBtn");
    this.resetBtn = document.getElementById("topkResetBtn");
  }

  attachEvents() {
    if (this.nextBtn) {
      this.nextBtn.addEventListener("click", () => {
        if (this.step < SAMPLE_CANDIDATES.length) {
          this.step++;
        } else {
          this.step = 0;
        }
        this.render();
      });
    }

    if (this.resetBtn) {
      this.resetBtn.addEventListener("click", () => {
        this.step = 0;
        this.render();
      });
    }
  }

  render() {
    if (!this.candidateList || !this.heapContainer || !this.explainText) return;

    this.candidateList.innerHTML = "";
    this.heapContainer.innerHTML = "";

    // Show candidate stream
    SAMPLE_CANDIDATES.forEach((cand, idx) => {
      const isCurrent = idx === (this.step - 1);
      const isProcessed = idx < this.step;

      const item = document.createElement("div");
      item.style.padding = "10px 14px";
      item.style.borderRadius = "6px";
      item.style.marginBottom = "8px";
      item.style.border = isCurrent ? "1px solid #00e5ff" : "1px solid #1e293b";
      item.style.backgroundColor = isCurrent ? "rgba(0, 229, 255, 0.08)" : (isProcessed ? "#111827" : "#0f172a");
      item.style.display = "flex";
      item.style.justifyContent = "space-between";
      item.style.alignItems = "center";

      item.innerHTML = `
        <div>
          <span style="font-family: monospace; font-weight: 700; color: #38bdf8;">#${cand.docId}</span>
          <strong style="margin-left: 8px; color: #f8fafc;">${cand.title}</strong>
        </div>
        <span style="font-family: monospace; font-weight: 700; color: ${cand.score > 4 ? '#10b981' : '#f59e0b'};">BM25: ${cand.score.toFixed(4)}</span>
      `;
      this.candidateList.appendChild(item);
    });

    // Heap State computation
    const activeHeap = [];
    for (let i = 0; i < this.step; i++) {
      const c = SAMPLE_CANDIDATES[i];
      if (activeHeap.length < 3) {
        activeHeap.push(c);
        activeHeap.sort((a, b) => a.score - b.score); // Min heap: index 0 is smallest
      } else {
        if (c.score > activeHeap[0].score) {
          activeHeap[0] = c;
          activeHeap.sort((a, b) => a.score - b.score);
        }
      }
    }

    if (activeHeap.length === 0) {
      this.heapContainer.innerHTML = `<p style="color: #64748b; font-style: italic;">Heap is empty. Click 'Process Next Candidate' to simulate.</p>`;
      this.explainText.textContent = "Bounded Min-Heap of size K=3 maintains the top 3 highest scores seen so far.";
    } else {
      activeHeap.forEach((doc, rank) => {
        const card = document.createElement("div");
        card.style.padding = "12px 16px";
        card.style.borderRadius = "6px";
        card.style.marginBottom = "8px";
        card.style.border = rank === 0 ? "1px solid #f59e0b" : "1px solid #10b981";
        card.style.backgroundColor = "#161f30";
        card.style.display = "flex";
        card.style.justifyContent = "space-between";

        card.innerHTML = `
          <div>
            <span style="color: ${rank === 0 ? '#f59e0b' : '#10b981'}; font-weight: 700;">
              ${rank === 0 ? 'HEAP ROOT (Min: Threshold to beat)' : `RANKED ITEM`}
            </span>
            <div style="font-size: 13px; color: #f8fafc; margin-top: 4px;">Doc #${doc.docId}: ${doc.title}</div>
          </div>
          <div style="font-family: monospace; font-size: 16px; font-weight: 800; color: #00e5ff;">
            ${doc.score.toFixed(4)}
          </div>
        `;
        this.heapContainer.appendChild(card);
      });

      const currentCand = SAMPLE_CANDIDATES[this.step - 1];
      this.explainText.textContent = currentCand ? currentCand.note : "All candidates processed! Polling the heap yields the exact top 3 sorted results.";
    }
  }
}

window.TopkExplorer = TopkExplorer;
