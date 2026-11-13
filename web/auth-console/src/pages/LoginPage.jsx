import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { login as apiLogin } from '../api.js';
import { useSession } from '../session/SessionProvider.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner } from '../ui/ErrorBanner.jsx';

/**
 * Handles post-login destination based on return URL and user role.
 * Per AUTH_SPEC.md §11.4:
 * - Return must be an absolute http(s) URL in VITE_RETURN_ORIGINS or internal path (/... not //)
 * - Store users may only go to VITE_PORTAL_HOME or /account
 * - Platform admins default to VITE_ADMIN_HOME, store users default to VITE_PORTAL_HOME
 * @param {import('../session/SessionProvider.jsx').MeUser} me
 * @param {string|null} returnParam
 */
function navigateAfterLogin(me, returnParam) {
  const allowedOrigins = (import.meta.env.VITE_RETURN_ORIGINS || '')
    .split(',')
    .map(o => o.trim())
    .filter(Boolean);

  let targetUrl = null;

  if (returnParam && !returnParam.includes('/login')) {
    if (returnParam.startsWith('/') && !returnParam.startsWith('//')) {
      targetUrl = returnParam;
    } else {
      try {
        const parsed = new URL(returnParam);
        if (
          (parsed.protocol === 'http:' || parsed.protocol === 'https:') &&
          allowedOrigins.includes(parsed.origin) &&
          !parsed.pathname.includes('/login')
        ) {
          targetUrl = returnParam;
        }
      } catch {
        // Invalid URL format
      }
    }
  }

  const isStoreUser = me.role === 'HOR_STAFF' || me.role === 'CLIENT_USER';
  if (isStoreUser) {
    const portalHome = import.meta.env.VITE_PORTAL_HOME || '';
    const isAllowedForStoreUser = targetUrl && (
      targetUrl === '/account' ||
      targetUrl.startsWith('/account?') ||
      (portalHome && targetUrl.startsWith(portalHome))
    );
    // Stay on auth-console (/account) unless an explicit portal return URL was passed
    targetUrl = isAllowedForStoreUser ? targetUrl : '/account';
  } else if (!targetUrl) {
    // Default platform admin to /admins on auth-console
    targetUrl = '/admins';
  }

  window.location.assign(targetUrl);
}

/**
 * Login screen for all HOR applications.
 */
export function LoginPage() {
  const [searchParams] = useSearchParams();
  const rawReturnParam = searchParams.get('return');
  // Strip out any recursive /login returns
  const returnParam = rawReturnParam && !rawReturnParam.includes('/login') ? rawReturnParam : null;

  const { me, ready, setMe } = useSession();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Clean URL in browser if it contained a corrupted / nested /login return parameter
  useEffect(() => {
    if (rawReturnParam && rawReturnParam.includes('/login')) {
      window.history.replaceState({}, '', '/login');
    }
  }, [rawReturnParam]);

  // If already logged in, redirect immediately
  useEffect(() => {
    if (ready && me) {
      navigateAfterLogin(me, returnParam);
    }
  }, [ready, me, returnParam]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) return;

    setLoading(true);
    setError(null);

    try {
      const user = await apiLogin({ email, password });
      setMe(user);
      navigateAfterLogin(user, returnParam);
    } catch (err) {
      setError(err);
      setLoading(false);
    }
  };

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-brand">
          <div className="brand-logo" aria-hidden="true">H</div>
          <h1>HOR Sign In</h1>
          <p>Hands Off Retail Authentication Console</p>
        </div>

        <ErrorBanner error={error} onDismiss={() => setError(null)} />

        <form onSubmit={handleSubmit} noValidate>
          <Field
            label="Email Address"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
            placeholder="admin@example.com"
            disabled={loading}
          />

          <Field
            label="Password"
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            placeholder="••••••••••••"
            disabled={loading}
          />

          <button
            type="submit"
            className="btn btn-primary"
            disabled={loading || !email || !password}
            style={{ width: '100%', marginTop: '0.75rem' }}
          >
            {loading ? (
              <>
                <span className="spinner" aria-hidden="true" style={{ width: 16, height: 16 }} />
                <span>Signing in...</span>
              </>
            ) : (
              'Sign In'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
