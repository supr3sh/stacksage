import { describe, it, expect, beforeEach } from 'vitest';
import { render } from '@testing-library/preact';
import { Navbar } from './Navbar.jsx';

beforeEach(() => {
  localStorage.clear();
});

describe('Navbar', () => {
  it('renders brand name and navigation links', () => {
    const { getByText } = render(<Navbar connected={false} />);
    expect(getByText('StackSage')).toBeInTheDocument();
    expect(getByText('Upload')).toBeInTheDocument();
    expect(getByText('History')).toBeInTheDocument();
  });

  it('shows "Live" text when connected', () => {
    const { queryByText } = render(<Navbar connected={true} />);
    expect(queryByText('Live')).toBeInTheDocument();
    expect(queryByText('Offline')).not.toBeInTheDocument();
  });

  it('shows "Offline" text when disconnected', () => {
    const { queryByText } = render(<Navbar connected={false} />);
    expect(queryByText('Offline')).toBeInTheDocument();
    expect(queryByText('Live')).not.toBeInTheDocument();
  });

  it('renders navigation links with correct hrefs', () => {
    const { container } = render(<Navbar connected={false} />);
    const links = container.querySelectorAll('a');
    const hrefs = Array.from(links).map(a => a.getAttribute('href'));
    expect(hrefs).toContain('/');
    expect(hrefs).toContain('/upload');
    expect(hrefs).toContain('/history');
  });
});
