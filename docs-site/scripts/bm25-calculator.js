/**
 * SearchForge BM25 Interactive Simulator & Dynamic Canvas Graph
 */

class Bm25Calculator {
  constructor() {
    this.tf = 5;
    this.k1 = 1.2;
    this.b = 0.75;
    this.docLength = 985;
    this.avgDocLength = 985;
    this.idf = 1.8827; // Representative Robertson-Spärck Jones IDF for term 'index'

    this.initDOM();
    this.attachEvents();
    this.calculateAndRender();
  }

  initDOM() {
    this.tfSlider = document.getElementById("bm25TfInput");
    this.k1Slider = document.getElementById("bm25K1Input");
    this.bSlider = document.getElementById("bm25BInput");
    this.lenSlider = document.getElementById("bm25LenInput");

    this.tfVal = document.getElementById("bm25TfVal");
    this.k1Val = document.getElementById("bm25K1Val");
    this.bVal = document.getElementById("bm25BVal");
    this.lenVal = document.getElementById("bm25LenVal");

    this.scoreDisplay = document.getElementById("bm25CalculatedScore");
    this.linearScoreDisplay = document.getElementById("linearCalculatedScore");
    this.canvas = document.getElementById("bm25Canvas");
    if (this.canvas) {
      this.ctx = this.canvas.getContext("2d");
    }
  }

  attachEvents() {
    if (this.tfSlider) {
      this.tfSlider.addEventListener("input", (e) => {
        this.tf = parseFloat(e.target.value);
        if (this.tfVal) this.tfVal.textContent = this.tf;
        this.calculateAndRender();
      });
    }

    if (this.k1Slider) {
      this.k1Slider.addEventListener("input", (e) => {
        this.k1 = parseFloat(e.target.value);
        if (this.k1Val) this.k1Val.textContent = this.k1.toFixed(1);
        this.calculateAndRender();
      });
    }

    if (this.bSlider) {
      this.bSlider.addEventListener("input", (e) => {
        this.b = parseFloat(e.target.value);
        if (this.bVal) this.bVal.textContent = this.b.toFixed(2);
        this.calculateAndRender();
      });
    }

    if (this.lenSlider) {
      this.lenSlider.addEventListener("input", (e) => {
        this.docLength = parseFloat(e.target.value);
        if (this.lenVal) this.lenVal.textContent = this.docLength;
        this.calculateAndRender();
      });
    }

    window.addEventListener("resize", () => {
      this.drawGraph();
    });
  }

  calculateBm25(tf, k1, b, docLen, avgLen, idf) {
    const lenNorm = 1.0 - b + (b * (docLen / avgLen));
    const denom = tf + (k1 * lenNorm);
    const saturation = (denom > 0) ? (tf * (k1 + 1.0)) / denom : 0.0;
    return idf * saturation;
  }

  calculateAndRender() {
    const bm25Score = this.calculateBm25(this.tf, this.k1, this.b, this.docLength, this.avgDocLength, this.idf);
    const linearScore = this.idf * (this.tf * 0.4); // Linear reference curve

    if (this.scoreDisplay) {
      this.scoreDisplay.textContent = bm25Score.toFixed(4);
    }
    if (this.linearScoreDisplay) {
      this.linearScoreDisplay.textContent = linearScore.toFixed(4);
    }

    this.drawGraph();
  }

  drawGraph() {
    if (!this.canvas || !this.ctx) return;

    const width = this.canvas.parentElement.clientWidth || 500;
    const height = 280;
    this.canvas.width = width * window.devicePixelRatio;
    this.canvas.height = height * window.devicePixelRatio;
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;

    const ctx = this.ctx;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    ctx.clearRect(0, 0, width, height);

    const padLeft = 45;
    const padBottom = 35;
    const padTop = 20;
    const padRight = 30;

    const graphWidth = width - padLeft - padRight;
    const graphHeight = height - padTop - padBottom;

    // Grid styling
    ctx.strokeStyle = "#1e293b";
    ctx.lineWidth = 1;

    // X Axis and Y Axis
    ctx.beginPath();
    ctx.moveTo(padLeft, padTop + graphHeight);
    ctx.lineTo(padLeft + graphWidth, padTop + graphHeight);
    ctx.moveTo(padLeft, padTop);
    ctx.lineTo(padLeft, padTop + graphHeight);
    ctx.stroke();

    // Horizontal Guidelines
    ctx.font = "10px monospace";
    ctx.fillStyle = "#64748b";
    for (let s = 1; s <= 4; s++) {
      const y = padTop + graphHeight - (s / 5.0) * graphHeight;
      ctx.beginPath();
      ctx.setLineDash([4, 4]);
      ctx.moveTo(padLeft, y);
      ctx.lineTo(padLeft + graphWidth, y);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillText(s.toFixed(1), padLeft - 28, y + 3);
    }
    ctx.fillText("0.0", padLeft - 24, padTop + graphHeight + 3);

    // Max TF plotted is 40
    const maxPlotTf = 40;

    // 1. Draw Linear TF Curve (Red)
    ctx.strokeStyle = "#ef4444";
    ctx.lineWidth = 1.5;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    for (let t = 0; t <= maxPlotTf; t += 0.5) {
      const x = padLeft + (t / maxPlotTf) * graphWidth;
      const score = this.idf * (t * 0.35);
      const y = padTop + graphHeight - (score / 5.0) * graphHeight;
      if (t === 0) ctx.moveTo(x, Math.max(padTop, y));
      else ctx.lineTo(x, Math.max(padTop, y));
    }
    ctx.stroke();
    ctx.setLineDash([]);

    // 2. Draw BM25 Saturation Curve (Cyan)
    ctx.strokeStyle = "#00e5ff";
    ctx.lineWidth = 3;
    ctx.beginPath();
    for (let t = 0; t <= maxPlotTf; t += 0.5) {
      const x = padLeft + (t / maxPlotTf) * graphWidth;
      const score = this.calculateBm25(t, this.k1, this.b, this.docLength, this.avgDocLength, this.idf);
      const y = padTop + graphHeight - (score / 5.0) * graphHeight;
      if (t === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // 3. Draw Asymptote line (Yellow)
    const maxAsymptote = this.idf * (this.k1 + 1.0);
    const asympY = padTop + graphHeight - (maxAsymptote / 5.0) * graphHeight;
    ctx.strokeStyle = "#f59e0b";
    ctx.lineWidth = 1;
    ctx.setLineDash([6, 4]);
    ctx.beginPath();
    ctx.moveTo(padLeft, asympY);
    ctx.lineTo(padLeft + graphWidth, asympY);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = "#f59e0b";
    ctx.fillText(`BM25 Cap: ${maxAsymptote.toFixed(2)}`, padLeft + graphWidth - 110, asympY - 6);

    // 4. Highlight Current TF point
    const currX = padLeft + (Math.min(this.tf, maxPlotTf) / maxPlotTf) * graphWidth;
    const currScore = this.calculateBm25(this.tf, this.k1, this.b, this.docLength, this.avgDocLength, this.idf);
    const currY = padTop + graphHeight - (currScore / 5.0) * graphHeight;

    ctx.fillStyle = "#00e5ff";
    ctx.beginPath();
    ctx.arc(currX, currY, 5, 0, Math.PI * 2);
    ctx.fill();

    // X Axis Label
    ctx.fillStyle = "#94a3b8";
    ctx.font = "11px sans-serif";
    ctx.fillText(`Current TF = ${this.tf}`, currX - 30, padTop + graphHeight + 18);
  }
}

window.Bm25Calculator = Bm25Calculator;
