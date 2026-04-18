import React, { useEffect, useState } from 'react';
import { Music, Loader2, Disc, PlayCircle } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';
import AlbumCard from '../components/AlbumCard';

const HomePage = () => {
  const [tracks, setTracks] = useState([]);
  const [albums, setAlbums] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [tracksRes, albumsRes] = await Promise.all([
          axiosClient.get('/api/tracks', { params: { pageNo: 0, pageSize: 12 } }),
          axiosClient.get('/api/albums', { params: { pageNo: 0, pageSize: 6 } })
        ]);
        
        setTracks(tracksRes.data.content || []);
        setAlbums(albumsRes.data.content || []);
      } catch (err) {
        console.error('Failed to fetch data:', err);
        setError('Could not load content. Please check your connection.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[70vh]">
        <Loader2 className="animate-spin text-primary h-12 w-12" />
        <div className="mt-6 flex flex-col items-center gap-2">
          <p className="text-gray-400 font-medium animate-pulse text-lg italic">Gathering melodies...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-[70vh] px-4">
        <div className="bg-red-500/10 border border-red-500/20 text-red-500 p-8 rounded-3xl max-w-md text-center backdrop-blur-md">
          <p className="font-bold text-xl mb-2">Oops!</p>
          <p className="opacity-80 leading-relaxed">{error}</p>
          <button 
            onClick={() => window.location.reload()}
            className="mt-6 px-8 py-3 bg-red-500 text-white rounded-full hover:bg-red-600 transition-all font-bold shadow-lg shadow-red-500/20 active:scale-95"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-16 pb-20">
      {/* Hero / Header Section */}
      <header className="relative py-16 px-10 rounded-[40px] bg-gradient-to-br from-primary/30 via-primary/5 to-transparent border border-white/5 overflow-hidden shadow-2xl">
        <div className="relative z-10">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-bold uppercase tracking-widest mb-6">
            <PlayCircle size={14} />
            New on MusicSocial
          </div>
          <h2 className="text-5xl md:text-6xl font-black mb-6 tracking-tight leading-[1.1]">
            Your Daily <br/> <span className="text-primary italic">Soundtrack</span>
          </h2>
          <p className="text-gray-400 text-lg max-w-lg leading-relaxed font-medium">
            Explore fresh albums and trending tracks from the creative souls in your community.
          </p>
        </div>
        
        {/* Animated Background Orbs */}
        <div className="absolute -top-20 -right-20 w-[400px] h-[400px] bg-primary/10 rounded-full blur-[120px] animate-pulse"></div>
        <div className="absolute -bottom-40 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-primary/5 rounded-full blur-[100px]"></div>
      </header>

      {/* Featured Albums Section */}
      <section>
        <div className="flex items-center gap-4 mb-8">
          <Disc size={28} className="text-primary" />
          <h3 className="text-3xl font-black tracking-tight">Featured Albums</h3>
          <div className="h-px flex-1 bg-gradient-to-r from-white/10 to-transparent ml-4"></div>
        </div>

        {albums.length === 0 ? (
          <div className="bg-white/5 border border-white/5 rounded-3xl p-16 text-center backdrop-blur-sm">
            <Disc className="h-12 w-12 text-gray-700 mx-auto mb-4 opacity-40" />
            <h3 className="text-xl font-bold text-gray-400">No albums available</h3>
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-6">
            {albums.map((album) => (
              <AlbumCard key={album.id} album={album} />
            ))}
          </div>
        )}
      </section>

      {/* Recent Tracks Section */}
      <section>
        <div className="flex items-center gap-4 mb-8">
          <Music size={28} className="text-primary" />
          <h3 className="text-3xl font-black tracking-tight">Recent Releases</h3>
          <div className="h-px flex-1 bg-gradient-to-r from-white/10 to-transparent ml-4"></div>
        </div>

        {tracks.length === 0 ? (
          <div className="bg-white/5 border border-white/5 rounded-3xl p-16 text-center backdrop-blur-sm">
            <Music className="h-12 w-12 text-gray-700 mx-auto mb-4 opacity-40" />
            <h3 className="text-xl font-bold text-gray-400">No tracks found</h3>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-6">
            {tracks.map((track) => (
              <TrackCard key={track.id} track={track} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default HomePage;
