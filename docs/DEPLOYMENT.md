# Frontend and backend deployment

The Vue frontend can be served by Vercel. The Spring Boot API needs a running Java/Docker service. `frontend/vite.config.js` proxies `/api` only during local development; it does not create a backend in a Vercel static deployment.

## Direct backend connection

1. Deploy `backend/Dockerfile` with the repository root as the build context. Configure the existing backend variables from `backend/.env.example`, including `SPRING_PROFILES_ACTIVE=live`. Never expose backend secrets through `VITE_` variables. The Dockerfile binds to `0.0.0.0`; other hosting methods must set `SERVER_ADDRESS=0.0.0.0`.
2. Set backend `FRONTEND_ORIGIN=https://www.onlyplans.courses` and restart/redeploy the backend. It must exactly match the frontend origin; CORS does not accept a trailing path.
3. Verify the public backend URL plus `/api/health` returns JSON with `status: ok` and `live: true`. `/api/reports` without a token should return 401, not a hosting-provider 404.
4. In the frontend Vercel project's Production environment, set `VITE_API_BASE_URL` to that public HTTPS backend origin, without a trailing slash or `/api`. The frontend adds `/api/...` itself. Keep `VITE_AUTH0_DOMAIN`, `VITE_AUTH0_CLIENT_ID` and `VITE_AUTH0_AUDIENCE` consistent with the backend.
5. Redeploy the frontend: Vite environment variables are compiled into the JavaScript at build time. Updating a variable alone does not change an existing deployment.
6. Auth0 SPA allowed callback URLs, logout URLs and web origins must include `https://www.onlyplans.courses`. API audience must match the backend's `AUTH0_AUDIENCE`.

Alternatively, configure a production `/api` reverse proxy to the real backend and leave `VITE_API_BASE_URL` empty. Such a proxy must be configured explicitly in the hosting platform; this repository currently has no Vercel API rewrite.

## Diagnosed 2026-10-24

The deployed frontend at `https://www.onlyplans.courses` used an empty API base. Both `/api/health` and `/api/reports` returned Vercel HTTP 404 `NOT_FOUND` text. Sign-in triggered the report-list request; parsing that text as JSON caused the misleading “unreadable response” message. Local API health was healthy, which does not verify the deployed API connection. A public backend origin and frontend redeployment are required to resolve the production routing problem.
