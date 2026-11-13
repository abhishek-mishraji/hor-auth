import React, { useState, useEffect, useCallback } from 'react';
import {
  listAdmins,
  createAdmin,
  updateAdmin,
  resetAdminPassword,
  unlockAdmin,
} from '../api.js';
import { useSession } from '../session/SessionProvider.jsx';
import { Table } from '../ui/Table.jsx';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Pager } from '../ui/Pager.jsx';
import { Modal } from '../ui/Modal.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';
import { formatDateTime } from '../ui/format.js';

/**
 * AdminsPage manages platform administrator accounts.
 */
export function AdminsPage() {
  const { me } = useSession();

  // Filters & Pagination
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(50);

  // Table Data
  const [data, setData] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(null);
  const [bannerSuccess, setBannerSuccess] = useState('');

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editAdmin, setEditAdmin] = useState(null);
  const [resetAdmin, setResetAdmin] = useState(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Form states
  const [createForm, setCreateForm] = useState({
    email: '',
    fullName: '',
    phoneNumber: '',
    password: '',
  });

  const [editForm, setEditForm] = useState({
    email: '',
    fullName: '',
    phoneNumber: '',
  });

  const [newPassword, setNewPassword] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setListError(null);
    try {
      const res = await listAdmins({
        q: q || undefined,
        status: statusFilter || undefined,
        limit,
        offset,
      });
      setData(res);
    } catch (err) {
      setListError(err);
    } finally {
      setLoading(false);
    }
  }, [q, statusFilter, limit, offset]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handle Search Input (reset offset on query change)
  const handleSearchChange = (e) => {
    setQ(e.target.value);
    setOffset(0);
  };

  const handleStatusFilterChange = (e) => {
    setStatusFilter(e.target.value);
    setOffset(0);
  };

  // Open Create Modal
  const openCreateModal = () => {
    setCreateForm({ email: '', fullName: '', phoneNumber: '', password: '' });
    setModalError(null);
    setCreateModalOpen(true);
  };

  // Submit Create
  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      await createAdmin({
        email: createForm.email.trim(),
        fullName: createForm.fullName.trim(),
        phoneNumber: createForm.phoneNumber.trim() || undefined,
        password: createForm.password,
      });
      setCreateModalOpen(false);
      setBannerSuccess(`Platform admin "${createForm.fullName}" created successfully.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  // Open Edit Modal
  const openEditModal = (admin) => {
    setEditAdmin(admin);
    setEditForm({
      email: admin.email || '',
      fullName: admin.fullName || '',
      phoneNumber: admin.phoneNumber || '',
    });
    setModalError(null);
  };

  // Submit Edit (profile fields only, status is separate per spec)
  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!editAdmin) return;
    setModalError(null);
    setModalLoading(true);

    try {
      const payload = {
        email: editForm.email.trim(),
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim(), // empty string removes it per spec §16
      };
      await updateAdmin(editAdmin.id, payload);
      setEditAdmin(null);
      setBannerSuccess(`Admin "${editForm.fullName}" updated.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  // Toggle Status (ACTIVE <-> DISABLED)
  const handleToggleStatus = async (admin) => {
    const isDisabling = admin.status === 'ACTIVE';
    const targetStatus = isDisabling ? 'DISABLED' : 'ACTIVE';
    const confirmMessage = isDisabling
      ? `Disable admin "${admin.fullName}"? This will terminate their active sessions.`
      : `Enable admin "${admin.fullName}"?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      // Must send status alone per spec §16
      await updateAdmin(admin.id, { status: targetStatus });
      setBannerSuccess(`Admin "${admin.fullName}" status updated to ${targetStatus}.`);
      loadData();
    } catch (err) {
      setListError(err);
    }
  };

  // Submit Reset Password
  const handleResetSubmit = async (e) => {
    e.preventDefault();
    if (!resetAdmin) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await resetAdminPassword(resetAdmin.id, newPassword);
      setResetAdmin(null);
      setNewPassword('');
      setBannerSuccess(`Password reset for "${resetAdmin.fullName}". All existing sessions revoked.`);
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  // Unlock Admin
  const handleUnlock = async (admin) => {
    try {
      await unlockAdmin(admin.id);
      setBannerSuccess(`Admin "${admin.fullName}" account unlocked.`);
      loadData();
    } catch (err) {
      setListError(err);
    }
  };

  const columns = [
    { key: 'fullName', header: 'Full Name' },
    { key: 'email', header: 'Email' },
    {
      key: 'phoneNumber',
      header: 'Phone',
      render: (row) => row.phoneNumber || <span className="cell-muted">—</span>,
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'locked',
      header: 'Lock State',
      render: (row) => {
        const isLocked = row.locked || (row.lockedUntil && new Date(row.lockedUntil) > new Date());
        return isLocked ? <StatusBadge locked /> : <span className="cell-muted">Normal</span>;
      },
    },
    { key: 'failedLoginCount', header: 'Failed Logins' },
    {
      key: 'lastLoginAt',
      header: 'Last Login',
      render: (row) => formatDateTime(row.lastLoginAt),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => {
        const isSelf = me?.id === row.id;
        const isLocked = row.locked || (row.lockedUntil && new Date(row.lockedUntil) > new Date());

        return (
          <div style={{ display: 'inline-flex', gap: '0.375rem', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={() => openEditModal(row)}
              className="btn btn-secondary btn-sm"
              title="Edit profile"
            >
              Edit
            </button>

            {isLocked && (
              <button
                type="button"
                onClick={() => handleUnlock(row)}
                className="btn btn-secondary btn-sm"
                title="Unlock account"
              >
                Unlock
              </button>
            )}

            <button
              type="button"
              onClick={() => {
                setResetAdmin(row);
                setNewPassword('');
                setModalError(null);
              }}
              className="btn btn-secondary btn-sm"
              title="Reset password"
            >
              Reset Pwd
            </button>

            <button
              type="button"
              onClick={() => handleToggleStatus(row)}
              disabled={isSelf && row.status === 'ACTIVE'}
              className={`btn btn-sm ${row.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-secondary'}`}
              title={isSelf ? 'Cannot disable your own admin account' : undefined}
            >
              {row.status === 'ACTIVE' ? 'Disable' : 'Enable'}
            </button>
          </div>
        );
      },
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1>Platform Administrators</h1>
          <p className="page-description">Manage platform administrators, credentials, and access locks</p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={openCreateModal} className="btn btn-primary">
            + New Platform Admin
          </button>
        </div>
      </div>

      <SuccessBanner message={bannerSuccess} onDismiss={() => setBannerSuccess('')} />
      <ErrorBanner error={listError} onDismiss={() => setListError(null)} />

      <div className="filter-bar">
        <div className="search-input-wrapper">
          <input
            type="search"
            value={q}
            onChange={handleSearchChange}
            placeholder="Search by name or email..."
            className="field-input"
            aria-label="Search administrators"
          />
        </div>

        <select
          value={statusFilter}
          onChange={handleStatusFilterChange}
          className="filter-select"
          aria-label="Filter by status"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="DISABLED">Disabled</option>
        </select>
      </div>

      <Table
        columns={columns}
        data={data.items}
        loading={loading}
        emptyMessage="No platform administrators match your criteria."
      />

      <Pager
        total={data.total}
        offset={offset}
        limit={limit}
        onOffsetChange={setOffset}
        onLimitChange={(newLimit) => {
          setLimit(newLimit);
          setOffset(0);
        }}
      />

      {/* Create Admin Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create Platform Administrator"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleCreateSubmit} id="create-admin-form" noValidate>
          <Field
            label="Full Name"
            name="fullName"
            value={createForm.fullName}
            onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
            required
            placeholder="John Doe"
            disabled={modalLoading}
          />

          <Field
            label="Email Address"
            name="email"
            type="email"
            value={createForm.email}
            onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
            required
            placeholder="admin@example.com"
            disabled={modalLoading}
          />

          <Field
            label="Phone Number"
            name="phoneNumber"
            type="tel"
            value={createForm.phoneNumber}
            onChange={(e) => setCreateForm({ ...createForm, phoneNumber: e.target.value })}
            placeholder="+1 555-123-4567"
            helpText="Optional contact phone number."
            disabled={modalLoading}
          />

          <Field
            label="Initial Password"
            name="password"
            type="password"
            value={createForm.password}
            onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
            required
            helpText="Minimum 12 characters. Platform admins can change it any time."
            placeholder="••••••••••••"
            disabled={modalLoading}
          />

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setCreateModalOpen(false)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={modalLoading || !createForm.fullName || !createForm.email || createForm.password.length < 12}
            >
              {modalLoading ? 'Creating...' : 'Create Admin'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit Admin Modal */}
      <Modal
        isOpen={Boolean(editAdmin)}
        onClose={() => setEditAdmin(null)}
        title={`Edit Platform Admin: ${editAdmin?.fullName || ''}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleEditSubmit} id="edit-admin-form" noValidate>
          <Field
            label="Full Name"
            name="fullName"
            value={editForm.fullName}
            onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
            required
            disabled={modalLoading}
          />

          <Field
            label="Email Address"
            name="email"
            type="email"
            value={editForm.email}
            onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
            required
            disabled={modalLoading}
          />

          <Field
            label="Phone Number"
            name="phoneNumber"
            type="tel"
            value={editForm.phoneNumber}
            onChange={(e) => setEditForm({ ...editForm, phoneNumber: e.target.value })}
            placeholder="+1 555-123-4567"
            helpText="Leave empty to remove existing phone number."
            disabled={modalLoading}
          />

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setEditAdmin(null)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={modalLoading || !editForm.fullName || !editForm.email}
            >
              {modalLoading ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Reset Password Modal */}
      <Modal
        isOpen={Boolean(resetAdmin)}
        onClose={() => setResetAdmin(null)}
        title={`Reset Password: ${resetAdmin?.fullName || ''}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem' }}>
          Resetting this administrator's password will immediately terminate all their active sessions across all devices.
        </p>
        <form onSubmit={handleResetSubmit} noValidate>
          <Field
            label="New Password"
            name="newPassword"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            helpText="Minimum 12 characters."
            placeholder="••••••••••••"
            disabled={modalLoading}
          />

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setResetAdmin(null)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-danger"
              disabled={modalLoading || newPassword.length < 12}
            >
              {modalLoading ? 'Resetting...' : 'Reset Password & End Sessions'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
