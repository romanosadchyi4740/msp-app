import React, { useEffect, useState } from 'react';
import { Music, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import TrackCard from '../components/TrackCard';

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
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 className="animate-spin text-primary h-12 w-12" />
        <p className="mt-4 text-gray-400 font-medium">Tuning the strings...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh] px-4">
        <div className="bg-red-500/10 border border-red-500/20 text-red-500 p-6 rounded-2xl max-w-md text-center">
          <p className="font-medium">{error}</p>
          <button 
            onClick={() => window.location.reload()}
            className="mt-4 px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors text-sm font-semibold"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-10 pb-12">
      <header className="relative py-12 px-8 rounded-3xl bg-gradient-to-r from-primary/20 to-transparent border border-white/5 overflow-hidden">
        <div className="relative z-10">
          <h2 className="text-4xl md:text-5xl font-black mb-4 tracking-tight">Explore Music</h2>
          <p className="text-gray-400 text-lg max-w-xl leading-relaxed">
            Discover the latest tracks from artists in your social circle and beyond.
          </p>
        </div>
        <div className="absolute top-0 right-0 -translate-y-1/2 translate-x-1/4 w-96 h-96 bg-primary/10 rounded-full blur-[100px]"></div>
      </header>

      <section>
        <div className="flex items-center justify-between mb-8">
          <h3 className="text-2xl font-bold">New Releases</h3>
          <div className="h-px flex-1 bg-white/5 mx-6 hidden md:block"></div>
        </div>

        {tracks.length === 0 ? (
          <div className="bg-white/5 border border-white/5 rounded-3xl p-16 text-center">
            <div className="w-20 h-20 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-6">
              <Music className="h-10 w-10 text-gray-600" />
            </div>
            <h3 className="text-2xl font-bold text-gray-300">No tracks found</h3>
            <p className="text-gray-500 mt-2 max-w-xs mx-auto text-balance">
              The catalog is currently empty. Be the first to share your music!
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-6">
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
