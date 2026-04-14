import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/preact';
import { SeverityBadge } from './SeverityBadge.jsx';

describe('SeverityBadge', () => {
  it('renders CRITICAL severity', () => {
    const { container } = render(<SeverityBadge severity="CRITICAL" />);
    const span = container.querySelector('span');
    expect(span.textContent).toBe('CRITICAL');
    expect(span.className).toContain('bg-red-100');
  });

  it('renders HIGH severity', () => {
    const { container } = render(<SeverityBadge severity="HIGH" />);
    expect(container.querySelector('span').textContent).toBe('HIGH');
    expect(container.querySelector('span').className).toContain('bg-orange-100');
  });

  it('renders MEDIUM severity', () => {
    const { container } = render(<SeverityBadge severity="MEDIUM" />);
    expect(container.querySelector('span').textContent).toBe('MEDIUM');
    expect(container.querySelector('span').className).toContain('bg-yellow-100');
  });

  it('renders LOW severity', () => {
    const { container } = render(<SeverityBadge severity="LOW" />);
    expect(container.querySelector('span').textContent).toBe('LOW');
    expect(container.querySelector('span').className).toContain('bg-green-100');
  });

  it('normalizes lowercase input to uppercase', () => {
    const { container } = render(<SeverityBadge severity="critical" />);
    expect(container.querySelector('span').textContent).toBe('CRITICAL');
  });

  it('defaults to MEDIUM when severity is null', () => {
    const { container } = render(<SeverityBadge severity={null} />);
    expect(container.querySelector('span').textContent).toBe('MEDIUM');
    expect(container.querySelector('span').className).toContain('bg-yellow-100');
  });

  it('defaults to MEDIUM for unknown severity', () => {
    const { container } = render(<SeverityBadge severity="UNKNOWN" />);
    expect(container.querySelector('span').textContent).toBe('UNKNOWN');
    expect(container.querySelector('span').className).toContain('bg-yellow-100');
  });
});
