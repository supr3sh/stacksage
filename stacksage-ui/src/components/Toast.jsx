import { useState, useCallback, useRef } from 'preact/hooks';
import { createContext } from 'preact';
import { useContext } from 'preact/hooks';

const ToastContext = createContext();

let nextId = 0;

export function useToast() {
  return useContext(ToastContext);
}

const VARIANTS = {
  info: 'bg-indigo-600 text-white',
  success: 'bg-green-600 text-white',
  error: 'bg-red-600 text-white',
  warning: 'bg-amber-500 text-white',
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef({});

  const addToast = useCallback((message, variant = 'info', duration = 5000) => {
    const id = ++nextId;
    setToasts((prev) => [...prev, { id, message, variant }]);
    timers.current[id] = setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
      delete timers.current[id];
    }, duration);
    return id;
  }, []);

  const removeToast = useCallback((id) => {
    clearTimeout(timers.current[id]);
    delete timers.current[id];
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={addToast}>
      {children}
      <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
        {toasts.map((t) => (
          <div
            key={t.id}
            class={`${VARIANTS[t.variant] || VARIANTS.info} px-4 py-3 rounded-lg shadow-xl text-sm flex items-start gap-2 animate-slide-in`}
          >
            <span class="flex-1">{t.message}</span>
            <button onClick={() => removeToast(t.id)} class="opacity-70 hover:opacity-100 shrink-0">&times;</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
