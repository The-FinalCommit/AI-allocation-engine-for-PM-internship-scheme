import React from 'react';

/**
 * Canonical PRAGATI brand mark.
 *
 * Renders the single, exact official logo asset (emblem + PRAGATI wordmark +
 * "AI Smart Allocation for PM Internship Scheme" tagline) — the artwork is
 * never redrawn, stretched or cropped, and no wordmark text is duplicated
 * beside it. The supplied artwork has a transparent background; `tile`
 * places it on a white brand plate where it sits on dark surfaces.
 */
export function Logo({ width = 150, tile = false, className = '' }: { width?: number; tile?: boolean; className?: string }) {
  const img = (
    <img
      src="/logo.png"
      alt="PRAGATI — AI Smart Allocation for PM Internship Scheme"
      style={{ width, height: 'auto', maxWidth: '100%', objectFit: 'contain', display: 'block' }}
      draggable={false}
    />
  );
  if (!tile) {
    return <div className={`inline-block ${className}`}>{img}</div>;
  }
  return (
    <div className={`inline-block rounded-2xl bg-white px-4 py-3 shadow-md shadow-black/10 ring-1 ring-white/20 ${className}`}>
      {img}
    </div>
  );
}

/**
 * Compact horizontal lockup for tight header bars: the emblem alone
 * (cropped from the same official artwork, no redrawing) beside the
 * "PRAGATI" wordmark and "SMART ALLOCATION" sub-label set in type. Use
 * this instead of `Logo` anywhere the full stacked mark (icon above
 * wordmark above tagline) is too tall for the space, e.g. a page header
 * that sits directly above other content.
 */
export function LogoMark({
  height = 36,
  textSize,
  className = '',
}: {
  /** Height of the icon/emblem, in px. */
  height?: number;
  /** Font size of the "PRAGATI" wordmark, in px. Defaults smaller than the icon. */
  textSize?: number;
  className?: string;
}) {
  const wordmarkSize = textSize ?? height * 0.42;
  return (
    <div className={`inline-flex items-center gap-2.5 ${className}`}>
      <img
        src="/logo-mark.png"
        alt=""
        style={{ height, width: 'auto', display: 'block' }}
        draggable={false}
      />
      <div className="leading-none">
        <div
          className="whitespace-nowrap font-display font-extrabold tracking-tight text-white"
          style={{ fontSize: wordmarkSize }}
        >
          PRAGATI
        </div>
        <div
          className="mt-0.5 whitespace-nowrap font-bold text-saffron-400"
          style={{ fontSize: wordmarkSize * 0.37, letterSpacing: '0.08em' }}
        >
          SMART ALLOCATION
        </div>
      </div>
    </div>
  );
}
