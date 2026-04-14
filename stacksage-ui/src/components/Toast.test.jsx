import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act, fireEvent } from '@testing-library/preact';
import { ToastProvider, useToast } from './Toast.jsx';

function TestConsumer() {
  const addToast = useToast();
  return (
    <div>
      <button data-testid="info" onClick={() => addToast('Info message', 'info')}>Info</button>
      <button data-testid="error" onClick={() => addToast('Error message', 'error')}>Error</button>
      <button data-testid="success" onClick={() => addToast('Success!', 'success')}>Success</button>
    </div>
  );
}

describe('Toast', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('renders a toast on addToast and auto-removes after duration', async () => {
    const { getByTestId, queryByText } = render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>
    );

    act(() => { fireEvent.click(getByTestId('info')); });
    expect(queryByText('Info message')).toBeInTheDocument();

    act(() => { vi.advanceTimersByTime(5500); });
    expect(queryByText('Info message')).not.toBeInTheDocument();
  });

  it('applies correct variant class', () => {
    const { getByTestId, queryByText } = render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>
    );

    act(() => { fireEvent.click(getByTestId('error')); });
    const el = queryByText('Error message');
    expect(el.closest('div[class]').className).toContain('bg-red-600');
  });

  it('removes toast when dismiss button is clicked', () => {
    const { getByTestId, queryByText, container } = render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>
    );

    act(() => { fireEvent.click(getByTestId('success')); });
    expect(queryByText('Success!')).toBeInTheDocument();

    const dismissBtn = container.querySelector('button[class*="opacity"]');
    act(() => { fireEvent.click(dismissBtn); });
    expect(queryByText('Success!')).not.toBeInTheDocument();
  });

  it('can show multiple toasts simultaneously', () => {
    const { getByTestId, queryByText } = render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>
    );

    act(() => {
      fireEvent.click(getByTestId('info'));
      fireEvent.click(getByTestId('error'));
      fireEvent.click(getByTestId('success'));
    });

    expect(queryByText('Info message')).toBeInTheDocument();
    expect(queryByText('Error message')).toBeInTheDocument();
    expect(queryByText('Success!')).toBeInTheDocument();
  });
});
