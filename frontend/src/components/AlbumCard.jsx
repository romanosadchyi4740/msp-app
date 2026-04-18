import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Music, Disc } from 'lucide-react';

const AlbumCard = ({ album }) => {
  const navigate = useNavigate();

  return (
    <div 
      onClick={() => navigate(`/albums/${album.id}`)}
      className="group bg-white/5 p-4 rounded-2xl hover:bg-white/10 transition-all duration-300 cursor-pointer border border-transparent hover:border-white/10"
    >
      <div className="relative aspect-square mb-4 rounded-xl overflow-hidden shadow-2xl">
        {album.coverUrl ? (
          <img 
            src={album.coverUrl} 
            alt={album.title} 
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
          />
        ) : (
          <div className="w-full h-full bg-gradient-to-br from-white/10 to-white/5 flex items-center justify-center">
            <Disc size={64} className="text-white/20" />
          </div>
        )}
        <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity" />
      </div>

      <div className="space-y-1">
        <h3 className="font-bold truncate text-white group-hover:text-primary transition-colors text-lg">
          {album.title}
        </h3>
        <p className="text-sm text-gray-400 truncate">
          {album.authorName}
        </p>
      </div>
    </div>
  );
};

export default AlbumCard;
