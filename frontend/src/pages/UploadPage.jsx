import React, { useState, useEffect } from 'react';
import { Upload, Plus, Music, Disc, Loader2, CheckCircle2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const UploadPage = () => {
  const [myAlbums, setMyAlbums] = useState([]);
  const [loadingAlbums, setLoadingAlbums] = useState(false);
  
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
  const [uploadingTrack, setUploadingTrack] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const [message, setMessage] = useState({ type: '', text: '' });

  useEffect(() => {
    fetchMyAlbums();
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

  const fetchMyAlbums = async () => {
    setLoadingAlbums(true);
    try {
      const response = await axiosClient.get('/api/albums/my');
      setMyAlbums(response.data);
    } catch (error) {
      console.error('Failed to fetch albums', error);
    } finally {
      setLoadingAlbums(false);
    }
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
      setMessage({ type: 'success', text: 'Album created successfully!' });
      setAlbumTitle('');
      setAlbumDesc('');
      setAlbumCoverUrl('');
      fetchMyAlbums();
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to create album.' });
    } finally {
      setCreatingAlbum(false);
    }
  };

  const handleUploadTrack = async (e) => {
    e.preventDefault();
    if (!trackFile || !selectedAlbumId) return;

    setUploadingTrack(true);
    setMessage({ type: '', text: '' });
    
    const formData = new FormData();
    formData.append('file', trackFile);
    formData.append('title', trackTitle);
    formData.append('albumId', selectedAlbumId);
    formData.append('duration', trackDuration);

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
      setTrackTitle('');
      setTrackFile(null);
      setTrackDuration(0);
      setUploadProgress(0);
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to upload track.' });
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
            <h2 className="text-xl font-black uppercase tracking-tighter">New Collection</h2>
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
              <label className="block text-[10px] font-black uppercase tracking-widest text-muted">Target Collection</label>
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
              disabled={uploadingTrack || loadingAlbums || myAlbums.length === 0}
              className="w-full bg-white text-black text-[11px] font-black uppercase tracking-[0.3em] py-5 hover:bg-zinc-200 transition-all disabled:opacity-50 flex items-center justify-center gap-3"
            >
              {uploadingTrack ? <Loader2 className="animate-spin" /> : 'Execute Upload'}
            </button>
            {myAlbums.length === 0 && !loadingAlbums && (
              <p className="text-[9px] text-center text-red-500 mt-4 font-black uppercase tracking-widest">Archive required for transmission.</p>
            )}
          </form>
        </section>
      </div>
    </div>
  );
};

export default UploadPage;
