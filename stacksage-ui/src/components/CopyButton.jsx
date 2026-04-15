import { useState } from 'preact/hooks';

function fallbackCopy(text) {
  const el = document.createElement('textarea');
  el.value = text;
  el.setAttribute('readonly', '');
  el.style.cssText = 'position:fixed;left:-9999px';
  document.body.appendChild(el);
  el.select();
  let ok = false;
  try { ok = document.execCommand('copy'); } catch { /* ignore */ }
  document.body.removeChild(el);
  return ok;
}

export function CopyButton({ text }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    let ok = false;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
        ok = true;
      }
    } catch { /* secure-context only — fall through */ }

    if (!ok) ok = fallbackCopy(text);

    if (ok) {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  }

  return (
    <button
      onClick={handleCopy}
      class="text-xs text-gray-400 hover:text-indigo-500 transition-colors"
      title="Copy to clipboard"
    >
      {copied ? '✓ Copied' : '📋 Copy'}
    </button>
  );
}
