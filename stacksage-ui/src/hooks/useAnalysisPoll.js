import { useState, useEffect, useRef } from 'preact/hooks';
import { getAnalysisByUpload, getAnalysis } from '../api/client.js';

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED']);
const POLL_INTERVAL = 3000;

export function useAnalysisPoll(id, mode = 'analysisId') {
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(!!id);
  const [error, setError] = useState(null);
  const stopped = useRef(false);

  useEffect(() => {
    if (!id) return;
    stopped.current = false;
    setLoading(true);
    setError(null);
    setAnalysis(null);

    const fetcher = mode === 'uploadId' ? () => getAnalysisByUpload(id) : () => getAnalysis(id);
    let timer;

    async function poll() {
      if (stopped.current) return;
      try {
        const data = await fetcher();
        setAnalysis(data);
        if (!TERMINAL_STATUSES.has(data.status)) {
          timer = setTimeout(poll, POLL_INTERVAL);
        } else {
          setLoading(false);
        }
      } catch (err) {
        setError(err.message);
        setLoading(false);
      }
    }

    poll();

    return () => {
      stopped.current = true;
      clearTimeout(timer);
    };
  }, [id, mode]);

  function stop() {
    stopped.current = true;
    setLoading(false);
  }

  return { analysis, loading, error, stop };
}
