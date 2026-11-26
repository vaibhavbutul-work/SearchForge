import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

const LIVE_BASE_URL = process.env.SEARCHFORGE_API_BASE_URL || 'http://localhost';
process.env.VITE_API_BASE_URL = LIVE_BASE_URL;

const { default: apiClient, ApiError } = await import('../../src/services/api/apiClient.js');
const { default: searchService } = await import('../../src/services/search/searchService.js');

async function isLiveBackendAvailable() {
  try {
    const res = await fetch(`${LIVE_BASE_URL}/api/v1/health`, { signal: AbortSignal.timeout(2500) });
    return res.ok;
  } catch {
    return false;
  }
}

test('Frontend-Backend Live HTTP Integration (Modes & Query Intelligence)', async (t) => {
  const live = await isLiveBackendAvailable();
  if (!live) {
    t.skip(`Live backend not reachable at ${LIVE_BASE_URL}`);
    return;
  }

  await t.test('executes hybrid, lexical, semantic, boolean, and filtered modes against live backend', async () => {
    for (const mode of ['hybrid', 'lexical', 'semantic']) {
      const res = await searchService.search('searchforge', { mode, limit: 5, explain: true });
      assert.equal(res.query, 'searchforge');
      assert.equal(res.mode, mode);
      assert.ok(typeof res.totalHits === 'number');
      assert.ok(typeof res.executionTimeMs === 'number');
      assert.ok(Array.isArray(res.results));
    }

    const boolRes = await searchService.search('java AND search', { mode: 'boolean', limit: 5 });
    assert.ok(Array.isArray(boolRes.results));

    const filtRes = await searchService.search('search', { mode: 'filtered', filter: 'java OR spring', limit: 5 });
    assert.ok(Array.isArray(filtRes.results));
  });

  await t.test('queries live autocomplete and spelling suggestion endpoints', async () => {
    const suggestions = await searchService.getAutocomplete('sea', 5);
    assert.ok(Array.isArray(suggestions));

    const spelling = await searchService.getSpellingSuggestion('serchforge');
    assert.ok(spelling === null || typeof spelling === 'string');
  });

  await t.test('live backend rejects invalid mode with HTTP 400 ApiError without stack trace', async () => {
    await assert.rejects(
      () => apiClient.get('/api/v1/search', { params: { q: 'java', mode: 'bogus_mode' } }),
      (err) => {
        assert.ok(err instanceof ApiError);
        assert.equal(err.status, 400);
        assert.ok(!JSON.stringify(err.details || {}).includes('java.lang.'));
        return true;
      }
    );
  });

  await t.test('live backend enforces 401 Unauthorized on protected admin route', async () => {
    await assert.rejects(
      () => apiClient.get('/api/v1/lifecycle/metrics'),
      (err) => {
        assert.ok(err instanceof ApiError);
        assert.equal(err.status, 401);
        return true;
      }
    );
  });
});

test('Frontend ApiClient Error Handling & Resilience (400/401/403/429/500/Network/Timeout)', async (t) => {
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/err/400')) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 400, message: 'Invalid query syntax' }));
    } else if (req.url.startsWith('/err/403')) {
      res.writeHead(403, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 403, message: 'Forbidden resource' }));
    } else if (req.url.startsWith('/err/429')) {
      res.writeHead(429, { 'Content-Type': 'application/json', 'Retry-After': '15' });
      res.end(JSON.stringify({ status: 429, message: 'Rate limit exceeded' }));
    } else if (req.url.startsWith('/err/500') || req.url.startsWith('/api/v1/search/autocomplete') || req.url.startsWith('/api/v1/search/suggest')) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 500, message: 'An internal server error occurred.' }));
    } else if (req.url.startsWith('/err/slow')) {
      setTimeout(() => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end('{}');
      }, 400);
    } else {
      res.writeHead(404);
      res.end();
    }
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const origBaseUrl = apiClient.baseUrl;
  apiClient.baseUrl = `http://127.0.0.1:${port}`;

  try {
    for (const code of [400, 403, 429, 500]) {
      await assert.rejects(
        () => apiClient.get(`/err/${code}`),
        (err) => err instanceof ApiError && err.status === code
      );
    }

    // Timeout handling (AbortController -> status 408, isTimeout: true)
    await assert.rejects(
      () => apiClient.get('/err/slow', { timeoutMs: 50 }),
      (err) => err instanceof ApiError && err.isTimeout === true && err.status === 408
    );

    // Query intelligence degrades silently on 500
    assert.deepEqual(await searchService.getAutocomplete('java'), []);
    assert.equal(await searchService.getSpellingSuggestion('jvaa'), null);

    // Network connection refused -> isNetworkError: true, status: 0
    apiClient.baseUrl = 'http://127.0.0.1:59998';
    await assert.rejects(
      () => apiClient.get('/api/v1/health'),
      (err) => err instanceof ApiError && err.isNetworkError === true && err.status === 0
    );
  } finally {
    apiClient.baseUrl = origBaseUrl;
    server.close();
  }
});
