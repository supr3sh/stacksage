import { useState } from 'preact/hooks';
import { route } from 'preact-router';
import { FileDropzone } from '../components/FileDropzone.jsx';
import { StatusIndicator } from '../components/StatusIndicator.jsx';
import { uploadFile } from '../api/client.js';
import { useAnalysisPoll } from '../hooks/useAnalysisPoll.js';
import { useToast } from '../components/Toast.jsx';

export function Upload({ addUpload, updateUpload, sessionId }) {
  const [retain, setRetain] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadId, setUploadId] = useState(null);
  const [uploadError, setUploadError] = useState(null);
  const addToast = useToast();

  const { analysis, loading: polling } = useAnalysisPoll(uploadId, 'uploadId');

  if (analysis?.status === 'COMPLETED' && analysis.analysisId) {
    updateUpload?.(uploadId, { analysisId: analysis.analysisId, status: 'COMPLETED' });
  }
  if (analysis?.status === 'FAILED') {
    updateUpload?.(uploadId, { status: 'FAILED' });
  }

  async function handleFile(file) {
    setUploading(true);
    setUploadError(null);
    try {
      const res = await uploadFile(file, retain, sessionId);
      setUploadId(res.id);
      addUpload?.({ uploadId: res.id, filename: res.filename, retain, status: 'UPLOADED' });
      addToast(`Uploaded ${res.filename} — analysis started`, 'success');
    } catch (err) {
      setUploadError(err.message);
      addToast(err.message, 'error');
    } finally {
      setUploading(false);
    }
  }

  return (
    <div class="max-w-xl mx-auto space-y-6">
      <h1 class="text-2xl font-bold">Upload Log File</h1>

      <FileDropzone onFile={handleFile} disabled={uploading || !!uploadId} />

      <label class="flex items-center gap-2 text-sm cursor-pointer">
        <input
          type="checkbox"
          checked={retain}
          onChange={(e) => setRetain(e.target.checked)}
          disabled={!!uploadId}
          class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
        />
        <span class="text-gray-700 dark:text-gray-300">Keep file on server after analysis</span>
      </label>

      {uploadError && (
        <div class="p-3 bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
          {uploadError}
        </div>
      )}

      {uploading && <StatusIndicator status="IN_PROGRESS" />}

      {uploadId && !uploading && (
        <div class="p-4 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500">Upload ID</span>
            <code class="text-xs bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">{uploadId}</code>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500">Status</span>
            <StatusIndicator status={analysis?.status || 'PENDING'} />
          </div>
          {analysis?.status === 'COMPLETED' && (
            <button
              onClick={() => route(`/analysis/${analysis.analysisId}`)}
              class="w-full mt-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm
                     font-medium rounded-lg transition-colors"
            >
              View Analysis Results
            </button>
          )}
          {analysis?.status === 'FAILED' && analysis.errorMessage && (
            <div class="text-sm text-red-600 dark:text-red-400">{analysis.errorMessage}</div>
          )}
        </div>
      )}
    </div>
  );
}
