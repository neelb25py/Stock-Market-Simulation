package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "team_holdings", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "stock_id"}))
@Getter @Setter @NoArgsConstructor
public class TeamHolding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id") private CompetitionTeam team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id") private CompetitionStock stock;
    @Column(nullable = false) private long quantity;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal averagePurchasePrice = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal realizedPnL = BigDecimal.ZERO;
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public TeamHolding(CompetitionTeam team, CompetitionStock stock) {
        this.team = team;
        this.stock = stock;
    }
}
