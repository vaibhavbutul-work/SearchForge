# Contributing to SearchForge

Thank you for your interest in contributing to **SearchForge**! We welcome contributions that uphold our core philosophy: **building search engine algorithms and data structures from first principles without relying on black-box external search libraries.**

---

## 1. Core Architectural Invariant

SearchForge implements all search subsystems from scratch. 
* **DO NOT** introduce external search engine dependencies such as Apache Lucene, Elasticsearch, Solr, or cloud search SDKs.
* All tokenizers, stemmers, inverted index structures, query parsers, and ranking functions must be implemented cleanly in pure Java 21.

---

## 2. Development Setup

### Prerequisites
* **Java 21 LTS** (Eclipse Temurin 21 recommended)
* **Maven:** Use the bundled wrapper (`./mvnw` or `.\mvnw.cmd`)
* **Docker & Docker Compose** (optional for PostgreSQL mode; H2 dev profile runs zero-Docker)

### Clone & Build
```bash
git clone https://github.com/vaibhavbutul-work/SearchForge.git
cd SearchForge/backend

# Verify all 185 tests pass
./mvnw test
```

---

## 3. Git & Branching Conventions

* **Branch Naming:**
  * `feature/<short-description>`: New search capabilities or algorithms.
  * `fix/<short-description>`: Bug fixes and performance patches.
  * `docs/<short-description>`: Documentation, guides, or visual updates.
* **Commit Messages:**
  Follow Conventional Commits:
  * `feat(ranking): implement BM25F field weighting`
  * `fix(crawler): handle malformed URL encoding in frontier`
  * `docs(readme): add visual architecture pipeline`

---

## 4. Testing Standards

* Every new feature or bug fix must include hermetic automated tests.
* Tests must run fast and offline against in-memory H2 database instances without external network dependencies.
* Run the entire suite before submitting a PR:
  ```bash
  cd backend && ./mvnw test
  ```
