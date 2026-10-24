// Auth0 owns token persistence and expiry handling; do not store a separate login flag.
export function createAuthOptions(env, origin) {
  return {
    domain: env.VITE_AUTH0_DOMAIN,
    clientId: env.VITE_AUTH0_CLIENT_ID,
    authorizationParams: {
      redirect_uri: origin,
      audience: env.VITE_AUTH0_AUDIENCE,
      scope: 'openid profile email offline_access'
    },
    cacheLocation: 'localstorage',
    useRefreshTokens: true,
    // Also supports an existing SSO session before the first rotating token is issued.
    useRefreshTokensFallback: true
  };
}

export function sessionStatus(configured, loading, authenticated) {
  if (!configured) return 'signed-out';
  if (loading) return 'restoring';
  return authenticated ? 'signed-in' : 'signed-out';
}
