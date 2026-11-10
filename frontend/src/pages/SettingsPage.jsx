import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AlertTriangle, ArrowLeft, Loader2, Save, User as UserIcon} from 'lucide-react';
import useAuthStore from '../store/authStore';
import axiosClient from '../api/axiosClient';
import TerminateIdentityModal from '../components/modals/TerminateIdentityModal';
import useRtcStore from '../store/rtcStore';

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
  const [isTerminateModalOpen, setIsTerminateModalOpen] = useState(false);
  const relayOnly = useRtcStore((state) => state.relayOnly);
  const setRelayOnly = useRtcStore((state) => state.setRelayOnly);

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
          <h1 className="text-4xl font-black uppercase tracking-tighter">Config</h1>
        </div>
      </header>

      <form onSubmit={handleSubmit} className="space-y-12">
        <div className="space-y-8">
          {/* Avatar Placeholder/Info */}
          <div className="flex items-center gap-8 p-8 border border-border bg-surface/50">
            <div className="w-20 h-20 bg-zinc-800 flex items-center justify-center">
              {formData.avatarUrl ? (
                <img src={formData.avatarUrl} alt="Avatar" referrerPolicy="no-referrer" className="w-full h-full object-cover grayscale" />
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

      {/* Room audio (SyncChord, KAN-47): plain wording, as on the room screens */}
      <section className="pt-16 border-t border-border space-y-6">
        <h2 className="text-xs font-black uppercase tracking-[0.3em]">Room audio</h2>
        <label className="flex items-start gap-4 cursor-pointer">
          <input
            type="checkbox"
            checked={relayOnly}
            onChange={(event) => setRelayOnly(event.target.checked)}
            className="mt-1 h-4 w-4 accent-zinc-900 dark:accent-white"
          />
          <span className="space-y-1">
            <span className="block text-sm font-bold">Relay only</span>
            <span className="block text-xs text-zinc-500 dark:text-zinc-400 max-w-md">
              Send and receive room audio only through the relay server, so the other participants never see your
              IP address. The route is longer, so the delay may grow. Kept in this browser.
            </span>
          </span>
        </label>
      </section>

      {/* Danger Zone */}
      <section className="pt-16 border-t border-red-900/30 space-y-8">
        <div className="flex items-center gap-3 text-red-500">
          <AlertTriangle size={16} />
          <h2 className="text-xs font-black uppercase tracking-[0.3em]">Danger Zone</h2>
        </div>
        
        <div className="p-8 border border-red-900/20 bg-red-950/5 flex flex-col md:flex-row items-center justify-between gap-8">
          <div className="space-y-2">
            <h3 className="text-[10px] font-black uppercase tracking-widest text-white">Terminate Identity</h3>
            <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 max-w-sm">
              Permanently purge your digital footprint and disconnect from the network.
            </p>
          </div>
          <button 
            onClick={() => setIsTerminateModalOpen(true)}
            className="px-8 py-3 border border-red-900 text-red-500 text-[9px] font-black uppercase tracking-widest hover:bg-red-500 hover:text-white transition-all whitespace-nowrap"
          >
            Terminate Identity
          </button>
        </div>
      </section>

      {isTerminateModalOpen && (
        <TerminateIdentityModal onClose={() => setIsTerminateModalOpen(false)} />
      )}
    </div>
  );
};

export default SettingsPage;
