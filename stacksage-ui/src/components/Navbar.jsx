import { useEffect, useState } from 'preact/hooks';

function ThemeToggle() {
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark');

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    localStorage.setItem('theme', dark ? 'dark' : 'light');
  }, [dark]);

  return (
    <button
      onClick={() => setDark((d) => !d)}
      class="p-2 rounded-lg hover:bg-indigo-500/20 transition-colors"
      aria-label="Toggle theme"
    >
      {dark ? '☀️' : '🌙'}
    </button>
  );
}

export function Navbar({ connected }) {
  return (
    <nav class="bg-indigo-600 dark:bg-indigo-900 text-white shadow-lg">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between">
        <a href="/" class="flex items-center gap-2 font-bold text-lg tracking-tight hover:opacity-90">
          <span class="text-2xl">🔍</span>
          <span>StackSage</span>
        </a>
        <div class="flex items-center gap-1 sm:gap-3 text-sm">
          <a href="/upload" class="px-3 py-1.5 rounded-lg hover:bg-indigo-500/30 transition-colors">Upload</a>
          <a href="/history" class="px-3 py-1.5 rounded-lg hover:bg-indigo-500/30 transition-colors">History</a>
          <div class="flex items-center gap-1.5 px-2 py-1 rounded-md bg-indigo-500/20 text-xs"
               title={connected ? 'Live connection active' : 'Reconnecting...'}>
            <span class={`relative flex h-2 w-2`}>
              {connected && <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />}
              <span class={`relative inline-flex rounded-full h-2 w-2 ${connected ? 'bg-green-400' : 'bg-gray-400'}`} />
            </span>
            <span class="hidden sm:inline">{connected ? 'Live' : 'Offline'}</span>
          </div>
          <ThemeToggle />
        </div>
      </div>
    </nav>
  );
}
