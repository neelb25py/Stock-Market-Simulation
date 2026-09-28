package org.api.stockmarket.modules.competition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CompetitionDtos {
    private CompetitionDtos() {}

    public record CreateCompetitionRequest(String name, Integer durationSeconds, Long tickIntervalMs,
                                           BigDecimal defaultStartingBudget, Long randomSeed) {}
    public record CompetitionView(Long id, String name, CompetitionStatus status, int durationSeconds,
                                  long tickIntervalMs, BigDecimal defaultStartingBudget, long randomSeed,
                                  int elapsedSeconds, int remainingSeconds, boolean allowShortSelling,
                                  double participantImpactMultiplier, int orderFlowLookbackSeconds,
                                  double maxOrderFlowImpactPerTick, String lockHash, Instant createdAt,
                                  Instant startedAt, Instant endedAt, long stockCount, long teamCount) {}
    public record StockInput(String ticker, String companyName, String sector, String marketCap,
                             Double volatility, Double liquidity, BigDecimal startingPrice) {}
    public record StockView(Long id, String ticker, String companyName, String sector, String marketCap,
                            double volatility, double liquidity, BigDecimal startingPrice,
                            BigDecimal livePrice, boolean enabled, double changePercent) {}
    public record TeamRequest(String name, BigDecimal startingBudget) {}
    public record RenameTeamRequest(String name) {}
    public record BulkTeamRequest(List<String> names, BigDecimal startingBudget) {}
    public record TeamCreated(Long id, String name, String accessCode, BigDecimal startingBudget) {}
    public record TeamView(Long id, String name, BigDecimal startingBudget, BigDecimal cashBalance, boolean active) {}
    public record BudgetRequest(BigDecimal amount, Long teamId) {}
    public record LoginRequest(Long competitionId, String teamName, String accessCode) {}
    public record LoginResponse(String token, Long competitionId, Long teamId, String teamName) {}
    public record OrderRequest(String ticker, long quantity) {}
    public record TradeView(Long id, String ticker, TradeSide side, long quantity, BigDecimal executionPrice,
                            BigDecimal grossValue, Instant timestamp, int competitionSecond) {}
    public record HoldingView(String ticker, long quantity, BigDecimal averagePurchasePrice,
                              BigDecimal livePrice, BigDecimal marketValue, BigDecimal realizedPnL,
                              BigDecimal unrealizedPnL) {}
    public record PortfolioView(Long teamId, String teamName, BigDecimal startingBudget, BigDecimal cashBalance,
                                BigDecimal holdingsMarketValue, BigDecimal totalPortfolioValue,
                                BigDecimal realizedPnL, BigDecimal unrealizedPnL, BigDecimal totalPnL,
                                double returnPercent, List<HoldingView> holdings) {}
    public record ImpactRequest(String ticker, double impactPercent) {}
    public record NewsRequest(String headline, String description, int releaseSecond,
                              Integer impactDurationSeconds, NewsImpactStyle impactStyle,
                              List<ImpactRequest> impacts) {}
    public record NewsView(Long id, String headline, String description, int releaseSecond,
                           int impactDurationSeconds, NewsImpactStyle impactStyle, boolean released,
                           Instant releasedAt, List<ImpactRequest> impacts) {}
    public record PublicNewsView(Long id, String headline, String description, int releaseSecond, Instant releasedAt) {}
    public record LeaderboardRow(int rank, Long teamId, String teamName, BigDecimal startingBudget,
                                 BigDecimal cash, BigDecimal totalPortfolioValue, BigDecimal profitLoss,
                                 double returnPercent) {}
    public record PricePointView(int secondOffset, BigDecimal price, double referenceReturn,
                                 double newsReturn, double participantReturn, double combinedReturn) {}
    public record MarketView(CompetitionView competition, List<StockView> stocks,
                             List<PublicNewsView> news, List<LeaderboardRow> leaderboard) {}
    public record EmergencyPriceRequest(String ticker, BigDecimal price, String reason) {}
    public record AuditView(Long id, String actor, String action, String details, Instant createdAt) {}
}
