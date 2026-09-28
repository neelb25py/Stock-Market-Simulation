package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "competition_stocks", uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "ticker"}))
@Getter @Setter @NoArgsConstructor
public class CompetitionStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @Column(nullable = false) private String ticker;
    @Column(nullable = false) private String companyName;
    @Column(nullable = false) private String sector;
    @Column(nullable = false) private String marketCap;
    @Column(nullable = false) private double volatility;
    @Column(nullable = false) private double liquidityScale;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal startingPrice;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal livePrice;
    @Column(nullable = false) private boolean enabled = true;

    public CompetitionStock(Competition competition, String ticker, String companyName, String sector,
                            String marketCap, double volatility, double liquidityScale, BigDecimal price) {
        this.competition = competition;
        this.ticker = ticker;
        this.companyName = companyName;
        this.sector = sector;
        this.marketCap = marketCap;
        this.volatility = volatility;
        this.liquidityScale = liquidityScale;
        this.startingPrice = price;
        this.livePrice = price;
    }
}
