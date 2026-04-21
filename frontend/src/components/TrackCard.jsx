import React from 'react';
import { Play, Pause, Music, MessageCircle, Heart } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import usePlayerStore from '../store/playerStore';
import useChatStore from '../store/chatStore';
import useLikeStore from '../store/likeStore';

const TrackCard = ({ track, queue = [] }) => {
  const navigate = useNavigate();
  const { playTrack, currentTrack, isPlaying } = usePlayerStore();
  const { startChatWithUser } = useChatStore();
  const { likedTrackIds, toggleLike } = useLikeStore();
  const isCurrentTrack = currentTrack?.id === track.id;
  const isLiked = !!likedTrackIds[track.id];

  const handleStartChat = async (e) => {
    e.stopPropagation();
    try {
      await startChatWithUser(track.authorId);
      navigate('/chat');
    } catch (error) {
      console.error("Could not start chat", error);
    }
  };

  const handleLike = (e) => {
    e.stopPropagation();
    toggleLike(track.id);
  };

  return (
    <div className="group transition-all duration-300 relative">
      <div className="relative aspect-square mb-3 overflow-hidden bg-surface">
        {track.coverUrl ? (
          <img 
            src={track.coverUrl} 
            alt={track.title} 
            className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 group-hover:brightness-100 transition-all duration-700"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center border border-border">
            <Music size={32} className="text-zinc-800" />
          </div>
        )}
        
        {/* Minimal Play Overlay */}
        <button
          onClick={() => playTrack(track, queue)}
          className={`absolute inset-0 flex items-center justify-center bg-background/40 backdrop-blur-sm transition-opacity duration-300
            ${isCurrentTrack && isPlaying ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`}
        >
          <div className="w-12 h-12 border border-white flex items-center justify-center text-white">
            {isCurrentTrack && isPlaying ? <Pause size={20} /> : <Play size={20} className="ml-1" />}
          </div>
        </button>
      </div>

      <div className="space-y-0.5">
        <h3 className="text-[11px] font-black uppercase tracking-tight text-white truncate">
          {track.title}
        </h3>
        <div className="flex items-center justify-between gap-2">
          <p className="text-[10px] font-medium uppercase tracking-widest text-muted truncate">
            {track.authorName}
          </p>
          <div className="flex items-center gap-2">
            <button 
              onClick={handleLike}
              className={`transition-colors ${isLiked ? 'text-white' : 'text-muted hover:text-white'}`}
            >
              <Heart size={12} fill={isLiked ? "currentColor" : "none"} />
            </button>
            <button 
              onClick={handleStartChat}
              className="text-muted hover:text-white transition-colors"
            >
              <MessageCircle size={12} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TrackCard;