const STATUS_MAP = {
  PENDING: { icon: '⏳', label: 'Pending', color: 'text-gray-500' },
  IN_PROGRESS: { icon: '⚙️', label: 'Analyzing...', color: 'text-indigo-500' },
  COMPLETED: { icon: '✅', label: 'Completed', color: 'text-green-600' },
  FAILED: { icon: '❌', label: 'Failed', color: 'text-red-600' },
  UPLOADED: { icon: '📤', label: 'Uploaded', color: 'text-blue-500' },
};

export function StatusIndicator({ status }) {
  const s = STATUS_MAP[status] || STATUS_MAP.PENDING;
  return (
    <span class={`inline-flex items-center gap-1 text-sm font-medium ${s.color}`}>
      <span>{s.icon}</span>
      <span>{s.label}</span>
    </span>
  );
}
