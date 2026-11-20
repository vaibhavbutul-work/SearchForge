import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import SearchBar from './SearchBar';
import AboutModal from '../layout/AboutModal';
import styles from './SearchHeader.module.css';

export default function SearchHeader({ query, onSearch }) {
  const [searchValue, setSearchValue] = useState(query || '');
  const [isAboutOpen, setIsAboutOpen] = useState(false);

  // Synchronize internal state when query prop changes (e.g. from URL changes)
  React.useEffect(() => {
    setSearchValue(query || '');
  }, [query]);

  const handleSearchSubmit = (val) => {
    if (onSearch) {
      onSearch(val);
    }
  };

  return (
    <>
      <header className={styles.header} role="banner">
        <div className={styles.headerContent}>
          <Link to="/" className={styles.brand} aria-label="SearchForge Home">
            <span>Search</span><span className={styles.brandForge}>Forge</span>
          </Link>

          <div className={styles.searchContainer}>
            <SearchBar
              value={searchValue}
              onChange={setSearchValue}
              onSearch={handleSearchSubmit}
              size="compact"
              showActions={false}
              autoFocus={false}
              placeholder="Search..."
            />
          </div>

          <nav className={styles.navLinks} aria-label="Quick Links">
            <a
              href="https://vaibhavbutul-work.github.io/SearchForge/"
              target="_blank"
              rel="noopener noreferrer"
              className={styles.navLink}
              title="Documentation Portal"
            >
              Docs &#8599;
            </a>
            <button
              type="button"
              className={styles.navLink}
              onClick={() => setIsAboutOpen(true)}
            >
              About
            </button>
          </nav>
        </div>
      </header>

      <AboutModal isOpen={isAboutOpen} onClose={() => setIsAboutOpen(false)} />
    </>
  );
}
