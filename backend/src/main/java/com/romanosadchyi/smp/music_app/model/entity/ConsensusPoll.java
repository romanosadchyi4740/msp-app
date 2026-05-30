package com.romanosadchyi.smp.music_app.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "consensus_polls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsensusPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false, unique = true)
    private StatusBroadcast broadcast;

    @Column(nullable = false)
    private String question;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<PollOption> options = new ArrayList<>();
}
