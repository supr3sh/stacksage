import { useState, useRef } from 'preact/hooks';

export function FileDropzone({ onFile, disabled }) {
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef();

  function handleDrop(e) {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer?.files?.[0];
    if (file) onFile(file);
  }

  function handleDragOver(e) {
    e.preventDefault();
    setDragging(true);
  }

  function handleChange(e) {
    const file = e.target.files?.[0];
    if (file) onFile(file);
  }

  return (
    <div
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={() => setDragging(false)}
      onClick={() => !disabled && inputRef.current?.click()}
      class={`relative border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-all
        ${dragging
          ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-950/30'
          : 'border-gray-300 dark:border-gray-600 hover:border-indigo-400 dark:hover:border-indigo-500'}
        ${disabled ? 'opacity-50 pointer-events-none' : ''}`}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".log,.txt"
        onChange={handleChange}
        class="hidden"
      />
      <div class="text-4xl mb-3">📁</div>
      <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
        Drop a <code class="text-indigo-600 dark:text-indigo-400">.log</code> or
        <code class="text-indigo-600 dark:text-indigo-400"> .txt</code> file here
      </p>
      <p class="text-xs text-gray-400 mt-1">or click to browse</p>
    </div>
  );
}
