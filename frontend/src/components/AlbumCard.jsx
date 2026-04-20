import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Music, Disc } from 'lucide-react';

const AlbumCard = ({ album }) => {
  const navigate = useNavigate();

  return (
    <div 
      onClick={() => navigate(`/albums/${album.id}`)}
      className="group cursor-pointer transition-all duration-300"
    >
      <div className="relative aspect-square mb-4 overflow-hidden bg-surface">
        {album.coverUrl ? (
          <img 
            src={album.coverUrl} 
            alt={album.title} 
            className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 group-hover:brightness-100 group-hover:scale-105 transition-all duration-700"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center border border-border">
            <Disc size={48} className="text-zinc-800" />
          </div>
        )}
      </div>

      <div className="space-y-1">
        <h3 className="text-sm font-black uppercase tracking-tight text-white truncate">
          {album.title}
        </h3>
        <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-muted truncate">
          {album.authorName}
        </p>
      </div>
    </div>
  );
};

export default AlbumCard;
