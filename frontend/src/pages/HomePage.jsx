import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/layout/Navbar';
import SearchBar from '../components/search/SearchBar';
import ExampleQueries from '../components/search/ExampleQueries';
import styles from './HomePage.module.css';

const EXAMPLE_QUERIES = [
  'Java virtual threads',
  'history of books',
  'domestic dog',
  'ergonomic chair',
  'galaxy and universe',
  'Spring Boot',
];

export default function HomePage() {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

  const handleSearch = (searchQuery) => {
    const targetQuery = (searchQuery !== undefined ? searchQuery : query).trim();
    if (targetQuery) {
      navigate(`/search?q=${encodeURIComponent(targetQuery)}`);
    }
  };

  const handleFeelingCurious = () => {
    const randomIndex = Math.floor(Math.random() * EXAMPLE_QUERIES.length);
    const randomQuery = EXAMPLE_QUERIES[randomIndex];
    setQuery(randomQuery);
    navigate(`/search?q=${encodeURIComponent(randomQuery)}`);
  };

  return (
    <div className={styles.homeContainer}>
      <Navbar showBrand={false} />

      <main className={styles.centerContent}>
        <div className={styles.brandGroup}>
          <h1 className={styles.logoTitle}>
            <span>Search</span>
            <span className={styles.logoAccent}>Forge</span>
          </h1>
          <p className={styles.tagline}>Search. Understand. Discover.</p>
        </div>

        <section className={styles.searchSection} aria-label="Search">
          <SearchBar
            value={query}
            onChange={setQuery}
            onSearch={handleSearch}
            showActions={true}
            onFeelingCurious={handleFeelingCurious}
            size="large"
          />

          <ExampleQueries
            examples={EXAMPLE_QUERIES}
            onSelectQuery={handleSearch}
          />
        </section>
      </main>
    </div>
  );
}
