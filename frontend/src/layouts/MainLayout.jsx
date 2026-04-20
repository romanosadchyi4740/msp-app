import React, { useState } from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { Home, Search, Library, MessageCircle, LogOut, Music, User, Upload, Star, Loader2 } from 'lucide-react';
import useAuthStore from '../store/authStore';
import AudioPlayer from '../components/AudioPlayer';

const MainLayout = () => {
  const { user, logout, becomeArtist, loading } = useAuthStore();
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
    { icon: Home, label: 'Home', path: '/' },
    { icon: Search, label: 'Search', path: '/search' },
    { icon: Library, label: 'Playlists', path: '/playlists' },
    { icon: MessageCircle, label: 'Chat', path: '/chat' },
  ];

  if (user?.role === 'ROLE_ARTIST') {
    navItems.push({ icon: Upload, label: 'Upload', path: '/upload' });
  }

  return (
    <div className="flex h-screen bg-background text-white overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 flex flex-col border-r border-border pb-24">
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
                    ? 'text-white font-bold' 
                    : 'text-muted hover:text-white'
                }`}
              >
                <Icon size={18} className={isActive ? 'text-white' : 'text-muted group-hover:text-white'} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Become Artist Promo */}
        {user?.role !== 'ROLE_ARTIST' && (
          <div className="mx-6 mb-8 p-6 border border-border space-y-4">
            <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-muted">Artist Mode</h4>
            <p className="text-xs text-muted leading-relaxed">
              Share your music with the community.
            </p>
            <button
              onClick={handleBecomeArtist}
              disabled={isArtistLoading}
              className="w-full py-2 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-accent transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isArtistLoading ? <Loader2 size={12} className="animate-spin" /> : 'Join Now'}
            </button>
          </div>
        )}

        <div className="p-6 border-t border-border space-y-6">
          <div className="flex items-center gap-3 px-2">
            <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center text-[10px] font-bold">
              {user?.displayName?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="text-xs font-bold truncate uppercase tracking-tight">{user?.displayName || 'Unknown User'}</p>
              <p className="text-[10px] text-muted truncate">{user?.userId}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-4 px-2 py-1 text-[10px] font-black uppercase tracking-widest text-muted hover:text-white transition-all"
          >
            <LogOut size={16} />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0 bg-background overflow-y-auto">
        <div className="p-12 max-w-7xl mx-auto w-full">
          <Outlet />
        </div>
      </main>

      {/* Global Audio Player */}
      <AudioPlayer />
    </div>
  );
};

export default MainLayout;
