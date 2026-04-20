import React, { useState, useEffect, useCallback } from 'react';
import { Search, Music, Disc, Loader2, X } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';
import AlbumCard from '../components/AlbumCard';

const SearchPage = () => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState({ tracks: [], albums: [] });
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim()) {
        performSearch(query);
      } else {
        setResults({ tracks: [], albums: [] });
        setHasSearched(false);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [query]);

  const performSearch = async (searchTerm) => {
    setLoading(true);
    try {
      const [tracksRes, albumsRes] = await Promise.all([
        axiosClient.get('/api/tracks/search', { params: { query: searchTerm, pageSize: 6 } }),
        axiosClient.get('/api/albums/search', { params: { query: searchTerm, size: 6 } })
      ]);

      setResults({
        tracks: tracksRes.data.content || [],
        albums: albumsRes.data.content || []
      });
      setHasSearched(true);
    } catch (error) {
      console.error('Search failed', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-16 pb-32">
      <header className="border-b border-border pb-12">
        <h1 className="text-6xl font-black mb-10 tracking-tighter uppercase">Discovery</h1>
        <div className="relative max-w-3xl">
          <Search className="absolute left-6 top-1/2 -translate-y-1/2 text-muted" size={24} />
          <input 
            autoFocus
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="INPUT SEARCH QUERY..."
            className="w-full bg-surface border border-border px-16 py-6 text-xl font-black uppercase tracking-tighter text-white placeholder-zinc-800 focus:outline-none focus:border-white transition-all"
          />
          {query && (
            <button 
              onClick={() => setQuery('')}
              className="absolute right-6 top-1/2 -translate-y-1/2 text-muted hover:text-white transition-colors"
            >
              <X size={24} />
            </button>
          )}
        </div>
      </header>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-24">
          <div className="w-12 h-12 border border-border flex items-center justify-center relative">
            <div className="w-full h-[1px] bg-white absolute animate-[ping_2s_infinite]"></div>
            <Search size={16} className="text-muted" />
          </div>
          <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-muted animate-pulse">Scanning Archive</p>
        </div>
      ) : hasSearched ? (
        <div className="space-y-24">
          {/* Tracks Results */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em]">Transmissions Found</h3>
              <span className="text-[10px] font-medium text-muted tabular-nums">{results.tracks.length.toString().padStart(2, '0')}</span>
            </div>
            
            {results.tracks.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-muted py-12">No audio data matching query.</p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
                {results.tracks.map((track) => (
                  <TrackCard key={track.id} track={track} queue={results.tracks} />
                ))}
              </div>
            )}
          </section>

          {/* Albums Results */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em]">Collections Found</h3>
              <span className="text-[10px] font-medium text-muted tabular-nums">{results.albums.length.toString().padStart(2, '0')}</span>
            </div>

            {results.albums.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-muted py-12">No archive data matching query.</p>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
                {results.albums.map((album) => (
                  <AlbumCard key={album.id} album={album} />
                ))}
              </div>
            )}
          </section>
        </div>
      ) : (
        <div className="py-24 border border-border border-dashed flex flex-col items-center justify-center text-center opacity-50">
          <Music className="mb-6 text-zinc-800" size={48} />
          <p className="max-w-xs text-[10px] font-black uppercase tracking-[0.3em] leading-loose text-muted">
            Enter search criteria to begin discovery protocol.
          </p>
        </div>
      )}
    </div>
  );
};

export default SearchPage;
