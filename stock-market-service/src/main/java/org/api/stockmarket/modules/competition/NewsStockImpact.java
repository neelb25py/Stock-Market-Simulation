package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "news_stock_impacts")
@Getter @Setter @NoArgsConstructor
public class NewsStockImpact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "news_id") private CompetitionNews news;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id") private CompetitionStock stock;
    @Column(nullable = false) private double impactPercent;

    public NewsStockImpact(CompetitionNews news, CompetitionStock stock, double impactPercent) {
        this.news = news;
        this.stock = stock;
        this.impactPercent = impactPercent;
    }
}
