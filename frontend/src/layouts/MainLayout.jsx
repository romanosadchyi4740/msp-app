import React, {useEffect, useState} from 'react';
import {Link, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {
  Activity,
  Layers,
  Loader2,
  LogOut,
  Music,
  Radar,
  RadioReceiver,
  Satellite,
  Search,
  Settings,
  Star,
  Terminal,
  Sun,
  Moon
} from 'lucide-react';
import useAuthStore from '../store/authStore';
import useLikeStore from '../store/likeStore';
import useFollowStore from '../store/followStore';
import useChatStore from '../store/chatStore';
import useUiStore from '../store/uiStore';
import AudioPlayer from '../components/AudioPlayer';
import ShareTrackModal from '../components/modals/ShareTrackModal';
import AddToPlaylistModal from '../components/modals/AddToPlaylistModal';

const MainLayout = () => {
  const { user, logout, becomeArtist, loading } = useAuthStore();
  const { fetchLikedTracks, initialized } = useLikeStore();
  const { fetchFollowing, initialized: followInitialized } = useFollowStore();
  const { initSocket, closeSocket } = useChatStore();
  const { theme, toggleTheme } = useUiStore();

  useEffect(() => {
    if (!initialized) {
      fetchLikedTracks();
    }
    if (!followInitialized) {
      fetchFollowing();
    }
  }, [fetchLikedTracks, initialized, fetchFollowing, followInitialized]);

  // Global WebSocket initialization for authenticated users
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token && user) {
      initSocket(token);
    }
    return () => {
      closeSocket();
    };
  }, [user, initSocket, closeSocket]);

  const navigate = useNavigate();
  const location = useLocation();
  const [isArtistLoading, setIsArtistLoading] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleBecomeArtist = async () => {
    setIsArtistLoading(true);
    const success = await becomeArtist();
    if (success) {
      navigate('/upload');
    }
    setIsArtistLoading(false);
  };

  const navItems = [
    { icon: Terminal, label: 'Mainframe', path: '/' },
    { icon: Search, label: 'Global Query', path: '/search' },
    { icon: Radar, label: 'Network Radar', path: '/explore' },
    { icon: Activity, label: 'Telemetry', path: '/telemetry' },
    { icon: Layers, label: 'Collections', path: '/playlists' },
    { icon: Star, label: 'Saved Transmissions', path: '/collection/tracks' },
    { icon: RadioReceiver, label: 'Comms Link', path: '/chat' },
    { icon: Settings, label: 'System Config', path: '/settings' },
  ];

  if (user?.role === 'ROLE_ARTIST') {
    navItems.push({ icon: Satellite, label: 'Data Uplink', path: '/upload' });
  }

  return (
    <div className="flex h-screen bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-white overflow-hidden transition-colors duration-300">
      {/* Sidebar */}
      <aside className="w-64 flex flex-col border-r border-zinc-200 dark:border-zinc-800 pb-24">
        <div className="p-8 pb-12 flex items-center gap-2">
          <Music className="h-6 w-6" />
          <h1 className="text-xl font-black tracking-tighter uppercase">MusicSocial</h1>
        </div>

        <nav className="flex-1 px-4 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-4 px-4 py-3 text-sm transition-all duration-200 group ${
                  isActive 
                    ? 'text-zinc-900 dark:text-white font-bold' 
                    : 'text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white'
                }`}
              >
                <Icon size={18} className={isActive ? 'text-zinc-900 dark:text-white' : 'text-zinc-500 dark:text-zinc-400 group-hover:text-zinc-900 dark:group-hover:text-white'} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Become Artist Promo */}
        {user?.role !== 'ROLE_ARTIST' && (
          <div className="mx-6 mb-8 p-6 border border-zinc-200 dark:border-zinc-800 space-y-4">
            <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">Artist Mode</h4>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
              Share your music with the community.
            </p>
            <button
              onClick={handleBecomeArtist}
              disabled={isArtistLoading}
              className="w-full py-2 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-100 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isArtistLoading ? <Loader2 size={12} className="animate-spin" /> : 'Join Now'}
            </button>
          </div>
        )}

        <div className="p-6 border-t border-zinc-200 dark:border-zinc-800 space-y-6">
          <Link 
            to={`/users/${user?.userId}`}
            className="flex items-center gap-3 px-2 hover:opacity-80 transition-opacity group"
          >
            <div className="w-8 h-8 bg-zinc-200 text-zinc-900 dark:bg-zinc-800 dark:text-white flex items-center justify-center text-[10px] font-bold group-hover:bg-zinc-300 dark:group-hover:bg-zinc-700 transition-colors">
              {user?.displayName?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="text-xs font-bold truncate uppercase tracking-tight group-hover:text-zinc-600 dark:group-hover:text-zinc-300 transition-colors">
                {user?.displayName || 'Unknown User'}
              </p>
              <p className="text-[10px] text-zinc-500 dark:text-zinc-400 truncate">{user?.userId}</p>
            </div>
          </Link>
          <div className="flex flex-col gap-2">
            <button
              onClick={toggleTheme}
              className="w-full flex items-center gap-4 px-2 py-1 text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-all"
            >
              {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
              <span>Invert Polarity</span>
            </button>
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-4 px-2 py-1 text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-all"
            >
              <LogOut size={16} />
              <span>Logout</span>
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0 bg-zinc-50 dark:bg-zinc-950 overflow-y-auto transition-colors duration-300">
        <div className="p-12 max-w-7xl mx-auto w-full">
          <Outlet />
        </div>
      </main>

      {/* Global Audio Player */}
      <AudioPlayer />
      
      {/* Share Track Modal */}
      <ShareTrackModal />
      
      {/* Add To Playlist Modal */}
      <AddToPlaylistModal />
    </div>
  );
};

export default MainLayout;
