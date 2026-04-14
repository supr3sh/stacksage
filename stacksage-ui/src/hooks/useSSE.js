import { useEffect, useRef, useState } from 'preact/hooks';

export function useSSE(onEvent) {
  const [sessionId, setSessionId] = useState(null);
  const [connected, setConnected] = useState(false);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    let es;
    let retryDelay = 1000;
    let destroyed = false;

    function connect() {
      if (destroyed) return;
      es = new EventSource('/api/v1/events');

      es.addEventListener('connected', (e) => {
        const data = JSON.parse(e.data);
        setSessionId(data.sessionId);
        setConnected(true);
        retryDelay = 1000;
      });

      es.addEventListener('file.cleanup', (e) => {
        onEventRef.current?.('file.cleanup', JSON.parse(e.data));
      });

      es.addEventListener('analysis.completed', (e) => {
        onEventRef.current?.('analysis.completed', JSON.parse(e.data));
      });

      es.addEventListener('analysis.failed', (e) => {
        onEventRef.current?.('analysis.failed', JSON.parse(e.data));
      });

      es.onerror = () => {
        setConnected(false);
        es.close();
        if (!destroyed) {
          setTimeout(connect, retryDelay);
          retryDelay = Math.min(retryDelay * 2, 30000);
        }
      };
    }

    connect();

    return () => {
      destroyed = true;
      es?.close();
    };
  }, []);

  return { sessionId, connected };
}
