import React, { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const ConsensusModule = ({ poll, onVoteSuccess }) => {
  const [voting, setVoting] = useState(false);

  const totalVotes = poll.options.reduce((sum, opt) => sum + opt.voteCount, 0);

  const handleVote = async (optionId) => {
    if (voting) return;
    setVoting(true);
    try {
      await axiosClient.post(`/api/telemetry/polls/options/${optionId}/vote`);
      if (onVoteSuccess) {
        onVoteSuccess();
      }
    } catch (error) {
      console.error('Failed to submit data:', error);
    } finally {
      setVoting(false);
    }
  };

  return (
    <div className="mt-6 border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 p-6">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-2 h-2 bg-zinc-900 dark:bg-white animate-pulse" />
        <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-900 dark:text-white">Consensus Module Active</h4>
      </div>
      
      <p className="text-sm font-bold uppercase tracking-widest mb-6 text-zinc-900 dark:text-white">{poll.question}</p>

      <div className="space-y-3">
        {poll.options.map((option) => {
          const percentage = totalVotes > 0 ? Math.round((option.voteCount / totalVotes) * 100) : 0;
          const isSelected = poll.hasVoted && poll.votedOptionId === option.id;

          if (poll.hasVoted) {
            return (
              <button 
                key={option.id} 
                onClick={() => handleVote(option.id)}
                disabled={voting}
                className="relative w-full text-left cursor-pointer group disabled:opacity-50"
              >
                <div 
                  className={`absolute inset-0 opacity-20 transition-all duration-1000 ${isSelected ? 'bg-zinc-900 dark:bg-white' : 'bg-zinc-300 dark:bg-zinc-600 group-hover:opacity-30'}`}
                  style={{ width: `${percentage}%` }}
                />
                <div className={`relative flex items-center justify-between p-4 border transition-colors ${isSelected ? 'border-zinc-900 text-zinc-900 dark:border-white dark:text-white' : 'border-zinc-200 text-zinc-500 dark:border-zinc-800 dark:text-zinc-400 group-hover:border-zinc-900 group-hover:text-zinc-900 dark:group-hover:border-white dark:group-hover:text-white'}`}>
                  <div className="flex items-center gap-3">
                    {isSelected && <CheckCircle2 size={14} />}
                    <span className="text-[10px] font-black uppercase tracking-widest">{option.optionText}</span>
                  </div>
                  <span className="text-[10px] font-black tracking-widest">{percentage}%</span>
                </div>
              </button>
            );
          }

          return (
            <button
              key={option.id}
              onClick={() => handleVote(option.id)}
              disabled={voting}
              className="w-full text-left p-4 border border-zinc-200 dark:border-zinc-800 hover:border-zinc-900 hover:text-zinc-900 dark:hover:border-white dark:hover:text-white text-zinc-500 dark:text-zinc-400 transition-all flex items-center justify-between group disabled:opacity-50"
            >
              <span className="text-[10px] font-black uppercase tracking-widest group-hover:pl-2 transition-all">{option.optionText}</span>
              <span className="text-[10px] font-black uppercase tracking-widest opacity-0 group-hover:opacity-100 transition-opacity">Submit Data</span>
            </button>
          );
        })}
      </div>
      <div className="mt-4 flex justify-between items-center text-zinc-500 dark:text-zinc-400">
        <span className="text-[9px] font-black uppercase tracking-widest">{totalVotes} data points collected</span>
        {poll.hasVoted && <span className="text-[9px] font-black uppercase tracking-widest text-zinc-900 dark:text-white">Data Submitted</span>}
      </div>
    </div>
  );
};

export default ConsensusModule;
