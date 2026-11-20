import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import AboutModal from './AboutModal';
import styles from './Navbar.module.css';

export default function Navbar({ showBrand = true }) {
  const [isAboutOpen, setIsAboutOpen] = useState(false);

  return (
    <>
      <nav className={styles.navbar} aria-label="Main Navigation">
        <div>
          {showBrand && (
            <Link to="/" className={styles.brand} aria-label="SearchForge Home">
              <span>Search</span><span className={styles.brandForge}>Forge</span>
            </Link>
          )}
        </div>

        <div className={styles.navLinks}>
          <a
            href="https://vaibhavbutul-work.github.io/SearchForge/"
            target="_blank"
            rel="noopener noreferrer"
            className={styles.navLink}
            title="Open Interactive Architecture & Documentation Portal"
          >
            Documentation <span className={styles.externalIcon}>&#8599;</span>
          </a>
          <button
            type="button"
            className={styles.navLink}
            onClick={() => setIsAboutOpen(true)}
          >
            About
          </button>
        </div>
      </nav>

      <AboutModal isOpen={isAboutOpen} onClose={() => setIsAboutOpen(false)} />
    </>
  );
}
