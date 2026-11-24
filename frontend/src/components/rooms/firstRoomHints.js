/**
 * The hints shown on a user's first room visit (KAN-53), by room role. Each hint is `{ id, title, text }`, plus
 * `link: { label, to }` where it points somewhere.
 */
export function roomHints({ isHost, roomPath }) {
  const audio = {
    id: 'audio',
    title: 'Talk and play',
    text: 'Turn your microphone on in the audio panel and use headphones: echo cancellation is off so the guitar '
      + 'sounds as it is.',
    link: { label: 'Check your microphone and output', to: `/device-check?next=${encodeURIComponent(roomPath)}` },
  };
  if (isHost) {
    return [
      {
        id: 'invite',
        title: 'Invite your students',
        text: 'Send the invite link from the side panel. Up to four participants can join you.',
      },
      {
        id: 'lesson',
        title: 'Start the lesson',
        text: 'Pick a tab and start. Only you play, pause, seek, change the tempo and switch the metronome; '
          + 'everyone\'s tab scrolls with yours and hears the same click.',
      },
      audio,
    ];
  }
  return [
    {
      id: 'follow',
      title: 'Follow the host',
      text: 'The host starts the lesson and controls playback. Your tab scrolls and the metronome clicks in time '
        + 'with everyone, so just play along.',
    },
    audio,
    {
      id: 'chat',
      title: 'Ask in the chat',
      text: 'Use the room chat in the side panel for questions, links and fingerings.',
    },
  ];
}
