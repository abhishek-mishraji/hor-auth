import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router';
import { listStores, createStore, updateStore } from '../api.js';
import { Table } from '../ui/Table.jsx';
import { StatusBadge } from '../ui/StatusBadge.jsx';
import { Pager } from '../ui/Pager.jsx';
import { Modal } from '../ui/Modal.jsx';
import { Field } from '../ui/Field.jsx';
import { ErrorBanner, SuccessBanner } from '../ui/ErrorBanner.jsx';

/**
 * Common US timezones for convenient selection.
 */
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
 * StoresPage manages convenience stores.
 */
export function StoresPage() {
  // Filters & Pagination
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [vendorFilter, setVendorFilter] = useState('');
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(50);

  // Table Data
  const [data, setData] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(null);
  const [bannerSuccess, setBannerSuccess] = useState('');

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editStore, setEditStore] = useState(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Create Form State
  const [createForm, setCreateForm] = useState({
    storeCode: '',
    storeName: '',
    posVendor: 'VERIFONE',
    s3Folder: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateCode: 'IN',
    postalCode: '',
    countryCode: 'US',
    contactNumber: '',
    timezone: 'America/Indiana/Indianapolis',
  });

  // Edit Form State (storeCode and posVendor are immutable per spec §8.4)
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

  const loadData = useCallback(async () => {
    setLoading(true);
    setListError(null);
    try {
      const res = await listStores({
        status: statusFilter || undefined,
        posVendor: vendorFilter || undefined,
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
  }, [statusFilter, vendorFilter, q, limit, offset]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSearchChange = (e) => {
    setQ(e.target.value);
    setOffset(0);
  };

  const handleStatusFilterChange = (e) => {
    setStatusFilter(e.target.value);
    setOffset(0);
  };

  const handleVendorFilterChange = (e) => {
    setVendorFilter(e.target.value);
    setOffset(0);
  };

  const openCreateModal = () => {
    setCreateForm({
      storeCode: '',
      storeName: '',
      posVendor: 'VERIFONE',
      s3Folder: '',
      addressLine1: '',
      addressLine2: '',
      city: '',
      stateCode: 'IN',
      postalCode: '',
      countryCode: 'US',
      contactNumber: '',
      timezone: 'America/Indiana/Indianapolis',
    });
    setModalError(null);
    setCreateModalOpen(true);
  };

  // Helper to suggest s3 folder when storeCode changes
  const handleStoreCodeChange = (code) => {
    const cleanCode = code.trim();
    setCreateForm((prev) => ({
      ...prev,
      storeCode: cleanCode,
      s3Folder: prev.s3Folder ? prev.s3Folder : `${cleanCode}/${cleanCode}/`,
    }));
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setModalError(null);
    setModalLoading(true);

    try {
      await createStore({
        storeCode: createForm.storeCode.trim(),
        storeName: createForm.storeName.trim(),
        posVendor: createForm.posVendor,
        s3Folder: createForm.s3Folder.trim(),
        addressLine1: createForm.addressLine1.trim(),
        addressLine2: createForm.addressLine2.trim() || undefined,
        city: createForm.city.trim(),
        stateCode: createForm.stateCode.trim().toUpperCase(),
        postalCode: createForm.postalCode.trim(),
        countryCode: createForm.countryCode.trim().toUpperCase() || 'US',
        contactNumber: createForm.contactNumber.trim() || undefined,
        timezone: createForm.timezone.trim(),
      });
      setCreateModalOpen(false);
      setBannerSuccess(`Store "${createForm.storeName}" (${createForm.storeCode}) created successfully.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const openEditModal = (store) => {
    setEditStore(store);
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
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!editStore) return;
    setModalError(null);
    setModalLoading(true);

    try {
      // Empty string removes optional addressLine2 or contactNumber per spec §16
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
      await updateStore(editStore.id || editStore.storeId, payload);
      setEditStore(null);
      setBannerSuccess(`Store "${editForm.storeName}" updated.`);
      loadData();
    } catch (err) {
      setModalError(err);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleStatus = async (store) => {
    const isDeactivating = store.status === 'ACTIVE';
    const targetStatus = isDeactivating ? 'INACTIVE' : 'ACTIVE';
    const storeId = store.id || store.storeId;
    const confirmMessage = isDeactivating
      ? `Deactivate store "${store.storeName}" (${store.storeCode})? Staff and client users will not see reports for this store until reactivated.`
      : `Activate store "${store.storeName}" (${store.storeCode})?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      await updateStore(storeId, { status: targetStatus });
      setBannerSuccess(`Store status changed to ${targetStatus}.`);
      loadData();
    } catch (err) {
      setListError(err);
    }
  };

  const columns = [
    {
      key: 'storeCode',
      header: 'Store Code',
      render: (row) => (
        <Link to={`/stores/${row.id || row.storeId}`} className="cell-mono" style={{ fontWeight: 600 }}>
          {row.storeCode}
        </Link>
      ),
    },
    { key: 'storeName', header: 'Store Name' },
    { key: 'posVendor', header: 'POS Vendor' },
    {
      key: 'ownerName',
      header: 'Owner',
      render: (row) => (
        row.ownerName ? (
          <span style={{ fontWeight: 500 }}>{row.ownerName}</span>
        ) : (
          <span className="badge badge-store-owner" style={{ opacity: 0.85 }}>
            No owner yet
          </span>
        )
      ),
    },
    {
      key: 'memberCount',
      header: 'Members',
      render: (row) => (
        <Link to={`/stores/${row.id || row.storeId}`} title="View store members">
          {row.memberCount ?? 0} {row.memberCount === 1 ? 'member' : 'members'}
        </Link>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div style={{ display: 'inline-flex', gap: '0.375rem', flexWrap: 'wrap' }}>
          <Link to={`/stores/${row.id || row.storeId}`} className="btn btn-secondary btn-sm" title="View details & members">
            Members
          </Link>
          <button
            type="button"
            onClick={() => openEditModal(row)}
            className="btn btn-secondary btn-sm"
            title="Edit store"
          >
            Edit
          </button>
          <button
            type="button"
            onClick={() => handleToggleStatus(row)}
            className={`btn btn-sm ${row.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-secondary'}`}
          >
            {row.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          </button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1>Stores</h1>
          <p className="page-description">Manage convenience store profiles, POS vendors, and member assignments</p>
        </div>
        <div className="page-actions">
          <button type="button" onClick={openCreateModal} className="btn btn-primary">
            + New Store
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
            placeholder="Search by store code or name..."
            className="field-input"
            aria-label="Search stores"
          />
        </div>

        <select
          value={vendorFilter}
          onChange={handleVendorFilterChange}
          className="filter-select"
          aria-label="Filter by POS vendor"
        >
          <option value="">All POS Vendors</option>
          <option value="VERIFONE">Verifone</option>
          <option value="GILBARCO">Gilbarco</option>
        </select>

        <select
          value={statusFilter}
          onChange={handleStatusFilterChange}
          className="filter-select"
          aria-label="Filter by status"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      <Table
        columns={columns}
        data={data.items}
        loading={loading}
        emptyMessage="No stores match your search criteria."
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

      {/* Create Store Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create New Store"
        maxWidth="720px"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleCreateSubmit} noValidate>
          <div className="form-grid">
            <Field
              label="Store Code"
              name="storeCode"
              value={createForm.storeCode}
              onChange={(e) => handleStoreCodeChange(e.target.value)}
              required
              placeholder="8730_IN-47872"
              helpText="Letters, digits, _ or - (fixed after creation)."
              disabled={modalLoading}
            />

            <Field
              label="Store Name"
              name="storeName"
              value={createForm.storeName}
              onChange={(e) => setCreateForm({ ...createForm, storeName: e.target.value })}
              required
              placeholder="Tiki Hut"
              disabled={modalLoading}
            />

            <Field
              label="POS Vendor"
              name="posVendor"
              as="select"
              value={createForm.posVendor}
              onChange={(e) => setCreateForm({ ...createForm, posVendor: e.target.value })}
              required
              helpText="POS vendor is fixed after creation."
              options={[
                { value: 'VERIFONE', label: 'Verifone' },
                { value: 'GILBARCO', label: 'Gilbarco' },
              ]}
              disabled={modalLoading}
            />

            <Field
              label="S3 Folder Path"
              name="s3Folder"
              value={createForm.s3Folder}
              onChange={(e) => setCreateForm({ ...createForm, s3Folder: e.target.value })}
              required
              placeholder="Tiki_8730_IN-47872/Tiki_8730_IN-47872/"
              helpText="Must end with /, not start with /, and contain store code."
              disabled={modalLoading}
            />

            <Field
              label="Address Line 1"
              name="addressLine1"
              value={createForm.addressLine1}
              onChange={(e) => setCreateForm({ ...createForm, addressLine1: e.target.value })}
              required
              placeholder="8730 E US Highway 36"
              disabled={modalLoading}
            />

            <Field
              label="Address Line 2"
              name="addressLine2"
              value={createForm.addressLine2}
              onChange={(e) => setCreateForm({ ...createForm, addressLine2: e.target.value })}
              placeholder="Suite B"
              helpText="Optional suite or unit."
              disabled={modalLoading}
            />

            <Field
              label="City"
              name="city"
              value={createForm.city}
              onChange={(e) => setCreateForm({ ...createForm, city: e.target.value })}
              required
              placeholder="Rockville"
              disabled={modalLoading}
            />

            <Field
              label="State (2 letters)"
              name="stateCode"
              value={createForm.stateCode}
              onChange={(e) => setCreateForm({ ...createForm, stateCode: e.target.value.toUpperCase() })}
              required
              placeholder="IN"
              maxLength={2}
              disabled={modalLoading}
            />

            <Field
              label="Postal Code (ZIP)"
              name="postalCode"
              value={createForm.postalCode}
              onChange={(e) => setCreateForm({ ...createForm, postalCode: e.target.value })}
              required
              placeholder="47872"
              helpText="5 or 9 digit US ZIP code."
              disabled={modalLoading}
            />

            <Field
              label="Country Code"
              name="countryCode"
              value={createForm.countryCode}
              onChange={(e) => setCreateForm({ ...createForm, countryCode: e.target.value.toUpperCase() })}
              placeholder="US"
              maxLength={2}
              disabled={modalLoading}
            />

            <Field
              label="Contact Phone"
              name="contactNumber"
              type="tel"
              value={createForm.contactNumber}
              onChange={(e) => setCreateForm({ ...createForm, contactNumber: e.target.value })}
              placeholder="+1 765-569-1234"
              helpText="Optional store contact number."
              disabled={modalLoading}
            />

            <Field
              label="Timezone"
              name="timezone"
              as="select"
              value={createForm.timezone}
              onChange={(e) => setCreateForm({ ...createForm, timezone: e.target.value })}
              required
              options={COMMON_TIMEZONES}
              disabled={modalLoading}
            />
          </div>

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
              disabled={
                modalLoading ||
                !createForm.storeCode ||
                !createForm.storeName ||
                !createForm.s3Folder ||
                !createForm.addressLine1 ||
                !createForm.city ||
                !createForm.stateCode ||
                !createForm.postalCode
              }
            >
              {modalLoading ? 'Creating Store...' : 'Create Store'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit Store Modal */}
      <Modal
        isOpen={Boolean(editStore)}
        onClose={() => setEditStore(null)}
        title={`Edit Store: ${editStore?.storeName || ''}`}
        maxWidth="720px"
      >
        <ErrorBanner error={modalError} onDismiss={() => setModalError(null)} />
        <form onSubmit={handleEditSubmit} noValidate>
          <div className="form-grid">
            <Field
              label="Store Code"
              name="storeCodeDisplay"
              value={editStore?.storeCode || ''}
              disabled
              helpText="Store code cannot be changed after creation."
            />

            <Field
              label="POS Vendor"
              name="posVendorDisplay"
              value={editStore?.posVendor || ''}
              disabled
              helpText="POS vendor cannot be changed after creation."
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
              onClick={() => setEditStore(null)}
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
