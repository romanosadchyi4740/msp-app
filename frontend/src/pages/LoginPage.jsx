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
    <div className="min-h-screen flex items-center justify-center bg-dark px-4">
      <div className="max-w-md w-full space-y-8 bg-light p-10 rounded-2xl shadow-xl">
        <div className="text-center">
          <div className="flex justify-center">
            <Music className="h-12 w-12 text-primary" />
          </div>
          <h2 className="mt-6 text-3xl font-extrabold text-white">
            Welcome back
          </h2>
          <p className="mt-2 text-sm text-gray-400">
            Log in to your account
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <div className="bg-red-500/10 border border-red-500 text-red-500 px-4 py-2 rounded-lg text-sm">
              {error}
            </div>
          )}
          
          <div className="rounded-md shadow-sm space-y-4">
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-500" />
              <input
                type="email"
                required
                className="w-full bg-dark border border-gray-700 rounded-lg py-3 px-10 text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition"
                placeholder="Email address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-500" />
              <input
                type="password"
                required
                className="w-full bg-dark border border-gray-700 rounded-lg py-3 px-10 text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={loading}
              className="group relative w-full flex justify-center py-3 px-4 border border-transparent text-sm font-medium rounded-lg text-black bg-primary hover:bg-green-400 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors disabled:opacity-50"
            >
              {loading ? <Loader2 className="animate-spin h-5 w-5" /> : 'Log in'}
            </button>
          </div>
        </form>

        <p className="text-center text-sm text-gray-400">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary hover:underline">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
