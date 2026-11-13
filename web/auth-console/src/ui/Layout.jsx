import React, { useState, useEffect } from 'react';
import { NavLink, Link, Outlet } from 'react-router';
import { useSession } from '../session/SessionProvider.jsx';
import { StatusBadge } from './StatusBadge.jsx';

/**
 * Main application layout frame with navigation, branding, user badge, and theme toggle.
 * @param {object} props
 * @param {React.ReactNode} [props.children]
 */
export function Layout({ children }) {
  const { me, logout } = useSession();
  const isAdmin = me?.role === 'PLATFORM_ADMIN';

  const adminHome = import.meta.env.VITE_ADMIN_HOME || 'http://localhost:5182/admin/';
  const portalHome = import.meta.env.VITE_PORTAL_HOME || 'http://localhost:5181/portal/';

  // Theme toggle: 'auto' | 'light' | 'dark'
  const [theme, setTheme] = useState(() => {
    return localStorage.getItem('hor-theme') || 'auto';
  });

  useEffect(() => {
    if (theme === 'auto') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', theme);
    }
    localStorage.setItem('hor-theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    if (theme === 'auto') {
      const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      setTheme(isDark ? 'light' : 'dark');
    } else if (theme === 'dark') {
      setTheme('light');
    } else {
      setTheme('dark');
    }
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="header-inner">
          <div className="brand-section">
            <Link to={isAdmin ? '/admins' : '/account'} className="brand-section" style={{ textDecoration: 'none' }}>
              <div className="brand-logo" aria-hidden="true">H</div>
              <span className="brand-title">HOR Auth</span>
            </Link>
            <span className="brand-badge">Console</span>
          </div>

          <nav aria-label="Main Navigation">
            <ul className="nav-links">
              {isAdmin && (
                <>
                  <li>
                    <NavLink
                      to="/admins"
                      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                    >
                      Admins
                    </NavLink>
                  </li>
                  <li>
                    <NavLink
                      to="/users"
                      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                    >
                      Users
                    </NavLink>
                  </li>
                  <li>
                    <NavLink
                      to="/stores"
                      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                    >
                      Stores
                    </NavLink>
                  </li>
                  <li>
                    <NavLink
                      to="/audit"
                      className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                    >
                      Audit Log
                    </NavLink>
                  </li>
                  <li>
                    <a
                      href={adminHome}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="nav-link"
                      title="Open HOR Store Operations Admin Console"
                    >
                      Admin Console
                      <span className="external-link-icon" aria-hidden="true">↗</span>
                    </a>
                  </li>
                  <li>
                    <a
                      href={portalHome}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="nav-link"
                      title="Open HOR Retail Analytics Client Portal"
                    >
                      Client Portal
                      <span className="external-link-icon" aria-hidden="true">↗</span>
                    </a>
                  </li>
                </>
              )}
            </ul>
          </nav>

          <div className="header-actions">
            <button
              type="button"
              onClick={toggleTheme}
              className="btn btn-secondary btn-sm"
              title="Toggle color theme"
              aria-label="Toggle color theme"
            >
              {theme === 'dark' ? '☀️' : theme === 'light' ? '🌙' : '🌓'}
            </button>

            {me && (
              <div className="user-profile-badge">
                <Link to="/account" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem', textDecoration: 'none' }}>
                  <span className="user-name">{me.fullName}</span>
                  <StatusBadge role={me.role} />
                </Link>
              </div>
            )}

            <button
              type="button"
              onClick={logout}
              className="btn btn-secondary btn-sm"
              aria-label="Log out"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="page-container">
        {children || <Outlet />}
      </main>
    </div>
  );
}
