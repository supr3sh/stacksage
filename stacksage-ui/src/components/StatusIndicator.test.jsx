import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/preact';
import { StatusIndicator } from './StatusIndicator.jsx';

describe('StatusIndicator', () => {
  it.each([
    ['PENDING', 'Pending', '⏳'],
    ['IN_PROGRESS', 'Analyzing...', '⚙️'],
    ['COMPLETED', 'Completed', '✅'],
    ['FAILED', 'Failed', '❌'],
    ['UPLOADED', 'Uploaded', '📤'],
  ])('renders %s status with label "%s" and icon "%s"', (status, label, icon) => {
    const { container } = render(<StatusIndicator status={status} />);
    const spans = container.querySelectorAll('span');
    const outerSpan = spans[0];
    expect(outerSpan.textContent).toContain(label);
    expect(outerSpan.textContent).toContain(icon);
  });

  it('defaults to PENDING for unknown status', () => {
    const { container } = render(<StatusIndicator status="INVALID" />);
    expect(container.textContent).toContain('Pending');
  });
});
