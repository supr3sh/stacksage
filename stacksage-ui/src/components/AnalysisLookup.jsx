import { useState } from 'preact/hooks';
import { route } from 'preact-router';

export function AnalysisLookup() {
  const [id, setId] = useState('');

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = id.trim();
    if (trimmed) route(`/analysis/${trimmed}`);
  }

  return (
    <form onSubmit={handleSubmit} class="flex gap-2">
      <input
        type="text"
        value={id}
        onInput={(e) => setId(e.target.value)}
        placeholder="Paste analysis ID..."
        class="flex-1 px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600
               bg-white dark:bg-gray-800 text-sm focus:outline-none focus:ring-2
               focus:ring-indigo-500 placeholder-gray-400"
      />
      <button
        type="submit"
        class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm
               font-medium rounded-lg transition-colors"
      >
        Look Up
      </button>
    </form>
  );
}
