import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Music, User, Mail, Lock, Loader2 } from 'lucide-react';
import useAuthStore from '../store/authStore';

const RegisterPage = () => {
  const [formData, setFormData] = useState({
    displayName: '',
    email: '',
    password: ''
  });
  const { register, loading, error } = useAuthStore();
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const success = await register(formData);
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
            Create Identity
          </h2>
          <p className="mt-2 text-[10px] font-bold uppercase tracking-[0.2em] text-muted">
            Establish your presence
          </p>
        </div>

        <form className="mt-8 space-y-4" onSubmit={handleSubmit}>
          {error && (
            <div className="border border-red-500/50 text-red-500 px-4 py-3 text-[10px] font-bold uppercase tracking-widest bg-red-500/5">
              Error: {error}
            </div>
          )}
          
          <div className="space-y-4">
            <input
              name="displayName"
              type="text"
              required
              className="w-full bg-background border border-border py-4 px-6 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-white transition-colors"
              placeholder="DISPLAY NAME"
              value={formData.displayName}
              onChange={handleChange}
            />

            <input
              name="email"
              type="email"
              required
              className="w-full bg-background border border-border py-4 px-6 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-white transition-colors"
              placeholder="EMAIL ADDRESS"
              value={formData.email}
              onChange={handleChange}
            />

            <input
              name="password"
              type="password"
              required
              className="w-full bg-background border border-border py-4 px-6 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-white transition-colors"
              placeholder="PASSWORD"
              value={formData.password}
              onChange={handleChange}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-4 px-4 bg-white text-black text-[11px] font-black uppercase tracking-[0.3em] hover:bg-zinc-200 focus:outline-none transition-colors disabled:opacity-50 mt-8"
          >
            {loading ? <Loader2 className="animate-spin h-5 w-5" /> : 'Confirm Registration'}
          </button>
        </form>

        <p className="text-center text-[10px] font-bold uppercase tracking-widest text-muted">
          Already Registered?{' '}
          <Link to="/login" className="text-white hover:underline underline-offset-4">
            Access Account
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
