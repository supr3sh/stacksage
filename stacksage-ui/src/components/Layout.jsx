import { Navbar } from './Navbar.jsx';

export function Layout({ children, connected }) {
  return (
    <div class="min-h-full flex flex-col">
      <Navbar connected={connected} />
      <main class="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 py-8">
        {children}
      </main>
      <footer class="text-center text-xs text-gray-400 dark:text-gray-600 py-4">
        StackSage — AI-powered Java debugging
      </footer>
    </div>
  );
}
