package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "competition_trades", indexes = @Index(name = "idx_trade_stock_time", columnList = "stock_id,executed_at"))
@Getter @Setter @NoArgsConstructor
public class CompetitionTrade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id") private CompetitionTeam team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id") private CompetitionStock stock;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TradeSide side;
    @Column(nullable = false) private long quantity;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal executionPrice;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal grossValue;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal slippage = BigDecimal.ZERO;
    @Column(name = "executed_at", nullable = false) private Instant timestamp = Instant.now();
    @Column(nullable = false) private int competitionSecond;
}
