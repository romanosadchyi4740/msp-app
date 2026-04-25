import React, { useRef, useEffect, useState } from 'react';
import { Play, Pause, SkipBack, SkipForward, Volume2, Music, VolumeX, Repeat, Repeat1, Heart, MessageSquare } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import usePlayerStore from '../store/playerStore';
import useLikeStore from '../store/likeStore';
import useChatStore from '../store/chatStore';

const AudioPlayer = () => {
  const audioRef = useRef(null);
  const navigate = useNavigate();
  const { 
    currentTrack, 
    isPlaying, 
    volume, 
    audioUrl, 
    initialSeekTime,
    togglePlayPause, 
    setVolume,
    toggleMute,
    setIsPlaying,
    playNext,
    playPrevious,
    queue,
    loopMode,
    toggleLoopMode
  } = usePlayerStore();
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);

  const { likedTrackIds, toggleLike } = useLikeStore();
  const { setAttachment, startChatWithUser, openShareModal } = useChatStore();
  const isLiked = currentTrack ? !!likedTrackIds[currentTrack.id] : false;

  // Sync isPlaying with audio element
  useEffect(() => {
    if (!audioRef.current || !audioUrl) return;

    if (isPlaying) {
      audioRef.current.play().catch(err => console.error("Playback failed", err));
    } else {
      audioRef.current.pause();
    }
  }, [isPlaying, audioUrl]);

  // Handle initialSeekTime
  useEffect(() => {
    if (audioRef.current && initialSeekTime !== null) {
      const attemptSeek = () => {
        if (audioRef.current.readyState >= 1) { // HAVE_METADATA or higher
          audioRef.current.currentTime = initialSeekTime;
          usePlayerStore.setState({ initialSeekTime: null });
        } else {
          const handleCanPlay = () => {
            audioRef.current.currentTime = initialSeekTime;
            usePlayerStore.setState({ initialSeekTime: null });
            audioRef.current.removeEventListener('loadedmetadata', handleCanPlay);
          };
          audioRef.current.addEventListener('loadedmetadata', handleCanPlay);
        }
      };
      attemptSeek();
    }
  }, [audioUrl, initialSeekTime]);

  // Sync volume with audio element
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
  }, [volume]);

  const handleTimeUpdate = () => {
    if (audioRef.current) {
      const current = audioRef.current.currentTime;
      const total = audioRef.current.duration;
      setProgress((current / total) * 100);
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

  const formatTime = (time) => {
    if (isNaN(time)) return "0:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const handleShareToChat = async () => {
    if (!currentTrack) return;
    const currentAudioTime = audioRef.current ? Math.floor(audioRef.current.currentTime) : 0;
    openShareModal(currentTrack, currentAudioTime);
  };

  if (!currentTrack) return null;

  const currentIndex = queue.findIndex(t => t.id === currentTrack.id);
  const hasNext = loopMode === 'all' || (currentIndex !== -1 && currentIndex < queue.length - 1);
  const hasPrevious = loopMode === 'all' || currentIndex > 0;

  const isLooping = loopMode === 'one' || (loopMode === 'all' && queue.length === 1);

  const handleManualSkipNext = () => {
    const beforeId = currentTrack?.id;
    playNext(true);
    // If the track didn't change (e.g. single track queue), manual restart
    if (usePlayerStore.getState().currentTrack?.id === beforeId) {
      audioRef.current.currentTime = 0;
      audioRef.current.play();
    }
  };

  const handleManualSkipPrevious = () => {
    const beforeId = currentTrack?.id;
    playPrevious();
    if (usePlayerStore.getState().currentTrack?.id === beforeId) {
      audioRef.current.currentTime = 0;
      audioRef.current.play();
    }
  };

  const renderLoopIcon = () => {
    switch (loopMode) {
      case 'one': return <Repeat1 size={16} className="text-white" />;
      case 'all': return <Repeat size={16} className="text-white" />;
      default: return <Repeat size={16} className="text-muted" />;
    }
  };

  return (
    <div className="fixed bottom-6 left-1/2 -translate-x-1/2 w-[calc(100%-3rem)] max-w-6xl bg-surface/90 backdrop-blur-xl border border-border px-8 py-3 flex items-center justify-between z-50 rounded-none shadow-2xl">
      <audio
        ref={audioRef}
        src={audioUrl}
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={handleLoadedMetadata}
        onEnded={() => playNext(false)}
        loop={isLooping}
      />

      {/* Track Info */}
      <div className="flex items-center gap-4 w-1/4">
        {currentTrack.coverUrl ? (
          <img 
            src={currentTrack.coverUrl} 
            alt={currentTrack.title} 
            className="w-10 h-10 object-cover grayscale brightness-90 hover:grayscale-0 transition-all duration-500"
          />
        ) : (
          <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center">
            <Music size={16} className="text-muted" />
          </div>
        )}
        <div className="overflow-hidden flex-1">
          <p className="text-[11px] font-black uppercase tracking-tight truncate text-white">{currentTrack.title}</p>
          <p className="text-[10px] uppercase tracking-widest text-muted truncate">{currentTrack.authorName}</p>
        </div>
        <div className="flex items-center gap-2">
          <button 
            onClick={() => toggleLike(currentTrack.id)}
            className={`transition-colors ${isLiked ? 'text-white' : 'text-muted hover:text-white'}`}
          >
            <Heart size={14} fill={isLiked ? "currentColor" : "none"} />
          </button>
          <button 
            onClick={handleShareToChat}
            className="text-muted hover:text-white transition-colors"
          >
            <MessageSquare size={14} />
          </button>
        </div>
      </div>

      {/* Controls */}
      <div className="flex flex-col items-center gap-1 w-2/4 px-8">
        <div className="flex items-center gap-8 mb-1">
          <button 
            onClick={toggleLoopMode}
            className="hover:text-white transition-colors"
            title={`Loop: ${loopMode}`}
          >
            {loopMode !== 'none' ? <Repeat size={16} className="text-white" /> : <Repeat size={16} className="text-muted" />}
          </button>

          <button 
            onClick={handleManualSkipPrevious}
            disabled={!hasPrevious}
            className={`transition-colors ${hasPrevious ? 'text-muted hover:text-white' : 'text-zinc-800 cursor-not-allowed'}`}
          >
            <SkipBack size={18} />
          </button>
          
          <button 
            onClick={togglePlayPause}
            className="text-white hover:scale-110 transition-transform p-2"
          >
            {isPlaying ? <Pause size={24} /> : <Play size={24} className="ml-1" />}
          </button>

          <button 
            onClick={handleManualSkipNext}
            disabled={!hasNext}
            className={`transition-colors ${hasNext ? 'text-muted hover:text-white' : 'text-zinc-800 cursor-not-allowed'}`}
          >
            <SkipForward size={18} />
          </button>

          <button 
            onClick={toggleMute} 
            className="text-muted hover:text-white transition-colors"
          >
            {volume === 0 ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </button>
        </div>

        <div className="w-full flex items-center gap-3">
          <span className="text-[9px] font-medium text-muted w-10 text-right tabular-nums">
            {formatTime((progress / 100) * duration || 0)}
          </span>
          <div className="flex-1 relative group h-4 flex items-center">
            <input
              type="range"
              min="0"
              max="100"
              value={progress || 0}
              onChange={handleSeek}
              className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
            />
            <div className="w-full h-[1px] bg-zinc-800 relative">
              <div 
                className="absolute h-full bg-white transition-all duration-100" 
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
          <span className="text-[9px] font-medium text-muted w-10 tabular-nums">
            {formatTime(duration)}
          </span>
        </div>
      </div>

      {/* Volume - Simplified for slim look */}
      <div className="w-1/4 flex justify-end items-center gap-4">
        <div className="w-24 relative group h-4 flex items-center">
          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            value={volume}
            onChange={(e) => setVolume(parseFloat(e.target.value))}
            className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
          />
          <div className="w-full h-[1px] bg-zinc-800 relative">
            <div 
              className="absolute h-full bg-white transition-all duration-100" 
              style={{ width: `${volume * 100}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default AudioPlayer;
AudioPlayer;
