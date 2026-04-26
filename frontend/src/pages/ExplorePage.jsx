import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Compass, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const ExplorePage = () => {
  const [genres, setGenres] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

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

  const bgColors = [
    'bg-red-600', 'bg-orange-600', 'bg-amber-600', 'bg-yellow-600', 'bg-lime-600',
    'bg-green-600', 'bg-emerald-600', 'bg-teal-600', 'bg-cyan-600', 'bg-sky-600',
    'bg-blue-600', 'bg-indigo-600', 'bg-violet-600', 'bg-purple-600', 'bg-fuchsia-600',
    'bg-pink-600', 'bg-rose-600'
  ];

  const getColorForGenre = (idStr) => {
    let hash = 0;
    for (let i = 0; i < idStr.length; i++) {
      hash = idStr.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % bgColors.length;
    return bgColors[index];
  };

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
            <button
              key={genre.id}
              onClick={() => navigate(`/genres/${genre.id}`, { state: { genreName: genre.name } })}
              className={`aspect-square relative overflow-hidden transition-transform hover:scale-105 active:scale-95 ${getColorForGenre(genre.id)}`}
            >
              <div className="absolute inset-0 bg-black/20 group-hover:bg-transparent transition-colors"></div>
              <h3 className="absolute top-4 left-4 text-2xl font-black text-white uppercase tracking-tighter drop-shadow-md text-left">
                {genre.name}
              </h3>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
};

export default ExplorePage;