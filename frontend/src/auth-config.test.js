import test from 'node:test';
import assert from 'node:assert/strict';
import { LocalStorageCache } from '@auth0/auth0-vue';
import { createAuthOptions, sessionStatus } from './auth-config.js';

test('restoring session never renders a signed-out state or an authenticated action', () => {
  assert.equal(sessionStatus(true, true, false), 'restoring');
  assert.equal(sessionStatus(true, true, true), 'restoring');
  assert.equal(sessionStatus(true, false, true), 'signed-in');
  assert.equal(sessionStatus(true, false, false), 'signed-out');
  assert.equal(sessionStatus(false, false, false), 'signed-out');
});

test('SDK persistent cache survives a new cache instance and logout removes it', () => {
  const env={VITE_AUTH0_DOMAIN:'example.auth0.com',VITE_AUTH0_CLIENT_ID:'test-client',VITE_AUTH0_AUDIENCE:'https://example-api'};
  const options=createAuthOptions(env,'http://localhost:5173');
  assert.equal(options.cacheLocation,'localstorage');
  assert.equal(options.useRefreshTokens,true);
  assert.ok(options.authorizationParams.scope.split(' ').includes('offline_access'));
  const values=new Map();
  const storage={setItem:(k,v)=>values.set(k,v),getItem:k=>values.get(k)??null,removeItem:k=>values.delete(k)};
  const oldWindow=globalThis.window, oldStorage=globalThis.localStorage;
  globalThis.window={localStorage:storage};globalThis.localStorage=storage;
  try {
    const key='@@auth0spajs@@::test-client::test-audience::openid';
    const cached={body:{access_token:'synthetic-test-token'},expiresAt:9999999999};
    new LocalStorageCache().set(key,cached);
    const reloadedCache=new LocalStorageCache();
    assert.deepEqual(reloadedCache.get(key),cached);
    reloadedCache.remove(key);
    assert.equal(new LocalStorageCache().get(key),undefined);
  } finally {
    if(oldWindow===undefined)delete globalThis.window;else globalThis.window=oldWindow;
    if(oldStorage===undefined)delete globalThis.localStorage;else globalThis.localStorage=oldStorage;
  }
});
