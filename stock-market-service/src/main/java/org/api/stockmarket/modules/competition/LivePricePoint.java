package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "live_price_points", indexes = @Index(name = "idx_live_history", columnList = "stock_id,second_offset"))
@Getter @Setter @NoArgsConstructor
public class LivePricePoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id") private CompetitionStock stock;
    @Column(name = "second_offset", nullable = false) private int secondOffset;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal price;
    @Column(nullable = false) private double referenceReturn;
    @Column(nullable = false) private double newsReturn;
    @Column(nullable = false) private double participantReturn;
    @Column(nullable = false) private double combinedReturn;
    @Column(nullable = false) private Instant recordedAt = Instant.now();
}
