const COLORS = {
  CRITICAL: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-400',
  HIGH: 'bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-400',
  MEDIUM: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-400',
  LOW: 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-400',
};

export function SeverityBadge({ severity }) {
  const s = severity?.toUpperCase() || 'MEDIUM';
  return (
    <span class={`inline-block px-2 py-0.5 rounded text-xs font-bold tracking-wide ${COLORS[s] || COLORS.MEDIUM}`}>
      {s}
    </span>
  );
}
