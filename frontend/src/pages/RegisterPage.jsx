import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Music, User, Mail, Lock, Loader2 } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import useAuthStore from '../store/authStore';

const RegisterPage = () => {
  const [formData, setFormData] = useState({
    displayName: '',
    email: '',
    password: ''
  });
  const { register, loginWithGoogle, loading, error } = useAuthStore();
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

  const handleGoogleSuccess = async (credentialResponse) => {
    if (credentialResponse.credential) {
      const success = await loginWithGoogle(credentialResponse.credential);
      if (success) {
        navigate('/');
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-zinc-950 px-4 transition-colors duration-300">
      <div className="max-w-md w-full space-y-8 bg-white dark:bg-zinc-900 p-12 border border-zinc-200 dark:border-zinc-800 shadow-2xl">
        <div className="text-center">
          <div className="flex justify-center">
            <Music className="h-10 w-10 text-zinc-900 dark:text-white" />
          </div>
          <h2 className="mt-8 text-3xl font-black uppercase tracking-tighter text-zinc-900 dark:text-white">
            Create Identity
          </h2>
          <p className="mt-2 text-[10px] font-bold uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">
            Establish your presence
          </p>
        </div>

        <form className="mt-8 space-y-4" onSubmit={handleSubmit}>
          {error && (
            <div className="border border-red-500/50 text-red-600 dark:text-red-500 px-4 py-3 text-[10px] font-bold uppercase tracking-widest bg-red-50 dark:bg-red-500/5">
              Error: {error}
            </div>
          )}
          
          <div className="space-y-4">
            <input
              name="displayName"
              type="text"
              required
              className="w-full bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 py-4 px-6 text-sm text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600 focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors"
              placeholder="DISPLAY NAME"
              value={formData.displayName}
              onChange={handleChange}
            />

            <input
              name="email"
              type="email"
              required
              className="w-full bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 py-4 px-6 text-sm text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600 focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors"
              placeholder="EMAIL ADDRESS"
              value={formData.email}
              onChange={handleChange}
            />

            <input
              name="password"
              type="password"
              required
              className="w-full bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 py-4 px-6 text-sm text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600 focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors"
              placeholder="PASSWORD"
              value={formData.password}
              onChange={handleChange}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-4 px-4 bg-zinc-900 text-white dark:bg-white dark:text-black text-[11px] font-black uppercase tracking-[0.3em] hover:bg-zinc-800 dark:hover:bg-zinc-200 focus:outline-none transition-colors disabled:opacity-50 mt-8"
          >
            {loading ? <Loader2 className="animate-spin h-5 w-5" /> : 'Confirm Registration'}
          </button>
        </form>

        <div className="relative mt-6">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-zinc-200 dark:border-zinc-800"></div>
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-white dark:bg-zinc-900 text-zinc-500 dark:text-zinc-400 text-[10px] font-bold uppercase tracking-widest">Or register with</span>
          </div>
        </div>

        <div className="flex justify-center mt-6">
          <GoogleLogin
            onSuccess={handleGoogleSuccess}
            onError={() => {
              console.error('Google Login Failed');
            }}
            theme="filled_black"
            shape="rectangular"
            text="signup_with"
            size="large"
          />
        </div>

        <p className="text-center mt-8 text-[10px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
          Already Registered?{' '}
          <Link to="/login" className="text-zinc-900 dark:text-white hover:underline underline-offset-4">
            Access Account
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
