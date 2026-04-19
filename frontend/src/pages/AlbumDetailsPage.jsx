import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Play, Clock, Music, Disc, ChevronLeft, Calendar } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import usePlayerStore from '../store/playerStore';

const AlbumDetailsPage = () => {
  const { albumId } = useParams();
  const navigate = useNavigate();
  const { playTrack, currentTrack, isPlaying } = usePlayerStore();
  const [album, setAlbum] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAlbum = async () => {
      try {
        const response = await axiosClient.get(`/api/albums/${albumId}`);
        setAlbum(response.data);
      } catch (error) {
        console.error('Failed to fetch album:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchAlbum();
  }, [albumId]);

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[70vh]">
        <Disc className="animate-spin text-primary h-12 w-12" />
      </div>
    );
  }

  if (!album) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold">Album not found</h2>
        <button 
          onClick={() => navigate('/')}
          className="mt-4 text-primary hover:underline"
        >
          Go back home
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-10 pb-20 animate-in fade-in duration-500">
      <button 
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-gray-400 hover:text-white transition-colors mb-4 group"
      >
        <ChevronLeft size={20} className="group-hover:-translate-x-1 transition-transform" />
        <span className="font-medium">Back</span>
      </button>

      {/* Album Header */}
      <div className="flex flex-col md:flex-row gap-10 items-end">
        <div className="relative w-64 h-64 md:w-80 md:h-80 flex-shrink-0 shadow-[0_20px_50px_rgba(0,0,0,0.5)] rounded-2xl overflow-hidden group">
          {album.coverUrl ? (
            <img 
              src={album.coverUrl} 
              alt={album.title} 
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-white/10 to-white/5 flex items-center justify-center">
              <Disc size={120} className="text-white/10" />
            </div>
          )}
        </div>

        <div className="flex-1 space-y-4">
          <p className="text-sm font-bold uppercase tracking-widest text-primary">Album</p>
          <h1 className="text-5xl md:text-7xl font-black tracking-tighter text-white">{album.title}</h1>
          <div className="flex items-center gap-4 text-gray-300">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-full bg-primary flex items-center justify-center text-[10px] text-black font-bold">
                {album.authorName.charAt(0)}
              </div>
              <span className="font-bold hover:underline cursor-pointer">{album.authorName}</span>
            </div>
            <span className="text-gray-600">•</span>
            <div className="flex items-center gap-2 text-sm">
              <Calendar size={14} />
              <span>{new Date(album.releaseDate).getFullYear()}</span>
            </div>
            <span className="text-gray-600">•</span>
            <span className="text-sm font-medium">{album.tracks.length} tracks</span>
          </div>
        </div>
      </div>

      {/* Tracks List */}
      <div className="mt-12 bg-white/5 rounded-3xl overflow-hidden border border-white/5 backdrop-blur-sm">
        <div className="grid grid-cols-[auto_1fr_auto] gap-4 px-8 py-4 border-b border-white/10 text-xs font-bold uppercase tracking-widest text-gray-500">
          <div className="w-8 text-center">#</div>
          <div>Title</div>
          <div className="pr-4"><Clock size={16} /></div>
        </div>

        <div className="divide-y divide-white/5">
          {album.tracks.map((track, index) => {
            const isCurrent = currentTrack?.id === track.id;
            const active = isCurrent && isPlaying;

            return (
              <div 
                key={track.id}
                className={`grid grid-cols-[auto_1fr_auto] gap-4 px-8 py-4 items-center hover:bg-white/10 transition-colors group ${
                  isCurrent ? 'text-primary' : 'text-white'
                }`}
              >
                <div className="w-8 text-center text-sm font-medium text-gray-500 group-hover:hidden">
                  {index + 1}
                </div>
                <button 
                  onClick={() => playTrack(track, album.tracks)}
                  className="w-8 hidden group-hover:flex items-center justify-center text-primary"
                >
                  <Play size={16} fill="currentColor" />
                </button>

                <div className="flex flex-col min-w-0">
                  <span className={`font-semibold truncate ${isCurrent ? 'text-primary' : ''}`}>
                    {track.title}
                  </span>
                  <span className="text-xs text-gray-500 truncate">{track.authorName}</span>
                </div>

                <div className="flex items-center gap-6">
                   <span className="text-sm text-gray-500 font-medium w-12 text-right">
                     {formatDuration(track.duration)}
                   </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default AlbumDetailsPage;
