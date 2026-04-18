import React from 'react';
import useAuthStore from '../store/authStore';
import { LogOut, Music } from 'lucide-react';

const HomePage = () => {
  const { user, logout } = useAuthStore();

  return (
    <div className="min-h-screen bg-dark text-white p-8">
      <div className="max-w-4xl mx-auto">
        <header className="flex justify-between items-center mb-12">
          <div className="flex items-center gap-3">
            <Music className="text-primary h-8 w-8" />
            <h1 className="text-2xl font-bold italic">MusicSocial</h1>
          </div>
          <button 
            onClick={logout}
            className="flex items-center gap-2 bg-light hover:bg-gray-700 px-4 py-2 rounded-full transition"
          >
            <LogOut size={18} />
            Logout
          </button>
        </header>

        <main>
          <h2 className="text-4xl font-bold mb-4">Good day, {user?.firstName || 'User'}!</h2>
          <p className="text-gray-400 text-lg">Your music ecosystem is ready.</p>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-12">
            <div className="bg-light p-6 rounded-xl border border-gray-800">
              <h3 className="text-xl font-semibold mb-2">My Library</h3>
              <p className="text-gray-400">Manage your playlists and liked tracks.</p>
            </div>
            <div className="bg-light p-6 rounded-xl border border-gray-800">
              <h3 className="text-xl font-semibold mb-2">Social Feed</h3>
              <p className="text-gray-400">See what your friends are listening to.</p>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

export default HomePage;
