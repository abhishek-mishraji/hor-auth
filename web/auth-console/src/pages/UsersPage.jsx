import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router';
import {
  listUsers,
  createUser,
  updateUser,
  resetUserPassword,
  unlockUser,
} from '../api.js';
import { Table } from '../ui/Table.jsx';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Pager } from '../ui/Pager.jsx';
import { Modal } from '../ui/Modal.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';
import { formatDateTime, formatUserType } from '../ui/format.js';

/**
 * UsersPage manages store users (HOR Staff and Client Users).
 */
export function UsersPage() {
  // Filters & Pagination
  const [q, setQ] = useState('');
  const [userTypeFilter, setUserTypeFilter] = useState('');
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
  const [editUser, setEditUser] = useState(null);
  const [resetUser, setResetUser] = useState(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Create Form
  const [createForm, setCreateForm] = useState({
    userType: 'CLIENT',
    email: '',
    fullName: '',
    phoneNumber: '',
    address: '',
    password: '',
  });

  // Edit Form
  const [editForm, setEditForm] = useState({
    email: '',
    fullName: '',
    phoneNumber: '',
    address: '',
  });

  const [newPassword, setNewPassword] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setListError(null);
    try {
      const res = await listUsers({
        userType: userTypeFilter || undefined,
        status: statusFilter || undefined,
        q: q || undefined,
        limit,
        offset,
      });
      setData(res);
    } catch (err) {
      setListError(err);
    } finally {
      setLoading(false);
    }
  }, [userTypeFilter, statusFilter, q, limit, offset]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSearchChange = (e) => {
    setQ(e.target.value);
    setOffset(0);
  };

  const handleUserTypeFilterChange = (e) => {
    setUserTypeFilter(e.target.value);
    setOffset(0);
  };

  const handleStatusFilterChange = (e) => {
    setStatusFilter(e.target.value);
    setOffset(0);
  };

  const openCreateModal = () => {
    setCreateForm({
      userType: 'CLIENT',
      email: '',
      fullName: '',
      phoneNumber: '',
      address: '',
      password: '',
    });
    setModalError(null);
    setCreateModalOpen(true);
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      await createUser({
        userType: createForm.userType,
        email: createForm.email.trim(),
        fullName: createForm.fullName.trim(),
        phoneNumber: createForm.phoneNumber.trim() || undefined,
        address: createForm.address.trim() || undefined,
        password: createForm.password,
      });
      setCreateModalOpen(false);
      setBannerSuccess(`User "${createForm.fullName}" created successfully.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const openEditModal = (user) => {
    setEditUser(user);
    setEditForm({
      email: user.email || '',
      fullName: user.fullName || '',
      phoneNumber: user.phoneNumber || '',
      address: user.address || '',
    });
    setModalError(null);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!editUser) return;
    setModalError(null);
    setModalLoading(true);

    try {
      // Empty string removes optional fields per spec §16
      const payload = {
        email: editForm.email.trim(),
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        address: editForm.address.trim(),
      };
      await updateUser(editUser.id, payload);
      setEditUser(null);
      setBannerSuccess(`User "${editForm.fullName}" updated.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleStatus = async (user) => {
    const isDisabling = user.status === 'ACTIVE';
    const targetStatus = isDisabling ? 'DISABLED' : 'ACTIVE';
    const confirmMessage = isDisabling
      ? `Disable user "${user.fullName}"? This will terminate all their active sessions.`
      : `Enable user "${user.fullName}"?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      await updateUser(user.id, { status: targetStatus });
      setBannerSuccess(`User "${user.fullName}" status updated to ${targetStatus}.`);
      loadData();
    } catch (err) {
      setListError(err);
    }
  };

  const handleResetSubmit = async (e) => {
    e.preventDefault();
    if (!resetUser) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await resetUserPassword(resetUser.id, newPassword);
      setResetUser(null);
      setNewPassword('');
      setBannerSuccess(`Password reset for "${resetUser.fullName}". All existing sessions revoked.`);
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleUnlock = async (user) => {
    try {
      await unlockUser(user.id);
      setBannerSuccess(`User "${user.fullName}" account unlocked.`);
      loadData();
    } catch (err) {
      setListError(err);
    }
  };

  const columns = [
    {
      key: 'fullName',
      header: 'Full Name',
      render: (row) => (
        <Link to={`/users/${row.id}`} style={{ fontWeight: 600 }}>
          {row.fullName}
        </Link>
      ),
    },
    { key: 'email', header: 'Email' },
    {
      key: 'userType',
      header: 'User Type',
      render: (row) => (
        <span className="badge" style={{ backgroundColor: 'var(--bg-surface-raised)' }}>
          {formatUserType(row.userType)}
        </span>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (row) => <StatusBadge role={row.role} />,
    },
    {
      key: 'storeCount',
      header: 'Stores',
      render: (row) => (
        <Link to={`/users/${row.id}`} title="View assigned stores">
          {row.storeCount ?? 0} {row.storeCount === 1 ? 'store' : 'stores'}
        </Link>
      ),
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
    {
      key: 'lastLoginAt',
      header: 'Last Login',
      render: (row) => formatDateTime(row.lastLoginAt),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => {
        const isLocked = row.locked || (row.lockedUntil && new Date(row.lockedUntil) > new Date());

        return (
          <div style={{ display: 'inline-flex', gap: '0.375rem', flexWrap: 'wrap' }}>
            <Link to={`/users/${row.id}`} className="btn btn-secondary btn-sm" title="View details & stores">
              Details
            </Link>

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
                setResetUser(row);
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
              className={`btn btn-sm ${row.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-secondary'}`}
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
          <h1>Store Users</h1>
          <p className="page-description">Manage HOR staff, client users, credentials, and store assignments</p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={openCreateModal} className="btn btn-primary">
            + New Store User
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
            aria-label="Search users"
          />
        </div>

        <select
          value={userTypeFilter}
          onChange={handleUserTypeFilterChange}
          className="filter-select"
          aria-label="Filter by user type"
        >
          <option value="">All Types (Staff & Client)</option>
          <option value="STAFF">HOR Staff</option>
          <option value="CLIENT">Client Users</option>
        </select>

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
        emptyMessage="No store users match your search criteria."
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

      {/* Create User Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create Store User"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleCreateSubmit} id="create-user-form" noValidate>
          <Field
            label="User Type"
            name="userType"
            as="select"
            value={createForm.userType}
            onChange={(e) => setCreateForm({ ...createForm, userType: e.target.value })}
            required
            helpText="Client users access their stores' reports. HOR Staff manage store operations. (Fixed once created)"
            options={[
              { value: 'CLIENT', label: 'Client User (Store Owner / Partner)' },
              { value: 'STAFF', label: 'HOR Staff Member' },
            ]}
            disabled={modalLoading}
          />

          <Field
            label="Full Name"
            name="fullName"
            value={createForm.fullName}
            onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
            required
            placeholder="Jane Smith"
            disabled={modalLoading}
          />

          <Field
            label="Email Address"
            name="email"
            type="email"
            value={createForm.email}
            onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
            required
            placeholder="user@example.com"
            disabled={modalLoading}
          />

          <Field
            label="Phone Number"
            name="phoneNumber"
            type="tel"
            value={createForm.phoneNumber}
            onChange={(e) => setCreateForm({ ...createForm, phoneNumber: e.target.value })}
            placeholder="+1 555-987-6543"
            helpText="Optional phone number."
            disabled={modalLoading}
          />

          <Field
            label="Address"
            name="address"
            value={createForm.address}
            onChange={(e) => setCreateForm({ ...createForm, address: e.target.value })}
            placeholder="123 Main St, Indianapolis, IN"
            helpText="Optional physical or mailing address."
            disabled={modalLoading}
          />

          <Field
            label="Initial Password"
            name="password"
            type="password"
            value={createForm.password}
            onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
            required
            helpText="Minimum 12 characters. The user can change it at any time."
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
              {modalLoading ? 'Creating...' : 'Create User'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit User Modal */}
      <Modal
        isOpen={Boolean(editUser)}
        onClose={() => setEditUser(null)}
        title={`Edit Store User: ${editUser?.fullName || ''}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleEditSubmit} id="edit-user-form" noValidate>
          <Field
            label="User Type"
            name="userTypeDisplay"
            value={editUser ? formatUserType(editUser.userType) : ''}
            disabled
            helpText="User type cannot be changed after creation (AUTH_SPEC.md §8.3)."
          />

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
            placeholder="+1 555-987-6543"
            helpText="Leave empty to remove existing phone number."
            disabled={modalLoading}
          />

          <Field
            label="Address"
            name="address"
            value={editForm.address}
            onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
            placeholder="Address..."
            helpText="Leave empty to remove existing address."
            disabled={modalLoading}
          />

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setEditUser(null)}
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
        isOpen={Boolean(resetUser)}
        onClose={() => setResetUser(null)}
        title={`Reset Password: ${resetUser?.fullName || ''}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem' }}>
          Resetting this user's password will immediately terminate all active sessions across all devices.
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
              onClick={() => setResetUser(null)}
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
