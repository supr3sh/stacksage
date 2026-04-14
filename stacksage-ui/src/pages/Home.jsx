import { AnalysisLookup } from '../components/AnalysisLookup.jsx';

export function Home() {
  return (
    <div class="space-y-10">
      <section class="text-center pt-8 pb-4">
        <h1 class="text-4xl font-extrabold tracking-tight text-gray-900 dark:text-white">
          Stack<span class="text-indigo-600 dark:text-indigo-400">Sage</span>
        </h1>
        <p class="mt-3 text-lg text-gray-500 dark:text-gray-400 max-w-xl mx-auto">
          Upload Java log files and get AI-powered root cause analysis, explanations, and actionable fixes in seconds.
        </p>
      </section>

      <div class="grid gap-6 sm:grid-cols-2 max-w-2xl mx-auto">
        <a
          href="/upload"
          class="group block p-6 rounded-xl border border-gray-200 dark:border-gray-700
                 bg-white dark:bg-gray-900 shadow-sm hover:shadow-md hover:border-indigo-400
                 dark:hover:border-indigo-500 transition-all"
        >
          <div class="text-3xl mb-3">📤</div>
          <h3 class="font-semibold text-lg group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
            Upload a Log File
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Drag &amp; drop a .log or .txt file for instant AI analysis.
          </p>
        </a>

        <a
          href="/history"
          class="group block p-6 rounded-xl border border-gray-200 dark:border-gray-700
                 bg-white dark:bg-gray-900 shadow-sm hover:shadow-md hover:border-indigo-400
                 dark:hover:border-indigo-500 transition-all"
        >
          <div class="text-3xl mb-3">📋</div>
          <h3 class="font-semibold text-lg group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
            View History
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Browse your session uploads and their analysis results.
          </p>
        </a>
      </div>

      <section class="max-w-lg mx-auto">
        <h2 class="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3 text-center">
          Look Up an Analysis
        </h2>
        <AnalysisLookup />
      </section>
    </div>
  );
}
