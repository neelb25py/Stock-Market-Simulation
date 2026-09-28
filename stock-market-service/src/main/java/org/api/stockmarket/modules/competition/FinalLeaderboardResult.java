package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "final_leaderboard_results")
@Getter @Setter @NoArgsConstructor
public class FinalLeaderboardResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id") private CompetitionTeam team;
    @Column(nullable = false) private int rankPosition;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal startingBudget;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal endingValue;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal profitLoss;
    @Column(nullable = false) private double returnPercent;
    @Column(nullable = false) private Instant finalizedAt = Instant.now();
}
