import { StatusIndicator } from '../components/StatusIndicator.jsx';
import { CopyButton } from '../components/CopyButton.jsx';

export function History({ uploads, clearAll }) {
  if (!uploads.length) {
    return (
      <div class="text-center py-16">
        <div class="text-4xl mb-3">📭</div>
        <h2 class="text-xl font-semibold text-gray-700 dark:text-gray-300">No uploads yet</h2>
        <p class="text-sm text-gray-400 mt-1">Upload a log file to see it here.</p>
        <a href="/upload" class="inline-block mt-4 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg transition-colors">
          Upload a File
        </a>
      </div>
    );
  }

  return (
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold">Upload History</h1>
        <button
          onClick={clearAll}
          class="text-xs text-red-500 hover:text-red-700 transition-colors"
        >
          Clear All
        </button>
      </div>
      <div class="space-y-3">
        {uploads.map((u) => (
          <div key={u.uploadId} class="p-4 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <p class="font-medium truncate">{u.filename}</p>
                <div class="flex items-center gap-2 mt-1 text-xs text-gray-400">
                  <span class="font-mono truncate">{u.uploadId}</span>
                  <CopyButton text={u.uploadId} />
                  {u.retain && (
                    <span class="px-1.5 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded text-[10px] font-medium">
                      RETAINED
                    </span>
                  )}
                </div>
              </div>
              <StatusIndicator status={u.status || 'UPLOADED'} />
            </div>
            <div class="flex items-center justify-between mt-3 text-xs text-gray-400">
              <span>{u.createdAt ? new Date(u.createdAt).toLocaleString() : ''}</span>
              {u.analysisId && (
                <a
                  href={`/analysis/${u.analysisId}`}
                  class="text-indigo-500 hover:text-indigo-700 font-medium transition-colors"
                >
                  View Analysis →
                </a>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
