import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router';
import {
  getUser,
  updateUser,
  resetUserPassword,
  unlockUser,
  listStores,
  assignStoreMember,
  removeStoreMember,
} from '../api.js';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Table } from '../ui/Table.jsx';
import { Modal } from '../ui/Modal.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';
import { formatDateTime, formatUserType } from '../ui/format.js';

/**
 * UserDetailPage manages a single store user's details and store assignments.
 */
export function UserDetailPage() {
  const { id } = useParams();

  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [bannerSuccess, setBannerSuccess] = useState('');

  // Modals state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [changeRoleTarget, setChangeRoleTarget] = useState(null);
  const [removeTarget, setRemoveTarget] = useState(null);

  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Available stores for assignment
  const [allStores, setAllStores] = useState([]);

  // Form states
  const [editForm, setEditForm] = useState({
    email: '',
    fullName: '',
    phoneNumber: '',
    address: '',
  });

  const [newPassword, setNewPassword] = useState('');

  const [assignForm, setAssignForm] = useState({
    storeId: '',
    storeRole: '',
  });

  const [newStoreRole, setNewStoreRole] = useState('');

  const loadUser = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const u = await getUser(id);
      setUser(u);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadUser();
  }, [loadUser]);

  // Load stores list when opening assign modal
  const openAssignModal = async () => {
    setModalError(null);
    setAssignForm({
      storeId: '',
      storeRole: user.userType === 'CLIENT' ? 'PARTNER' : 'STAFF',
    });
    setAssignModalOpen(true);

    try {
      const res = await listStores({ limit: 500 });
      setAllStores(res.items || []);
    } catch (err) {
      setModalError(err);
    }
  };

  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    if (!assignForm.storeId || !assignForm.storeRole) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await assignStoreMember(assignForm.storeId, user.id, assignForm.storeRole);
      setAssignModalOpen(false);
      setBannerSuccess('Store assigned successfully.');
      loadUser();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleChangeRoleSubmit = async (e) => {
    e.preventDefault();
    if (!changeRoleTarget || !newStoreRole) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await assignStoreMember(changeRoleTarget.storeId, user.id, newStoreRole);
      setChangeRoleTarget(null);
      setBannerSuccess('Store assignment role updated.');
      loadUser();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleRemoveConfirm = async () => {
    if (!removeTarget) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await removeStoreMember(removeTarget.storeId, user.id);
      setRemoveTarget(null);
      setBannerSuccess(`Removed store assignment for ${removeTarget.storeName || removeTarget.storeCode}.`);
      loadUser();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleEditOpen = () => {
    setEditForm({
      email: user.email || '',
      fullName: user.fullName || '',
      phoneNumber: user.phoneNumber || '',
      address: user.address || '',
    });
    setModalError(null);
    setEditModalOpen(true);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      await updateUser(user.id, {
        email: editForm.email.trim(),
        fullName: editForm.fullName.trim(),
        phoneNumber: editForm.phoneNumber.trim(),
        address: editForm.address.trim(),
      });
      setEditModalOpen(false);
      setBannerSuccess('User profile updated.');
      loadUser();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleStatus = async () => {
    const isDisabling = user.status === 'ACTIVE';
    const targetStatus = isDisabling ? 'DISABLED' : 'ACTIVE';
    const confirmMessage = isDisabling
      ? `Disable user "${user.fullName}"? This will terminate all active sessions.`
      : `Enable user "${user.fullName}"?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      await updateUser(user.id, { status: targetStatus });
      setBannerSuccess(`User status changed to ${targetStatus}.`);
      loadUser();
    } catch (err) {
      setError(err);
    }
  };

  const handleUnlock = async () => {
    try {
      await unlockUser(user.id);
      setBannerSuccess('User account unlocked.');
      loadUser();
    } catch (err) {
      setError(err);
    }
  };

  const handleResetSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      await resetUserPassword(user.id, newPassword);
      setResetModalOpen(false);
      setNewPassword('');
      setBannerSuccess('Password reset successfully. Active sessions ended.');
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  if (loading && !user) {
    return (
      <div className="loading-container">
        <span className="spinner" aria-hidden="true" />
        <span>Loading user details...</span>
      </div>
    );
  }

  if (error && !user) {
    return (
      <div>
        <ErrorBanner error={error} />
        <Link to="/users" className="btn btn-secondary">
          ← Back to Users
        </Link>
      </div>
    );
  }

  const isLocked = user.locked || (user.lockedUntil && new Date(user.lockedUntil) > new Date());

  const storeColumns = [
    {
      key: 'storeCode',
      header: 'Store Code',
      render: (row) => (
        <Link to={`/stores/${row.storeId}`} className="cell-mono" style={{ fontWeight: 600 }}>
          {row.storeCode}
        </Link>
      ),
    },
    { key: 'storeName', header: 'Store Name' },
    { key: 'posVendor', header: 'POS Vendor' },
    {
      key: 'status',
      header: 'Store Status',
      render: (row) => <StatusBadge status={row.storeStatus || row.status} />,
    },
    {
      key: 'storeRole',
      header: 'Assigned Role',
      render: (row) => <StatusBadge storeRole={row.storeRole} />,
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div style={{ display: 'inline-flex', gap: '0.375rem' }}>
          {user.userType === 'CLIENT' && (
            <button
              type="button"
              onClick={() => {
                setChangeRoleTarget(row);
                setNewStoreRole(row.storeRole === 'OWNER' ? 'PARTNER' : 'OWNER');
                setModalError(null);
              }}
              className="btn btn-secondary btn-sm"
            >
              Change Role
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              setRemoveTarget(row);
              setModalError(null);
            }}
            className="btn btn-outline-danger btn-sm"
          >
            Remove
          </button>
        </div>
      ),
    },
  ];

  // Filter out stores already assigned to this user
  const assignedStoreIds = new Set((user.stores || []).map((s) => Number(s.storeId)));
  const assignableStores = allStores.filter((s) => !assignedStoreIds.has(Number(s.id || s.storeId)));

  return (
    <div>
      <nav aria-label="Breadcrumb" className="breadcrumb">
        <Link to="/users">Users</Link>
        <span className="breadcrumb-separator">/</span>
        <span className="breadcrumb-current">{user.fullName}</span>
      </nav>

      <div className="page-header">
        <div className="page-title-group">
          <h1>{user.fullName}</h1>
          <p className="page-description">
            {formatUserType(user.userType)} User ({user.email})
          </p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={handleEditOpen} className="btn btn-secondary">
            Edit Profile
          </button>
          {isLocked && (
            <button type="button" onClick={handleUnlock} className="btn btn-secondary">
              Unlock Account
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              setNewPassword('');
              setModalError(null);
              setResetModalOpen(true);
            }}
            className="btn btn-secondary"
          >
            Reset Password
          </button>
          <button
            type="button"
            onClick={handleToggleStatus}
            className={`btn ${user.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-secondary'}`}
          >
            {user.status === 'ACTIVE' ? 'Disable Account' : 'Enable Account'}
          </button>
        </div>
      </div>

      <SuccessBanner message={bannerSuccess} onDismiss={() => setBannerSuccess('')} />
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">User Information</h2>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <StatusBadge role={user.role} />
            <StatusBadge status={user.status} />
            {isLocked && <StatusBadge locked />}
          </div>
        </div>

        <div className="detail-grid">
          <div className="detail-item">
            <span className="detail-label">User ID</span>
            <span className="detail-value cell-mono">{user.id}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">User Type</span>
            <span className="detail-value">{formatUserType(user.userType)} (Fixed)</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Email</span>
            <span className="detail-value">{user.email}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Phone</span>
            <span className="detail-value">{user.phoneNumber || '—'}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Address</span>
            <span className="detail-value">{user.address || '—'}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Failed Logins</span>
            <span className="detail-value">{user.failedLoginCount ?? 0} / 10</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Last Login</span>
            <span className="detail-value">{formatDateTime(user.lastLoginAt)}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Created At</span>
            <span className="detail-value">{formatDateTime(user.createdAt)}</span>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Assigned Stores ({(user.stores || []).length})</h2>
          <button type="button" onClick={openAssignModal} className="btn btn-primary btn-sm">
            + Assign Store
          </button>
        </div>

        <Table
          columns={storeColumns}
          data={user.stores || []}
          keyField="storeId"
          emptyMessage="No stores currently assigned to this user."
        />
      </div>

      {/* Assign Store Modal */}
      <Modal
        isOpen={assignModalOpen}
        onClose={() => setAssignModalOpen(false)}
        title={`Assign Store to ${user.fullName}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleAssignSubmit} noValidate>
          <Field
            label="Select Store"
            name="storeId"
            as="select"
            value={assignForm.storeId}
            onChange={(e) => setAssignForm({ ...assignForm, storeId: e.target.value })}
            required
            disabled={modalLoading}
          >
            <option value="">-- Choose a store --</option>
            {assignableStores.map((s) => (
              <option key={s.id || s.storeId} value={s.id || s.storeId}>
                {s.storeCode} - {s.storeName} ({s.status})
              </option>
            ))}
          </Field>

          {user.userType === 'CLIENT' ? (
            <Field
              label="Store Role"
              name="storeRole"
              as="select"
              value={assignForm.storeRole}
              onChange={(e) => setAssignForm({ ...assignForm, storeRole: e.target.value })}
              required
              helpText="A store may have at most ONE Owner. Other client users must be assigned as Partners."
              options={[
                { value: 'PARTNER', label: 'PARTNER' },
                { value: 'OWNER', label: 'OWNER' },
              ]}
              disabled={modalLoading}
            />
          ) : (
            <Field
              label="Store Role"
              name="storeRoleDisplay"
              value="STAFF"
              disabled
              helpText="HOR Staff members are assigned with the STAFF role."
            />
          )}

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setAssignModalOpen(false)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={modalLoading || !assignForm.storeId}
            >
              {modalLoading ? 'Assigning...' : 'Assign Store'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Change Role Modal */}
      <Modal
        isOpen={Boolean(changeRoleTarget)}
        onClose={() => setChangeRoleTarget(null)}
        title={`Change Role: ${changeRoleTarget?.storeName || ''}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleChangeRoleSubmit} noValidate>
          <Field
            label="Store Role"
            name="newRole"
            as="select"
            value={newStoreRole}
            onChange={(e) => setNewStoreRole(e.target.value)}
            required
            options={[
              { value: 'PARTNER', label: 'PARTNER' },
              { value: 'OWNER', label: 'OWNER' },
            ]}
            disabled={modalLoading}
          />
          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setChangeRoleTarget(null)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={modalLoading}>
              {modalLoading ? 'Updating...' : 'Update Role'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Remove Confirmation Modal */}
      <Modal
        isOpen={Boolean(removeTarget)}
        onClose={() => setRemoveTarget(null)}
        title="Remove Store Assignment"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          Are you sure you want to unassign <strong>{user.fullName}</strong> from store{' '}
          <strong>{removeTarget?.storeCode} ({removeTarget?.storeName})</strong>?
        </p>
        <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
          <button
            type="button"
            onClick={() => setRemoveTarget(null)}
            className="btn btn-secondary"
            disabled={modalLoading}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleRemoveConfirm}
            className="btn btn-danger"
            disabled={modalLoading}
          >
            {modalLoading ? 'Removing...' : 'Confirm Remove'}
          </button>
        </div>
      </Modal>

      {/* Edit Profile Modal */}
      <Modal
        isOpen={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit User Profile"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleEditSubmit} noValidate>
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
              onClick={() => setEditModalOpen(false)}
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
        isOpen={resetModalOpen}
        onClose={() => setResetModalOpen(false)}
        title={`Reset Password for ${user.fullName}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem' }}>
          Resetting password will revoke all active sessions for this user.
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
              onClick={() => setResetModalOpen(false)}
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
              {modalLoading ? 'Resetting...' : 'Reset Password'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
