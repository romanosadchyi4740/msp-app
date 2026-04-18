import React from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { Home, Library, MessageCircle, LogOut, Music, User, Upload } from 'lucide-react';
import useAuthStore from '../store/authStore';
import AudioPlayer from '../components/AudioPlayer';

const MainLayout = () => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
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
