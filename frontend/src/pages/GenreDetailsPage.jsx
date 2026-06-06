import React, { useState, useEffect } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { Loader2, ArrowLeft } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';

const GenreDetailsPage = () => {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const genreName = location.state?.genreName || 'Genre';
  const colorHex = location.state?.colorHex || '#ffffff';
  
  const [tracks, setTracks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTracks = async () => {
      setLoading(true);
      try {
        const res = await axiosClient.get(`/api/tracks/genre/${id}`, { params: { pageNo: 0, pageSize: 50 } });
        setTracks(res.data.content || []);
      } catch (err) {
        console.error('Failed to fetch genre tracks', err);
      } finally {
        setLoading(false);
      }
    };
    fetchTracks();
  }, [id]);

  return (
    <div className="space-y-16 pb-32">
      <header 
        className="pb-12 flex items-center gap-6"
        style={{ borderBottom: `1px solid ${colorHex}40` }}
      >
        <button 
          onClick={() => navigate(-1)}
          className="p-3 transition-colors"
          style={{ color: colorHex }}
        >
          <ArrowLeft size={24} />
        </button>
        <div>
          <h1 
            className="text-6xl font-black tracking-tighter uppercase"
            style={{ color: colorHex }}
          >
            {genreName}
          </h1>
          <p className="text-zinc-500 dark:text-zinc-400 text-xs font-bold uppercase tracking-[0.2em] mt-2">Genre Archive</p>
        </div>
      </header>

      {loading ? (
        <div className="flex flex-col items-center justify-center h-[40vh]">
          <Loader2 size={32} className="text-zinc-500 dark:text-zinc-400 animate-spin mb-4" />
          <p className="text-[10px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400">Scanning Transmissions</p>
        </div>
      ) : tracks.length === 0 ? (
        <div className="border border-zinc-200 dark:border-zinc-800 p-24 text-center border-dashed">
          <h3 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">No Audio Found for this Genre</h3>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
          {tracks.map(track => (
            <TrackCard key={track.id} track={track} queue={tracks} showGenre={false} />
          ))}
        </div>
      )}
    </div>
  );
};

export default GenreDetailsPage;