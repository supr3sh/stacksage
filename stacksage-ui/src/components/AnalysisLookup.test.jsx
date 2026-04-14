import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, fireEvent } from '@testing-library/preact';
import { AnalysisLookup } from './AnalysisLookup.jsx';

vi.mock('preact-router', () => ({
  route: vi.fn(),
}));

import { route } from 'preact-router';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AnalysisLookup', () => {
  it('renders input and submit button', () => {
    const { container, getByText } = render(<AnalysisLookup />);
    expect(container.querySelector('input[type="text"]')).toBeInTheDocument();
    expect(getByText('Look Up')).toBeInTheDocument();
  });

  it('navigates to analysis page on submit', () => {
    const { container, getByText } = render(<AnalysisLookup />);
    const input = container.querySelector('input');
    fireEvent.input(input, { target: { value: 'abc-123' } });
    fireEvent.submit(container.querySelector('form'));
    expect(route).toHaveBeenCalledWith('/analysis/abc-123');
  });

  it('trims whitespace from input', () => {
    const { container } = render(<AnalysisLookup />);
    const input = container.querySelector('input');
    fireEvent.input(input, { target: { value: '  abc-456  ' } });
    fireEvent.submit(container.querySelector('form'));
    expect(route).toHaveBeenCalledWith('/analysis/abc-456');
  });

  it('does not navigate when input is empty', () => {
    const { container } = render(<AnalysisLookup />);
    fireEvent.submit(container.querySelector('form'));
    expect(route).not.toHaveBeenCalled();
  });

  it('does not navigate when input is only whitespace', () => {
    const { container } = render(<AnalysisLookup />);
    const input = container.querySelector('input');
    fireEvent.input(input, { target: { value: '   ' } });
    fireEvent.submit(container.querySelector('form'));
    expect(route).not.toHaveBeenCalled();
  });
});
