package org.api.stockmarket.modules.competition;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
@Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competition_id") private Competition competition;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String action;
    @Column(nullable = false, length = 4000) private String details;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    public AuditEvent(Competition competition, String actor, String action, String details) {
        this.competition = competition;
        this.actor = actor;
        this.action = action;
        this.details = details;
    }
}
