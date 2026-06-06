import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Compass, Loader2} from 'lucide-react';
import axiosClient from '../api/axiosClient';

const GenreCard = ({ genre }) => {
  const navigate = useNavigate();
  const [isHovered, setIsHovered] = useState(false);

  return (
    <button
      onClick={() => navigate(`/genres/${genre.id}`, { state: { genreName: genre.name, colorHex: genre.colorHex } })}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className="aspect-square relative overflow-hidden transition-all duration-300 bg-white dark:bg-zinc-900 border flex items-end p-6"
      style={{
        borderColor: isHovered ? genre.colorHex || '#ffffff' : 'var(--color-border)', // Using a CSS variable or defaulting is fine, we'll let Tailwind classes handle the base border if we can, but since it's inline, we manage it here. We'll use a generic approach.
      }}
    >
      {/* We need a clean way to handle the border color. Let's just use Tailwind for the default and inline for hover. */}
      <div className={`absolute inset-0 border transition-colors duration-300 ${isHovered ? 'border-transparent' : 'border-zinc-200 dark:border-zinc-800'}`} />
      <div 
        className="absolute inset-0 transition-opacity duration-300 pointer-events-none opacity-0"
        style={{
          border: `1px solid ${genre.colorHex || '#ffffff'}`,
          boxShadow: `0 0 20px ${(genre.colorHex || '#ffffff')}40`,
          opacity: isHovered ? 1 : 0
        }}
      />

      <h3 
        className={`text-2xl font-black uppercase tracking-tighter text-left transition-colors duration-300 z-10 ${isHovered ? '' : 'text-zinc-900 dark:text-white'}`}
        style={{ color: isHovered ? genre.colorHex || '#000000' : undefined }}
      >
        {genre.name}
      </h3>
    </button>
  );
};

const ExplorePage = () => {
  const [genres, setGenres] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchGenres = async () => {
      try {
        const res = await axiosClient.get('/api/genres');
        setGenres(res.data);
      } catch (err) {
        console.error('Failed to fetch genres', err);
      } finally {
        setLoading(false);
      }
    };
    fetchGenres();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 size={32} className="text-zinc-500 dark:text-zinc-400 animate-spin mb-4" />
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400">Mapping Sectors</p>
      </div>
    );
  }

  return (
    <div className="space-y-16 pb-32">
      <header className="border-b border-zinc-200 dark:border-zinc-800 pb-12">
        <div className="flex items-center gap-4 mb-6">
          <Compass size={48} className="text-zinc-900 dark:text-white" />
          <h1 className="text-6xl font-black tracking-tighter uppercase text-zinc-900 dark:text-white">Network Radar</h1>
        </div>
        <p className="text-zinc-500 dark:text-zinc-400 text-xs font-bold uppercase tracking-[0.2em]">Navigate sonic territories</p>
      </header>

      <section>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
          {genres.map(genre => (
            <GenreCard key={genre.id} genre={genre} />
          ))}
        </div>
      </section>
    </div>
  );
};

export default ExplorePage;