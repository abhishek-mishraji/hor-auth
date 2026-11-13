import React, { useState, useEffect, useCallback } from 'react';
import { getAuditLog } from '../api.js';
import { Table } from '../ui/Table.jsx';
import { Pager } from '../ui/Pager.jsx';
import { Modal } from '../ui/Modal.jsx';
import { ErrorBanner } from '../ui/ErrorBanner.jsx';
import { formatDateTime, formatAuditAction } from '../ui/format.js';

const AUDIT_ACTIONS = [
  'ADMIN_CREATED',
  'ADMIN_UPDATED',
  'ADMIN_DISABLED',
  'ADMIN_ENABLED',
  'ADMIN_PASSWORD_RESET',
  'ADMIN_UNLOCKED',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_DISABLED',
  'USER_ENABLED',
  'USER_PASSWORD_RESET',
  'USER_UNLOCKED',
  'STORE_CREATED',
  'STORE_UPDATED',
  'STORE_DEACTIVATED',
  'STORE_ACTIVATED',
  'STORE_ASSIGNED',
  'STORE_ASSIGNMENT_CHANGED',
  'STORE_UNASSIGNED',
];

const TARGET_TYPES = ['ADMIN', 'USER', 'STORE', 'ASSIGNMENT'];

/**
 * AuditPage allows platform admins to inspect append-only security and operational audit logs.
 */
export function AuditPage() {
  // Filter state
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [targetTypeFilter, setTargetTypeFilter] = useState('');
  const [targetIdFilter, setTargetIdFilter] = useState('');

  // Pagination
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(50);

  // Data & loading
  const [data, setData] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Detail viewer modal
  const [viewDetailRow, setViewDetailRow] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Format dates to ISO strings if provided
      let fromIso = undefined;
      let toIso = undefined;
      if (fromDate) {
        fromIso = new Date(fromDate).toISOString();
      }
      if (toDate) {
        // End of the day or exact ISO
        toIso = new Date(toDate).toISOString();
      }

      const res = await getAuditLog({
        from: fromIso,
        to: toIso,
        action: actionFilter || undefined,
        targetType: targetTypeFilter || undefined,
        targetId: targetIdFilter || undefined,
        limit,
        offset,
      });
      setData(res);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate, actionFilter, targetTypeFilter, targetIdFilter, limit, offset]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleResetFilters = () => {
    setFromDate('');
    setToDate('');
    setActionFilter('');
    setTargetTypeFilter('');
    setTargetIdFilter('');
    setOffset(0);
  };

  const columns = [
    {
      key: 'createdAt',
      header: 'Timestamp',
      render: (row) => (
        <span className="cell-mono" style={{ fontSize: '0.8125rem' }}>
          {formatDateTime(row.createdAt)}
        </span>
      ),
    },
    {
      key: 'actor',
      header: 'Actor Admin',
      render: (row) => (
        <div>
          <span style={{ fontWeight: 600 }}>{row.actorName}</span>
          <span className="cell-muted" style={{ fontSize: '0.75rem', marginLeft: '6px' }}>
            (ID: {row.actorAdminId})
          </span>
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      render: (row) => {
        let badgeColor = 'var(--bg-surface-raised)';
        if (row.action.includes('CREATED') || row.action.includes('ACTIVATED') || row.action.includes('ASSIGNED')) {
          badgeColor = 'var(--success-bg)';
        } else if (row.action.includes('DISABLED') || row.action.includes('DEACTIVATED') || row.action.includes('UNASSIGNED')) {
          badgeColor = 'var(--danger-bg)';
        } else if (row.action.includes('RESET') || row.action.includes('UNLOCKED')) {
          badgeColor = 'var(--warning-bg)';
        }

        return (
          <span className="badge" style={{ backgroundColor: badgeColor, border: '1px solid var(--border-subtle)' }}>
            {formatAuditAction(row.action)}
          </span>
        );
      },
    },
    {
      key: 'target',
      header: 'Target',
      render: (row) => (
        <div>
          <span style={{ fontWeight: 600 }}>{row.targetType}: </span>
          <span>{row.targetLabel || row.targetId}</span>
        </div>
      ),
    },
    {
      key: 'detail',
      header: 'Change Detail',
      render: (row) => {
        const detailKeys = Object.keys(row.detail || {});
        if (detailKeys.length === 0) {
          return <span className="cell-muted">—</span>;
        }

        return (
          <button
            type="button"
            onClick={() => setViewDetailRow(row)}
            className="btn btn-secondary btn-sm"
            title="Inspect audit detail payload"
          >
            View Details ({detailKeys.length})
          </button>
        );
      },
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1>Security & Operational Audit Log</h1>
          <p className="page-description">Immutable append-only record of administrative actions, account changes, and store assignments</p>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="filter-bar">
        <label style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          From Date
          <input
            type="date"
            value={fromDate}
            onChange={(e) => {
              setFromDate(e.target.value);
              setOffset(0);
            }}
            className="filter-select"
            aria-label="From date"
          />
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          To Date
          <input
            type="date"
            value={toDate}
            onChange={(e) => {
              setToDate(e.target.value);
              setOffset(0);
            }}
            className="filter-select"
            aria-label="To date"
          />
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Action
          <select
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setOffset(0);
            }}
            className="filter-select"
            aria-label="Filter by action"
          >
            <option value="">All Actions</option>
            {AUDIT_ACTIONS.map((act) => (
              <option key={act} value={act}>
                {formatAuditAction(act)}
              </option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Target Type
          <select
            value={targetTypeFilter}
            onChange={(e) => {
              setTargetTypeFilter(e.target.value);
              setOffset(0);
            }}
            className="filter-select"
            aria-label="Filter by target type"
          >
            <option value="">All Targets</option>
            {TARGET_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Target ID
          <input
            type="text"
            value={targetIdFilter}
            onChange={(e) => {
              setTargetIdFilter(e.target.value);
              setOffset(0);
            }}
            placeholder="Target ID..."
            className="filter-select"
            style={{ width: '100px' }}
            aria-label="Filter by target ID"
          />
        </label>

        <div style={{ display: 'flex', alignItems: 'flex-end', marginLeft: 'auto' }}>
          <button
            type="button"
            onClick={handleResetFilters}
            className="btn btn-secondary btn-sm"
          >
            Reset Filters
          </button>
        </div>
      </div>

      <Table
        columns={columns}
        data={data.items}
        loading={loading}
        emptyMessage="No audit log entries found for the selected criteria."
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

      {/* Detail Viewer Modal */}
      <Modal
        isOpen={Boolean(viewDetailRow)}
        onClose={() => setViewDetailRow(null)}
        title={`Audit Event Details: ${viewDetailRow ? formatAuditAction(viewDetailRow.action) : ''}`}
        maxWidth="640px"
      >
        {viewDetailRow && (
          <div>
            <div className="detail-grid" style={{ marginBottom: '1.25rem' }}>
              <div className="detail-item">
                <span className="detail-label">Event Timestamp</span>
                <span className="detail-value cell-mono">{formatDateTime(viewDetailRow.createdAt)}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Actor Admin</span>
                <span className="detail-value">{viewDetailRow.actorName} (ID: {viewDetailRow.actorAdminId})</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Action</span>
                <span className="detail-value">{viewDetailRow.action}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Target</span>
                <span className="detail-value">{viewDetailRow.targetType} #{viewDetailRow.targetId} ({viewDetailRow.targetLabel || '—'})</span>
              </div>
            </div>

            <div style={{ marginBottom: '0.5rem', fontWeight: 600, fontSize: '0.875rem' }}>
              Recorded Attribute Changes:
            </div>
            <pre
              style={{
                backgroundColor: 'var(--bg-surface-raised)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '1rem',
                fontSize: '0.8125rem',
                fontFamily: 'var(--font-mono)',
                overflowX: 'auto',
                color: 'var(--text-main)',
              }}
            >
              {JSON.stringify(viewDetailRow.detail, null, 2)}
            </pre>
          </div>
        )}

        <div className="modal-footer" style={{ margin: '1.5rem -1.5rem -1.5rem -1.5rem' }}>
          <button
            type="button"
            onClick={() => setViewDetailRow(null)}
            className="btn btn-secondary"
          >
            Close
          </button>
        </div>
      </Modal>
    </div>
  );
}
