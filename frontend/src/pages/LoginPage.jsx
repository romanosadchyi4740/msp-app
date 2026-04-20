import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Music, Mail, Lock, Loader2 } from 'lucide-react';
import useAuthStore from '../store/authStore';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login, loading, error } = useAuthStore();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    const success = await login(email, password);
    if (success) {
      navigate('/');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="max-w-md w-full space-y-12 bg-surface p-12 border border-border shadow-2xl">
        <div className="text-center">
          <div className="flex justify-center">
            <Music className="h-10 w-10 text-white" />
          </div>
          <h2 className="mt-8 text-3xl font-black uppercase tracking-tighter text-white">
            Access Archive
          </h2>
          <p className="mt-2 text-[10px] font-bold uppercase tracking-[0.2em] text-muted">
            Identify your credentials
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <div className="border border-red-500/50 text-red-500 px-4 py-3 text-[10px] font-bold uppercase tracking-widest bg-red-500/5">
              Error: {error}
            </div>
          )}
          
          <div className="space-y-4">
            <div className="relative">
              <input
                type="email"
                required
                className="w-full bg-background border border-border py-4 px-6 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-white transition-colors"
                placeholder="EMAIL@DOMAIN.COM"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="relative">
              <input
                type="password"
                required
                className="w-full bg-background border border-border py-4 px-6 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-white transition-colors"
                placeholder="PASSWORD"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <div className="pt-4">
            <button
              type="submit"
              disabled={loading}
              className="group relative w-full flex justify-center py-4 px-4 bg-white text-black text-[11px] font-black uppercase tracking-[0.3em] hover:bg-zinc-200 focus:outline-none transition-colors disabled:opacity-50"
            >
              {loading ? <Loader2 className="animate-spin h-5 w-5" /> : 'Execute Login'}
            </button>
          </div>
        </form>

        <p className="text-center text-[10px] font-bold uppercase tracking-widest text-muted">
          New User?{' '}
          <Link to="/register" className="text-white hover:underline underline-offset-4">
            Create Identity
          </Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
