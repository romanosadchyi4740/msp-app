import { Link } from 'react-router-dom';
import { Headphones, Loader2, Mic, MicOff, Volume2 } from 'lucide-react';
import useRtcStore from '../../store/rtcStore';
import { disableMicrophone, enableMicrophone, resumeSound } from '../../rtc/rtcMesh';
import { badgeClass, primaryButtonClass, secondaryButtonClass, sectionTitleClass } from './roomStyles';

/**
 * This participant's side of the room's audio (KAN-47): the microphone switch, the button browsers require before
 * they play the others, and the advice to use headphones. Listening needs no microphone.
 */
export default function AudioPanel() {
  const micState = useRtcStore((state) => state.micState);
  const micError = useRtcStore((state) => state.micError);
  const soundBlocked = useRtcStore((state) => state.soundBlocked);
  const relayOnly = useRtcStore((state) => state.relayOnly);
  const micOn = micState === 'on';

  return (
    <section className="space-y-3">
      <h2 className={sectionTitleClass}>Audio</h2>
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => (micOn ? disableMicrophone() : enableMicrophone())}
          disabled={micState === 'starting'}
          aria-pressed={micOn}
          className={micOn ? primaryButtonClass : secondaryButtonClass}
        >
          {micState === 'starting' && <Loader2 size={14} className="animate-spin" />}
          {micState !== 'starting' && (micOn ? <Mic size={14} /> : <MicOff size={14} />)}
          {micOn ? 'Microphone on' : 'Turn microphone on'}
        </button>
        {soundBlocked && (
          <button type="button" onClick={() => resumeSound()} className={secondaryButtonClass}>
            <Volume2 size={14} /> Enable sound
          </button>
        )}
        {relayOnly && (
          <Link to="/settings" className={badgeClass} title="Audio goes only through the relay server">
            Relay only
          </Link>
        )}
      </div>
      {micState === 'error' && micError && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">{micError}</p>
      )}
      <p className="flex items-start gap-2 text-xs text-zinc-500 dark:text-zinc-400">
        <Headphones size={14} className="mt-0.5 shrink-0" />
        Use headphones. Echo cancellation and noise suppression are off so the guitar sounds as it is, which means
        speakers would feed the others&apos; sound back into your microphone.
      </p>
    </section>
  );
}
