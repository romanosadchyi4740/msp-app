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
  const [loading, setLoading] = useState(true);
  const [isMessaging, setIsMessaging] = useState(false);

  const isOwnProfile = currentUser?.userId === userId || currentUser?.id === userId;
  const isFollowing = !!followingIds[userId];

  useEffect(() => {
    const fetchProfileData = async () => {
      setLoading(true);
      try {
        const [userRes, albumsRes, tracksRes] = await Promise.all([
          axiosClient.get(`/api/users/${userId}`),
          axiosClient.get(`/api/albums/search`, { params: { query: '', userId: userId } }), 
          axiosClient.get(`/api/tracks/search`, { params: { query: '', userId: userId } })
        ]);
        
        setProfile(userRes.data);
        setAlbums(albumsRes.data.content || []);
        setTracks(tracksRes.data.content || []);
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
        <div className="w-12 h-12 border border-border flex items-center justify-center relative">
          <div className="w-full h-[1px] bg-white absolute animate-[ping_2s_infinite]"></div>
          <UserIcon size={16} className="text-muted" />
        </div>
        <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-muted animate-pulse">Syncing Profile</p>
      </div>
    );
  }

  if (!profile) return null;

  return (
    <div className="space-y-24 pb-32">
      {/* Profile Header */}
      <header className="flex flex-col md:flex-row gap-16 items-start border-b border-border pb-24">
        <div className="w-48 h-48 md:w-64 md:h-64 bg-surface border border-border overflow-hidden relative group">
          {profile.avatarUrl ? (
            <img src={profile.avatarUrl} alt={profile.displayName} className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 transition-all duration-700" />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <UserIcon size={64} className="text-zinc-800" />
            </div>
          )}
        </div>

        <div className="flex-1 space-y-8 pt-4">
          <div className="inline-block px-3 py-1 border border-border text-[9px] font-black uppercase tracking-[0.3em] text-muted">
            {profile.role === 'ROLE_ARTIST' ? 'Verified Artist' : 'Community Member'}
          </div>
          <h1 className="text-6xl md:text-8xl font-black tracking-tighter text-white uppercase leading-[0.85]">{profile.displayName}</h1>
          
          <div className="flex gap-6 text-[10px] font-black uppercase tracking-widest text-muted">
            <span><span className="text-white">{profile.followersCount || 0}</span> Followers</span>
            <span><span className="text-white">{profile.followingCount || 0}</span> Following</span>
          </div>

          <p className="text-muted text-sm max-w-xl leading-relaxed font-medium uppercase tracking-tight">
            {profile.biography || 'NO DATA TRANSMITTED FOR THIS BIOGRAPHY.'}
          </p>

          <div className="pt-4 flex gap-4">
            {isOwnProfile ? (
              <button 
                onClick={() => navigate('/settings')}
                className="flex items-center gap-3 px-8 py-4 border border-white text-[10px] font-black uppercase tracking-widest hover:bg-white hover:text-black transition-all"
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
                      ? 'border-border text-white hover:border-white' 
                      : 'border-white bg-white text-black hover:bg-zinc-200 hover:border-zinc-200'
                  }`}
                >
                  {isFollowing ? <UserMinus size={14} /> : <UserPlus size={14} />}
                  {isFollowing ? 'Unfollow' : 'Follow'}
                </button>
                <button 
                  onClick={handleMessage}
                  disabled={isMessaging}
                  className="flex items-center gap-3 px-8 py-4 border border-border text-white text-[10px] font-black uppercase tracking-widest hover:border-white transition-all disabled:opacity-50"
                >
                  {isMessaging ? <Loader2 size={14} className="animate-spin" /> : <MessageCircle size={14} />}
                  Send Transmission
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Discography */}
      {profile.role === 'ROLE_ARTIST' && (
        <div className="space-y-32">
          {/* Albums Section */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em]">Collections</h3>
              <span className="text-[10px] font-medium text-muted tabular-nums">{albums.length.toString().padStart(2, '0')}</span>
            </div>
            {albums.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-muted py-12">No archive collections available.</p>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8">
                {albums.map((album) => <AlbumCard key={album.id} album={album} />)}
              </div>
            )}
          </section>

          {/* Tracks Section */}
          <section>
            <div className="flex items-baseline justify-between mb-12 border-b border-border pb-4">
              <h3 className="text-xs font-black uppercase tracking-[0.4em]">Transmissions</h3>
              <span className="text-[10px] font-medium text-muted tabular-nums">{tracks.length.toString().padStart(2, '0')}</span>
            </div>
            {tracks.length === 0 ? (
              <p className="text-[10px] font-black uppercase tracking-widest text-muted py-12">No individual transmissions available.</p>
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
