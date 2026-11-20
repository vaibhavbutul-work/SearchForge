import test from 'node:test';
import assert from 'node:assert/strict';
import { SEARCH_MODES, resolveSearchMode } from '../src/components/search/searchModes.js';

test('SearchModeSelector - mode definitions and contracts', async (t) => {
  await t.test('defines all five core retrieval modes', () => {
    const ids = SEARCH_MODES.map((m) => m.id);
    assert.deepEqual(ids, ['hybrid', 'lexical', 'semantic', 'boolean', 'filtered']);
  });

  await t.test('each mode has non-empty label, badge, and descriptive explanation', () => {
    for (const mode of SEARCH_MODES) {
      assert.ok(mode.id, 'Mode must have an id');
      assert.ok(mode.label && mode.label.trim().length > 0, `Mode ${mode.id} must have a label`);
      assert.ok(mode.badge && mode.badge.trim().length > 0, `Mode ${mode.id} must have a badge`);
      assert.ok(mode.description && mode.description.trim().length > 0, `Mode ${mode.id} must have an explanatory description`);
    }
  });

  await t.test('hybrid mode description communicates RRF and multi-modal integration', () => {
    const hybrid = SEARCH_MODES.find((m) => m.id === 'hybrid');
    assert.ok(hybrid.description.includes('Reciprocal Rank Fusion') || hybrid.description.includes('RRF'));
    assert.ok(hybrid.description.includes('BM25') || hybrid.description.includes('lexical'));
    assert.ok(hybrid.description.includes('semantic') || hybrid.description.includes('vector'));
  });

  await t.test('semantic mode description communicates dense vectors and continuous similarity', () => {
    const semantic = SEARCH_MODES.find((m) => m.id === 'semantic');
    assert.ok(semantic.description.includes('embeddings') || semantic.description.includes('vector'));
    assert.ok(semantic.description.includes('cosine') || semantic.description.includes('conceptual'));
  });
});
