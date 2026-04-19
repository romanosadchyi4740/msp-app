import React from 'react';
import { Play, Music, MessageCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import usePlayerStore from '../store/playerStore';
import useChatStore from '../store/chatStore';

const TrackCard = ({ track, queue = [] }) => {
  const navigate = useNavigate();
  const { playTrack, currentTrack, isPlaying } = usePlayerStore();
  const { startChatWithUser } = useChatStore();
  const isCurrentTrack = currentTrack?.id === track.id;

  const handleStartChat = async (e) => {
    e.stopPropagation();
    try {
      await startChatWithUser(track.authorId);
      navigate('/chat');
    } catch (error) {
      console.error("Could not start chat", error);
    }
  };

  return (
    <div className="group bg-white/5 p-4 rounded-xl hover:bg-white/10 transition-all duration-300 relative overflow-hidden border border-transparent hover:border-white/10">
      <div className="relative aspect-square mb-4">
        {track.coverUrl ? (
          <img 
            src={track.coverUrl} 
            alt={track.title} 
            className="w-full h-full object-cover rounded-lg shadow-2xl"
          />
        ) : (
          <div className="w-full h-full bg-white/5 rounded-lg flex items-center justify-center">
            <Music size={48} className="text-white/20" />
          </div>
        )}
        
        {/* Play Button Overlay */}
        <button
          onClick={() => playTrack(track, queue)}
          className={`absolute bottom-2 right-2 w-12 h-12 rounded-full bg-primary flex items-center justify-center text-black shadow-xl 
            transition-all duration-300 transform 
            ${isCurrentTrack && isPlaying ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-2 scale-90 group-hover:opacity-100 group-hover:translate-y-0 group-hover:scale-100'}
            hover:scale-110 active:scale-95`}
        >
          <Play size={24} fill="currentColor" className="ml-1" />
        </button>
      </div>

      <div className="space-y-1">
        <h3 className="font-semibold truncate text-white group-hover:text-primary transition-colors">
          {track.title}
        </h3>
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm text-gray-400 truncate flex-1">
            {track.authorName}
          </p>
          <button 
            onClick={handleStartChat}
            className="text-gray-500 hover:text-primary transition-colors p-1"
            title="Message Artist"
          >
            <MessageCircle size={16} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default TrackCard;
