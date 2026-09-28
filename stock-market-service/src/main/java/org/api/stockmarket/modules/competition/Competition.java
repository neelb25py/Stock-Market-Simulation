package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "competitions")
@Getter @Setter @NoArgsConstructor
public class Competition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CompetitionStatus status = CompetitionStatus.DRAFT;
    @Column(nullable = false) private int durationSeconds = 3600;
    @Column(nullable = false) private long tickIntervalMs = 1000;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal defaultStartingBudget = new BigDecimal("1000000.00");
    @Column(nullable = false) private long randomSeed;
    @Column(nullable = false) private boolean allowShortSelling = false;
    @Column(nullable = false) private double participantImpactMultiplier = 0.0005;
    @Column(nullable = false) private int orderFlowLookbackSeconds = 30;
    @Column(nullable = false) private double maxOrderFlowImpactPerTick = 0.0005;
    @Column(nullable = false) private int elapsedSeconds = 0;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant readyAt;
    private Instant lockedAt;
    private Instant startedAt;
    private Instant pausedAt;
    private Instant endedAt;
    private String lockHash;

    public Competition(String name, int durationSeconds, long tickIntervalMs, BigDecimal budget, long seed) {
        this.name = name;
        this.durationSeconds = durationSeconds;
        this.tickIntervalMs = tickIntervalMs;
        this.defaultStartingBudget = budget;
        this.randomSeed = seed;
    }
}
