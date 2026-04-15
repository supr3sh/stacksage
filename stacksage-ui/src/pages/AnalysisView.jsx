import { useState } from 'preact/hooks';
import { useAnalysisPoll } from '../hooks/useAnalysisPoll.js';
import { AnalysisCard } from '../components/AnalysisCard.jsx';
import { StatusIndicator } from '../components/StatusIndicator.jsx';
import { CopyButton } from '../components/CopyButton.jsx';
import { Pagination } from '../components/Pagination.jsx';

const PAGE_SIZE = 10;

export function AnalysisView({ id }) {
  const { analysis, loading, error } = useAnalysisPoll(id, 'analysisId');
  const [page, setPage] = useState(1);

  if (error) {
    return (
      <div class="max-w-2xl mx-auto text-center py-16">
        <div class="text-4xl mb-3">⚠️</div>
        <h2 class="text-xl font-semibold text-red-600">Error loading analysis</h2>
        <p class="text-sm text-gray-500 mt-2">{error}</p>
        <a href="/" class="inline-block mt-4 text-indigo-500 hover:text-indigo-700 text-sm">← Back home</a>
      </div>
    );
  }

  if (!analysis && loading) {
    return (
      <div class="max-w-2xl mx-auto text-center py-16">
        <StatusIndicator status="IN_PROGRESS" />
        <p class="text-sm text-gray-400 mt-3">Loading analysis...</p>
      </div>
    );
  }

  if (!analysis) {
    return (
      <div class="max-w-2xl mx-auto text-center py-16">
        <p class="text-gray-500">No analysis found.</p>
      </div>
    );
  }

  const results = analysis.results || [];
  const totalPages = Math.ceil(results.length / PAGE_SIZE);
  const safePage = Math.min(page, Math.max(totalPages, 1));
  const start = (safePage - 1) * PAGE_SIZE;
  const visible = results.slice(start, start + PAGE_SIZE);

  return (
    <div class="max-w-3xl mx-auto space-y-6">
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold">
            Analysis Results
            {results.length > 0 && (
              <span class="text-sm font-normal text-gray-400 ml-2">({results.length} exceptions)</span>
            )}
          </h1>
          <div class="flex items-center gap-2 mt-1 text-xs text-gray-400">
            <span class="font-mono">{id}</span>
            <CopyButton text={id} />
          </div>
        </div>
        <StatusIndicator status={analysis.status} />
      </div>

      {analysis.source && (
        <p class="text-sm text-gray-500">Source: <span class="font-medium">{analysis.source}</span></p>
      )}

      {analysis.status === 'FAILED' && analysis.errorMessage && (
        <div class="p-4 bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-xl text-sm text-red-700 dark:text-red-400">
          {analysis.errorMessage}
        </div>
      )}

      {analysis.status === 'COMPLETED' && results.length === 0 && (
        <p class="text-gray-500 text-sm">No exceptions found in the log file.</p>
      )}

      {visible.length > 0 && (
        <div class="space-y-4">
          {visible.map((r, i) => (
            <AnalysisCard key={start + i} result={r} index={start + i} />
          ))}
        </div>
      )}

      <Pagination currentPage={safePage} totalPages={totalPages} onPageChange={setPage} />

      {loading && (
        <p class="text-sm text-gray-400 text-center">Polling for updates...</p>
      )}
    </div>
  );
}
