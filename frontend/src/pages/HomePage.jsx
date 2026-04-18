import React, { useEffect, useState } from 'react';
import { Play, Heart, Music, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const HomePage = () => {
  const [tracks, setTracks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchTracks = async () => {
      try {
        const response = await axiosClient.get('/api/tracks', {
          params: { pageNo: 0, pageSize: 20 }
        });
        // Spring Data Page object has 'content' field
        setTracks(response.data.content || []);
      } catch (err) {
        console.error('Failed to fetch tracks:', err);
        setError('Could not load tracks. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchTracks();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-64">
        <Loader2 className="animate-spin text-primary h-12 w-12" />
        <p className="mt-4 text-gray-400">Tuning the strings...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-500/10 border border-red-500 text-red-500 p-6 rounded-xl">
        <p>{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <header>
        <h2 className="text-3xl font-bold mb-2">Explore Tracks</h2>
        <p className="text-gray-400">Discover new music from the community</p>
      </header>

      {tracks.length === 0 ? (
        <div className="bg-white/5 border border-white/10 rounded-2xl p-12 text-center">
          <Music className="h-16 w-16 text-gray-600 mx-auto mb-4" />
          <h3 className="text-xl font-medium text-gray-300">No tracks available yet</h3>
          <p className="text-gray-500 mt-2">Be the first to upload a track!</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
          {tracks.map((track) => (
            <div 
              key={track.id} 
              className="group bg-white/5 hover:bg-white/10 p-4 rounded-xl transition-all duration-300 cursor-pointer border border-transparent hover:border-white/10"
            >
              <div className="relative aspect-square mb-4 rounded-lg overflow-hidden shadow-lg">
                {track.coverUrl ? (
                  <img 
                    src={track.coverUrl} 
                    alt={track.title} 
                    className="object-cover w-full h-full group-hover:scale-110 transition-transform duration-500"
                  />
                ) : (
                  <div className="w-full h-full bg-light flex items-center justify-center">
                    <Music className="h-12 w-12 text-gray-700" />
                  </div>
                )}
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <div className="bg-primary p-3 rounded-full text-black transform translate-y-4 group-hover:translate-y-0 transition-transform">
                    <Play fill="black" size={24} />
                  </div>
                </div>
              </div>
              
              <h3 className="font-bold truncate text-white">{track.title}</h3>
              <p className="text-sm text-gray-400 truncate mt-1">{track.authorName || 'Unknown Artist'}</p>
              
              <div className="flex items-center justify-between mt-4">
                <span className="text-xs text-gray-500 uppercase tracking-wider">{track.genre || 'Single'}</span>
                <Heart size={16} className="text-gray-500 hover:text-primary transition-colors" />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default HomePage;
