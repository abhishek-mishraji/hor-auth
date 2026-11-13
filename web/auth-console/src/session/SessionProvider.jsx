import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { getMe, logout as apiLogout } from '../api.js';

/**
 * @typedef {object} StoreAssignment
 * @property {number} storeId
 * @property {string} storeCode
 * @property {string} storeName
 * @property {string} storeRole - 'OWNER' | 'PARTNER' | 'STAFF'
 */

/**
 * @typedef {object} MeUser
 * @property {string} accountType - 'ADMIN' | 'STORE_USER'
 * @property {number} id
 * @property {string} email
 * @property {string} fullName
 * @property {string} role - 'PLATFORM_ADMIN' | 'HOR_STAFF' | 'CLIENT_USER'
 * @property {boolean} allStores
 * @property {Array<StoreAssignment>} stores
 * @property {string} passwordChangedAt
 * @property {string} [lastLoginAt]
 */

/**
 * @typedef {object} SessionContextType
 * @property {MeUser|null} me - Currently authenticated user
 * @property {boolean} ready - Initial check completed
 * @property {boolean} isAuthenticated - Whether a valid session exists
 * @property {() => Promise<void>} refreshMe - Refetch /me
 * @property {(newMe: MeUser|null) => void} setMe - Set current me object
 * @property {() => Promise<void>} logout - Sign out
 */

const SessionContext = createContext(/** @type {SessionContextType|null} */ (null));

/**
 * SessionProvider manages the authenticated user session via /me.
 * @param {object} props
 * @param {React.ReactNode} props.children
 */
export function SessionProvider({ children }) {
  const [me, setMe] = useState(null);
  const [ready, setReady] = useState(false);

  const refreshMe = useCallback(async () => {
    try {
      const user = await getMe();
      setMe(user);
    } catch {
      // 401 / 403 or network failure -> not authenticated
      setMe(null);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    refreshMe();
  }, [refreshMe]);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } catch {
      // Ignore logout errors
    } finally {
      setMe(null);
      window.location.assign('/login');
    }
  }, []);

  const value = {
    me,
    ready,
    isAuthenticated: Boolean(me),
    refreshMe,
    setMe,
    logout,
  };

  return (
    <SessionContext.Provider value={value}>
      {children}
    </SessionContext.Provider>
  );
}

/**
 * Hook to consume session state.
 * @returns {SessionContextType}
 */
export function useSession() {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error('useSession must be used within a SessionProvider');
  }
  return context;
}
