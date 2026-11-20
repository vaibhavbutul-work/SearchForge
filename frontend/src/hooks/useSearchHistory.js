import { useState, useEffect, useCallback } from 'react';

const STORAGE_KEY = 'searchforge_search_history';
const MAX_HISTORY_ITEMS = 8;

export function useSearchHistory() {
  const [history, setHistory] = useState(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  });

  // Sync to localStorage whenever history changes
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    } catch {
      // Ignore quota or private browsing errors
    }
  }, [history]);

  const addSearch = useCallback((query) => {
    if (!query || !query.trim()) return;
    const cleanQuery = query.trim();

    setHistory((prevHistory) => {
      // Deduplicate: remove existing occurrence and place at front
      const filtered = prevHistory.filter(
        (item) => item.toLowerCase() !== cleanQuery.toLowerCase()
      );
      return [cleanQuery, ...filtered].slice(0, MAX_HISTORY_ITEMS);
    });
  }, []);

  const removeSearch = useCallback((queryToRemove) => {
    setHistory((prevHistory) =>
      prevHistory.filter((item) => item !== queryToRemove)
    );
  }, []);

  const clearHistory = useCallback(() => {
    setHistory([]);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Ignore
    }
  }, []);

  return {
    history,
    addSearch,
    removeSearch,
    clearHistory,
  };
}
