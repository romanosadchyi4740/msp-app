import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangle, Check, Headphones, Loader2, Mic, RefreshCw, Volume2, X } from 'lucide-react';
import useRtcStore from '../store/rtcStore';
import { openMicrophone } from '../rtc/rtcMesh';
import {
  availableDevice, browserSupport, groupDevices, HEARD_LEVEL, isCompatible, levelOf, MICROPHONE_STOPPED,
  microphoneProblem, requiredChecks, safeNextPath,
} from '../rtc/deviceCheck';
import { primaryButtonClass, secondaryButtonClass, sectionTitleClass } from '../components/rooms/roomStyles';

const selectClass = 'w-full border border-zinc-300 bg-transparent p-3 text-sm dark:border-zinc-700 '
  + 'dark:bg-zinc-950';

/**
 * The device check before a room (KAN-49, UR-2, FR-6): whether this browser can take part in a room's audio, the
 * microphone with a live level meter, which microphone and output to use, a test sound, and the advice to use
 * headphones. What is wrong comes with what to do about it and a "Check again" button. The chosen devices are kept
 * in this browser and used by the room. `?next=<path>` adds a button that goes on to that page, e.g. the invite.
 */
export default function DeviceCheckPage() {
  const [searchParams] = useSearchParams();
  const next = safeNextPath(searchParams.get('next'), window.location.origin);
  const inputDeviceId = useRtcStore((state) => state.inputDeviceId);
  const outputDeviceId = useRtcStore((state) => state.outputDeviceId);
  const setInputDevice = useRtcStore((state) => state.setInputDevice);
  const setOutputDevice = useRtcStore((state) => state.setOutputDevice);

  const [support, setSupport] = useState(() => browserSupport(window));
  // 'idle' until the user allows the microphone, then 'asking', 'on' or 'error' (with `problem`)
  const [microphone, setMicrophone] = useState({ status: 'idle', problem: null });
  const [devices, setDevices] = useState({ inputs: [], outputs: [] });
  const [heard, setHeard] = useState(false);
  // 'played' after the test sound, 'failed' if the browser could not play it
  const [tone, setTone] = useState(null);

  const meter = useRef(null);
  // What has to be released: the microphone, its audio graph and the meter's animation frame
  const session = useRef({ stream: null, context: null, frame: null });
  // Counts the releases: a microphone that arrives after the page was left, or after another start, is not kept
  const generation = useRef(0);

  const release = useCallback(() => {
    generation.current += 1;
    const { stream, context, frame } = session.current;
    cancelAnimationFrame(frame);
    stream?.getTracks().forEach((track) => {
      track.onended = null;
      track.stop();
    });
    context?.close().catch(() => {});
    session.current = { stream: null, context: null, frame: null };
  }, []);

  // A saved device that is not in the list (unplugged, or not named yet before the microphone is allowed) stays
  // saved: the selects show the system's default for it, and the room falls back to the default by itself
  const refreshDevices = useCallback(async () => {
    if (!navigator.mediaDevices?.enumerateDevices) return;
    const found = groupDevices(await navigator.mediaDevices.enumerateDevices());
    setDevices(found);
  }, []);

  const startMicrophone = useCallback(async (deviceId) => {
    release();
    const started = generation.current;
    setHeard(false);
    setMicrophone({ status: 'asking', problem: null });
    try {
      const stream = await openMicrophone(deviceId);
      if (generation.current !== started) {
        // The page was left, or the check started again, while the browser was asking
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      const context = new AudioContextClass();
      // Created after the permission prompt, so outside the click: some browsers start it suspended
      context.resume().catch(() => {});
      const analyser = context.createAnalyser();
      analyser.fftSize = 1024;
      context.createMediaStreamSource(stream).connect(analyser);
      // Through a silent output, since older Safari does not run nodes that lead nowhere
      const silent = context.createGain();
      silent.gain.value = 0;
      analyser.connect(silent).connect(context.destination);
      const samples = new Float32Array(analyser.fftSize);
      const draw = () => {
        session.current.frame = requestAnimationFrame(draw);
        analyser.getFloatTimeDomainData(samples);
        const level = levelOf(samples);
        if (meter.current) {
          meter.current.style.width = `${Math.round(level * 100)}%`;
          meter.current.dataset.level = level.toFixed(2);
        }
        if (level > HEARD_LEVEL) setHeard(true);
      };
      session.current = { stream, context, frame: requestAnimationFrame(draw) };
      // The microphone was unplugged, or the permission taken back
      stream.getAudioTracks()[0].onended = () => {
        release();
        setMicrophone({ status: 'error', problem: MICROPHONE_STOPPED });
      };
      setMicrophone({ status: 'on', problem: null });
    } catch (error) {
      if (generation.current !== started) return;
      release();
      setMicrophone({ status: 'error', problem: microphoneProblem(error?.name) });
      return;
    }
    // Browsers name the devices only once the microphone is allowed; a failure here leaves the microphone on
    await refreshDevices().catch(() => {});
  }, [release, refreshDevices]);

  // A returning user who already allowed the microphone is not asked to click again; nobody gets a permission
  // prompt just for opening the page
  useEffect(() => {
    let current = true;
    if (isCompatible(browserSupport(window))) {
      navigator.permissions?.query({ name: 'microphone' })
        .then((permission) => {
          if (current && permission.state === 'granted') startMicrophone(useRtcStore.getState().inputDeviceId);
        })
        // Firefox and Safari do not tell; the button asks
        .catch(() => {});
    }
    return () => {
      current = false;
      release();
    };
  }, [startMicrophone, release]);

  useEffect(() => {
    const mediaDevices = navigator.mediaDevices;
    if (!mediaDevices?.addEventListener) return undefined;
    // Plugging headphones in or out changes the lists
    const onChange = () => refreshDevices().catch(() => {});
    mediaDevices.addEventListener('devicechange', onChange);
    return () => mediaDevices.removeEventListener('devicechange', onChange);
  }, [refreshDevices]);

  const checkAgain = () => {
    const current = browserSupport(window);
    setSupport(current);
    setTone(null);
    if (isCompatible(current)) startMicrophone(useRtcStore.getState().inputDeviceId);
  };

  const chooseInput = (deviceId) => {
    setInputDevice(deviceId);
    startMicrophone(deviceId);
  };

  const playTestSound = async () => {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    const context = new AudioContextClass();
    try {
      const { outputDeviceId: output } = useRtcStore.getState();
      // A chosen output that is gone, or that the browser cannot use for this, plays on the default one
      if (output) await context.setSinkId?.(output).catch(() => {});
      await context.resume();
      // Two short tones, an octave apart, on the audio clock
      [440, 880].forEach((frequency, index) => {
        const start = context.currentTime + 0.05 + index * 0.35;
        const oscillator = context.createOscillator();
        const envelope = context.createGain();
        oscillator.frequency.value = frequency;
        envelope.gain.setValueAtTime(0, start);
        envelope.gain.linearRampToValueAtTime(0.3, start + 0.02);
        envelope.gain.linearRampToValueAtTime(0, start + 0.25);
        oscillator.connect(envelope).connect(context.destination);
        oscillator.start(start);
        oscillator.stop(start + 0.3);
      });
      setTone('played');
    } catch {
      setTone('failed');
    } finally {
      setTimeout(() => context.close().catch(() => {}), 1200);
    }
  };

  const checks = requiredChecks(support);
  const compatible = isCompatible(support);
  const micOn = microphone.status === 'on';
  const ready = compatible && micOn;

  return (
    <div className="max-w-2xl space-y-10 pb-32">
      <header className="border-b border-zinc-200 pb-8 dark:border-zinc-800">
        <h1 className="text-5xl font-black tracking-tighter">Device check</h1>
        <p className="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
          Make sure the others will hear you, and you them, before you join a room.
        </p>
      </header>

      <section className="space-y-3">
        <h2 className={sectionTitleClass}>Browser</h2>
        <ul className="space-y-2">
          {checks.map((check) => (
            <li key={check.id} data-check={check.id} data-ok={check.ok} className="space-y-1">
              <span className="flex items-center gap-2 text-sm">
                {check.ok
                  ? <Check size={16} className="shrink-0 text-emerald-600 dark:text-emerald-500" aria-label="OK" />
                  : <X size={16} className="shrink-0 text-red-600 dark:text-red-500" aria-label="Missing" />}
                {check.label}
              </span>
              {!check.ok && <p className="pl-6 text-xs text-zinc-500 dark:text-zinc-400">{check.hint}</p>}
            </li>
          ))}
        </ul>
        {!compatible && (
          <p role="alert" className="flex items-start gap-2 text-sm font-bold text-red-600 dark:text-red-500">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            This browser cannot take part in a room&apos;s audio yet. Follow the steps above, then check again.
          </p>
        )}
      </section>

      {compatible && (
        <section className="space-y-4">
          <h2 className={sectionTitleClass}>Microphone</h2>
          {!micOn && (
            <button
              type="button"
              onClick={() => startMicrophone(inputDeviceId)}
              disabled={microphone.status === 'asking'}
              className={primaryButtonClass}
            >
              {microphone.status === 'asking' ? <Loader2 size={14} className="animate-spin" /> : <Mic size={14} />}
              Allow microphone
            </button>
          )}
          {microphone.status === 'error' && (
            <div role="alert" className="space-y-1">
              <p className="text-sm font-bold text-red-600 dark:text-red-500">{microphone.problem.message}</p>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">{microphone.problem.hint}</p>
            </div>
          )}
          {micOn && (
            <>
              <div>
                <div
                  className="h-3 w-full overflow-hidden border border-zinc-300 dark:border-zinc-700"
                  role="img"
                  aria-label="Microphone level"
                >
                  <div ref={meter} data-level="0" className="h-full w-0 bg-emerald-500" />
                </div>
                <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400" data-heard={heard}>
                  {heard
                    ? 'The microphone hears you.'
                    : 'Play a note or say something: the bar should move.'}
                </p>
              </div>
              {devices.inputs.length > 0 && (
                <div className="space-y-2">
                  <label htmlFor="device-check-input" className="block text-xs text-zinc-500 dark:text-zinc-400">
                    Microphone
                  </label>
                  <select
                    id="device-check-input"
                    value={availableDevice(inputDeviceId, devices.inputs)}
                    onChange={(event) => chooseInput(event.target.value)}
                    className={selectClass}
                  >
                    <option value="">System default</option>
                    {devices.inputs.map((device) => (
                      <option key={device.deviceId} value={device.deviceId}>{device.label}</option>
                    ))}
                  </select>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {compatible && (
        <section className="space-y-4">
          <h2 className={sectionTitleClass}>Sound</h2>
          {support.outputSelection && devices.outputs.length > 0 ? (
            <div className="space-y-2">
              <label htmlFor="device-check-output" className="block text-xs text-zinc-500 dark:text-zinc-400">
                Headphones or speakers
              </label>
              <select
                id="device-check-output"
                value={availableDevice(outputDeviceId, devices.outputs)}
                onChange={(event) => setOutputDevice(event.target.value)}
                className={selectClass}
              >
                <option value="">System default</option>
                {devices.outputs.map((device) => (
                  <option key={device.deviceId} value={device.deviceId}>{device.label}</option>
                ))}
              </select>
            </div>
          ) : (
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              {support.outputSelection && !micOn
                ? 'Allow the microphone to choose where the sound plays.'
                : 'This browser plays on the output chosen in your system\'s sound settings.'}
            </p>
          )}
          <button type="button" onClick={playTestSound} className={secondaryButtonClass}>
            <Volume2 size={14} /> Play test sound
          </button>
          {tone === 'failed' && (
            <p role="alert" className="text-xs text-red-600 dark:text-red-500">
              The browser could not play the test sound. Check the output in your system's sound settings.
            </p>
          )}
          {tone === 'played' && (
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              You should have heard two short tones. If not, check the volume and the output above.
            </p>
          )}
          <p className="flex items-start gap-2 text-xs text-zinc-500 dark:text-zinc-400">
            <Headphones size={14} className="mt-0.5 shrink-0" />
            Plug in headphones. Echo cancellation is off so the guitar sounds as it is; with speakers the others
            would hear themselves through your microphone.
          </p>
        </section>
      )}

      <div className="flex flex-wrap items-center gap-3 border-t border-zinc-200 pt-8 dark:border-zinc-800">
        <button
          type="button"
          onClick={checkAgain}
          disabled={microphone.status === 'asking'}
          className={secondaryButtonClass}
        >
          <RefreshCw size={14} /> Check again
        </button>
        {next && (
          <Link to={next} className={ready ? primaryButtonClass : secondaryButtonClass}>
            {ready ? 'Continue' : 'Continue anyway'}
          </Link>
        )}
        <span className="text-xs text-zinc-500 dark:text-zinc-400" data-ready={ready}>
          {ready && 'Everything a room needs works.'}
          {!ready && compatible && 'You can listen in a room without a microphone.'}
          {!compatible && 'In a room you would neither hear the others nor be heard.'}
        </span>
      </div>
    </div>
  );
}
