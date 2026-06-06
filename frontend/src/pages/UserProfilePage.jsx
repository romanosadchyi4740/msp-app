import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MessageCircle, Settings, Music, Disc, Loader2, User as UserIcon, UserPlus, UserMinus } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import useAuthStore from '../store/authStore';
import useChatStore from '../store/chatStore';
import useFollowStore from '../store/followStore';
import TrackCard from '../components/TrackCard';
import AlbumCard from '../components/AlbumCard';

const UserProfilePage = () => {
  const { userId } = useParams();
  const navigate = useNavigate();
  const { user: currentUser } = useAuthStore();
  const { startChatWithUser } = useChatStore();
  const { followingIds, toggleFollow } = useFollowStore();
  
  const [profile, setProfile] = useState(null);
  const [albums, setAlbums] = useState([]);
  const [tracks, setTracks] = useState([]);
  const [playlists, setPlaylists] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isMessaging, setIsMessaging] = useState(false);

  const isOwnProfile = currentUser?.userId === userId || currentUser?.id === userId;
  const isFollowing = !!followingIds[userId];

  useEffect(() => {
    const fetchProfileData = async () => {
      setLoading(true);
      try {
        const [userRes, albumsRes, tracksRes, playlistsRes] = await Promise.all([
          axiosClient.get(`/api/users/${userId}`),
          axiosClient.get(`/api/albums/search`, { params: { query: '', userId: userId } }), 
          axiosClient.get(`/api/tracks/search`, { params: { query: '', userId: userId } }),
          axiosClient.get(`/api/playlists/user/${userId}`)
        ]);
        
        setProfile(userRes.data);
        setAlbums(albumsRes.data.content || []);
        setTracks(tracksRes.data.content || []);
        setPlaylists(playlistsRes.data || []);
      } catch (error) {
        console.error('Failed to fetch profile', error);
      } finally {
        setLoading(false);
      }
    };

    fetchProfileData();
  }, [userId]);

  const handleMessage = async () => {
    setIsMessaging(true);
    try {
      await startChatWithUser(userId);
      navigate('/chat');
    } catch (error) {
      console.error('Failed to start chat', error);
    } finally {
      setIsMessaging(false);
    }
  };

  const handleFollowToggle = () => {
    toggleFollow(userId);
    setProfile(prev => ({
      ...prev,
      followersCount: isFollowing 
        ? Math.max(0, (prev.followersCount || 0) - 1) 
        : (prev.followersCount || 0) + 1
    }));
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <div className="w-12 h-12 border border-zinc-200 dark:border-zinc-800 flex items-center justify-center relative">
          <div className="w-full h-[1px] bg-zinc-900 dark:bg-white absolute animate-[ping_2s_infinite]"></div>
          <UserIcon size={16} className="text-zinc-400 dark:text-zinc-500" />
        </div>
        <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400 animate-pulse">Syncing Profile</p>
      </div>
    );
  }

  if (!profile) return null;

  return (
    <div className="space-y-24 pb-32">
      {/* Profile Header */}
      <header className="flex flex-col md:flex-row gap-16 items-start border-b border-zinc-200 dark:border-zinc-800 pb-24">
        <div className="w-48 h-48 md:w-64 md:h-64 bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 overflow-hidden relative group">
          {profile.avatarUrl ? (
            <img src={profile.avatarUrl} alt={profile.displayName} referrerPolicy="no-referrer" className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 transition-all duration-700" />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <UserIcon size={64} className="text-zinc-300 dark:text-zinc-800" />
            </div>
          )}
        </div>

        <div className="flex-1 space-y-8 pt-4">
          <div className="inline-block px-3 py-1 border border-zinc-200 dark:border-zinc-800 text-[9px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400">
            {profile.role === 'ROLE_ARTIST' ? 'Verified Artist' : 'Community Member'}
          </div>
          <h1 className="text-6xl md:text-8xl font-black tracking-tighter text-zinc-900 dark:text-white uppercase leading-[0.85]">{profile.displayName}</h1>
          
          <div className="flex gap-6 text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
            <span><span className="text-zinc-900 dark:text-white">{profile.followersCount || 0}</span> Followers</span>
            <span><span className="text-zinc-900 dark:text-white">{profile.followingCount || 0}</span> Following</span>
          </div>

          <p className="text-zinc-500 dark:text-zinc-400 text-sm max-w-xl leading-relaxed font-medium uppercase tracking-tight">
            {profile.biography || 'NO DATA TRANSMITTED FOR THIS BIOGRAPHY.'}
          </p>

          <div className="pt-4 flex gap-4">
            {isOwnProfile ? (
              <button 
                onClick={() => navigate('/settings')}
                className="flex items-center gap-3 px-8 py-4 border border-zinc-900 dark:border-white text-[10px] font-black uppercase tracking-widest hover:bg-zinc-900 hover:text-white dark:hover:bg-white dark:hover:text-black transition-all"
              >
                <Settings size={14} />
                Edit Profile
              </button>
            ) : (
              <>
                <button
                  onClick={handleFollowToggle}
                  className={`flex items-center gap-3 px-8 py-4 border text-[10px] font-black uppercase tracking-widest transition-all ${
                    isFollowing 
                      ? 'border-zinc-200 dark:border-zinc-800 text-zinc-900 dark:text-white hover:border-zinc-900 dark:hover:border-white' 
                      : 'border-zinc-900 dark:border-white bg-zinc-900 text-white dark:bg-white dark:text-black hover:bg-zinc-800 hover:border-zinc-800 dark:hover:bg-zinc-200 dark:hover:border-zinc-200'
                  }`}
                >
                  {isFollowing ? <UserMinus size={14} /> : <UserPlus size={14} />}
                  {isFollowing ? 'Unfollow' : 'Follow'}
                </button>
                <button 
                  onClick={handleMessage}
                  disabled={isMessaging}
                  className="flex items-center gap-3 px-8 py-4 border border-zinc-200 dark:border-zinc-800 text-zinc-900 dark:text-white text-[10px] font-black uppercase tracking-widest hover:border-zinc-900 dark:hover:border-white transition-all disabled:opacity-50"
                >
                  {isMessaging ? <Loader2 size={14} className="animate-spin" /> : <MessageCircle size={14} />}
                  Send Transmission
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Playlists Section */}
      {playlists.length > 0 && (
        <section className="mb-32">
          <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
            <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Public Playlists</h3>
            <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{playlists.length.toString().padStart(2, '0')}</span>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
            {playlists.map((playlist) => (
              <button
                key={playlist.id}
                onClick={() => navigate(`/playlists/${playlist.id}`)}
                className="group relative flex flex-col text-left transition-all duration-300"
              >
                <div className="relative aspect-square w-full mb-3 overflow-hidden bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 group-hover:border-zinc-900/30 dark:group-hover:border-white/30 transition-colors">
                  {playlist.coverUrl ? (
                    <img 
                      src={playlist.coverUrl} 
                      alt={playlist.title} 
                      className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 group-hover:brightness-100 group-hover:scale-105 transition-all duration-700"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <Music size={32} className="text-zinc-300 dark:text-zinc-800" />
                    </div>
                  )}
                </div>
                <div className="space-y-1">
                  <h3 className="text-sm font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">
                    {playlist.title}
                  </h3>
                  <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400 truncate">
                    {playlist.tracksCount || 0} Tracks
                  </p>
                </div>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* Discography */}
      {profile.role === 'ROLE_ARTIST' && (
        <div className="space-y-32">
          {/* Albums Section */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Archives</h3>
              <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{albums.length.toString().padStart(2, '0')}</span>
            </div>
            {albums.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 py-12">No archive collections available.</p>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
                {albums.map((album) => <AlbumCard key={album.id} album={album} />)}
              </div>
            )}
          </section>

          {/* Tracks Section */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em] text-zinc-900 dark:text-white">Transmissions</h3>
              <span className="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">{tracks.length.toString().padStart(2, '0')}</span>
            </div>
            {tracks.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 py-12">No individual transmissions available.</p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-x-8 gap-y-16">
                {tracks.map((track) => <TrackCard key={track.id} track={track} queue={tracks} />)}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
};

export default UserProfilePage;
