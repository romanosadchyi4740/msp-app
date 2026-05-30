import React, { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const ConsensusModule = ({ poll, onVoteSuccess }) => {
  const [voting, setVoting] = useState(false);

  const totalVotes = poll.options.reduce((sum, opt) => sum + opt.voteCount, 0);

  const handleVote = async (optionId) => {
    if (poll.hasVoted || voting) return;
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
    <div className="mt-6 border border-border bg-background p-6">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-2 h-2 bg-accent animate-pulse" />
        <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-accent">Consensus Module Active</h4>
      </div>
      
      <p className="text-sm font-bold uppercase tracking-widest mb-6">{poll.question}</p>

      <div className="space-y-3">
        {poll.options.map((option) => {
          const percentage = totalVotes > 0 ? Math.round((option.voteCount / totalVotes) * 100) : 0;
          const isSelected = poll.hasVoted && poll.votedOptionId === option.id;

          if (poll.hasVoted) {
            return (
              <div key={option.id} className="relative">
                <div 
                  className={`absolute inset-0 opacity-20 transition-all duration-1000 ${isSelected ? 'bg-accent' : 'bg-white'}`}
                  style={{ width: `${percentage}%` }}
                />
                <div className={`relative flex items-center justify-between p-4 border transition-colors ${isSelected ? 'border-accent text-accent' : 'border-border text-muted'}`}>
                  <div className="flex items-center gap-3">
                    {isSelected && <CheckCircle2 size={14} />}
                    <span className="text-[10px] font-black uppercase tracking-widest">{option.optionText}</span>
                  </div>
                  <span className="text-[10px] font-black tracking-widest">{percentage}%</span>
                </div>
              </div>
            );
          }

          return (
            <button
              key={option.id}
              onClick={() => handleVote(option.id)}
              disabled={voting}
              className="w-full text-left p-4 border border-border hover:border-white hover:text-white text-muted transition-all flex items-center justify-between group disabled:opacity-50"
            >
              <span className="text-[10px] font-black uppercase tracking-widest group-hover:pl-2 transition-all">{option.optionText}</span>
              <span className="text-[10px] font-black uppercase tracking-widest opacity-0 group-hover:opacity-100 transition-opacity">Submit Data</span>
            </button>
          );
        })}
      </div>
      <div className="mt-4 flex justify-between items-center text-muted">
        <span className="text-[9px] font-black uppercase tracking-widest">{totalVotes} data points collected</span>
        {poll.hasVoted && <span className="text-[9px] font-black uppercase tracking-widest text-accent">Data Submitted</span>}
      </div>
    </div>
  );
};

export default ConsensusModule;
