package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "reference_price_points", uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "second_offset"}),
       indexes = @Index(name = "idx_reference_lookup", columnList = "competition_id,second_offset"))
@Getter @Setter @NoArgsConstructor
public class ReferencePricePoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id") private CompetitionStock stock;
    @Column(name = "second_offset", nullable = false) private int secondOffset;
    @Column(nullable = false, precision = 19, scale = 6) private BigDecimal referencePrice;
    @Column(nullable = false) private double referenceReturn;

    public ReferencePricePoint(Competition competition, CompetitionStock stock, int offset, BigDecimal price, double value) {
        this.competition = competition;
        this.stock = stock;
        this.secondOffset = offset;
        this.referencePrice = price;
        this.referenceReturn = value;
    }
}
