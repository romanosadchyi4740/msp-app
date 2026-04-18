import React from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { Home, Library, MessageCircle, LogOut, Music, User } from 'lucide-react';
import useAuthStore from '../store/authStore';

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

      {/* Player Placeholder */}
      <div className="fixed bottom-0 left-0 right-0 h-24 bg-dark border-t border-white/5 px-6 flex items-center justify-between z-40">
        <div className="flex items-center gap-4 w-1/3">
           <div className="w-14 h-14 bg-white/10 rounded flex items-center justify-center">
             <Music className="text-gray-500" />
           </div>
           <div>
             <p className="text-sm font-medium">Select a track</p>
             <p className="text-xs text-gray-500">No artist selected</p>
           </div>
        </div>
        
        <div className="flex flex-col items-center gap-2 w-1/3">
          <div className="flex items-center gap-6 text-gray-400">
            {/* Playback controls icons here */}
            <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition cursor-pointer">
               <Music size={20} fill="currentColor" />
            </div>
          </div>
          <div className="w-full max-w-md h-1 bg-white/10 rounded-full">
            <div className="w-1/3 h-full bg-primary rounded-full"></div>
          </div>
        </div>

        <div className="w-1/3 flex justify-end items-center gap-4 text-gray-400">
          <div className="w-24 h-1 bg-white/10 rounded-full">
            <div className="w-2/3 h-full bg-white/40 rounded-full"></div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MainLayout;
