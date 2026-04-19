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
    <div className="max-w-5xl mx-auto space-y-12 pb-20">
      <header>
        <h1 className="text-4xl font-black mb-2 tracking-tight">Artist Studio</h1>
        <p className="text-gray-400">Manage your discography and share your latest creations.</p>
      </header>

      {message.text && (
        <div className={`p-4 rounded-xl flex items-center gap-3 border ${
          message.type === 'success' 
            ? 'bg-primary/10 border-primary/20 text-primary' 
            : 'bg-red-500/10 border-red-500/20 text-red-500'
        }`}>
          <CheckCircle2 size={20} />
          <p className="font-medium text-sm">{message.text}</p>
        </div>
      )}

      <div className="grid md:grid-cols-2 gap-10">
        {/* Create Album Form */}
        <section className="bg-white/5 p-8 rounded-[32px] border border-white/5 backdrop-blur-sm space-y-6">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-full bg-primary/20 flex items-center justify-center text-primary">
              <Plus size={24} />
            </div>
            <h2 className="text-2xl font-bold">New Album</h2>
          </div>

          <form onSubmit={handleCreateAlbum} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Album Title</label>
              <input 
                required
                value={albumTitle}
                onChange={(e) => setAlbumTitle(e.target.value)}
                placeholder="Give your album a name"
                className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none focus:border-primary/50 transition-all text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Description</label>
              <textarea 
                value={albumDesc}
                onChange={(e) => setAlbumDesc(e.target.value)}
                placeholder="Tell us about this album"
                className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none focus:border-primary/50 transition-all text-sm h-24 resize-none"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Cover Image URL</label>
              <input 
                value={albumCoverUrl}
                onChange={(e) => setAlbumCoverUrl(e.target.value)}
                placeholder="https://example.com/cover.jpg"
                className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none focus:border-primary/50 transition-all text-sm"
              />
            </div>
            <button 
              disabled={creatingAlbum}
              className="w-full bg-primary text-black font-bold py-4 rounded-2xl hover:scale-[1.02] active:scale-[0.98] transition-all disabled:opacity-50 mt-4 flex items-center justify-center gap-2"
            >
              {creatingAlbum ? <Loader2 className="animate-spin" /> : <Disc size={20} />}
              Create Album
            </button>
          </form>
        </section>

        {/* Upload Track Form */}
        <section className="bg-white/5 p-8 rounded-[32px] border border-white/5 backdrop-blur-sm space-y-6">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-full bg-primary/20 flex items-center justify-center text-primary">
              <Upload size={24} />
            </div>
            <h2 className="text-2xl font-bold">Upload Track</h2>
          </div>

          <form onSubmit={handleUploadTrack} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Select Album</label>
              <select 
                required
                value={selectedAlbumId}
                onChange={(e) => setSelectedAlbumId(e.target.value)}
                className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none focus:border-primary/50 transition-all text-sm appearance-none"
              >
                <option value="">Choose an album...</option>
                {myAlbums.map(album => (
                  <option key={album.id} value={album.id}>{album.title}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Track Title</label>
              <input 
                required
                value={trackTitle}
                onChange={(e) => setTrackTitle(e.target.value)}
                placeholder="Track name"
                className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none focus:border-primary/50 transition-all text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-widest text-gray-500 mb-2 ml-1">Audio File</label>
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
                  className="w-full bg-black/40 border border-white/10 rounded-2xl px-5 py-3.5 focus:outline-none border-dashed hover:border-primary/50 transition-all text-sm flex items-center justify-center gap-3 cursor-pointer text-gray-400"
                >
                  <Music size={18} />
                  {trackFile ? trackFile.name : 'Choose MP3 file'}
                </label>
              </div>
            </div>

            {uploadingTrack && (
              <div className="w-full bg-white/5 h-2 rounded-full overflow-hidden mt-2">
                <div 
                  className="bg-primary h-full transition-all duration-300" 
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}

            <button 
              disabled={uploadingTrack || loadingAlbums || myAlbums.length === 0}
              className="w-full bg-primary text-black font-bold py-4 rounded-2xl hover:scale-[1.02] active:scale-[0.98] transition-all disabled:opacity-50 mt-4 flex items-center justify-center gap-2"
            >
              {uploadingTrack ? <Loader2 className="animate-spin" /> : <Upload size={20} />}
              Upload Track
            </button>
            {myAlbums.length === 0 && !loadingAlbums && (
              <p className="text-[10px] text-center text-red-400 mt-2 font-medium">Create an album first before uploading tracks.</p>
            )}
          </form>
        </section>
      </div>
    </div>
  );
};

export default UploadPage;
