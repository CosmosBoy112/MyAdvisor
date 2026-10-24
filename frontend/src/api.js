// A hard deadline includes authentication, network IO and reading the response body.
export async function apiRequest(url, options = {}, {
  getHeaders, fetchImpl = fetch, timeoutMs = 140000, authTimeoutMs = 20000,
  signal, onStage = () => {}
} = {}) {
  const controller = new AbortController();
  let deadline, authTimer, rejectStop;
  const stopped = new Promise((_, reject) => { rejectStop = reject; });
  const stop = message => { rejectStop(new Error(message)); controller.abort(); };
  const cancel = () => stop('Request cancelled. The server may still finish processing; check My reports before retrying.');
  const work = async () => {
    onStage('auth');
    authTimer = setTimeout(() => stop('Sign-in verification took too long. Sign out and sign in again, then retry.'), authTimeoutMs);
    const headers = await getHeaders();
    clearTimeout(authTimer);
    // A late auth response must never send a cancelled upload.
    if (controller.signal.aborted) return;
    onStage('analysis');
    const response = await fetchImpl(url, {...options, signal:controller.signal, headers:{...headers,...options.headers}});
    if (response.status === 204) return null;
    const raw = await response.text();
    let data = {};
    try { data = raw ? JSON.parse(raw) : {}; }
    catch { throw new Error('The server returned an unreadable response. Please retry.'); }
    if (response.status === 401) throw new Error('Your session could not be verified. Sign out and sign in again.');
    if (response.status === 403) throw new Error('Access is unavailable. Check the backend live profile.');
    if (!response.ok) throw new Error(data.message || 'The request could not be completed.');
    return data;
  };
  deadline = setTimeout(() => stop('The request timed out. Check My reports before retrying; the server may have saved the result.'), timeoutMs);
  signal?.addEventListener('abort', cancel, {once:true});
  try {
    if (signal?.aborted) { cancel(); return await stopped; }
    return await Promise.race([stopped,work()]);
  } catch (e) {
    if (e instanceof TypeError) throw new Error('Cannot reach the analysis server. Check your connection and retry.');
    throw e;
  } finally {
    clearTimeout(deadline);clearTimeout(authTimer);signal?.removeEventListener('abort',cancel);
  }
}
