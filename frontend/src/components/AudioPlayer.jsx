import React, { useRef, useEffect, useState } from 'react';
import { Play, Pause, SkipBack, SkipForward, Volume2, Music, VolumeX, Repeat, Repeat1 } from 'lucide-react';
import usePlayerStore from '../store/playerStore';

const AudioPlayer = () => {
  const audioRef = useRef(null);
  const { 
    currentTrack, 
    isPlaying, 
    volume, 
    audioUrl, 
    togglePlayPause, 
    setVolume, 
    setIsPlaying,
    playNext,
    playPrevious,
    queue,
    loopMode,
    toggleLoopMode
  } = usePlayerStore();
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);

  // Sync isPlaying with audio element
  useEffect(() => {
    if (!audioRef.current || !audioUrl) return;

    if (isPlaying) {
      audioRef.current.play().catch(err => console.error("Playback failed", err));
    } else {
      audioRef.current.pause();
    }
  }, [isPlaying, audioUrl]);

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
      case 'one': return <Repeat1 size={20} className="text-primary" />;
      case 'all': return <Repeat size={20} className="text-primary" />;
      default: return <Repeat size={20} className="text-gray-400" />;
    }
  };

  return (
    <div className="fixed bottom-0 left-0 right-0 h-24 bg-dark border-t border-white/5 px-6 flex items-center justify-between z-50">
      <audio
        ref={audioRef}
        src={audioUrl}
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={handleLoadedMetadata}
        onEnded={() => playNext(false)}
        loop={isLooping}
      />

      {/* Track Info */}
      <div className="flex items-center gap-4 w-1/3">
        {currentTrack.coverUrl ? (
          <img 
            src={currentTrack.coverUrl} 
            alt={currentTrack.title} 
            className="w-14 h-14 rounded object-cover shadow-lg"
          />
        ) : (
          <div className="w-14 h-14 bg-white/10 rounded flex items-center justify-center">
            <Music className="text-gray-500" />
          </div>
        )}
        <div className="overflow-hidden">
          <p className="text-sm font-medium truncate text-white">{currentTrack.title}</p>
          <p className="text-xs text-gray-400 truncate">{currentTrack.authorName}</p>
        </div>
      </div>

      {/* Controls */}
      <div className="flex flex-col items-center gap-2 w-1/3">
        <div className="flex items-center gap-6">
          <button 
            onClick={toggleLoopMode}
            className="hover:scale-110 transition active:scale-95"
            title={`Loop: ${loopMode}`}
          >
            {renderLoopIcon()}
          </button>

          <button 
            onClick={handleManualSkipPrevious}
            disabled={!hasPrevious}
            className={`transition ${hasPrevious ? 'text-gray-400 hover:text-white' : 'text-gray-600 cursor-not-allowed'}`}
          >
            <SkipBack size={20} fill="currentColor" />
          </button>
          
          <button 
            onClick={togglePlayPause}
            className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition"
          >
            {isPlaying ? <Pause size={20} fill="currentColor" /> : <Play size={20} fill="currentColor" className="ml-1" />}
          </button>

          <button 
            onClick={handleManualSkipNext}
            disabled={!hasNext}
            className={`transition ${hasNext ? 'text-gray-400 hover:text-white' : 'text-gray-600 cursor-not-allowed'}`}
          >
            <SkipForward size={20} fill="currentColor" />
          </button>
        </div>

        <div className="w-full flex items-center gap-2 max-w-md">
          <span className="text-[10px] text-gray-400 w-8 text-right">
            {formatTime(audioRef.current?.currentTime)}
          </span>
          <input
            type="range"
            min="0"
            max="100"
            value={progress || 0}
            onChange={handleSeek}
            className="flex-1 h-1 bg-white/10 rounded-full appearance-none cursor-pointer accent-primary hover:accent-primary"
            style={{
              background: `linear-gradient(to right, #1DB954 ${progress}%, #333 ${progress}%)`
            }}
          />
          <span className="text-[10px] text-gray-400 w-8">
            {formatTime(duration)}
          </span>
        </div>
      </div>

      {/* Volume */}
      <div className="w-1/3 flex justify-end items-center gap-3">
        <button onClick={() => setVolume(volume === 0 ? 0.5 : 0)} className="text-gray-400 hover:text-white">
          {volume === 0 ? <VolumeX size={20} /> : <Volume2 size={20} />}
        </button>
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={volume}
          onChange={(e) => setVolume(parseFloat(e.target.value))}
          className="w-24 h-1 bg-white/10 rounded-full appearance-none cursor-pointer accent-white hover:accent-primary"
          style={{
            background: `linear-gradient(to right, #fff ${volume * 100}%, #333 ${volume * 100}%)`
          }}
        />
      </div>
    </div>
  );
};

export default AudioPlayer;
