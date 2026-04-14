import Router from 'preact-router';
import { useCallback } from 'preact/hooks';
import { Layout } from './components/Layout.jsx';
import { ToastProvider, useToast } from './components/Toast.jsx';
import { useSSE } from './hooks/useSSE.js';
import { useUploadHistory } from './hooks/useUploadHistory.js';
import { Home } from './pages/Home.jsx';
import { Upload } from './pages/Upload.jsx';
import { History } from './pages/History.jsx';
import { AnalysisView } from './pages/AnalysisView.jsx';

function AppRoutes() {
  const addToast = useToast();
  const { uploads, addUpload, updateUpload, removeUpload, clearAll } = useUploadHistory();

  const handleSSE = useCallback((event, data) => {
    if (event === 'file.cleanup') {
      removeUpload(data.uploadId);
      addToast(`File "${data.filename}" was cleaned up by the server`, 'warning');
    }
    if (event === 'analysis.completed') {
      updateUpload(data.uploadId, { analysisId: data.analysisId, status: 'COMPLETED' });
      addToast('Analysis completed', 'success');
    }
    if (event === 'analysis.failed') {
      updateUpload(data.uploadId, { status: 'FAILED' });
      addToast(`Analysis failed: ${data.error || 'unknown error'}`, 'error');
    }
  }, [removeUpload, updateUpload, addToast]);

  const { sessionId, connected } = useSSE(handleSSE);

  return (
    <Layout connected={connected}>
      <Router>
        <Home path="/" />
        <Upload path="/upload" addUpload={addUpload} updateUpload={updateUpload} sessionId={sessionId} />
        <History path="/history" uploads={uploads} clearAll={clearAll} />
        <AnalysisView path="/analysis/:id" />
        <NotFound default />
      </Router>
    </Layout>
  );
}

function NotFound() {
  return (
    <div class="text-center py-16">
      <div class="text-4xl mb-3">🔍</div>
      <h2 class="text-xl font-semibold">Page not found</h2>
      <a href="/" class="text-indigo-500 hover:text-indigo-700 text-sm mt-2 inline-block">← Back home</a>
    </div>
  );
}

export function App() {
  return (
    <ToastProvider>
      <AppRoutes />
    </ToastProvider>
  );
}
