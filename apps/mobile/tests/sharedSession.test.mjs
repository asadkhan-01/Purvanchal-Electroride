import assert from 'node:assert/strict';
import test from 'node:test';

process.env.EXPO_PUBLIC_API_URL = 'http://localhost:8080';
const { setLoginCredentials, clearLoginCredentials, enquiryApi, request } =
  await import('../api/enquiries.ts');

test('one in-memory login authorizes requests across API modules', async () => {
  const original = globalThis.fetch;
  const seen = [];
  globalThis.fetch = async (url, options) => {
    seen.push({ url, headers: options.headers });
    return { ok: true, json: async () => ({ username: 'admin', role: 'ADMIN', branch: null }) };
  };
  try {
    setLoginCredentials('admin', 'test-password-123');
    await enquiryApi.me();
    await request('/api/v1/inventory');
    assert.equal(seen.length, 2);
    assert.equal(seen[0].headers.Authorization, 'Basic YWRtaW46dGVzdC1wYXNzd29yZC0xMjM=');
    assert.equal(seen[1].headers.Authorization, seen[0].headers.Authorization);
    assert.equal(seen[1].url, 'http://localhost:8080/api/v1/inventory');
  } finally {
    clearLoginCredentials();
    globalThis.fetch = original;
  }
});

test('sign-out removes authorization headers from subsequent requests', async () => {
  const original = globalThis.fetch;
  let header;
  globalThis.fetch = async (_url, options) => {
    header = options.headers.Authorization;
    return { ok: true, json: async () => [] };
  };
  try {
    setLoginCredentials('admin', 'test-password-123');
    clearLoginCredentials();
    await request('/api/v1/inventory');
    assert.equal(header, undefined);
  } finally {
    clearLoginCredentials();
    globalThis.fetch = original;
  }
});

test('invalid development credentials never replace an existing logged-in session', async () => {
  clearLoginCredentials();
  assert.throws(() => setLoginCredentials('admin', 'nonascii-£'), /ASCII/);
});

test('401 clears credentials and notifies the app session gate', async () => {
  const original = globalThis.fetch;
  const { setUnauthorizedHandler } = await import('../api/enquiries.ts');
  let expired = 0;
  let auth;
  setUnauthorizedHandler(() => { expired++; });
  globalThis.fetch = async (_url, options) => {
    auth = options.headers.Authorization;
    return { ok: false, status: 401, text: async () => 'Unauthorized' };
  };
  try {
    setLoginCredentials('admin', 'test-password-123');
    await assert.rejects(request('/api/v1/inventory'), /API 401/);
    assert.ok(auth?.startsWith('Basic '));
    assert.equal(expired, 1);
    globalThis.fetch = async (_url, options) => {
      auth = options.headers.Authorization;
      return { ok: true, json: async () => [] };
    };
    await request('/api/v1/inventory');
    assert.equal(auth, undefined);
  } finally {
    clearLoginCredentials();
    setUnauthorizedHandler(null);
    globalThis.fetch = original;
  }
});
