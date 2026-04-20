import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Save, ArrowLeft, Loader2, User as UserIcon } from 'lucide-react';
import useAuthStore from '../store/authStore';
import axiosClient from '../api/axiosClient';

const SettingsPage = () => {
  const { user, updateProfile, loading: isUpdating } = useAuthStore();
  const navigate = useNavigate();
  
  const [formData, setFormData] = useState({
    displayName: '',
    biography: '',
    avatarUrl: ''
  });
  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        const response = await axiosClient.get('/api/users/me');
        const userData = response.data;
        setFormData({
          displayName: userData.displayName || '',
          biography: userData.biography || '',
          avatarUrl: userData.avatarUrl || ''
        });
      } catch (err) {
        console.error('Failed to fetch user data', err);
        setError('Failed to load profile data');
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    setSuccess(false);
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const result = await updateProfile(formData);
    if (result) {
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } else {
      setError('Failed to update profile');
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 size={32} className="text-muted animate-spin" />
        <p className="mt-8 text-[10px] font-black uppercase tracking-[0.3em] text-muted">Retrieving Data</p>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-16 pb-32">
      <header className="flex items-center justify-between border-b border-border pb-8">
        <div className="flex items-center gap-4">
          <button 
            onClick={() => navigate(-1)}
            className="p-2 hover:bg-surface transition-colors"
          >
            <ArrowLeft size={20} />
          </button>
          <h1 className="text-4xl font-black uppercase tracking-tighter">Settings</h1>
        </div>
      </header>

      <form onSubmit={handleSubmit} className="space-y-12">
        <div className="space-y-8">
          {/* Avatar Placeholder/Info */}
          <div className="flex items-center gap-8 p-8 border border-border bg-surface/50">
            <div className="w-20 h-20 bg-zinc-800 flex items-center justify-center">
              {formData.avatarUrl ? (
                <img src={formData.avatarUrl} alt="Avatar" className="w-full h-full object-cover grayscale" />
              ) : (
                <UserIcon size={32} className="text-muted" />
              )}
            </div>
            <div className="space-y-1">
              <h4 className="text-[10px] font-black uppercase tracking-widest">Profile Identity</h4>
              <p className="text-xs text-muted">Avatar uploads are managed via verified providers.</p>
            </div>
          </div>

          {/* Display Name */}
          <div className="space-y-4">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] text-muted">Public Alias</label>
            <input
              type="text"
              name="displayName"
              value={formData.displayName}
              onChange={handleChange}
              className="w-full bg-transparent border border-border p-4 text-sm focus:outline-none focus:border-white transition-colors uppercase font-bold"
              placeholder="ENTER DISPLAY NAME"
              required
            />
          </div>

          {/* Biography */}
          <div className="space-y-4">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] text-muted">Personnel Bio</label>
            <textarea
              name="biography"
              value={formData.biography}
              onChange={handleChange}
              rows={5}
              className="w-full bg-transparent border border-border p-4 text-sm focus:outline-none focus:border-white transition-colors uppercase font-medium leading-relaxed resize-none"
              placeholder="WRITE YOUR BIOGRAPHY..."
            />
          </div>

          {/* Avatar URL (Optional for now) */}
          <div className="space-y-4">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] text-muted">Avatar Resource URL</label>
            <input
              type="url"
              name="avatarUrl"
              value={formData.avatarUrl}
              onChange={handleChange}
              className="w-full bg-transparent border border-border p-4 text-sm focus:outline-none focus:border-white transition-colors"
              placeholder="https://example.com/avatar.jpg"
            />
          </div>
        </div>

        <div className="flex flex-col gap-4">
          {error && (
            <div className="p-4 border border-red-900 bg-red-950/20 text-red-500 text-[10px] font-black uppercase tracking-widest">
              {error}
            </div>
          )}
          {success && (
            <div className="p-4 border border-green-900 bg-green-950/20 text-green-500 text-[10px] font-black uppercase tracking-widest">
              System updated successfully.
            </div>
          )}
          
          <button
            type="submit"
            disabled={isUpdating}
            className="flex items-center justify-center gap-3 w-full py-4 bg-white text-black text-[10px] font-black uppercase tracking-[0.2em] hover:bg-accent transition-all disabled:opacity-50"
          >
            {isUpdating ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            Commit Changes
          </button>
        </div>
      </form>
    </div>
  );
};

export default SettingsPage;
