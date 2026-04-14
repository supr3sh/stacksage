import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/preact';
import { FileDropzone } from './FileDropzone.jsx';

describe('FileDropzone', () => {
  it('renders instructional text', () => {
    const { getByText } = render(<FileDropzone onFile={vi.fn()} />);
    expect(getByText(/\.log/)).toBeInTheDocument();
    expect(getByText(/\.txt/)).toBeInTheDocument();
    expect(getByText('or click to browse')).toBeInTheDocument();
  });

  it('calls onFile when a file is selected via input', () => {
    const onFile = vi.fn();
    const { container } = render(<FileDropzone onFile={onFile} />);
    const input = container.querySelector('input[type="file"]');

    const file = new File(['log data'], 'test.log', { type: 'text/plain' });
    fireEvent.change(input, { target: { files: [file] } });
    expect(onFile).toHaveBeenCalledWith(file);
  });

  it('calls onFile on drop', () => {
    const onFile = vi.fn();
    const { container } = render(<FileDropzone onFile={onFile} />);
    const dropzone = container.firstChild;

    const file = new File(['data'], 'app.log', { type: 'text/plain' });
    fireEvent.drop(dropzone, { dataTransfer: { files: [file] } });
    expect(onFile).toHaveBeenCalledWith(file);
  });

  it('applies disabled styling when disabled', () => {
    const { container } = render(<FileDropzone onFile={vi.fn()} disabled={true} />);
    expect(container.firstChild.className).toContain('opacity-50');
    expect(container.firstChild.className).toContain('pointer-events-none');
  });

  it('does not have disabled styling when enabled', () => {
    const { container } = render(<FileDropzone onFile={vi.fn()} disabled={false} />);
    expect(container.firstChild.className).not.toContain('opacity-50');
  });

  it('toggles dragging style on dragover and dragleave', () => {
    const { container } = render(<FileDropzone onFile={vi.fn()} />);
    const dropzone = container.firstChild;

    fireEvent.dragOver(dropzone);
    expect(dropzone.className).toContain('bg-indigo-50');

    fireEvent.dragLeave(dropzone);
    expect(dropzone.className).not.toContain('bg-indigo-50');
  });
});
