import { useEffect } from 'react';

export function useGlobalShortcuts({ searchInputRef, onEscape }) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      const activeEl = document.activeElement;
      const isInputActive =
        activeEl &&
        (activeEl.tagName === 'INPUT' ||
          activeEl.tagName === 'TEXTAREA' ||
          activeEl.isContentEditable);

      // '/' shortcut to focus search input
      if (e.key === '/' && !isInputActive) {
        e.preventDefault();
        if (searchInputRef?.current) {
          searchInputRef.current.focus();
          searchInputRef.current.select();
        }
      }

      // 'Escape' shortcut
      if (e.key === 'Escape') {
        if (onEscape) {
          onEscape();
        } else if (isInputActive && searchInputRef?.current) {
          searchInputRef.current.blur();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [searchInputRef, onEscape]);
}
