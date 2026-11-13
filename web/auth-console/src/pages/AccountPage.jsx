import React, { useState } from 'react';
import { useSession } from '../session/SessionProvider.jsx';
import { changeMyPassword } from '../api.js';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';
import { Table } from '../ui/Table.jsx';
import { formatDateTime } from '../ui/format.js';

/**
 * AccountPage provides current user details, assigned stores, and self-service password change.
 */
export function AccountPage() {
  const { me, refreshMe, logout } = useSession();

  // Password change form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState('');

  if (!me) return null;

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMsg('');

    if (newPassword.length < 12) {
      setError(new Error('New password must be at least 12 characters long.'));
      return;
    }

    if (newPassword !== confirmPassword) {
      setError(new Error('New password and confirmation do not match.'));
      return;
    }

    setLoading(true);
    try {
      await changeMyPassword({ currentPassword, newPassword });
      setSuccessMsg('Your password has been changed successfully. All other active sessions have been signed out.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      await refreshMe();
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  const storeColumns = [
    { key: 'storeCode', header: 'Store Code', render: (row) => <span className="cell-mono">{row.storeCode}</span> },
    { key: 'storeName', header: 'Store Name' },
    {
      key: 'storeRole',
      header: 'Assigned Role',
      render: (row) => <StatusBadge storeRole={row.storeRole} />,
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1>My Account</h1>
          <p className="page-description">Manage your personal account settings and security</p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={logout} className="btn btn-secondary">
            Sign Out
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Profile Overview</h2>
          <StatusBadge role={me.role} />
        </div>

        <div className="detail-grid">
          <div className="detail-item">
            <span className="detail-label">Full Name</span>
            <span className="detail-value">{me.fullName}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Email Address</span>
            <span className="detail-value">{me.email}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Account Type</span>
            <span className="detail-value">{me.accountType === 'ADMIN' ? 'Platform Administrator' : 'Store User'}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Last Login</span>
            <span className="detail-value">{formatDateTime(me.lastLoginAt)}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Password Last Changed</span>
            <span className="detail-value">{formatDateTime(me.passwordChangedAt)}</span>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Store Access</h2>
        </div>

        {me.allStores ? (
          <div className="banner banner-info" style={{ margin: 0 }}>
            <div className="banner-icon" aria-hidden="true">ℹ️</div>
            <div className="banner-content">
              <strong>Full Platform Store Access:</strong> As a Platform Administrator, you have complete access to all stores and management tools across the system.
            </div>
          </div>
        ) : (
          <div>
            <p style={{ marginBottom: '1rem', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
              You have access to the following stores in the Client Portal:
            </p>
            <Table
              columns={storeColumns}
              data={me.stores || []}
              keyField="storeId"
              emptyMessage="No active store assignments. Please contact an administrator to assign stores."
            />
          </div>
        )}
      </div>

      <div className="card" style={{ maxWidth: '640px' }}>
        <div className="card-header">
          <h2 className="card-title">Change Password</h2>
        </div>

        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        <SuccessBanner message={successMsg} onDismiss={() => setSuccessMsg('')} />

        <form onSubmit={handlePasswordChange} noValidate>
          <Field
            label="Current Password"
            name="currentPassword"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
            placeholder="••••••••••••"
            disabled={loading}
          />

          <Field
            label="New Password"
            name="newPassword"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            helpText="Minimum 12 characters. Use a mix of letters, numbers, and symbols."
            autoComplete="new-password"
            placeholder="••••••••••••"
            disabled={loading}
          />

          <Field
            label="Confirm New Password"
            name="confirmPassword"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            autoComplete="new-password"
            placeholder="••••••••••••"
            disabled={loading}
          />

          <button
            type="submit"
            className="btn btn-primary"
            disabled={loading || !currentPassword || !newPassword || !confirmPassword}
            style={{ marginTop: '0.5rem' }}
          >
            {loading ? 'Changing Password...' : 'Update Password'}
          </button>
        </form>
      </div>
    </div>
  );
}
