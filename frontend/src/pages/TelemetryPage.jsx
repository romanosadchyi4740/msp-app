import React, { useState, useEffect } from 'react';
import { Loader2, Send, Plus, X, Music } from 'lucide-react';
import toast from 'react-hot-toast';
import axiosClient from '../api/axiosClient';
import BroadcastCard from '../components/telemetry/BroadcastCard';
import useAuthStore from '../store/authStore';

const TelemetryPage = () => {
  const { user } = useAuthStore();
  const [broadcasts, setBroadcasts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  // Broadcast Form State
  const [content, setContent] = useState('');
  const [showPoll, setShowPoll] = useState(false);
  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptions, setPollOptions] = useState(['', '']);
  const [myTracks, setMyTracks] = useState([]);
  const [selectedTrackId, setSelectedTrackId] = useState('');
  const [sending, setSending] = useState(false);

  useEffect(() => {
    fetchFeed(0);
    fetchMyTracks();
  }, []);

  const fetchFeed = async (pageNumber) => {
    try {
      const response = await axiosClient.get(`/api/telemetry/feed?page=${pageNumber}&size=10`);
      if (pageNumber === 0) {
        setBroadcasts(response.data.content);
      } else {
        setBroadcasts(prev => [...prev, ...response.data.content]);
      }
      setHasMore(!response.data.last);
      setPage(pageNumber);
    } catch (error) {
      // axiosClient's interceptor shows the error toast (or redirects to login on 401)
      console.error('Failed to fetch telemetry feed:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchMyTracks = async () => {
    if (user?.role === 'ROLE_ARTIST') {
      try {
        const response = await axiosClient.get(`/api/users/${user.userId}/tracks`);
        setMyTracks(response.data);
      } catch (error) {
        console.error('Failed to fetch tracks:', error);
      }
    }
  };

  const handleAddPollOption = () => {
    if (pollOptions.length < 4) {
      setPollOptions([...pollOptions, '']);
    }
  };

  const handleRemovePollOption = (index) => {
    if (pollOptions.length > 2) {
      setPollOptions(pollOptions.filter((_, i) => i !== index));
    }
  };

  const handlePollOptionChange = (index, value) => {
    const newOptions = [...pollOptions];
    newOptions[index] = value;
    setPollOptions(newOptions);
  };

  const handleSendBroadcast = async () => {
    if (!content.trim()) return;

    const payload = {
      content,
      attachedTrackId: selectedTrackId || null,
      poll: null
    };

    if (showPoll) {
      const validOptions = pollOptions.filter(opt => opt.trim() !== '');
      if (!pollQuestion.trim() || validOptions.length < 2) {
        toast.error('INVALID_MODULE: Consensus requires a question and at least 2 options.');
        return;
      }
      payload.poll = {
        question: pollQuestion,
        options: validOptions
      };
    }

    setSending(true);
    try {
      await axiosClient.post('/api/telemetry/broadcast', payload);
      toast.success('BROADCAST_SENT: Signal injected into the network.');
      setContent('');
      setShowPoll(false);
      setPollQuestion('');
      setPollOptions(['', '']);
      setSelectedTrackId('');
      fetchFeed(0); // Refresh feed
    } catch (error) {
      // axiosClient's interceptor shows the error toast (or redirects to login on 401)
      console.error('Failed to send broadcast:', error);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto pb-32">
      <header className="mb-12 border-b border-zinc-200 dark:border-zinc-800 pb-8">
        <h1 className="text-4xl font-black mb-4 tracking-tighter uppercase text-zinc-900 dark:text-white">Network Telemetry</h1>
        <p className="text-zinc-500 dark:text-zinc-400 text-xs font-bold uppercase tracking-[0.2em] flex items-center gap-2">
          <span className="w-2 h-2 bg-zinc-900 dark:bg-white animate-pulse inline-block" />
          Decrypting incoming signals from followed nodes...
        </p>
      </header>

      {/* Broadcast Composer */}
      {user?.role === 'ROLE_ARTIST' && (
        <section className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-6 mb-12">
          <h2 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 mb-4">Initialize Broadcast</h2>
          
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="INPUT SIGNAL DATA..."
            className="w-full bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 p-4 text-sm font-medium focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors resize-none h-24 mb-4 text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
          />

          {showPoll && (
            <div className="border border-zinc-200 dark:border-zinc-800 p-4 bg-zinc-50 dark:bg-zinc-950 mb-4 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-[10px] font-black uppercase tracking-widest text-zinc-900 dark:text-white flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-zinc-900 dark:bg-white" />
                  Consensus Module
                </h3>
                <button onClick={() => setShowPoll(false)} className="text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-colors">
                  <X size={14} />
                </button>
              </div>
              
              <input
                value={pollQuestion}
                onChange={(e) => setPollQuestion(e.target.value)}
                placeholder="QUERY PARAMETERS..."
                className="w-full bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-3 text-xs font-bold focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
              />

              <div className="space-y-2">
                {pollOptions.map((opt, index) => (
                  <div key={index} className="flex gap-2">
                    <input
                      value={opt}
                      onChange={(e) => handlePollOptionChange(index, e.target.value)}
                      placeholder={`OPTION_0${index + 1}`}
                      className="flex-1 bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-3 text-xs font-bold focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-colors text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
                    />
                    {pollOptions.length > 2 && (
                      <button 
                        onClick={() => handleRemovePollOption(index)}
                        className="w-10 flex items-center justify-center border border-zinc-200 dark:border-zinc-800 hover:border-red-500 hover:text-red-500 text-zinc-500 dark:text-zinc-400 transition-colors"
                      >
                        <X size={12} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
              
              {pollOptions.length < 4 && (
                <button 
                  onClick={handleAddPollOption}
                  className="text-[9px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white flex items-center gap-2 transition-colors"
                >
                  <Plus size={10} /> Add Variable
                </button>
              )}
            </div>
          )}

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                onClick={() => setShowPoll(!showPoll)}
                className={`text-[10px] font-black uppercase tracking-widest flex items-center gap-2 transition-colors px-3 py-2 border ${showPoll ? 'border-zinc-900 dark:border-white text-zinc-900 dark:text-white' : 'border-zinc-200 dark:border-zinc-800 text-zinc-500 dark:text-zinc-400 hover:border-zinc-900 dark:hover:border-white hover:text-zinc-900 dark:hover:text-white'}`}
              >
                <Plus size={12} /> Consensus Module
              </button>
              
              {myTracks.length > 0 && (
                <div className="flex items-center gap-2 border border-zinc-200 dark:border-zinc-800 px-3 py-2 bg-zinc-50 dark:bg-zinc-950">
                  <Music size={12} className="text-zinc-500 dark:text-zinc-400" />
                  <select
                    value={selectedTrackId}
                    onChange={(e) => setSelectedTrackId(e.target.value)}
                    className="bg-transparent text-[10px] font-black uppercase tracking-widest text-zinc-900 dark:text-white focus:outline-none appearance-none"
                  >
                    <option value="" className="text-zinc-500 dark:text-zinc-400">ATTACH TRANSMISSION (OPTIONAL)</option>
                    {myTracks.map(t => (
                      <option key={t.id} value={t.id} className="text-zinc-900 dark:text-white bg-white dark:bg-zinc-900">{t.title}</option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            <button
              onClick={handleSendBroadcast}
              disabled={sending || !content.trim()}
              className="bg-zinc-900 text-white dark:bg-white dark:text-black px-6 py-2 text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-colors disabled:opacity-50 flex items-center gap-2"
            >
              {sending ? <Loader2 size={12} className="animate-spin" /> : <Send size={12} />}
              Execute
            </button>
          </div>
        </section>
      )}

      {/* Feed Display */}
      <section className="space-y-4">
        {loading && page === 0 ? (
          <div className="flex items-center justify-center p-12">
            <Loader2 className="animate-spin text-zinc-500 dark:text-zinc-400" size={32} />
          </div>
        ) : broadcasts.length === 0 ? (
          <div className="p-12 text-center border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900">
            <p className="text-xs font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">NO SIGNALS DETECTED IN SECTOR.</p>
            <p className="text-[10px] mt-2 text-zinc-400 dark:text-zinc-500">Establish connections with other nodes to receive telemetry.</p>
          </div>
        ) : (
          <>
            {broadcasts.map(broadcast => (
              <BroadcastCard 
                key={broadcast.id} 
                broadcast={broadcast} 
                onVoteSuccess={() => fetchFeed(0)} // Refresh to show results
                onDeleteSuccess={(deletedId) => setBroadcasts(prev => prev.filter(b => b.id !== deletedId))}
              />
            ))}
            
            {hasMore && (
              <button
                onClick={() => fetchFeed(page + 1)}
                disabled={loading}
                className="w-full py-4 border border-zinc-200 dark:border-zinc-800 text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 hover:border-zinc-900 dark:hover:text-white dark:hover:border-white transition-colors disabled:opacity-50"
              >
                {loading ? 'Decrypting...' : 'Load Prior Transmissions'}
              </button>
            )}
          </>
        )}
      </section>
    </div>
  );
};

export default TelemetryPage;
