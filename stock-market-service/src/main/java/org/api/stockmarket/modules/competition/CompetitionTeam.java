package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "competition_teams", uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class CompetitionTeam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String accessCodeHash;
    private String sessionTokenHash;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal startingBudget;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal cashBalance;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    public CompetitionTeam(Competition competition, String name, String accessCodeHash, BigDecimal budget) {
        this.competition = competition;
        this.name = name;
        this.accessCodeHash = accessCodeHash;
        this.startingBudget = budget;
        this.cashBalance = budget;
    }
}
