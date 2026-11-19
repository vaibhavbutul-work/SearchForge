# SearchForge Web Crawler API Reference

Base URL: `http://localhost:8080/api/v1/crawler`

---

## 1. Start Asynchronous Crawl

Launches an autonomous crawl supervisor in a Java 21 Virtual Thread.

* **Method:** `POST`
* **Path:** `/api/v1/crawler/start`
* **Request Body (JSON, Optional):**
```json
{
  "seedUrls": [
    "https://www.postgresql.org/docs/current/indexes.html",
    "https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html",
    "https://spring.io/microservices"
  ]
}
```
* **Status Code:** `202 Accepted` on launch; `409 Conflict` if already running.

---

## 2. Stop Crawl

Signals all active virtual workers to gracefully terminate crawling after finishing current tasks.

* **Method:** `POST`
* **Path:** `/api/v1/crawler/stop`

---

## 3. Reset Crawler State

Clears the URL frontier queue, visited URL filter, in-memory robots.txt cache, and politeness locks.

* **Method:** `POST`
* **Path:** `/api/v1/crawler/reset`

---

## 4. Get Crawler Status & Telemetry

Returns real-time progress, pages crawled, failures, and frontier queue length.

* **Method:** `GET`
* **Path:** `/api/v1/crawler/status`

### Example Response (`200 OK`)
```json
{
  "state": "COMPLETED",
  "isRunning": false,
  "pagesCrawled": 16,
  "pagesFailed": 0,
  "frontierQueueSize": 91,
  "startedAt": "2026-11-19T11:20:04.192Z",
  "elapsedSeconds": 19
}
```

---

## 5. List Crawled Documents

Returns a paginated list of documents persisted in the database.

* **Method:** `GET`
* **Path:** `/api/v1/crawler/documents?page=0&size=20`
