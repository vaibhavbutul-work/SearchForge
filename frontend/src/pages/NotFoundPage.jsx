import React from 'react';
import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <main style={{ padding: '4rem 2rem', textAlign: 'center', maxWidth: '500px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '3rem', fontWeight: 800, color: 'var(--color-accent)', marginBottom: '0.5rem' }}>404</h1>
      <h2 style={{ fontSize: 'var(--text-xl)', marginBottom: '1rem', color: 'var(--color-text-primary)' }}>Page Not Found</h2>
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: '2rem' }}>
        The page you are looking for does not exist or has been moved.
      </p>
      <Link
        to="/"
        style={{
          display: 'inline-block',
          padding: '0.75rem 1.5rem',
          backgroundColor: 'var(--color-accent)',
          color: '#FFFFFF',
          borderRadius: 'var(--radius-md)',
          fontWeight: 500,
        }}
      >
        Return to Search
      </Link>
    </main>
  );
}
