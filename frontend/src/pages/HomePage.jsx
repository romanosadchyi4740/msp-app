import React, { useEffect, useState } from 'react';
import { Music, Loader2, Disc, PlayCircle } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';
import AlbumCard from '../components/AlbumCard';

const HomePage = () => {
  const [tracks, setTracks] = useState([]);
  const [albums, setAlbums] = useState([]);
  const [recommended, setRecommended] = useState({ genreName: '', tracks: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [tracksRes, albumsRes, recRes] = await Promise.all([
          axiosClient.get('/api/tracks', { params: { pageNo: 0, pageSize: 12 } }),
          axiosClient.get('/api/albums', { params: { pageNo: 0, pageSize: 6 } }),
          axiosClient.get('/api/tracks/recommended')
        ]);
        
        setTracks(tracksRes.data.content || []);
        setAlbums(albumsRes.data.content || []);
        setRecommended(recRes.data || { genreName: '', tracks: [] });
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
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <div className="w-12 h-12 border border-border flex items-center justify-center relative">
          <div className="w-full h-[1px] bg-white absolute animate-[ping_2s_infinite]"></div>
          <Music size={16} className="text-muted" />
        </div>
        <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-muted animate-pulse">Loading Archive</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-[60vh] px-4">
        <div className="border border-border p-12 max-w-md text-center">
          <p className="text-xs font-black uppercase tracking-widest mb-4">Connection Error</p>
          <p className="text-muted text-[11px] leading-relaxed mb-8">{error}</p>
          <button 
            onClick={() => window.location.reload()}
            className="px-8 py-3 border border-white text-[10px] font-black uppercase tracking-widest hover:bg-white hover:text-black transition-all"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-32 pb-32">
      {/* Editorial Hero */}
      <header className="py-24 border-b border-border">
        <div className="max-w-4xl">
          <div className="inline-block px-3 py-1 border border-border text-[9px] font-black uppercase tracking-[0.3em] text-muted mb-12">
            Status: Live
          </div>
          <h2 className="text-7xl md:text-8xl font-black mb-12 tracking-tighter leading-[0.85] uppercase">
            The Digital <br/> <span className="text-muted">Soundscape</span>
          </h2>
          <div className="flex flex-col md:flex-row gap-12 items-start">
            <p className="text-muted text-sm max-w-sm leading-relaxed font-medium uppercase tracking-tight">
              Curated audio experiences from the independent community. High-fidelity social interaction through sound.
            </p>
            <div className="h-px w-24 bg-border mt-3 hidden md:block"></div>
            <p className="text-muted text-[10px] max-w-[200px] leading-loose uppercase tracking-widest">
              Available 24/7. Updated in real-time. Join the conversation.
            </p>
          </div>
        </div>
      </header>

      {/* Recommended Section */}
      {recommended.tracks.length > 0 && (
        <section>
          <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
            <h3 className="text-xs font-black uppercase tracking-[0.4em]">Recommended For You (Based on your love for {recommended.genreName})</h3>
            <span className="text-[10px] font-medium text-muted tabular-nums">
              01 — {recommended.tracks.length.toString().padStart(2, '0')}
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
            {recommended.tracks.map((track) => (
              <TrackCard key={`rec-${track.id}`} track={track} queue={recommended.tracks} />
            ))}
          </div>
        </section>
      )}

      {/* Featured Albums Section */}
      <section>
        <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
          <h3 className="text-xs font-black uppercase tracking-[0.4em]">Featured Collections</h3>
          <span className="text-[10px] font-medium text-muted tabular-nums">01 — 06</span>
        </div>

        {albums.length === 0 ? (
          <div className="border border-border p-24 text-center">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-muted">Empty Collection</h3>
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
            {albums.map((album) => (
              <AlbumCard key={album.id} album={album} />
            ))}
          </div>
        )}
      </section>

      {/* Recent Tracks Section */}
      <section>
        <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
          <h3 className="text-xs font-black uppercase tracking-[0.4em]">Recent Transmissions</h3>
          <span className="text-[10px] font-medium text-muted tabular-nums">01 — 12</span>
        </div>

        {tracks.length === 0 ? (
          <div className="border border-border p-24 text-center">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-muted">No Audio Found</h3>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
            {tracks.map((track) => (
              <TrackCard key={track.id} track={track} queue={tracks} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default HomePage;
