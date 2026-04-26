import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Compass, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const GenreCard = ({ genre }) => {
  const navigate = useNavigate();
  const [isHovered, setIsHovered] = useState(false);

  return (
    <button
      onClick={() => navigate(`/genres/${genre.id}`, { state: { genreName: genre.name, colorHex: genre.colorHex } })}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className="aspect-square relative overflow-hidden transition-all duration-300 bg-zinc-900 border flex items-end p-6"
      style={{
        borderColor: isHovered ? genre.colorHex || '#ffffff' : '#27272a', // zinc-800
        boxShadow: isHovered ? `0 0 20px ${(genre.colorHex || '#ffffff')}40` : 'none'
      }}
    >
      <h3 
        className="text-2xl font-black text-white uppercase tracking-tighter text-left transition-colors duration-300"
        style={{ color: isHovered ? genre.colorHex || '#ffffff' : '#ffffff' }}
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
        <Loader2 size={32} className="text-muted animate-spin mb-4" />
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-muted">Mapping Sectors</p>
      </div>
    );
  }

  return (
    <div className="space-y-16 pb-32">
      <header className="border-b border-border pb-12">
        <div className="flex items-center gap-4 mb-6">
          <Compass size={48} className="text-white" />
          <h1 className="text-6xl font-black tracking-tighter uppercase">Explore</h1>
        </div>
        <p className="text-muted text-xs font-bold uppercase tracking-[0.2em]">Navigate sonic territories</p>
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