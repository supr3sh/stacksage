import { useState } from 'preact/hooks';

export function CopyButton({ text }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard may not be available */ }
  }

  return (
    <button
      onClick={handleCopy}
      class="text-xs text-gray-400 hover:text-indigo-500 transition-colors"
      title="Copy to clipboard"
    >
      {copied ? '✓' : '📋'}
    </button>
  );
}
