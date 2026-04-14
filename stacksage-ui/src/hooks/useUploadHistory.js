import { useState, useCallback } from 'preact/hooks';

const STORAGE_KEY = 'stacksage_uploads';
const MAX_ENTRIES = 50;

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
  } catch {
    return [];
  }
}

function save(entries) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

export function useUploadHistory() {
  const [uploads, setUploads] = useState(load);

  const addUpload = useCallback((entry) => {
    setUploads((prev) => {
      const next = [{ ...entry, createdAt: new Date().toISOString() }, ...prev].slice(0, MAX_ENTRIES);
      save(next);
      return next;
    });
  }, []);

  const updateUpload = useCallback((uploadId, updates) => {
    setUploads((prev) => {
      const next = prev.map((u) => (u.uploadId === uploadId ? { ...u, ...updates } : u));
      save(next);
      return next;
    });
  }, []);

  const removeUpload = useCallback((uploadId) => {
    setUploads((prev) => {
      const next = prev.filter((u) => u.uploadId !== uploadId);
      save(next);
      return next;
    });
  }, []);

  const clearAll = useCallback(() => {
    save([]);
    setUploads([]);
  }, []);

  return { uploads, addUpload, updateUpload, removeUpload, clearAll };
}
