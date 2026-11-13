/**
 * @file format.js
 * Utility formatters for dates, roles, statuses, and error messages.
 */

/**
 * Formats an ISO-8601 timestamp string into a readable date and time.
 * @param {string|null|undefined} isoString
 * @returns {string}
 */
export function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZoneName: 'short',
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Formats an ISO-8601 timestamp string into a readable date only.
 * @param {string|null|undefined} isoString
 * @returns {string}
 */
export function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Converts an account role code to a friendly label.
 * @param {string} role
 * @returns {string}
 */
export function formatAccountRole(role) {
  switch (role) {
    case 'PLATFORM_ADMIN':
      return 'Platform Admin';
    case 'HOR_STAFF':
      return 'HOR Staff';
    case 'CLIENT_USER':
      return 'Client User';
    default:
      return role || '—';
  }
}

/**
 * Converts a store role code to a friendly label.
 * @param {string} role
 * @returns {string}
 */
export function formatStoreRole(role) {
  switch (role) {
    case 'OWNER':
      return 'Owner';
    case 'PARTNER':
      return 'Partner';
    case 'STAFF':
      return 'Staff';
    default:
      return role || '—';
  }
}

/**
 * Converts userType to a readable string.
 * @param {string} type
 * @returns {string}
 */
export function formatUserType(type) {
  switch (type) {
    case 'STAFF':
      return 'Staff';
    case 'CLIENT':
      return 'Client';
    default:
      return type || '—';
  }
}

/**
 * Converts audit action to friendly description.
 * @param {string} action
 * @returns {string}
 */
export function formatAuditAction(action) {
  if (!action) return '—';
  return action
    .split('_')
    .map(word => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Known error code dictionary.
 */
export const ERROR_MESSAGES = {
  BAD_CREDENTIALS: 'The email or password you entered is incorrect.',
  ACCOUNT_DISABLED: 'This account has been disabled. Please contact a platform administrator.',
  ACCOUNT_LOCKED: 'Account is temporarily locked due to 10 consecutive failed login attempts. Please wait 15 minutes or contact an admin to unlock.',
  EMAIL_TAKEN: 'An account with this email address already exists.',
  STORE_CODE_TAKEN: 'A store with this store code already exists.',
  S3_FOLDER_TAKEN: 'A store with this S3 folder path already exists.',
  OWNER_EXISTS: 'This store already has an assigned owner. Reassign or remove the existing owner first.',
  WRONG_PASSWORD: 'The current password you entered is incorrect.',
  CANNOT_DISABLE_SELF: 'You cannot disable your own platform admin account.',
  LAST_ACTIVE_ADMIN: 'Cannot disable or lock the last active platform administrator.',
  STATUS_CHANGE_ALONE: 'Status changes must be performed separately from profile updates.',
  USER_TYPE_FIXED: 'User type is fixed and cannot be changed after creation.',
  STORE_CODE_FIXED: 'Store code is fixed and cannot be modified.',
  POS_VENDOR_FIXED: 'POS vendor is fixed and cannot be modified.',
  INVALID_STORE_ROLE: 'Selected store role is invalid for this user type (clients may only be OWNER or PARTNER; staff may only be STAFF).',
  INVALID_TIMEZONE: 'Invalid timezone. Must be a valid IANA timezone identifier (e.g. America/Indiana/Indianapolis).',
  FORBIDDEN: 'You do not have permission to perform this action.',
  CSRF_REJECTED: 'Security validation failed (CSRF header missing or rejected).',
  NOT_FOUND: 'The requested resource was not found.',
  VALIDATION_FAILED: 'Validation failed. Please review the highlighted fields below.',
  SESSION_ENDED: 'Your session has expired. Please sign in again.',
};

/**
 * Translates an ApiError or Error into a user-friendly message.
 * @param {Error|null} error
 * @returns {string}
 */
export function getFriendlyErrorMessage(error) {
  if (!error) return '';
  if (error.code && ERROR_MESSAGES[error.code]) {
    return ERROR_MESSAGES[error.code];
  }
  return error.message || 'An unexpected error occurred. Please try again.';
}
