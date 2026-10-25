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
    const response = await fetchImpl(url, {...options, signal:controller.signal, headers:{Accept:'application/json',...headers,...options.headers}});
    if (response.status === 204) return null;
    // Authentication failures often have an empty or HTML body. Preserve the
    // HTTP meaning before parsing so a proxy/login page cannot hide the cause.
    if (response.status === 401) throw new Error('Your session could not be verified. Sign out and sign in again.');
    if (response.status === 403) throw new Error('Access is unavailable. Check the backend live profile.');
    const raw = await response.text();
    let data;
    try { data = raw.trim() ? JSON.parse(raw) : null; }
    catch {
      if (!response.ok) throw new Error(`The API server returned HTTP ${response.status} instead of JSON. Check that the Spring Boot backend is running and the /api proxy points to it.`);
      if (/^\s*</.test(raw)) throw new Error('The API request returned a web page instead of data. Check VITE_API_BASE_URL and the /api proxy configuration, then reload.');
      throw new Error('The server returned an unreadable response. Please retry.');
    }
    if (!response.ok) throw new Error(typeof data?.message === 'string' ? data.message : `The API request failed (HTTP ${response.status}). Please retry.`);
    if (data === null && options.method?.toUpperCase() === 'DELETE') return null;
    if (data === null) throw new Error('The API server returned an empty response. Please retry.');
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
