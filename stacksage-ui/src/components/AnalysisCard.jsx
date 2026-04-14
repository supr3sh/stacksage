import { useState } from 'preact/hooks';
import { SeverityBadge } from './SeverityBadge.jsx';

export function AnalysisCard({ result, index }) {
  const [expanded, setExpanded] = useState(false);
  const ex = result.exception || {};
  const diag = result.diagnosis;

  return (
    <div class="border border-gray-200 dark:border-gray-700 rounded-xl bg-white dark:bg-gray-900 overflow-hidden">
      <div class="p-4 space-y-3">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <span class="text-xs text-gray-400 font-mono">#{index + 1}</span>
            <h3 class="font-semibold text-red-700 dark:text-red-400 break-all">
              {ex.exceptionType || 'Unknown Exception'}
            </h3>
            {ex.message && (
              <p class="text-sm text-gray-600 dark:text-gray-400 mt-0.5 break-words">{ex.message}</p>
            )}
          </div>
          {diag?.severity && <SeverityBadge severity={diag.severity} />}
        </div>

        {ex.stackTrace?.length > 0 && (
          <div>
            <button
              onClick={() => setExpanded((e) => !e)}
              class="text-xs text-indigo-500 hover:text-indigo-700 transition-colors"
            >
              {expanded ? 'Hide' : 'Show'} stack trace ({ex.stackTrace.length} frames)
            </button>
            {expanded && (
              <pre class="mt-2 p-3 bg-gray-50 dark:bg-gray-800 rounded-lg text-xs overflow-x-auto max-h-60 text-gray-700 dark:text-gray-300">
                {ex.stackTrace.join('\n')}
              </pre>
            )}
          </div>
        )}

        {diag && (
          <div class="space-y-2 pt-2 border-t border-gray-100 dark:border-gray-800">
            <Detail label="Root Cause" value={diag.rootCause} />
            <Detail label="Explanation" value={diag.explanation} />
            <Detail label="Suggested Fix" value={diag.suggestedFix} />
          </div>
        )}

        {!diag && (
          <p class="text-sm text-gray-400 italic">AI diagnosis not available for this exception.</p>
        )}
      </div>
    </div>
  );
}

function Detail({ label, value }) {
  if (!value) return null;
  return (
    <div>
      <span class="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">{label}</span>
      <p class="text-sm text-gray-800 dark:text-gray-200 mt-0.5">{value}</p>
    </div>
  );
}
