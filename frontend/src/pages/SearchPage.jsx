import React, {useEffect, useState} from 'react';
import {Music, Search, User as UserIcon, X} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';
import AlbumCard from '../components/AlbumCard';

const SearchPage = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState({ tracks: [], albums: [], users: [] });
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim()) {
        performSearch(query);
      } else {
        setResults({ tracks: [], albums: [], users: [] });
        setHasSearched(false);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [query]);

  const performSearch = async (searchTerm) => {
    setLoading(true);
    try {
      const response = await axiosClient.get('/api/search', { params: { q: searchTerm } });
      setResults({
        tracks: response.data.tracks.content || [],
        albums: response.data.albums.content || [],
        users: response.data.users.content || []
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
      <header className="border-b border-zinc-200 dark:border-zinc-800 pb-12">
        <h1 className="text-6xl font-black mb-10 tracking-tighter uppercase text-zinc-900 dark:text-white">Global Query</h1>
        <div className="relative max-w-3xl">
          <Search className="absolute left-6 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500" size={24} />
          <input 
            autoFocus
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="INPUT SEARCH QUERY..."
            className="w-full bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 px-16 py-6 text-xl font-black uppercase tracking-tighter text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600 focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all"
          />
          {query && (
            <button onClick={() => setQuery('')} className="absolute right-6 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500 hover:text-zinc-900 dark:hover:text-white transition-colors">
              <X size={24} />
            </button>
          )}
        </div>
      </header>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-24">
          <div className="w-12 h-12 border border-zinc-200 dark:border-zinc-800 flex items-center justify-center relative bg-white dark:bg-zinc-950">
            <div className="w-full h-[1px] bg-zinc-900 dark:bg-white absolute animate-[ping_2s_infinite]"></div>
            <Search size={16} className="text-zinc-400 dark:text-zinc-500" />
          </div>
          <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400 animate-pulse">Scanning Archive</p>
        </div>
      ) : hasSearched ? (
        <div className="space-y-24">
          {/* Users Results */}
          {results.users.length > 0 && (
            <section>
              <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
                <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Identities Identified</h3>
                <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{results.users.length.toString().padStart(2, '0')}</span>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-8">
                {results.users.map((user) => (
                  <div 
                    key={`user-${user.id}`} 
                    onClick={() => navigate(`/users/${user.id}`)}
                    className="group cursor-pointer space-y-4"
                  >
                    <div className="aspect-square bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 overflow-hidden">
                      {user.avatarUrl ? (
                        <img src={user.avatarUrl} alt={user.displayName} referrerPolicy="no-referrer" className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 transition-all duration-700" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <UserIcon size={32} className="text-zinc-300 dark:text-zinc-800" />
                        </div>
                      )}
                    </div>
                    <div className="space-y-1">
                      <p className="text-[11px] font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">{user.displayName}</p>
                      <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">{user.role === 'ROLE_ARTIST' ? 'Artist' : 'Member'}</p>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Tracks Results */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Transmissions Found</h3>
              <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{results.tracks.length.toString().padStart(2, '0')}</span>
            </div>
            {results.tracks.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 py-12">No audio data matching query.</p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
                {results.tracks.map((track) => <TrackCard key={`track-${track.id}`} track={track} queue={results.tracks} />)}
              </div>
            )}
          </section>

          {/* Albums Results */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Archives Found</h3>
              <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{results.albums.length.toString().padStart(2, '0')}</span>
            </div>
            {results.albums.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 py-12">No archive data matching query.</p>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
                {results.albums.map((album) => <AlbumCard key={`album-${album.id}`} album={album} />)}
              </div>
            )}
          </section>
        </div>
      ) : (
        <div className="py-24 border border-zinc-200 dark:border-zinc-800 border-dashed flex flex-col items-center justify-center text-center bg-zinc-50 dark:bg-zinc-950/50">
          <Music className="mb-6 text-zinc-300 dark:text-zinc-800" size={48} />
          <p className="max-w-xs text-[10px] font-black uppercase tracking-[0.3em] leading-loose text-zinc-500 dark:text-zinc-500">Enter search criteria to begin discovery protocol.</p>
        </div>
      )}
    </div>
  );
};

export default SearchPage;
