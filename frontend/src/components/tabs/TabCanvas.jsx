import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import { AlphaTabApi, LayoutMode, model, StaveProfile } from '@coderline/alphatab';
import useUiStore from '../../store/uiStore';

// High-contrast colours for the rendered tab, per app theme.
const COLORS = {
  dark: {
    mainGlyphColor: '#fafafa',
    secondaryGlyphColor: 'rgba(250,250,250,0.7)',
    scoreInfoColor: '#fafafa',
    staffLineColor: '#a1a1aa',
    barSeparatorColor: '#d4d4d8',
    barNumberColor: '#a1a1aa',
  },
  light: {
    mainGlyphColor: '#09090b',
    secondaryGlyphColor: 'rgba(9,9,11,0.7)',
    scoreInfoColor: '#09090b',
    staffLineColor: '#52525b',
    barSeparatorColor: '#27272a',
    barNumberColor: '#52525b',
  },
};

// When the cursor reaches a line outside the comfortable part of the view, that line is scrolled to this fraction
// of the view height from the top.
const SCROLL_TOP_RATIO = 0.25;

/**
 * Renders a tab with alphaTab and shows the current position. It has no playback of its own: the position comes
 * from the caller through the ref, `ref.current.setPosition(measure, beat)`, which is cheap enough to call on every
 * animation frame (it moves two absolutely positioned elements and scrolls only when the cursor reaches another line).
 *
 * - `source`: `{ alphaTex }` for an ASCII tab, or `{ fileUrl }` for a Guitar Pro file (e.g. a presigned URL). A new
 *   object loads the tab again; a source with neither is shown as an error.
 * - `onLoaded({ measureCount, beatsPerMeasure })`: once per loaded source, after its first render. `beatsPerMeasure`
 *   is the first measure's; the cursor itself follows each measure's own time signature.
 * - `onError(error)`: when the tab cannot be loaded or rendered.
 *
 * `measure` is 0-based; `beat` is the position within the measure in beats of its time signature, and may be
 * fractional (1.5 is halfway through the second beat). Out-of-range values are clamped; a non-finite beat is
 * ignored.
 *
 * The canvas is its own scroll container, so `className` must give it a bounded height (e.g. `h-[70vh]`). Changing
 * the app theme recolours the tab without loading it again.
 */
export default function TabCanvas({ source, onLoaded, onError, className = '', ref }) {
  const theme = useUiStore((state) => state.theme);
  const scrollRef = useRef(null);
  const hostRef = useRef(null);
  const barRef = useRef(null);
  const lineRef = useRef(null);
  // What the cursor needs between frames; mutated outside React rendering
  const viewRef = useRef({ api: null, geometry: new Map(), position: null, lineTop: null, measure: null });
  const callbacksRef = useRef({ onLoaded, onError });
  const themeRef = useRef(theme);
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    callbacksRef.current = { onLoaded, onError };
  }, [onLoaded, onError]);

  useImperativeHandle(ref, () => ({
    setPosition(measure, beat) {
      if (!Number.isFinite(measure) || !Number.isFinite(beat)) return;
      viewRef.current.position = { measure, beat };
      drawCursor(viewRef.current, scrollRef.current, barRef.current, lineRef.current);
    },
  }), []);

  useEffect(() => {
    if (!source || !hostRef.current) return undefined;
    const view = viewRef.current;
    let cancelled = false;
    let loaded = false;
    setStatus('loading');
    view.position = null;
    resetCursor(view, barRef.current, lineRef.current);

    const fail = (error) => {
      if (cancelled) return;
      setStatus('error');
      callbacksRef.current.onError?.(error);
    };
    if (!source.alphaTex && !source.fileUrl) {
      fail(new Error('The tab has no content that can be shown'));
      return undefined;
    }

    const api = new AlphaTabApi(hostRef.current, {
      // The whole tab is rendered up front, so every measure has bounds for the cursor. Rendering runs on the main
      // thread: it happens once per load, and alphaTab's web worker does not start under the Vite dev server.
      // (With workers, the bounds are only ready on postRenderFinished.)
      core: { enableLazyLoading: false, useWorkers: false },
      display: {
        layoutMode: LayoutMode.Page,
        staveProfile: StaveProfile.Tab,
        scale: 1.3,
        resources: COLORS[themeRef.current] ?? COLORS.dark,
      },
      player: { enablePlayer: false, enableCursor: false, enableUserInteraction: false },
    });
    view.api = api;

    // Also fires after re-renders for a resize or a theme change, which move the measures
    api.renderFinished.on(() => {
      if (cancelled) return;
      resetCursor(view, barRef.current, lineRef.current);
      setStatus('ready');
      if (!loaded) {
        loaded = true;
        const score = api.score;
        callbacksRef.current.onLoaded?.({
          measureCount: score?.masterBars.length ?? 0,
          beatsPerMeasure: score?.masterBars[0]?.timeSignatureNumerator ?? 4,
        });
      }
      drawCursor(view, scrollRef.current, barRef.current, lineRef.current);
    });
    api.error.on(fail);

    if (source.alphaTex) {
      api.tex(source.alphaTex);
    } else {
      fetch(source.fileUrl)
        .then((response) => {
          if (!response.ok) throw new Error(`The tab file could not be downloaded (${response.status})`);
          return response.arrayBuffer();
        })
        .then((buffer) => {
          if (!cancelled) api.load(new Uint8Array(buffer));
        })
        .catch(fail);
    }

    return () => {
      cancelled = true;
      api.destroy();
      view.api = null;
    };
  }, [source]);

  // A theme change recolours the rendered tab in place, so a Guitar Pro file is not downloaded again
  useEffect(() => {
    // Also runs on mount, when the instance was just created with this theme and is still rendering
    if (themeRef.current === theme) return;
    themeRef.current = theme;
    const api = viewRef.current.api;
    if (!api?.score) return;
    Object.assign(api.settings.display.resources, toColors(COLORS[theme] ?? COLORS.dark));
    api.updateSettings();
    api.render();
  }, [theme]);

  return (
    <div className={`relative ${className}`}>
      <div ref={scrollRef} className="absolute inset-0 overflow-y-auto">
        <div className="relative">
          <div ref={hostRef} />
          <div className="pointer-events-none absolute inset-0" aria-hidden="true">
            <div
              ref={barRef}
              className="absolute left-0 top-0 bg-amber-400/15"
              style={{ visibility: 'hidden', willChange: 'transform' }}
            />
            <div
              ref={lineRef}
              className="absolute left-0 top-0 -ml-px w-[3px] bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.8)]"
              style={{ visibility: 'hidden', willChange: 'transform' }}
            />
          </div>
        </div>
      </div>
      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center text-sm opacity-60">Loading tab…</div>
      )}
      {status === 'error' && (
        <div className="absolute inset-0 flex items-center justify-center text-sm text-red-500">
          The tab could not be shown.
        </div>
      )}
    </div>
  );
}

/**
 * The colour settings as alphaTab colour objects, for changing them on a running instance.
 */
function toColors(colors) {
  return Object.fromEntries(Object.entries(colors).map(([key, value]) => [key, model.Color.fromJson(value)]));
}

/**
 * Forgets measure positions after a render and hides the cursor until it is drawn again.
 */
function resetCursor(view, barElement, lineElement) {
  view.geometry = new Map();
  view.lineTop = null;
  view.measure = null;
  if (barElement) barElement.style.visibility = 'hidden';
  if (lineElement) lineElement.style.visibility = 'hidden';
}

/**
 * Moves the bar highlight and the cursor line to the view's position, and scrolls when the line changes. Per frame
 * only the line's transform changes; sizes are written when the measure changes.
 */
function drawCursor(view, scroller, barElement, lineElement) {
  const { api, position } = view;
  if (!position || !api?.score || !barElement || !lineElement) return;
  const measureCount = api.score.masterBars.length;
  const measure = Math.min(Math.max(Math.floor(position.measure), 0), measureCount - 1);
  const geometry = geometryOf(view, measure);
  if (!geometry) return;

  if (view.measure !== measure) {
    view.measure = measure;
    const barStyle = barElement.style;
    barStyle.transform = `translate3d(${geometry.barX}px, ${geometry.top}px, 0)`;
    barStyle.width = `${geometry.barW}px`;
    barStyle.height = `${geometry.height}px`;
    barStyle.visibility = 'visible';
    lineElement.style.height = `${geometry.height}px`;
    lineElement.style.visibility = 'visible';
  }
  const fraction = Math.min(Math.max(position.beat / geometry.beats, 0), 1);
  const x = geometry.startX + (geometry.endX - geometry.startX) * fraction;
  lineElement.style.transform = `translate3d(${x}px, ${geometry.top}px, 0)`;

  if (view.lineTop !== geometry.top) {
    view.lineTop = geometry.top;
    scrollToLine(scroller, geometry);
  }
}

/**
 * Where a measure is drawn, cached until the next render: the x range its beats use and its line's y range.
 * alphaTab's bounds are already scaled to the rendered size.
 */
function geometryOf(view, measure) {
  const cached = view.geometry.get(measure);
  if (cached) return cached;
  const { api } = view;
  const lookup = api.boundsLookup;
  const masterBar = api.score.masterBars[measure];
  const bounds = lookup?.findMasterBarByIndex(measure);
  if (!masterBar || !bounds) return null;

  // Notes start after a clef or time signature at the start of a line, so the cursor starts at the first beat
  const firstBeat = api.score.tracks[0]?.staves[0]?.bars[measure]?.voices[0]?.beats[0];
  const firstBeatBounds = firstBeat ? lookup.findBeat(firstBeat) : null;
  const bar = bounds.realBounds;
  const line = bounds.lineAlignedBounds;
  const geometry = {
    startX: firstBeatBounds ? firstBeatBounds.realBounds.x : bar.x,
    endX: bar.x + bar.w,
    barX: bar.x,
    barW: bar.w,
    top: line.y,
    height: line.h,
    beats: masterBar.timeSignatureNumerator,
  };
  view.geometry.set(measure, geometry);
  return geometry;
}

/**
 * Scrolls the line into the upper part of the view unless it is already comfortably visible.
 */
function scrollToLine(scroller, geometry) {
  if (!scroller) return;
  const viewTop = scroller.scrollTop;
  const comfortableBottom = viewTop + scroller.clientHeight * (1 - SCROLL_TOP_RATIO);
  if (geometry.top >= viewTop && geometry.top + geometry.height <= comfortableBottom) return;
  scroller.scrollTo({
    top: Math.max(geometry.top - scroller.clientHeight * SCROLL_TOP_RATIO, 0),
    behavior: 'smooth',
  });
}
