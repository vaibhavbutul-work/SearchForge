import test from 'node:test';
import assert from 'node:assert/strict';
import { searchService } from '../src/services/search/searchService.js';
import apiClient from '../src/services/api/apiClient.js';

test('SearchService - search mode routing and API parameters', async (t) => {
  let capturedUrl = '';
  let capturedConfig = {};

  // Mock apiClient.get
  apiClient.get = async (url, config) => {
    capturedUrl = url;
    capturedConfig = config;
    return {
      query: config.params?.q || '',
      mode: config.params?.mode || 'hybrid',
      totalHits: 1,
      returnedHits: 1,
      executionTimeMs: 15,
      results: [
        {
          docId: 42,
          documentId: 42,
          url: 'https://example.com/java',
          title: 'Java Concurrency',
          domain: 'example.com',
          snippet: 'Java virtual threads...',
          highlightedSnippet: 'Java <mark>virtual</mark> threads...',
          score: 0.0325,
          rank: 1,
          finalRank: 1,
          bm25Score: 5.4,
          semanticScore: 0.88,
          bm25Rank: 1,
          semanticRank: 2,
          rrfScore: 0.0325,
          matchedTerms: ['java', 'virtual'],
        },
      ],
    };
  };

  await t.test('routes hybrid mode correctly to /api/v1/search?mode=hybrid', async () => {
    const res = await searchService.search('virtual threads', { mode: 'hybrid', limit: 10, explain: true });
    assert.equal(capturedUrl, '/api/v1/search');
    assert.equal(capturedConfig.params.q, 'virtual threads');
    assert.equal(capturedConfig.params.mode, 'hybrid');
    assert.equal(capturedConfig.params.limit, 10);
    assert.equal(capturedConfig.params.explain, true);

    assert.equal(res.mode, 'hybrid');
    assert.equal(res.results.length, 1);
    const hit = res.results[0];
    assert.equal(hit.docId, 42);
    assert.equal(hit.documentId, 42);
    assert.equal(hit.bm25Score, 5.4);
    assert.equal(hit.semanticScore, 0.88);
    assert.equal(hit.bm25Rank, 1);
    assert.equal(hit.semanticRank, 2);
    assert.equal(hit.rrfScore, 0.0325);
  });

  await t.test('routes semantic mode correctly to /api/v1/search?mode=semantic', async () => {
    await searchService.search('concurrency models', { mode: 'semantic', limit: 5 });
    assert.equal(capturedUrl, '/api/v1/search');
    assert.equal(capturedConfig.params.mode, 'semantic');
    assert.equal(capturedConfig.params.limit, 5);
  });

  await t.test('routes lexical and legacy ranked mode to /api/v1/search?mode=lexical', async () => {
    await searchService.search('java memory', { mode: 'lexical', limit: 8 });
    assert.equal(capturedUrl, '/api/v1/search');
    assert.equal(capturedConfig.params.mode, 'lexical');

    await searchService.search('java memory', { mode: 'ranked', limit: 8 });
    assert.equal(capturedUrl, '/api/v1/search');
    assert.equal(capturedConfig.params.mode, 'lexical');
  });

  await t.test('routes boolean mode to /api/v1/search/ranked-boolean', async () => {
    await searchService.search('java AND threads', { mode: 'boolean', limit: 10 });
    assert.equal(capturedUrl, '/api/v1/search/ranked-boolean');
    assert.equal(capturedConfig.params.q, 'java AND threads');
  });

  await t.test('routes filtered mode to /api/v1/search/filtered', async () => {
    await searchService.search('java', { mode: 'filtered', filter: 'NOT reactive', limit: 10 });
    assert.equal(capturedUrl, '/api/v1/search/filtered');
    assert.equal(capturedConfig.params.q, 'java');
    assert.equal(capturedConfig.params.filter, 'NOT reactive');
  });

  await t.test('normalizes fallback and degradation telemetry from backend', async () => {
    apiClient.get = async () => ({
      query: 'deep learning',
      mode: 'lexical',
      fallback: true,
      fallbackReason: 'Dense vector memory exhausted; degraded to lexical BM25',
      totalHits: 0,
      returnedHits: 0,
      executionTimeMs: 5,
      results: [],
    });

    const res = await searchService.search('deep learning', { mode: 'hybrid' });
    assert.equal(res.mode, 'lexical');
    assert.equal(res.fallback, true);
    assert.equal(res.fallbackReason, 'Dense vector memory exhausted; degraded to lexical BM25');
  });

  await t.test('handles empty and whitespace query safely', async () => {
    const res = await searchService.search('   ');
    assert.equal(res.totalHits, 0);
    assert.equal(res.results.length, 0);
  });
});
