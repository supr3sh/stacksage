export function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;

  const pages = [];
  for (let i = 1; i <= totalPages; i++) {
    if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
      pages.push(i);
    } else if (pages[pages.length - 1] !== '...') {
      pages.push('...');
    }
  }

  return (
    <div class="flex items-center justify-center gap-1 pt-4">
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
        class="px-3 py-1.5 text-sm rounded-lg border border-gray-200 dark:border-gray-700
               disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-100
               dark:hover:bg-gray-800 transition-colors"
      >
        ‹ Prev
      </button>
      {pages.map((p, i) =>
        p === '...' ? (
          <span key={`ellipsis-${i}`} class="px-2 text-gray-400 text-sm">…</span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            class={`px-3 py-1.5 text-sm rounded-lg border transition-colors ${
              p === currentPage
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800'
            }`}
          >
            {p}
          </button>
        )
      )}
      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
        class="px-3 py-1.5 text-sm rounded-lg border border-gray-200 dark:border-gray-700
               disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-100
               dark:hover:bg-gray-800 transition-colors"
      >
        Next ›
      </button>
    </div>
  );
}
