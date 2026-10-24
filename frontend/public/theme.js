// Apply the saved appearance before the first paint.
(() => {
  let theme;
  try { theme = localStorage.getItem('lionpathway-theme'); } catch {}
  if (theme !== 'light' && theme !== 'dark') {
    theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#091525' : '#ffffff');
})();
