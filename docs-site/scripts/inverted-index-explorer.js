/**
 * SearchForge Inverted Index Explorer
 * Displays real vocabulary terms and interactive postings lists.
 */

const SAMPLE_TERMS = {
  "virtual": {
    idf: 1.1206,
    docCount: 10,
    postings: [
      { docId: 7, title: "Virtual Threads", domain: "docs.oracle.com", tf: 120, positions: [0, 15, 34, 52, 78, 114, 156, "..."] },
      { docId: 12, title: "Thread (Java SE 21)", domain: "docs.oracle.com", tf: 58, positions: [14, 89, 142, 210, 312, "..."] },
      { docId: 6, title: "Java 21 LTS Overview", domain: "oracle.com", tf: 3, positions: [5, 18, 42] },
      { docId: 14, title: "Executors (Java SE 21)", domain: "docs.oracle.com", tf: 4, positions: [12, 45, 98, 145] }
    ]
  },
  "thread": {
    idf: 0.6098,
    docCount: 12,
    postings: [
      { docId: 12, title: "Thread (Java SE 21)", domain: "docs.oracle.com", tf: 680, positions: [0, 2, 8, 15, 23, 31, "..."] },
      { docId: 7, title: "Virtual Threads", domain: "docs.oracle.com", tf: 273, positions: [1, 16, 35, 53, 79, "..."] },
      { docId: 14, title: "Executors (Java SE 21)", domain: "docs.oracle.com", tf: 130, positions: [4, 9, 21, 37, "..."] },
      { docId: 13, title: "Thread.Builder", domain: "docs.oracle.com", tf: 82, positions: [1, 5, 12, 28, "..."] }
    ]
  },
  "index": {
    idf: 1.8827,
    docCount: 4,
    postings: [
      { docId: 9, title: "PostgreSQL: Chapter 11. Indexes", domain: "www.postgresql.org", tf: 18, positions: [3, 12, 24, 45, 68, 89, "..."] },
      { docId: 22, title: "SimpleDateFormat", domain: "docs.oracle.com", tf: 12, positions: [45, 112, 178, 230, "..."] },
      { docId: 16, title: "The jcmd Command", domain: "docs.oracle.com", tf: 1, positions: [84] }
    ]
  },
  "postgresql": {
    idf: 2.2192,
    docCount: 2,
    postings: [
      { docId: 9, title: "PostgreSQL: Chapter 11. Indexes", domain: "www.postgresql.org", tf: 3, positions: [0, 8, 15] },
      { docId: 3, title: "PostgreSQL The World's Most Advanced RDBMS", domain: "postgresql.org", tf: 3, positions: [0, 14, 28] }
    ]
  },
  "microservic": {
    idf: 1.8827,
    docCount: 4,
    postings: [
      { docId: 8, title: "Microservices", domain: "spring.io", tf: 18, positions: [0, 6, 14, 28, 42, 60, "..."] },
      { docId: 5, title: "Microservices Guide Architecture", domain: "martinfowler.com", tf: 2, positions: [1, 24] },
      { docId: 1, title: "Spring Boot Microservices", domain: "spring.io", tf: 2, positions: [2, 38] }
    ]
  },
  "inject": {
    idf: 2.7300,
    docCount: 1,
    postings: [
      { docId: 2, title: "Spring Framework Core and Dependency Injection", domain: "spring.io", tf: 1, positions: [6] }
    ]
  },
  "semaphore": {
    idf: 2.2192,
    docCount: 4,
    postings: [
      { docId: 18, title: "Semaphore (Java SE 21 & JDK 21)", domain: "docs.oracle.com", tf: 110, positions: [0, 4, 12, 25, 48, "..."] },
      { docId: 20, title: "Virtual Thread Scheduling & Pinning", domain: "docs.oracle.com", tf: 2, positions: [14, 38] },
      { docId: 7, title: "Virtual Threads", domain: "docs.oracle.com", tf: 1, positions: [142] }
    ]
  }
};

class InvertedIndexExplorer {
  constructor() {
    this.selectedTerm = "virtual";
    this.initDOM();
    this.renderTermPills();
    this.renderPostings();
  }

  initDOM() {
    this.pillsContainer = document.getElementById("termsPillCloud");
    this.activeTermTitle = document.getElementById("indexActiveTerm");
    this.activeTermIdf = document.getElementById("indexActiveIdf");
    this.activeTermDf = document.getElementById("indexActiveDf");
    this.postingsTableBody = document.getElementById("indexPostingsTableBody");
  }

  renderTermPills() {
    if (!this.pillsContainer) return;
    this.pillsContainer.innerHTML = "";

    Object.keys(SAMPLE_TERMS).forEach(term => {
      const btn = document.createElement("button");
      btn.className = `term-pill ${term === this.selectedTerm ? "active" : ""}`;
      btn.textContent = term;
      btn.addEventListener("click", () => {
        this.selectedTerm = term;
        this.renderTermPills();
        this.renderPostings();
      });
      this.pillsContainer.appendChild(btn);
    });
  }

  renderPostings() {
    const data = SAMPLE_TERMS[this.selectedTerm];
    if (!data) return;

    if (this.activeTermTitle) this.activeTermTitle.textContent = `Term: "${this.selectedTerm}"`;
    if (this.activeTermIdf) this.activeTermIdf.textContent = `RSJ IDF: ${data.idf.toFixed(4)}`;
    if (this.activeTermDf) this.activeTermDf.textContent = `Document Frequency (df): ${data.docCount}`;

    if (!this.postingsTableBody) return;
    this.postingsTableBody.innerHTML = "";

    data.postings.forEach(p => {
      const tr = document.createElement("tr");

      const posBadges = p.positions.map(pos => `<span class="position-badge">${pos}</span>`).join(" ");

      tr.innerHTML = `
        <td style="font-family: monospace; font-weight: 700; color: #00e5ff;">Doc #${p.docId}</td>
        <td>
          <strong style="color: #f8fafc;">${p.title}</strong>
          <br><small style="color: #64748b;">${p.domain}</small>
        </td>
        <td style="font-family: monospace; font-weight: 600; color: #10b981;">${p.tf}</td>
        <td>${posBadges}</td>
      `;
      this.postingsTableBody.appendChild(tr);
    });
  }
}

window.InvertedIndexExplorer = InvertedIndexExplorer;
