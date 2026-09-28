package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "competition_news")
@Getter @Setter @NoArgsConstructor
public class CompetitionNews {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @Column(nullable = false) private String headline;
    @Column(nullable = false, length = 4000) private String description;
    @Column(nullable = false) private int releaseSecond;
    @Column(nullable = false) private int impactDurationSeconds = 60;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NewsImpactStyle impactStyle = NewsImpactStyle.RAMP;
    @Column(nullable = false) private boolean released = false;
    private Instant releasedAt;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @OneToMany(mappedBy = "news", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<NewsStockImpact> impacts = new ArrayList<>();
}
