import React, { useState } from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { Home, Library, MessageCircle, LogOut, Music, User, Upload, Star, Loader2 } from 'lucide-react';
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
    { icon: Library, label: 'Playlists', path: '/playlists' },
    { icon: MessageCircle, label: 'Chat', path: '/chat' },
  ];

  if (user?.role === 'ROLE_ARTIST') {
    navItems.push({ icon: Upload, label: 'Upload', path: '/upload' });
  }

  return (
    <div className="flex h-screen bg-black text-white overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-dark flex flex-col border-r border-white/5 pb-24">
        <div className="p-6 flex items-center gap-3">
          <Music className="text-primary h-8 w-8" />
          <h1 className="text-xl font-bold italic tracking-tighter">MusicSocial</h1>
        </div>

        <nav className="flex-1 px-4 py-4 space-y-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-4 px-4 py-3 rounded-lg transition-all duration-200 group ${
                  isActive 
                    ? 'bg-primary text-black font-semibold' 
                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`}
              >
                <Icon size={22} className={isActive ? 'text-black' : 'group-hover:text-white'} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Become Artist Promo */}
        {user?.role !== 'ROLE_ARTIST' && (
          <div className="mx-4 mb-4 p-4 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 border border-primary/20 space-y-3">
            <div className="flex items-center gap-2 text-primary">
              <Star size={18} fill="currentColor" />
              <span className="text-xs font-bold uppercase tracking-wider">Artist Mode</span>
            </div>
            <p className="text-[11px] text-gray-400 leading-relaxed">
              Want to share your music? Become an artist today!
            </p>
            <button
              onClick={handleBecomeArtist}
              disabled={isArtistLoading}
              className="w-full py-2 bg-primary text-black text-xs font-bold rounded-xl hover:scale-[1.02] active:scale-[0.98] transition-all disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isArtistLoading ? <Loader2 size={14} className="animate-spin" /> : 'Join the stage'}
            </button>
          </div>
        )}

        <div className="p-4 border-t border-white/5 space-y-4">
          <div className="flex items-center gap-3 px-4 py-2">
            <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-black font-bold">
              {user?.displayName?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="text-sm font-medium truncate">{user?.displayName || 'Unknown User'}</p>
              <p className="text-xs text-gray-500 truncate">{user?.userId}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-4 px-4 py-3 text-gray-400 hover:text-white hover:bg-white/5 rounded-lg transition-all"
          >
            <LogOut size={22} />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0 bg-gradient-to-b from-light to-dark overflow-y-auto pb-24">
        <div className="p-8">
          <Outlet />
        </div>
      </main>

      {/* Global Audio Player */}
      <AudioPlayer />
    </div>
  );
};

export default MainLayout;
