package com.romanosadchyi.smp.music_app.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "poll_votes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"voter_id", "option_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;
}
