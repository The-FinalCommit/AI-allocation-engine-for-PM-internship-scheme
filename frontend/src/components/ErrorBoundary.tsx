import React from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

interface State { hasError: boolean; message: string; }

/**
 * Last line of defense: if any component throws during render, show a clean
 * branded card instead of a blank white screen. The error is also logged so
 * it can be diagnosed.
 */
export default class ErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { hasError: false, message: '' };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error?.message ?? 'Unexpected error' };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[PRAGATI] UI error boundary caught:', error, info?.componentStack);
  }

  reset = () => this.setState({ hasError: false, message: '' });

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-6">
        <div className="card flex max-w-md flex-col items-center gap-3 p-8 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-saffron-50 ring-1 ring-saffron-200">
            <AlertTriangle className="h-6 w-6 text-saffron-500" />
          </div>
          <div>
            <div className="font-display text-[17px] font-bold text-ink">Something went wrong on this page</div>
            <p className="mt-1.5 text-[13px] leading-relaxed text-navy-500">
              The rest of PRAGATI is still running. Reload the page to continue —
              your data and any completed runs are safe.
            </p>
          </div>
          <pre className="max-h-24 w-full overflow-auto rounded-lg bg-navy-50 p-2 text-left text-[11px] text-navy-500">
            {this.state.message}
          </pre>
          <div className="flex gap-2">
            <button className="btn-primary" onClick={() => window.location.reload()}>
              <RotateCcw className="h-4 w-4" /> Reload page
            </button>
            <button className="btn-ghost" onClick={() => { this.reset(); window.location.href = '/'; }}>
              Go to dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }
}
