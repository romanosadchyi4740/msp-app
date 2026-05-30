import React from 'react';
import { formatDistanceToNow } from 'date-fns';
import { RadioTower, Music } from 'lucide-react';
import ConsensusModule from './ConsensusModule';
import TrackCard from '../TrackCard';

const BroadcastCard = ({ broadcast, onVoteSuccess }) => {
  return (
    <article className="border-b border-border p-8 hover:bg-surface/30 transition-colors">
      <div className="flex items-start gap-4">
        {/* Author Avatar / Node ID */}
        <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center border border-border shrink-0">
          <span className="text-[10px] font-black text-white">{broadcast.author.displayName?.charAt(0) || 'U'}</span>
        </div>

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <span className="text-[11px] font-black uppercase tracking-widest text-white">{broadcast.author.displayName}</span>
              <span className="text-[9px] font-bold uppercase tracking-widest text-muted">NODE_{broadcast.author.id.substring(0, 6)}</span>
            </div>
            <div className="flex items-center gap-2 text-muted">
              <RadioTower size={12} />
              <span className="text-[9px] font-black uppercase tracking-widest">
                {formatDistanceToNow(new Date(broadcast.timestamp), { addSuffix: true })}
              </span>
            </div>
          </div>

          {/* Content */}
          <p className="text-sm leading-relaxed text-zinc-300 font-medium whitespace-pre-wrap mb-6">
            {broadcast.content}
          </p>

          {/* Attached Track */}
          {broadcast.attachedTrack && (
            <div className="mb-6 border border-border p-4 bg-background">
              <div className="flex items-center gap-2 mb-3">
                <Music size={12} className="text-muted" />
                <span className="text-[9px] font-black uppercase tracking-widest text-muted">Attached Transmission</span>
              </div>
              <TrackCard track={broadcast.attachedTrack} />
            </div>
          )}

          {/* Consensus Module */}
          {broadcast.poll && (
            <ConsensusModule poll={broadcast.poll} onVoteSuccess={onVoteSuccess} />
          )}
        </div>
      </div>
    </article>
  );
};

export default BroadcastCard;
