import React from 'react';
import { Navigate, useLocation, Link, Outlet } from 'react-router';
import { useSession } from './SessionProvider.jsx';

/**
 * Route guard requiring any authenticated user.
 * @param {object} props
 * @param {React.ReactNode} [props.children]
 */
export function RequireAuth({ children }) {
  const { me, ready } = useSession();
  const location = useLocation();

  if (!ready) {
    return (
      <div className="loading-container">
        <span className="spinner" aria-hidden="true" />
        <span>Loading session...</span>
      </div>
    );
  }

  if (!me) {
    const currentPath = location.pathname + location.search;
    if (currentPath.includes('/login') || currentPath === '/') {
      return <Navigate to="/login" replace />;
    }
    const returnUrl = encodeURIComponent(currentPath);
    return <Navigate to={`/login?return=${returnUrl}`} replace />;
  }

  return children || <Outlet />;
}

/**
 * Route guard requiring PLATFORM_ADMIN role.
 * If user is logged in as a store user (STAFF/CLIENT), shows "You do not have access"
 * with links to the client portal and my account (AUTH_SPEC.md §11.4).
 * @param {object} props
 * @param {React.ReactNode} [props.children]
 */
export function RequireAdmin({ children }) {
  const { me, ready } = useSession();
  const location = useLocation();
  const portalHome = import.meta.env.VITE_PORTAL_HOME || 'http://localhost:5181/portal/';

  if (!ready) {
    return (
      <div className="loading-container">
        <span className="spinner" aria-hidden="true" />
        <span>Loading session...</span>
      </div>
    );
  }

  if (!me) {
    const currentPath = location.pathname + location.search;
    if (currentPath.includes('/login') || currentPath === '/') {
      return <Navigate to="/login" replace />;
    }
    const returnUrl = encodeURIComponent(currentPath);
    return <Navigate to={`/login?return=${returnUrl}`} replace />;
  }

  if (me.role !== 'PLATFORM_ADMIN') {
    return (
      <div className="card access-denied-card">
        <div style={{ fontSize: '3rem', marginBottom: '1rem' }} aria-hidden="true">🔒</div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.5rem', color: 'var(--text-main)' }}>
          You do not have access
        </h1>
        <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem', fontSize: '0.9375rem' }}>
          Platform administration screens are restricted to platform administrators. Store users can view assigned store reports in the Client Portal or manage their account settings.
        </p>
        <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <a
            href={portalHome}
            className="btn btn-primary"
            target="_blank"
            rel="noopener noreferrer"
          >
            Open Client Portal ↗
          </a>
          <Link to="/account" className="btn btn-secondary">
            Go to My Account
          </Link>
        </div>
      </div>
    );
  }

  return children || <Outlet />;
}
