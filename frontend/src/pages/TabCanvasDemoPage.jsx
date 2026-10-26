import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import axiosClient from '../api/axiosClient';
import TabCanvas from '../components/tabs/TabCanvas';

const DEMO_MEASURES = 128;
const BEATS_PER_MEASURE = 4;

/**
 * A long generated tab: an arpeggio over four chords, one per measure.
 */
function demoAlphaTex() {
  const chords = [
    ['0.5', '2.4', '2.3', '1.2', '0.1', '1.2', '2.3', '2.4'],
    ['3.6', '2.5', '0.4', '0.3', '0.2', '3.1', '0.2', '0.3'],
    ['x.6', '3.5', '2.4', '0.3', '1.2', '0.1', '1.2', '0.3'],
    ['3.6', '2.5', '0.4', '0.3', '3.2', '3.1', '3.2', '0.3'],
  ];
  const bars = Array.from({ length: DEMO_MEASURES }, (_, i) => `:8 ${chords[i % chords.length].join(' ')}`);
  return String.raw`\title "Canvas demo"
\track "Guitar"
\staff {tabs}
\tuning (E4 B3 G3 D3 A2 E2)
\ts (4 4) \tempo 120 ` + bars.join(' |\n') + ' |\n';
}

/**
 * Development page for the tab canvas (KAN-32): drives `setPosition` from requestAnimationFrame, as the sync engine
 * will, and shows the frame rate. `?tab=<id>` shows one of the user's tabs instead of the generated one.
 */
export default function TabCanvasDemoPage() {
  const [params] = useSearchParams();
  const tabId = params.get('tab');
  const canvasRef = useRef(null);
  const [tabSource, setTabSource] = useState(null);
  const [tempo, setTempo] = useState(120);
  const [playing, setPlaying] = useState(false);
  const [info, setInfo] = useState(null);
  const [fps, setFps] = useState(null);
  const [position, setPosition] = useState('1 : 1');
  const demoSource = useMemo(() => ({ alphaTex: demoAlphaTex() }), []);
  const source = tabId ? tabSource : demoSource;

  useEffect(() => {
    if (!tabId) return undefined;
    let stale = false;
    const load = async () => {
      const { data } = await axiosClient.get(`/api/tabs/${tabId}`);
      if (data.format === 'ASCII') return { alphaTex: data.alphaTex };
      const { data: url } = await axiosClient.get(`/api/tabs/${tabId}/url`);
      return { fileUrl: url };
    };
    // axiosClient already shows request errors
    load().then((loaded) => !stale && setTabSource(loaded)).catch(() => {});
    return () => {
      stale = true;
    };
  }, [tabId]);

  const handleLoaded = useCallback((loaded) => setInfo(loaded), []);

  // Lets a benchmark in the browser console drive the canvas: window.tabCanvasDemo.setPosition(measure, beat)
  useEffect(() => {
    window.tabCanvasDemo = canvasRef.current;
    return () => {
      delete window.tabCanvasDemo;
    };
  }, [info]);

  useEffect(() => {
    if (!playing || !info) return undefined;
    const start = performance.now();
    let frames = 0;
    let lastReport = start;
    let lastLabel = '';
    let handle;
    const step = (now) => {
      const beats = ((now - start) / 60000) * tempo;
      const beatsPerMeasure = info.beatsPerMeasure || BEATS_PER_MEASURE;
      const measure = Math.floor(beats / beatsPerMeasure) % info.measureCount;
      const beat = beats % beatsPerMeasure;
      canvasRef.current?.setPosition(measure, beat);

      frames++;
      if (now - lastReport >= 1000) {
        setFps(Math.round((frames * 1000) / (now - lastReport)));
        frames = 0;
        lastReport = now;
      }
      const label = `${measure + 1} : ${Math.floor(beat) + 1}`;
      if (label !== lastLabel) {
        lastLabel = label;
        setPosition(label);
      }
      handle = requestAnimationFrame(step);
    };
    handle = requestAnimationFrame(step);
    return () => cancelAnimationFrame(handle);
  }, [playing, info, tempo]);

  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <div className="flex flex-wrap items-center gap-4 text-sm">
        <h1 className="text-lg font-bold">Tab canvas demo</h1>
        <button
          type="button"
          className="border border-current px-4 py-1 font-bold disabled:opacity-40"
          disabled={!info}
          onClick={() => setPlaying((value) => !value)}
        >
          {playing ? 'Stop' : 'Play'}
        </button>
        <label className="flex items-center gap-2">
          Tempo
          <input
            type="number"
            min="20"
            max="300"
            value={tempo}
            onChange={(event) => setTempo(Number(event.target.value) || 120)}
            className="w-20 border border-current bg-transparent px-2 py-1"
          />
        </label>
        <span>Measures: {info?.measureCount ?? '…'}</span>
        <span>Position (measure : beat): {position}</span>
        <span>Frame rate: {fps ?? '–'} fps</span>
      </div>
      <TabCanvas ref={canvasRef} source={source} onLoaded={handleLoaded} className="h-[calc(100vh-10rem)] border" />
    </div>
  );
}
