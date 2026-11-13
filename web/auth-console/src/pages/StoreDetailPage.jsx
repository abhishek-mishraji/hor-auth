import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router';
import {
  getStore,
  updateStore,
  getStoreMembers,
  assignStoreMember,
  removeStoreMember,
  listUsers,
} from '../api.js';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Table } from '../ui/Table.jsx';
import { Modal } from '../ui/Modal.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';
import { formatDateTime, formatUserType } from '../ui/format.js';

const COMMON_TIMEZONES = [
  { value: 'America/Indiana/Indianapolis', label: 'America/Indiana/Indianapolis (Eastern - Indiana)' },
  { value: 'America/New_York', label: 'America/New_York (Eastern)' },
  { value: 'America/Chicago', label: 'America/Chicago (Central)' },
  { value: 'America/Denver', label: 'America/Denver (Mountain)' },
  { value: 'America/Phoenix', label: 'America/Phoenix (Mountain - no DST)' },
  { value: 'America/Los_Angeles', label: 'America/Los_Angeles (Pacific)' },
  { value: 'America/Anchorage', label: 'America/Anchorage (Alaska)' },
  { value: 'Pacific/Honolulu', label: 'Pacific/Honolulu (Hawaii)' },
];

/**
 * StoreDetailPage displays store details and manages store members.
 */
export function StoreDetailPage() {
  const { id } = useParams();

  const [store, setStore] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [bannerSuccess, setBannerSuccess] = useState('');

  // Modals state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [addMemberModalOpen, setAddMemberModalOpen] = useState(false);
  const [changeRoleTarget, setChangeRoleTarget] = useState(null);
  const [removeTarget, setRemoveTarget] = useState(null);

  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Available users for member addition
  const [allUsers, setAllUsers] = useState([]);

  // Edit store form state
  const [editForm, setEditForm] = useState({
    storeName: '',
    s3Folder: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateCode: '',
    postalCode: '',
    countryCode: 'US',
    contactNumber: '',
    timezone: '',
  });

  // Add member form state
  const [addMemberForm, setAddMemberForm] = useState({
    userId: '',
    storeRole: 'PARTNER',
  });

  const [newStoreRole, setNewStoreRole] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, m] = await Promise.all([
        getStore(id),
        getStoreMembers(id),
      ]);
      setStore(s);
      setMembers(m);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Open Edit Store Modal
  const handleEditOpen = () => {
    setEditForm({
      storeName: store.storeName || '',
      s3Folder: store.s3Folder || '',
      addressLine1: store.addressLine1 || '',
      addressLine2: store.addressLine2 || '',
      city: store.city || '',
      stateCode: store.stateCode || '',
      postalCode: store.postalCode || '',
      countryCode: store.countryCode || 'US',
      contactNumber: store.contactNumber || '',
      timezone: store.timezone || 'America/Indiana/Indianapolis',
    });
    setModalError(null);
    setEditModalOpen(true);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      const payload = {
        storeName: editForm.storeName.trim(),
        s3Folder: editForm.s3Folder.trim(),
        addressLine1: editForm.addressLine1.trim(),
        addressLine2: editForm.addressLine2.trim(),
        city: editForm.city.trim(),
        stateCode: editForm.stateCode.trim().toUpperCase(),
        postalCode: editForm.postalCode.trim(),
        countryCode: editForm.countryCode.trim().toUpperCase(),
        contactNumber: editForm.contactNumber.trim(),
        timezone: editForm.timezone.trim(),
      };
      await updateStore(store.id || id, payload);
      setEditModalOpen(false);
      setBannerSuccess('Store details updated.');
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleStatus = async () => {
    const isDeactivating = store.status === 'ACTIVE';
    const targetStatus = isDeactivating ? 'INACTIVE' : 'ACTIVE';
    const confirmMessage = isDeactivating
      ? `Deactivate store "${store.storeName}" (${store.storeCode})?`
      : `Activate store "${store.storeName}" (${store.storeCode})?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      await updateStore(store.id || id, { status: targetStatus });
      setBannerSuccess(`Store status changed to ${targetStatus}.`);
      loadData();
    } catch (err) {
      setError(err);
    }
  };

  // Open Add Member Modal
  const openAddMemberModal = async () => {
    setModalError(null);
    setAddMemberForm({
      userId: '',
      storeRole: 'PARTNER',
    });
    setAddMemberModalOpen(true);

    try {
      const res = await listUsers({ limit: 500 });
      setAllUsers(res.items || []);
    } catch (err) {
      setModalError(err);
    }
  };

  const selectedUserForAdd = allUsers.find(
    (u) => String(u.id) === String(addMemberForm.userId)
  );

  const handleUserSelectChange = (userId) => {
    const selected = allUsers.find((u) => String(u.id) === String(userId));
    const defaultRole = selected?.userType === 'STAFF' ? 'STAFF' : 'PARTNER';
    setAddMemberForm({
      userId,
      storeRole: defaultRole,
    });
  };

  const handleAddMemberSubmit = async (e) => {
    e.preventDefault();
    if (!addMemberForm.userId || !addMemberForm.storeRole) return;
    setModalError(null);
    setModalLoading(true);

    try {
      await assignStoreMember(store.id || id, addMemberForm.userId, addMemberForm.storeRole);
      setAddMemberModalOpen(false);
      setBannerSuccess('Store member assigned successfully.');
      loadData();
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
      await assignStoreMember(store.id || id, changeRoleTarget.userId, newStoreRole);
      setChangeRoleTarget(null);
      setBannerSuccess(`Updated role for "${changeRoleTarget.fullName}" to ${newStoreRole}.`);
      loadData();
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
      await removeStoreMember(store.id || id, removeTarget.userId);
      setRemoveTarget(null);
      setBannerSuccess(`Removed "${removeTarget.fullName}" from store members.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  if (loading && !store) {
    return (
      <div className="loading-container">
        <span className="spinner" aria-hidden="true" />
        <span>Loading store details...</span>
      </div>
    );
  }

  if (error && !store) {
    return (
      <div>
        <ErrorBanner error={error} />
        <Link to="/stores" className="btn btn-secondary">
          ← Back to Stores
        </Link>
      </div>
    );
  }

  const ownerMember = members.find((m) => m.storeRole === 'OWNER');

  // Filter out users already assigned to this store
  const existingMemberUserIds = new Set(members.map((m) => Number(m.userId)));
  const assignableUsers = allUsers.filter((u) => !existingMemberUserIds.has(Number(u.id)));

  const memberColumns = [
    {
      key: 'fullName',
      header: 'Member Name',
      render: (row) => (
        <Link to={`/users/${row.userId}`} style={{ fontWeight: 600 }}>
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
      key: 'storeRole',
      header: 'Store Role',
      render: (row) => <StatusBadge storeRole={row.storeRole} />,
    },
    {
      key: 'status',
      header: 'Account Status',
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'assignedAt',
      header: 'Assigned Date',
      render: (row) => formatDateTime(row.assignedAt),
    },
    {
      key: 'assignedBy',
      header: 'Assigned By',
      render: (row) => row.assignedBy || '—',
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div style={{ display: 'inline-flex', gap: '0.375rem' }}>
          {row.userType === 'CLIENT' && (
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

  return (
    <div>
      <nav aria-label="Breadcrumb" className="breadcrumb">
        <Link to="/stores">Stores</Link>
        <span className="breadcrumb-separator">/</span>
        <span className="breadcrumb-current">
          {store.storeCode} ({store.storeName})
        </span>
      </nav>

      <div className="page-header">
        <div className="page-title-group">
          <h1>{store.storeName}</h1>
          <p className="page-description">
            Store Code: <span className="cell-mono">{store.storeCode}</span> · POS Vendor: {store.posVendor}
          </p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={handleEditOpen} className="btn btn-secondary">
            Edit Store
          </button>
          <button
            type="button"
            onClick={handleToggleStatus}
            className={`btn ${store.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-secondary'}`}
          >
            {store.status === 'ACTIVE' ? 'Deactivate Store' : 'Activate Store'}
          </button>
        </div>
      </div>

      <SuccessBanner message={bannerSuccess} onDismiss={() => setBannerSuccess('')} />
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {/* Owner Highlight Card */}
      {ownerMember ? (
        <div className="card" style={{ borderColor: 'var(--primary-border)', backgroundColor: 'var(--primary-subtle)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <span className="badge badge-store-owner" style={{ marginBottom: '0.25rem' }}>Store Owner</span>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: '0.25rem 0' }}>
                <Link to={`/users/${ownerMember.userId}`}>{ownerMember.fullName}</Link>
              </h2>
              <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                {ownerMember.email} · Assigned by {ownerMember.assignedBy || 'Admin'} on {formatDateTime(ownerMember.assignedAt)}
              </span>
            </div>
          </div>
        </div>
      ) : (
        <div className="banner banner-warning" style={{ alignItems: 'center' }}>
          <div className="banner-icon" aria-hidden="true">⚠️</div>
          <div className="banner-content">
            <strong>No owner yet:</strong> This store does not have an assigned OWNER client. You can assign an Owner client member below.
          </div>
        </div>
      )}

      {/* Store Information */}
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Store Details</h2>
          <StatusBadge status={store.status} />
        </div>

        <div className="detail-grid">
          <div className="detail-item">
            <span className="detail-label">Store Code</span>
            <span className="detail-value cell-mono">{store.storeCode}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">POS Vendor</span>
            <span className="detail-value">{store.posVendor}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">S3 Ingestion Folder</span>
            <span className="detail-value cell-mono" style={{ fontSize: '0.8125rem' }}>{store.s3Folder}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Address</span>
            <span className="detail-value">
              {store.addressLine1}
              {store.addressLine2 ? `, ${store.addressLine2}` : ''}
              <br />
              {store.city}, {store.stateCode} {store.postalCode} ({store.countryCode || 'US'})
            </span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Contact Number</span>
            <span className="detail-value">{store.contactNumber || '—'}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Timezone</span>
            <span className="detail-value">{store.timezone}</span>
          </div>

          <div className="detail-item">
            <span className="detail-label">Created At</span>
            <span className="detail-value">{formatDateTime(store.createdAt)}</span>
          </div>
        </div>
      </div>

      {/* Members Section */}
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Store Members ({members.length})</h2>
          <button type="button" onClick={openAddMemberModal} className="btn btn-primary btn-sm">
            + Add Member
          </button>
        </div>

        <Table
          columns={memberColumns}
          data={members}
          keyField="userId"
          emptyMessage="No members currently assigned to this store."
        />
      </div>

      {/* Add Member Modal */}
      <Modal
        isOpen={addMemberModalOpen}
        onClose={() => setAddMemberModalOpen(false)}
        title={`Add Member to ${store.storeName}`}
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleAddMemberSubmit} noValidate>
          <Field
            label="Select User"
            name="userId"
            as="select"
            value={addMemberForm.userId}
            onChange={(e) => handleUserSelectChange(e.target.value)}
            required
            disabled={modalLoading}
          >
            <option value="">-- Choose a user --</option>
            {assignableUsers.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName} ({u.email}) - {formatUserType(u.userType)}
              </option>
            ))}
          </Field>

          {selectedUserForAdd?.userType === 'CLIENT' ? (
            <Field
              label="Store Role"
              name="storeRole"
              as="select"
              value={addMemberForm.storeRole}
              onChange={(e) => setAddMemberForm({ ...addMemberForm, storeRole: e.target.value })}
              required
              helpText="A store may have at most one OWNER. Other client users are PARTNERs."
              options={[
                { value: 'PARTNER', label: 'PARTNER' },
                { value: 'OWNER', label: 'OWNER' },
              ]}
              disabled={modalLoading}
            />
          ) : selectedUserForAdd?.userType === 'STAFF' ? (
            <Field
              label="Store Role"
              name="staffRole"
              value="STAFF"
              disabled
              helpText="HOR Staff members are assigned the STAFF role."
            />
          ) : (
            <Field
              label="Store Role"
              name="rolePlaceholder"
              value="Select a user first"
              disabled
            />
          )}

          <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
            <button
              type="button"
              onClick={() => setAddMemberModalOpen(false)}
              className="btn btn-secondary"
              disabled={modalLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={modalLoading || !addMemberForm.userId}
            >
              {modalLoading ? 'Adding Member...' : 'Add Member'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Change Role Modal */}
      <Modal
        isOpen={Boolean(changeRoleTarget)}
        onClose={() => setChangeRoleTarget(null)}
        title={`Change Role: ${changeRoleTarget?.fullName || ''}`}
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
              {modalLoading ? 'Updating Role...' : 'Update Role'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Remove Member Confirmation Modal */}
      <Modal
        isOpen={Boolean(removeTarget)}
        onClose={() => setRemoveTarget(null)}
        title="Remove Member from Store"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          Are you sure you want to remove <strong>{removeTarget?.fullName}</strong> ({removeTarget?.storeRole}) from{' '}
          <strong>{store.storeName}</strong>?
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

      {/* Edit Store Modal */}
      <Modal
        isOpen={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title={`Edit Store: ${store.storeName}`}
        maxWidth="720px"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleEditSubmit} noValidate>
          <div className="form-grid">
            <Field
              label="Store Code"
              name="codeDisplay"
              value={store.storeCode}
              disabled
              helpText="Store code is fixed after creation."
            />

            <Field
              label="POS Vendor"
              name="vendorDisplay"
              value={store.posVendor}
              disabled
              helpText="POS vendor is fixed after creation."
            />

            <Field
              label="Store Name"
              name="storeName"
              value={editForm.storeName}
              onChange={(e) => setEditForm({ ...editForm, storeName: e.target.value })}
              required
              disabled={modalLoading}
            />

            <Field
              label="S3 Folder Path"
              name="s3Folder"
              value={editForm.s3Folder}
              onChange={(e) => setEditForm({ ...editForm, s3Folder: e.target.value })}
              required
              helpText="Must end with / and contain store code."
              disabled={modalLoading}
            />

            <Field
              label="Address Line 1"
              name="addressLine1"
              value={editForm.addressLine1}
              onChange={(e) => setEditForm({ ...editForm, addressLine1: e.target.value })}
              required
              disabled={modalLoading}
            />

            <Field
              label="Address Line 2"
              name="addressLine2"
              value={editForm.addressLine2}
              onChange={(e) => setEditForm({ ...editForm, addressLine2: e.target.value })}
              placeholder="Suite..."
              helpText="Leave empty to remove."
              disabled={modalLoading}
            />

            <Field
              label="City"
              name="city"
              value={editForm.city}
              onChange={(e) => setEditForm({ ...editForm, city: e.target.value })}
              required
              disabled={modalLoading}
            />

            <Field
              label="State (2 letters)"
              name="stateCode"
              value={editForm.stateCode}
              onChange={(e) => setEditForm({ ...editForm, stateCode: e.target.value.toUpperCase() })}
              required
              maxLength={2}
              disabled={modalLoading}
            />

            <Field
              label="Postal Code (ZIP)"
              name="postalCode"
              value={editForm.postalCode}
              onChange={(e) => setEditForm({ ...editForm, postalCode: e.target.value })}
              required
              disabled={modalLoading}
            />

            <Field
              label="Country Code"
              name="countryCode"
              value={editForm.countryCode}
              onChange={(e) => setEditForm({ ...editForm, countryCode: e.target.value.toUpperCase() })}
              maxLength={2}
              disabled={modalLoading}
            />

            <Field
              label="Contact Phone"
              name="contactNumber"
              type="tel"
              value={editForm.contactNumber}
              onChange={(e) => setEditForm({ ...editForm, contactNumber: e.target.value })}
              helpText="Leave empty to remove."
              disabled={modalLoading}
            />

            <Field
              label="Timezone"
              name="timezone"
              as="select"
              value={editForm.timezone}
              onChange={(e) => setEditForm({ ...editForm, timezone: e.target.value })}
              required
              options={COMMON_TIMEZONES}
              disabled={modalLoading}
            />
          </div>

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
              disabled={
                modalLoading ||
                !editForm.storeName ||
                !editForm.s3Folder ||
                !editForm.addressLine1 ||
                !editForm.city ||
                !editForm.stateCode ||
                !editForm.postalCode
              }
            >
              {modalLoading ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
