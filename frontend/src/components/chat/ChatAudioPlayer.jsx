import React, { useState, useRef, useEffect } from 'react';
import { Play, Pause, Loader2, Download } from 'lucide-react';
import usePlayerStore from '../../store/playerStore';
import useChatAudioStore from '../../store/chatAudioStore';
import axiosClient from '../../api/axiosClient';

const ChatAudioPlayer = ({ url, id }) => {
  const audioRef = useRef(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [presignedUrl, setPresignedUrl] = useState(null);
  const [loading, setLoading] = useState(false);

  const { pauseForGlobalAudio } = usePlayerStore();
  const { activeMessageId, setActiveMessageId } = useChatAudioStore();

  // Fetch presigned URL once when component mounts
  useEffect(() => {
    const fetchUrl = async () => {
      try {
        setLoading(true);
        const res = await axiosClient.get('/api/messages/url', { params: { path: url } });
        setPresignedUrl(res.data);
      } catch (err) {
        console.error("Failed to load message audio", err);
      } finally {
        setLoading(false);
      }
    };
    if (url) fetchUrl();
  }, [url]);

  // Pause if another chat audio starts
  useEffect(() => {
    if (activeMessageId !== id && isPlaying) {
      audioRef.current?.pause();
      // It's better to update state based on events or let the button handle it, but here we can just let handlePlayPause logic or events sync.
      // Wait, we can safely call setIsPlaying here if it's the external trigger stopping us.
      setIsPlaying(false);
    }
  }, [activeMessageId, id, isPlaying]);

  // Listen to global player store. If global player starts, pause chat audio.
  useEffect(() => {
    const unsub = usePlayerStore.subscribe((state) => {
      if (state.isPlaying && isPlaying) {
        audioRef.current?.pause();
        setIsPlaying(false);
      }
    });
    return unsub;
  }, [isPlaying]);

  const handlePlayPause = () => {
    if (!audioRef.current || !presignedUrl) return;

    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      pauseForGlobalAudio(); // Stop global audio
      setActiveMessageId(id); // Stop other chat audios
      audioRef.current.play().catch(err => console.error("Chat audio play failed", err));
      setIsPlaying(true);
    }
  };

  const handleTimeUpdate = () => {
    if (audioRef.current) {
      const current = audioRef.current.currentTime;
      const total = audioRef.current.duration;
      if (total) {
        setProgress((current / total) * 100);
      }
    }
  };

  const handleLoadedMetadata = () => {
    if (audioRef.current) {
      setDuration(audioRef.current.duration);
    }
  };

  const handleSeek = (e) => {
    const seekTime = (e.target.value / 100) * duration;
    if (audioRef.current) {
      audioRef.current.currentTime = seekTime;
    }
  };

  const handleEnded = () => {
    setIsPlaying(false);
    setProgress(0);
    if (audioRef.current) {
      audioRef.current.currentTime = 0;
    }
  };

  const formatTime = (time) => {
    if (isNaN(time)) return "0:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const handleDownload = async () => {
    if (!presignedUrl) return;
    try {
      const response = await fetch(presignedUrl);
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      const filename = url.split('/').pop() || 'audio-message.mp3';
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (err) {
      console.error("Failed to download audio via fetch, falling back to window.open", err);
      window.open(presignedUrl, '_blank');
    }
  };

  return (
    <div className="flex items-center gap-3 bg-surface border border-border p-2 rounded-sm w-[280px]">
      {presignedUrl && (
        <audio
          ref={audioRef}
          src={presignedUrl}
          onTimeUpdate={handleTimeUpdate}
          onLoadedMetadata={handleLoadedMetadata}
          onEnded={handleEnded}
        />
      )}
      
      <button 
        onClick={handlePlayPause}
        disabled={loading || !presignedUrl}
        className="w-8 h-8 flex-shrink-0 bg-white text-black flex items-center justify-center hover:bg-zinc-200 transition-colors disabled:opacity-50"
      >
        {loading ? <Loader2 size={14} className="animate-spin" /> : isPlaying ? <Pause size={14} /> : <Play size={14} className="ml-0.5" />}
      </button>
      
      <div className="flex-1 relative h-6 flex items-center">
        <input
          type="range"
          min="0"
          max="100"
          value={progress || 0}
          onChange={handleSeek}
          disabled={!presignedUrl}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10 disabled:cursor-not-allowed"
        />
        <div className="w-full h-1 bg-zinc-800 relative pointer-events-none">
          <div 
            className="absolute h-full bg-white transition-all duration-100" 
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
      
      <span className="text-[9px] font-medium text-muted tabular-nums flex-shrink-0 w-7 text-right">
        {formatTime((progress / 100) * duration || 0)}
      </span>

      <button 
        onClick={handleDownload}
        disabled={loading || !presignedUrl}
        className="text-muted hover:text-white transition-colors disabled:opacity-50 p-1 flex-shrink-0"
        title="Download Audio"
      >
        <Download size={14} />
      </button>
    </div>
  );
};

export default ChatAudioPlayer;
