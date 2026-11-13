import React from 'react';
import { formatAccountRole, formatStoreRole } from './format.js';

/**
 * StatusBadge component displays status, role, or locked indicators.
 * @param {object} props
 * @param {string} [props.status] - 'ACTIVE' | 'DISABLED' | 'INACTIVE'
 * @param {string} [props.role] - Account role: 'PLATFORM_ADMIN' | 'HOR_STAFF' | 'CLIENT_USER'
 * @param {string} [props.storeRole] - Store assignment role: 'OWNER' | 'PARTNER' | 'STAFF'
 * @param {boolean} [props.locked] - If account is locked
 * @param {string} [props.className]
 */
export function StatusBadge({ status, role, storeRole, locked, className = '' }) {
  if (locked) {
    return (
      <span className={`badge badge-locked ${className}`} role="status">
        Locked
      </span>
    );
  }

  if (status) {
    const isSuccess = status === 'ACTIVE';
    const badgeClass = isSuccess ? 'badge-active' : 'badge-disabled';
    return (
      <span className={`badge ${badgeClass} ${className}`} role="status">
        {status}
      </span>
    );
  }

  if (role) {
    let roleClass = 'badge-role-client';
    if (role === 'PLATFORM_ADMIN') roleClass = 'badge-role-admin';
    if (role === 'HOR_STAFF') roleClass = 'badge-role-staff';
    return (
      <span className={`badge ${roleClass} ${className}`} role="status">
        {formatAccountRole(role)}
      </span>
    );
  }

  if (storeRole) {
    let storeRoleClass = 'badge-store-partner';
    if (storeRole === 'OWNER') storeRoleClass = 'badge-store-owner';
    if (storeRole === 'STAFF') storeRoleClass = 'badge-store-staff';
    return (
      <span className={`badge ${storeRoleClass} ${className}`} role="status">
        {formatStoreRole(storeRole)}
      </span>
    );
  }

  return null;
}
