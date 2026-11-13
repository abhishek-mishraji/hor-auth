# auth-console (Frontend) Implementation Plan

## 1. Location and Structure

- **Path:** `web/auth-console/` (in this repository)
- **Stack:** React 19.2, JavaScript (JSX, no TypeScript), Vite 8, react-router 7.x, plain CSS (tokens, dark mode), oxlint
- **Entrypoint:** `src/main.jsx`, Vite server port 5180. Proxy `/api/auth/` to backend at localhost:8091.

## 2. Directory Structure (Recommended)

```
web/auth-console/
├── src/
│   ├── api.js        # All API calls and refresh-logic
│   ├── main.jsx      # Entry
│   ├── App.jsx       # Router setup
│   ├── session/SessionProvider.jsx
│   ├── hooks/
│   │   ├── useLoad.js
│   │   └── useAction.js
│   ├── ui/           # Fields, Table, Modal, StatusBadge, ErrorBanner, Layout, Pager, format.js
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── AccountPage.jsx
│   │   ├── AdminsPage.jsx
│   │   ├── UsersPage.jsx
│   │   ├── UserDetailPage.jsx
│   │   ├── StoresPage.jsx
│   │   ├── StoreDetailPage.jsx
│   │   ├── AuditPage.jsx
│   │   └── NotFoundPage.jsx
├── package.json
├── vite.config.js
├── .env.development
├── .env.production
├── oxlint config etc.
└── ...
```

## 3. Design and Development Principles

> Imagine you are a senior React engineer with 100 years of experience; insist on these principles:

### **1. Source of Truth and API Contracts**
- Consume backend APIs exactly as defined in [`../docs/AUTH_SPEC.md`](./AUTH_SPEC.md) section 8. All error codes, Problem+JSON, field errors must be handled and shown precisely as described.
- Treat backend as canonical source: do not invent or expand field semantics on the frontend.

### **2. Security and Session**
- Implement cookie-based session and refresh as described in the backend spec.
- Auto-refresh on 401 as described; use navigator.locks to serialize refresh in all tabs.
- DO NOT log/print/request/store any credentials, tokens, cookies, or sensitive values in JS, logging, or dev tools. Console logs must not leak credentials.
- Use only CSRF headers and cookie authentication.

### **3. UI/UX**
- Accessible-by-default: semantic HTML, keyboard navigation, aria-* on status/error as needs.
- Strict error and field error surfacing for all ProblemDetails (+JSON) errors.
- CSS with variable tokens and dark/light themes; use `prefers-color-scheme`.
- Forms: validate required/optional and submit only valid fields; show all backend errors as surfaced by API.
- All routing must be SPA (react-router), performant, and restoration-friendly.

### **4. Code Quality**
- No TypeScript at all (see spec). JS/JSX only; all component props/types documented with JSDoc.
- All code must pass `npm run lint` (oxlint, strict configuration), and `npm run build`.
- Small, composable components. Side effects and fetches outside render. Use effect and memo hooks deliberately.
- Page containers do all fetch and route state; dumb/pure presentational components receive only needed data and callbacks.
- Centralize API fetch/refresh state and all session/cookie logic in `api.js` and `SessionProvider.jsx`.

### **5. Testing and Hand-off**
- The application should require only `npm install`, correct Vite setup, and the running backend. Lint and build must pass out-of-the-box.
- Structure UI so more designers/engineers can contribute to features/pages/components with minimal context switch.

### **6. Explicit Out-of-Scope**
- No TypeScript, no ESLint/Prettier configs, no CSS preprocessor, no UI frameworks; only CSS and CSS variables.
- Do not implement emails, MFA, social login, analytics, or any backend logic frontend-side.
- No direct JWT/cookie manipulation outside fetch/refresh.

## 4. Startup Sequence (Minimal)

1. Clone or pull this repository.
2. From `web/auth-console/`, `npm install`.
3. Run `npm run dev` (Vite, proxies backend `/api/auth/`).
4. All screens, errors, flows, and forms must follow backend API, error field mapping, and security logic exactly as in [`AUTH_SPEC.md`](./AUTH_SPEC.md).

---

**Give this plan, along with `AUTH_SPEC.md`, to any experienced frontend engineer or AI. It will be able to deliver exactly-matching UI with minimal ambiguity or spec questions.**
