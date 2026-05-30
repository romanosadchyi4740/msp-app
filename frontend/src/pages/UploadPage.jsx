import React, {useEffect, useState} from 'react';
import {Loader2, Music} from 'lucide-react';
import toast from 'react-hot-toast';
import axiosClient from '../api/axiosClient';

const UploadPage = () => {
  const [myAlbums, setMyAlbums] = useState([]);
  const [genres, setGenres] = useState([]);
  const [loadingData, setLoadingData] = useState(false);
  
  // Album Form State
  const [albumTitle, setAlbumTitle] = useState('');
  const [albumDesc, setAlbumDesc] = useState('');
  const [albumCoverUrl, setAlbumCoverUrl] = useState('');
  const [creatingAlbum, setCreatingAlbum] = useState(false);

  // Track Form State
  const [trackTitle, setTrackTitle] = useState('');
  const [trackFile, setTrackFile] = useState(null);
  const [trackDuration, setTrackDuration] = useState(0);
  const [selectedAlbumId, setSelectedAlbumId] = useState('');
  const [selectedGenres, setSelectedGenres] = useState([]);
  const [uploadingTrack, setUploadingTrack] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const [message, setMessage] = useState({ type: '', text: '' });

  useEffect(() => {
    fetchInitialData();
  }, []);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setTrackFile(file);
      
      const audio = new Audio();
      audio.src = URL.createObjectURL(file);
      audio.onloadedmetadata = () => {
        setTrackDuration(Math.round(audio.duration));
        URL.revokeObjectURL(audio.src);
      };
    }
  };

  const fetchInitialData = async () => {
    setLoadingData(true);
    try {
      const [albumsRes, genresRes] = await Promise.all([
        axiosClient.get('/api/albums/my'),
        axiosClient.get('/api/genres')
      ]);
      setMyAlbums(albumsRes.data);
      setGenres(genresRes.data);
    } catch (error) {
      console.error('Failed to fetch initial data', error);
    } finally {
      setLoadingData(false);
    }
  };

  const toggleGenre = (genreId) => {
    setSelectedGenres(prev => {
      if (prev.includes(genreId)) {
        return prev.filter(id => id !== genreId);
      }
      if (prev.length < 4) {
        return [...prev, genreId];
      }
      return prev;
    });
  };

  const handleCreateAlbum = async (e) => {
    e.preventDefault();
    setCreatingAlbum(true);
    setMessage({ type: '', text: '' });
    try {
      await axiosClient.post('/api/albums', {
        title: albumTitle,
        description: albumDesc,
        coverUrl: albumCoverUrl
      });
      setMessage({ type: 'success', text: 'Archive created successfully!' });
      setAlbumTitle('');
      setAlbumDesc('');
      setAlbumCoverUrl('');
      // Refetch albums to update the dropdown
      const response = await axiosClient.get('/api/albums/my');
      setMyAlbums(response.data);
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to create archive.' });
    } finally {
      setCreatingAlbum(false);
    }
  };

  const handleUploadTrack = async (e) => {
    e.preventDefault();
    if (!trackFile || !selectedAlbumId || selectedGenres.length === 0) return;

    setUploadingTrack(true);
    setMessage({ type: '', text: '' });
    
    const formData = new FormData();
    formData.append('file', trackFile);
    formData.append('title', trackTitle);
    formData.append('albumId', selectedAlbumId);
    formData.append('duration', trackDuration);
    
    selectedGenres.forEach(genreId => {
      formData.append('genreIds', genreId);
    });

    try {
      await axiosClient.post('/api/tracks', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setUploadProgress(percentCompleted);
        }
      });
      setMessage({ type: 'success', text: 'Track uploaded successfully!' });
      toast.success('TRANSMISSION_COMPLETE: Node synchronized.');
      setTrackTitle('');
      setTrackFile(null);
      setTrackDuration(0);
      setSelectedGenres([]);
      setUploadProgress(0);
    } catch (error) {
      if (error.response?.status === 409) {
        toast.error('UPLOAD_REJECTED: An identical audio transmission already exists in the global network.', { duration: 5000 });
        setMessage({ type: 'error', text: 'Duplicate detected. Transmission aborted.' });
      } else {
        setMessage({ type: 'error', text: error.response?.data?.message || 'Failed to upload track.' });
      }
    } finally {
      setUploadingTrack(false);
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-24 pb-32">
      <header className="border-b border-border pb-12">
        <h1 className="text-6xl font-black mb-6 tracking-tighter uppercase">Studio</h1>
        <p className="text-muted text-xs font-bold uppercase tracking-[0.2em]">Manage discography / Digital transmissions</p>
      </header>

      {message.text && (
        <div className={`p-6 border text-[10px] font-black uppercase tracking-widest ${
          message.type === 'success' 
            ? 'border-white bg-white/5 text-white' 
            : 'border-red-500/50 bg-red-500/5 text-red-500'
        }`}>
          {message.text}
        </div>
      )}

      <div className="grid md:grid-cols-2 gap-16">
        {/* Create Album Form */}
        <section className="bg-surface p-10 border border-border space-y-10">
          <div className="flex items-center gap-4">
            <h2 className="text-xl font-black uppercase tracking-tighter">New Archive</h2>
          </div>

          <form onSubmit={handleCreateAlbum} className="space-y-8">
            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Title</label>
              <input 
                required
                value={albumTitle}
                onChange={(e) => setAlbumTitle(e.target.value)}
                placeholder="INPUT TITLE..."
                className="w-full bg-background border border-border px-6 py-4 focus:outline-none focus:border-white transition-all text-xs font-bold uppercase tracking-widest"
              />
            </div>
            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Description</label>
              <textarea 
                value={albumDesc}
                onChange={(e) => setAlbumDesc(e.target.value)}
                placeholder="INPUT DATA..."
                className="w-full bg-background border border-border px-6 py-4 focus:outline-none focus:border-white transition-all text-xs font-bold uppercase tracking-widest h-32 resize-none"
              />
            </div>
            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Cover URL</label>
              <input 
                value={albumCoverUrl}
                onChange={(e) => setAlbumCoverUrl(e.target.value)}
                placeholder="HTTPS://IMAGE.SOURCE"
                className="w-full bg-background border border-border px-6 py-4 focus:outline-none focus:border-white transition-all text-xs font-bold uppercase tracking-widest"
              />
            </div>
            <button 
              disabled={creatingAlbum}
              className="w-full bg-white text-black text-[11px] font-black uppercase tracking-[0.3em] py-5 hover:bg-zinc-200 transition-all disabled:opacity-50 flex items-center justify-center gap-3"
            >
              {creatingAlbum ? <Loader2 className="animate-spin" /> : 'Execute Creation'}
            </button>
          </form>
        </section>

        {/* Upload Track Form */}
        <section className="bg-surface p-10 border border-border space-y-10">
          <div className="flex items-center gap-4">
            <h2 className="text-xl font-black uppercase tracking-tighter">Transmit Track</h2>
          </div>

          <form onSubmit={handleUploadTrack} className="space-y-8">
            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Target Archive</label>
              <select 
                required
                value={selectedAlbumId}
                onChange={(e) => setSelectedAlbumId(e.target.value)}
                className="w-full bg-background border border-border px-6 py-4 focus:outline-none focus:border-white transition-all text-[10px] font-bold uppercase tracking-widest appearance-none rounded-none"
              >
                <option value="" className="bg-surface">SELECT ARCHIVE...</option>
                {myAlbums.map(album => (
                  <option key={album.id} value={album.id} className="bg-surface">{album.title.toUpperCase()}</option>
                ))}
              </select>
            </div>
            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Track Title</label>
              <input 
                required
                value={trackTitle}
                onChange={(e) => setTrackTitle(e.target.value)}
                placeholder="INPUT TITLE..."
                className="w-full bg-background border border-border px-6 py-4 focus:outline-none focus:border-white transition-all text-xs font-bold uppercase tracking-widest"
              />
            </div>
            
            <div className="space-y-3">
              <div className="flex items-baseline justify-between">
                <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Genres (1-4)</label>
                <span className="text-[9px] font-bold text-muted">{selectedGenres.length} selected</span>
              </div>
              <div className="flex flex-wrap gap-2">
                {loadingData ? (
                  <Loader2 size={16} className="animate-spin text-muted" />
                ) : (
                  genres.map(genre => {
                    const isSelected = selectedGenres.includes(genre.id);
                    const isDisabled = !isSelected && selectedGenres.length >= 4;
                    return (
                      <button
                        key={genre.id}
                        type="button"
                        onClick={() => toggleGenre(genre.id)}
                        disabled={isDisabled}
                        className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-widest transition-all border ${
                          isSelected 
                            ? 'bg-white text-black border-white' 
                            : 'bg-surface text-muted border-border hover:border-white/30'
                        } ${isDisabled ? 'opacity-30 cursor-not-allowed' : ''}`}
                      >
                        {genre.name}
                      </button>
                    );
                  })
                )}
              </div>
            </div>

            <div className="space-y-3">
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Source File</label>
              <div className="relative">
                <input 
                  required
                  type="file"
                  accept="audio/*"
                  onChange={handleFileChange}
                  className="hidden"
                  id="track-upload"
                />
                <label 
                  htmlFor="track-upload"
                  className="w-full bg-background border border-border px-6 py-5 focus:outline-none border-dashed hover:border-white transition-all text-[10px] font-bold uppercase tracking-[0.2em] flex items-center justify-center gap-3 cursor-pointer text-muted"
                >
                  <Music size={14} />
                  {trackFile ? trackFile.name.toUpperCase() : 'UPLOAD AUDIO (.MP3)'}
                </label>
              </div>
            </div>

            {uploadingTrack && (
              <div className="w-full bg-zinc-800 h-[1px] mt-4">
                <div 
                  className="bg-white h-full transition-all duration-300" 
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}

            <button 
              disabled={uploadingTrack || loadingData || myAlbums.length === 0 || selectedGenres.length === 0}
              className="w-full bg-white text-black text-[11px] font-black uppercase tracking-[0.3em] py-5 hover:bg-zinc-200 transition-all disabled:opacity-50 flex items-center justify-center gap-3 mt-8"
            >
              {uploadingTrack ? <Loader2 className="animate-spin" /> : 'Execute Upload'}
            </button>
            {myAlbums.length === 0 && !loadingData && (
              <p className="text-[9px] text-center text-red-500 mt-4 font-black uppercase tracking-widest">Archive required for transmission.</p>
            )}
            {selectedGenres.length === 0 && myAlbums.length > 0 && (
              <p className="text-[9px] text-center text-red-500 mt-4 font-black uppercase tracking-widest">At least one genre must be selected.</p>
            )}
          </form>
        </section>
      </div>
    </div>
  );
};

export default UploadPage;
