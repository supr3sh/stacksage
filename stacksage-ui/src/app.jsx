import Router from 'preact-router';

export function App() {
  return (
    <div class="min-h-full">
      <header class="bg-indigo-600 dark:bg-indigo-800 text-white px-6 py-4">
        <h1 class="text-xl font-bold">StackSage</h1>
        <p class="text-indigo-200 text-sm">AI-powered Java debugging</p>
      </header>
      <main class="max-w-4xl mx-auto px-6 py-10">
        <div class="text-center">
          <h2 class="text-2xl font-semibold mb-2">Welcome to StackSage</h2>
          <p class="text-gray-500 dark:text-gray-400">Upload, analyze, and debug Java exceptions with AI.</p>
        </div>
      </main>
    </div>
  );
}
