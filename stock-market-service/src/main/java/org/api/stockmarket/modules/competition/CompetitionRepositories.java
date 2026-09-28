package org.api.stockmarket.modules.competition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface CompetitionRepository extends JpaRepository<Competition, Long> {
    List<Competition> findByStatus(CompetitionStatus status);
}

interface CompetitionStockRepository extends JpaRepository<CompetitionStock, Long> {
    List<CompetitionStock> findByCompetitionIdOrderByTicker(Long competitionId);
    Optional<CompetitionStock> findByCompetitionIdAndTickerIgnoreCase(Long competitionId, String ticker);
    long countByCompetitionId(Long competitionId);
    void deleteByCompetitionId(Long competitionId);
}

interface CompetitionTeamRepository extends JpaRepository<CompetitionTeam, Long> {
    List<CompetitionTeam> findByCompetitionIdOrderByName(Long competitionId);
    Optional<CompetitionTeam> findByCompetitionIdAndNameIgnoreCase(Long competitionId, String name);
    Optional<CompetitionTeam> findBySessionTokenHash(String hash);
    long countByCompetitionId(Long competitionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CompetitionTeam t where t.id = :id")
    Optional<CompetitionTeam> findByIdForUpdate(@Param("id") Long id);
}

interface TeamHoldingRepository extends JpaRepository<TeamHolding, Long> {
    List<TeamHolding> findByTeamId(Long teamId);
    Optional<TeamHolding> findByTeamIdAndStockId(Long teamId, Long stockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from TeamHolding h where h.team.id = :teamId and h.stock.id = :stockId")
    Optional<TeamHolding> findForUpdate(@Param("teamId") Long teamId, @Param("stockId") Long stockId);
}

interface CompetitionTradeRepository extends JpaRepository<CompetitionTrade, Long> {
    List<CompetitionTrade> findByTeamIdOrderByTimestampDesc(Long teamId);
    List<CompetitionTrade> findByCompetitionIdOrderByTimestampAsc(Long competitionId);
    List<CompetitionTrade> findByStockIdAndTimestampAfter(Long stockId, Instant after);
    List<CompetitionTrade> findByCompetitionIdAndStockIdAndCompetitionSecondGreaterThanEqual(
            Long competitionId, Long stockId, int minimumSecond);
}

interface ReferencePricePointRepository extends JpaRepository<ReferencePricePoint, Long> {
    Optional<ReferencePricePoint> findByStockIdAndSecondOffset(Long stockId, int secondOffset);
    List<ReferencePricePoint> findByCompetitionIdAndSecondOffset(Long competitionId, int secondOffset);
    List<ReferencePricePoint> findByCompetitionIdAndStockIdOrderBySecondOffset(Long competitionId, Long stockId);
    long countByCompetitionId(Long competitionId);

    @Modifying
    @Query("delete from ReferencePricePoint p where p.competition.id = :competitionId")
    void deleteForCompetition(@Param("competitionId") Long competitionId);
}

interface LivePricePointRepository extends JpaRepository<LivePricePoint, Long> {
    List<LivePricePoint> findByCompetitionIdAndStockIdAndSecondOffsetLessThanEqualOrderBySecondOffset(
            Long competitionId, Long stockId, int secondOffset);
}

interface CompetitionNewsRepository extends JpaRepository<CompetitionNews, Long> {
    List<CompetitionNews> findByCompetitionIdOrderByReleaseSecond(Long competitionId);
    List<CompetitionNews> findByCompetitionIdAndReleasedTrueOrderByReleaseSecond(Long competitionId);
    long countByCompetitionId(Long competitionId);
}

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByCompetitionIdOrderByCreatedAtDesc(Long competitionId);
}

interface FinalLeaderboardResultRepository extends JpaRepository<FinalLeaderboardResult, Long> {
    List<FinalLeaderboardResult> findByCompetitionIdOrderByRankPosition(Long competitionId);
    void deleteByCompetitionId(Long competitionId);
}
