/**
 * @file api.js
 * Central API client for HOR Auth Console.
 * Implements the contract defined in AUTH_SPEC.md §8 and §11.3:
 * - Credentials: same-origin cookie transport.
 * - CSRF: X-HOR-Request: 1 header on all mutation/fetch calls.
 * - Automatic 401 refresh serialized across tabs using navigator.locks.
 * - Never prints or leaks credentials or tokens.
 */

/**
 * Custom error class capturing RFC 9457 Problem+JSON details.
 */
export class ApiError extends Error {
  /**
   * @param {number} status - HTTP status code
   * @param {string} code - Application error code (e.g. VALIDATION_FAILED, BAD_CREDENTIALS)
   * @param {string} message - Human-readable error message
   * @param {Array<{field: string, message: string}>} [fieldErrors] - Field-level validation errors
   * @param {object} [raw] - Raw problem JSON body
   */
  constructor(status, code, message, fieldErrors = [], raw = null) {
    super(message || code || `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code || '';
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : [];
    this.raw = raw;
  }
}

const CSRF_HEADER = 'X-HOR-Request';
const REFRESH_LOCK_NAME = 'hor-refresh';

/**
 * Parses response problem JSON or falls back to standard text.
 * @param {Response} res
 * @returns {Promise<ApiError>}
 */
async function parseErrorResponse(res) {
  let body = null;
  try {
    const text = await res.text();
    if (text) {
      body = JSON.parse(text);
    }
  } catch {
    // Non-JSON error body
  }

  const status = res.status;
  const code = body?.code || (status === 401 ? 'UNAUTHENTICATED' : status === 403 ? 'FORBIDDEN' : status === 404 ? 'NOT_FOUND' : 'ERROR');
  const message = body?.detail || body?.title || res.statusText || 'Unknown error';
  const fieldErrors = body?.errors || [];

  return new ApiError(status, code, message, fieldErrors, body);
}

/**
 * Internal helper to execute a fetch request.
 * @param {string} path
 * @param {RequestInit} [options]
 * @returns {Promise<Response>}
 */
async function doFetch(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set(CSRF_HEADER, '1');

  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  return fetch(path, {
    ...options,
    credentials: 'same-origin',
    headers,
  });
}

/**
 * Executes a refresh with navigator.locks synchronization.
 * If refresh fails with 401, redirects to login.
 * @param {() => Promise<Response>} retryOriginalRequest
 * @returns {Promise<Response>}
 */
async function handle401WithLock(retryOriginalRequest) {
  const performRefresh = async () => {
    // 1. Inside lock, retry original request first (another tab may have already refreshed)
    const probe = await retryOriginalRequest();
    if (probe.status !== 401) {
      return probe;
    }

    // 2. Call POST /api/auth/refresh
    const refreshRes = await doFetch('/api/auth/refresh', {
      method: 'POST',
    });

    if (refreshRes.status === 204 || refreshRes.ok) {
      // 3. Retry original request again after successful refresh
      return retryOriginalRequest();
    }

    if (refreshRes.status === 401) {
      // Session has ended. Only redirect to login if not already on the login page
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        const currentUrl = window.location.href;
        if (!currentUrl.includes('/login')) {
          const redirectUrl = `/login?return=${encodeURIComponent(currentUrl)}`;
          window.location.assign(redirectUrl);
        } else {
          window.location.assign('/login');
        }
      }
      throw await parseErrorResponse(refreshRes);
    }

    throw await parseErrorResponse(refreshRes);
  };

  if (typeof navigator !== 'undefined' && navigator.locks && typeof navigator.locks.request === 'function') {
    return navigator.locks.request(REFRESH_LOCK_NAME, performRefresh);
  }

  return performRefresh();
}

/**
 * Main request wrapper with error parsing and transparent 401 retry.
 * @param {string} path
 * @param {RequestInit} [options]
 * @param {boolean} [allowAutoRefresh=true]
 * @returns {Promise<any>}
 */
async function request(path, options = {}, allowAutoRefresh = true) {
  const isAuthEndpoint = path.startsWith('/api/auth/login') ||
    path.startsWith('/api/auth/refresh') ||
    path.startsWith('/api/auth/logout') ||
    path.startsWith('/api/auth/me/password');

  const execute = () => doFetch(path, options);

  let res = await execute();

  if (res.status === 401 && allowAutoRefresh && !isAuthEndpoint) {
    res = await handle401WithLock(execute);
  }

  if (!res.ok) {
    throw await parseErrorResponse(res);
  }

  if (res.status === 204) {
    return null;
  }

  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return res.json();
  }

  return res.text();
}

/**
 * Builds URL query string from params object.
 * @param {Record<string, any>} params
 * @returns {string}
 */
function toQueryString(params = {}) {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value));
    }
  }
  const str = searchParams.toString();
  return str ? `?${str}` : '';
}

/* ==========================================================================
   Session API (AUTH_SPEC.md §8.1)
   ========================================================================== */

/**
 * Logs in with email and password.
 * @param {{email: string, password: string}} credentials
 * @returns {Promise<object>} Returns Me object
 */
export async function login({ email, password }) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  }, false);
}

/**
 * Rotates the refresh token and receives a new access token cookie.
 * @returns {Promise<void>}
 */
export async function refresh() {
  return request('/api/auth/refresh', {
    method: 'POST',
  }, false);
}

/**
 * Logs out and revokes the active refresh token.
 * @returns {Promise<void>}
 */
export async function logout() {
  return request('/api/auth/logout', {
    method: 'POST',
  }, false);
}

/**
 * Gets currently logged-in account details.
 * @returns {Promise<object>} Me object
 */
export async function getMe() {
  return request('/api/auth/me', {
    method: 'GET',
  });
}

/**
 * Changes current user's password.
 * @param {{currentPassword: string, newPassword: string}} payload
 * @returns {Promise<void>}
 */
export async function changeMyPassword({ currentPassword, newPassword }) {
  return request('/api/auth/me/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  }, false);
}

/* ==========================================================================
   Platform Admins API (AUTH_SPEC.md §8.2)
   ========================================================================== */

/**
 * Lists platform admins with pagination and filtering.
 * @param {{status?: string, q?: string, limit?: number, offset?: number}} [params]
 * @returns {Promise<{items: Array<object>, total: number, limit: number, offset: number}>}
 */
export async function listAdmins(params = {}) {
  const qs = toQueryString(params);
  return request(`/api/auth/admin/platform-admins${qs}`);
}

/**
 * Creates a new platform admin.
 * @param {{email: string, fullName: string, phoneNumber?: string, password: string}} payload
 * @returns {Promise<object>}
 */
export async function createAdmin(payload) {
  return request('/api/auth/admin/platform-admins', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * Gets details of a platform admin.
 * @param {number|string} id
 * @returns {Promise<object>}
 */
export async function getAdmin(id) {
  return request(`/api/auth/admin/platform-admins/${encodeURIComponent(id)}`);
}

/**
 * Updates a platform admin's profile or status.
 * @param {number|string} id
 * @param {Record<string, any>} fields
 * @returns {Promise<object>}
 */
export async function updateAdmin(id, fields) {
  return request(`/api/auth/admin/platform-admins/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(fields),
  });
}

/**
 * Resets a platform admin's password.
 * @param {number|string} id
 * @param {string} newPassword
 * @returns {Promise<void>}
 */
export async function resetAdminPassword(id, newPassword) {
  return request(`/api/auth/admin/platform-admins/${encodeURIComponent(id)}/password-reset`, {
    method: 'POST',
    body: JSON.stringify({ newPassword }),
  });
}

/**
 * Unlocks a locked platform admin account.
 * @param {number|string} id
 * @returns {Promise<void>}
 */
export async function unlockAdmin(id) {
  return request(`/api/auth/admin/platform-admins/${encodeURIComponent(id)}/unlock`, {
    method: 'POST',
  });
}

/* ==========================================================================
   Store Users API (AUTH_SPEC.md §8.3)
   ========================================================================== */

/**
 * Lists store users with optional filtering.
 * @param {{userType?: string, status?: string, q?: string, limit?: number, offset?: number}} [params]
 * @returns {Promise<{items: Array<object>, total: number, limit: number, offset: number}>}
 */
export async function listUsers(params = {}) {
  const qs = toQueryString(params);
  return request(`/api/auth/admin/users${qs}`);
}

/**
 * Creates a new store user (STAFF or CLIENT).
 * @param {{userType: string, email: string, fullName: string, phoneNumber?: string, address?: string, password: string}} payload
 * @returns {Promise<object>}
 */
export async function createUser(payload) {
  return request('/api/auth/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * Gets store user details and assigned stores.
 * @param {number|string} id
 * @returns {Promise<object>}
 */
export async function getUser(id) {
  return request(`/api/auth/admin/users/${encodeURIComponent(id)}`);
}

/**
 * Updates a store user's profile or status.
 * @param {number|string} id
 * @param {Record<string, any>} fields
 * @returns {Promise<object>}
 */
export async function updateUser(id, fields) {
  return request(`/api/auth/admin/users/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(fields),
  });
}

/**
 * Resets a store user's password.
 * @param {number|string} id
 * @param {string} newPassword
 * @returns {Promise<void>}
 */
export async function resetUserPassword(id, newPassword) {
  return request(`/api/auth/admin/users/${encodeURIComponent(id)}/password-reset`, {
    method: 'POST',
    body: JSON.stringify({ newPassword }),
  });
}

/**
 * Unlocks a locked store user account.
 * @param {number|string} id
 * @returns {Promise<void>}
 */
export async function unlockUser(id) {
  return request(`/api/auth/admin/users/${encodeURIComponent(id)}/unlock`, {
    method: 'POST',
  });
}

/* ==========================================================================
   Stores API (AUTH_SPEC.md §8.4)
   ========================================================================== */

/**
 * Lists stores with filters and pagination.
 * @param {{status?: string, posVendor?: string, q?: string, limit?: number, offset?: number}} [params]
 * @returns {Promise<{items: Array<object>, total: number, limit: number, offset: number}>}
 */
export async function listStores(params = {}) {
  const qs = toQueryString(params);
  return request(`/api/auth/admin/stores${qs}`);
}

/**
 * Creates a new store.
 * @param {object} payload
 * @returns {Promise<object>}
 */
export async function createStore(payload) {
  return request('/api/auth/admin/stores', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * Gets details of a specific store.
 * @param {number|string} id
 * @returns {Promise<object>}
 */
export async function getStore(id) {
  return request(`/api/auth/admin/stores/${encodeURIComponent(id)}`);
}

/**
 * Updates a store. Code and posVendor cannot be updated.
 * @param {number|string} id
 * @param {Record<string, any>} fields
 * @returns {Promise<object>}
 */
export async function updateStore(id, fields) {
  return request(`/api/auth/admin/stores/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(fields),
  });
}

/* ==========================================================================
   Store Assignments API (AUTH_SPEC.md §8.5)
   ========================================================================== */

/**
 * Gets all members assigned to a store.
 * @param {number|string} storeId
 * @returns {Promise<Array<object>>}
 */
export async function getStoreMembers(storeId) {
  return request(`/api/auth/admin/stores/${encodeURIComponent(storeId)}/members`);
}

/**
 * Assigns or updates a member's role on a store.
 * @param {number|string} storeId
 * @param {number|string} userId
 * @param {string} storeRole - OWNER, PARTNER, or STAFF
 * @returns {Promise<any>}
 */
export async function assignStoreMember(storeId, userId, storeRole) {
  return request(`/api/auth/admin/stores/${encodeURIComponent(storeId)}/members/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify({ storeRole }),
  });
}

/**
 * Removes a member assignment from a store.
 * @param {number|string} storeId
 * @param {number|string} userId
 * @returns {Promise<void>}
 */
export async function removeStoreMember(storeId, userId) {
  return request(`/api/auth/admin/stores/${encodeURIComponent(storeId)}/members/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  });
}

/* ==========================================================================
   Audit Log API (AUTH_SPEC.md §8.6)
   ========================================================================== */

/**
 * Queries the audit log with optional filters.
 * @param {{from?: string, to?: string, action?: string, targetType?: string, targetId?: number|string, limit?: number, offset?: number}} [params]
 * @returns {Promise<{items: Array<object>, total: number, limit: number, offset: number}>}
 */
export async function getAuditLog(params = {}) {
  const qs = toQueryString(params);
  return request(`/api/auth/admin/audit-log${qs}`);
}
