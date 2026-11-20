import React from 'react';
import { Outlet } from 'react-router-dom';

export default function App() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <a href="#main-content" className="skipLink">
        Skip to main content
      </a>

      <div id="main-content" style={{ flex: 1 }}>
        <Outlet />
      </div>

      <footer
        style={{
          padding: '1.25rem var(--space-6)',
          borderTop: '1px solid var(--color-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: 'var(--text-xs)',
          color: 'var(--color-text-secondary)',
          backgroundColor: 'var(--color-surface)',
          flexWrap: 'wrap',
          gap: 'var(--space-2)',
        }}
        role="contentinfo"
      >
        <div>
          <span>SearchForge v1.1.0</span> &bull; <span>First-Principles Search Engine in Java 21 & Spring Boot</span>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
          <a
            href="https://vaibhavbutul-work.github.io/SearchForge/"
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            Architecture Portal
          </a>
          <a
            href="https://github.com/vaibhavbutul-work/SearchForge"
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            GitHub
          </a>
        </div>
      </footer>
    </div>
  );
}
