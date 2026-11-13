import React from 'react';
import { Routes, Route, Navigate } from 'react-router';
import { SessionProvider, useSession } from './session/SessionProvider.jsx';
import { RequireAuth, RequireAdmin } from './session/RequireAuth.jsx';
import { Layout } from './ui/Layout.jsx';

import { LoginPage } from './pages/LoginPage.jsx';
import { AccountPage } from './pages/AccountPage.jsx';
import { AdminsPage } from './pages/AdminsPage.jsx';
import { UsersPage } from './pages/UsersPage.jsx';
import { UserDetailPage } from './pages/UserDetailPage.jsx';
import { StoresPage } from './pages/StoresPage.jsx';
import { StoreDetailPage } from './pages/StoreDetailPage.jsx';
import { AuditPage } from './pages/AuditPage.jsx';
import { NotFoundPage } from './pages/NotFoundPage.jsx';

/**
 * Directs the root URL '/' to the appropriate home based on authentication and role.
 */
function RootIndex() {
  const { me, ready } = useSession();

  if (!ready) {
    return (
      <div className="loading-container">
        <span className="spinner" aria-hidden="true" />
      </div>
    );
  }

  if (!me) {
    return <Navigate to="/login" replace />;
  }

  if (me.role === 'PLATFORM_ADMIN') {
    return <Navigate to="/admins" replace />;
  }

  return <Navigate to="/account" replace />;
}

/**
 * Root Application Router Configuration.
 */
export function App() {
  return (
    <SessionProvider>
      <Routes>
        {/* Public login route */}
        <Route path="/login" element={<LoginPage />} />

        {/* Authenticated routes wrapped with Layout */}
        <Route element={<Layout />}>
          <Route path="/" element={<RootIndex />} />

          {/* Any authenticated role */}
          <Route
            path="/account"
            element={
              <RequireAuth>
                <AccountPage />
              </RequireAuth>
            }
          />

          {/* Platform Administrator only */}
          <Route
            path="/admins"
            element={
              <RequireAdmin>
                <AdminsPage />
              </RequireAdmin>
            }
          />

          <Route
            path="/users"
            element={
              <RequireAdmin>
                <UsersPage />
              </RequireAdmin>
            }
          />

          <Route
            path="/users/:id"
            element={
              <RequireAdmin>
                <UserDetailPage />
              </RequireAdmin>
            }
          />

          <Route
            path="/stores"
            element={
              <RequireAdmin>
                <StoresPage />
              </RequireAdmin>
            }
          />

          <Route
            path="/stores/:id"
            element={
              <RequireAdmin>
                <StoreDetailPage />
              </RequireAdmin>
            }
          />

          <Route
            path="/audit"
            element={
              <RequireAdmin>
                <AuditPage />
              </RequireAdmin>
            }
          />

          {/* Fallback 404 */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </SessionProvider>
  );
}
