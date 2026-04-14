import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/preact';
import { useUploadHistory } from './useUploadHistory.js';

beforeEach(() => {
  localStorage.clear();
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2025-01-01T00:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useUploadHistory', () => {
  it('starts with empty array when localStorage is empty', () => {
    const { result } = renderHook(() => useUploadHistory());
    expect(result.current.uploads).toEqual([]);
  });

  it('loads existing entries from localStorage', () => {
    const existing = [{ uploadId: 'u1', fileName: 'a.log', createdAt: '2025-01-01T00:00:00Z' }];
    localStorage.setItem('stacksage_uploads', JSON.stringify(existing));

    const { result } = renderHook(() => useUploadHistory());
    expect(result.current.uploads).toEqual(existing);
  });

  it('addUpload prepends entry and persists', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      result.current.addUpload({ uploadId: 'u1', fileName: 'app.log' });
    });

    expect(result.current.uploads).toHaveLength(1);
    expect(result.current.uploads[0].uploadId).toBe('u1');
    expect(result.current.uploads[0].createdAt).toBe('2025-01-01T00:00:00.000Z');

    const stored = JSON.parse(localStorage.getItem('stacksage_uploads'));
    expect(stored).toHaveLength(1);
    expect(stored[0].uploadId).toBe('u1');
  });

  it('addUpload caps at 50 entries', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      for (let i = 0; i < 55; i++) {
        result.current.addUpload({ uploadId: `u${i}`, fileName: `file${i}.log` });
      }
    });

    expect(result.current.uploads).toHaveLength(50);
    expect(result.current.uploads[0].uploadId).toBe('u54');
  });

  it('updateUpload modifies matching entry', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      result.current.addUpload({ uploadId: 'u1', fileName: 'a.log', status: 'UPLOADED' });
    });
    act(() => {
      result.current.updateUpload('u1', { status: 'COMPLETED', analysisId: 'a1' });
    });

    expect(result.current.uploads[0].status).toBe('COMPLETED');
    expect(result.current.uploads[0].analysisId).toBe('a1');
    expect(result.current.uploads[0].fileName).toBe('a.log');
  });

  it('updateUpload does nothing when uploadId not found', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      result.current.addUpload({ uploadId: 'u1', fileName: 'a.log' });
    });
    act(() => {
      result.current.updateUpload('unknown', { status: 'FAILED' });
    });

    expect(result.current.uploads).toHaveLength(1);
    expect(result.current.uploads[0].uploadId).toBe('u1');
  });

  it('removeUpload deletes matching entry and persists', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      result.current.addUpload({ uploadId: 'u1', fileName: 'a.log' });
      result.current.addUpload({ uploadId: 'u2', fileName: 'b.log' });
    });
    act(() => {
      result.current.removeUpload('u1');
    });

    expect(result.current.uploads).toHaveLength(1);
    expect(result.current.uploads[0].uploadId).toBe('u2');

    const stored = JSON.parse(localStorage.getItem('stacksage_uploads'));
    expect(stored).toHaveLength(1);
  });

  it('clearAll empties everything', () => {
    const { result } = renderHook(() => useUploadHistory());

    act(() => {
      result.current.addUpload({ uploadId: 'u1', fileName: 'a.log' });
    });
    act(() => {
      result.current.clearAll();
    });

    expect(result.current.uploads).toEqual([]);
    expect(JSON.parse(localStorage.getItem('stacksage_uploads'))).toEqual([]);
  });

  it('handles corrupted localStorage gracefully', () => {
    localStorage.setItem('stacksage_uploads', '{invalid json');
    const { result } = renderHook(() => useUploadHistory());
    expect(result.current.uploads).toEqual([]);
  });
});
