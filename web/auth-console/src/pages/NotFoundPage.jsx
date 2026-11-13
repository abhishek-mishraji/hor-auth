import React from 'react';
import { Link } from 'react-router';
import { useSession } from '../session/SessionProvider.jsx';

/**
 * NotFoundPage displays a clean 404 state.
 */
export function NotFoundPage() {
  const { me } = useSession();
  const isAdmin = me?.role === 'PLATFORM_ADMIN';

  return (
    <div className="card access-denied-card">
      <div style={{ fontSize: '3rem', marginBottom: '1rem' }} aria-hidden="true">🔍</div>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '0.5rem', color: 'var(--text-main)' }}>
        404 - Page Not Found
      </h1>
      <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem', fontSize: '0.9375rem' }}>
        The page you are looking for does not exist or has been moved.
      </p>
      <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem' }}>
        <Link to={isAdmin ? '/admins' : '/account'} className="btn btn-primary">
          Return to Dashboard
        </Link>
        <Link to="/account" className="btn btn-secondary">
          My Account
        </Link>
      </div>
    </div>
  );
}
