import { describe, it, expect, vi, beforeEach } from 'vitest';
import { uploadFile, getUpload, deleteUpload, getAnalysisByUpload, getAnalysis } from './client.js';

function mockFetch(body, status = 200) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: 'OK',
    json: () => Promise.resolve(body),
  });
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('API client', () => {
  it('uploadFile sends POST with FormData and session header', async () => {
    const resp = { id: 'u1', fileName: 'app.log' };
    globalThis.fetch = mockFetch(resp);

    const file = new File(['content'], 'app.log', { type: 'text/plain' });
    const result = await uploadFile(file, true, 'sess-123');

    expect(fetch).toHaveBeenCalledOnce();
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/uploads?retain=true');
    expect(opts.method).toBe('POST');
    expect(opts.headers['X-Session-Id']).toBe('sess-123');
    expect(opts.body).toBeInstanceOf(FormData);
    expect(result).toEqual(resp);
  });

  it('uploadFile omits session header when sessionId is null', async () => {
    globalThis.fetch = mockFetch({ id: 'u1' });
    await uploadFile(new File(['x'], 'a.log'), false, null);
    const headers = fetch.mock.calls[0][1].headers;
    expect(headers['X-Session-Id']).toBeUndefined();
  });

  it('getUpload calls correct URL with content flag', async () => {
    const data = { id: 'u1', content: 'hello' };
    globalThis.fetch = mockFetch(data);

    const result = await getUpload('u1', true);
    expect(fetch).toHaveBeenCalledWith('/api/v1/uploads/u1?content=true', {});
    expect(result).toEqual(data);
  });

  it('deleteUpload sends DELETE', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true, status: 204, statusText: 'No Content',
      json: () => Promise.reject(new Error('no body')),
    });

    const result = await deleteUpload('u1');
    expect(fetch).toHaveBeenCalledWith('/api/v1/uploads/u1', { method: 'DELETE' });
    expect(result).toBeNull();
  });

  it('getAnalysisByUpload calls correct URL', async () => {
    const data = { id: 'a1', status: 'COMPLETED' };
    globalThis.fetch = mockFetch(data);

    const result = await getAnalysisByUpload('u1');
    expect(fetch).toHaveBeenCalledWith('/api/v1/uploads/u1/analysis', {});
    expect(result).toEqual(data);
  });

  it('getAnalysis calls correct URL', async () => {
    const data = { id: 'a1', status: 'IN_PROGRESS' };
    globalThis.fetch = mockFetch(data);

    const result = await getAnalysis('a1');
    expect(fetch).toHaveBeenCalledWith('/api/v1/analyses/a1', {});
    expect(result).toEqual(data);
  });

  it('throws on non-ok response with error body', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: () => Promise.resolve({ error: 'Upload not found' }),
    });

    await expect(getUpload('bad-id')).rejects.toThrow('Upload not found');
  });

  it('throws with status text when error body parsing fails', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      json: () => Promise.reject(new Error('parse fail')),
    });

    await expect(getAnalysis('x')).rejects.toThrow('Internal Server Error');
  });
});
