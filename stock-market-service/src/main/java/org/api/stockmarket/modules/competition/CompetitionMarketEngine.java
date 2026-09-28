package org.api.stockmarket.modules.competition;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CompetitionMarketEngine {
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("0.01");
    private final CompetitionRepository competitionRepository;
    private final CompetitionStockRepository stockRepository;
    private final CompetitionTradeRepository tradeRepository;
    private final ReferencePricePointRepository referenceRepository;
    private final LivePricePointRepository livePriceRepository;
    private final CompetitionNewsRepository newsRepository;
    private final AuditEventRepository auditRepository;
    private final CompetitionService competitionService;
    private final CompetitionEventBroadcaster broadcaster;
    private final Map<Long, Long> lastTickMillis = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${competition.scheduler-resolution-ms:250}")
    @Transactional
    public void scheduleRunningCompetitions() {
        long now = System.currentTimeMillis();
        for (Competition competition : competitionRepository.findByStatus(CompetitionStatus.RUNNING)) {
            long previous = lastTickMillis.getOrDefault(competition.getId(), now - competition.getTickIntervalMs());
            if (now - previous >= competition.getTickIntervalMs()) {
                tick(competition.getId());
                lastTickMillis.put(competition.getId(), now);
            }
        }
    }

    @Transactional
    public synchronized void tick(Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId).orElse(null);
        if (competition == null || competition.getStatus() != CompetitionStatus.RUNNING) return;
        int second = competition.getElapsedSeconds();
        if (second >= competition.getDurationSeconds()) {
            competitionService.finish(competition, "DURATION_REACHED");
            broadcaster.broadcast(competitionId, "finished", competitionService.leaderboard(competitionId));
            return;
        }

        List<CompetitionNews> news = newsRepository.findByCompetitionIdOrderByReleaseSecond(competitionId);
        for (CompetitionNews event : news) {
            if (!event.isReleased() && event.getReleaseSecond() == second) {
                event.setReleased(true);
                event.setReleasedAt(Instant.now());
                newsRepository.save(event);
                auditRepository.save(new AuditEvent(competition, "SYSTEM", "NEWS_RELEASED", event.getHeadline()));
                broadcaster.broadcast(competitionId, "news", Map.of(
                        "id", event.getId(), "headline", event.getHeadline(), "description", event.getDescription(),
                        "releaseSecond", event.getReleaseSecond()));
            }
        }

        Map<Long, Double> referenceReturns = new HashMap<>();
        for (ReferencePricePoint point : referenceRepository.findByCompetitionIdAndSecondOffset(competitionId, second)) {
            referenceReturns.put(point.getStock().getId(), point.getReferenceReturn());
        }

        List<LivePricePoint> points = new ArrayList<>();
        List<Map<String, Object>> priceEvents = new ArrayList<>();
        for (CompetitionStock stock : stockRepository.findByCompetitionIdOrderByTicker(competitionId)) {
            if (!stock.isEnabled()) continue;
            double referenceReturn = referenceReturns.getOrDefault(stock.getId(), 0.0);
            double newsReturn = newsReturn(news, stock.getId(), second);
            double participantReturn = participantReturn(competition, stock, second);
            double combinedReturn = referenceReturn + newsReturn + participantReturn;
            double raw = stock.getLivePrice().doubleValue() * (1.0 + combinedReturn);
            BigDecimal price = safePrice(raw);
            stock.setLivePrice(price);

            LivePricePoint point = new LivePricePoint();
            point.setCompetition(competition); point.setStock(stock); point.setSecondOffset(second + 1); point.setPrice(price);
            point.setReferenceReturn(referenceReturn); point.setNewsReturn(newsReturn);
            point.setParticipantReturn(participantReturn); point.setCombinedReturn(combinedReturn);
            points.add(point);
            priceEvents.add(Map.of("ticker", stock.getTicker(), "price", price, "referenceReturn", referenceReturn,
                    "newsReturn", newsReturn, "participantReturn", participantReturn, "combinedReturn", combinedReturn));
        }
        stockRepository.saveAll(stockRepository.findByCompetitionIdOrderByTicker(competitionId));
        livePriceRepository.saveAll(points);
        competition.setElapsedSeconds(second + 1);
        competitionRepository.save(competition);

        broadcaster.broadcast(competitionId, "tick", Map.of("elapsedSeconds", competition.getElapsedSeconds(), "prices", priceEvents));
        broadcaster.broadcast(competitionId, "leaderboard", competitionService.leaderboard(competitionId));
        if (competition.getElapsedSeconds() >= competition.getDurationSeconds()) {
            competitionService.finish(competition, "DURATION_REACHED");
            broadcaster.broadcast(competitionId, "finished", competitionService.leaderboard(competitionId));
        }
    }

    public double newsReturn(List<CompetitionNews> events, Long stockId, int second) {
        double total = 0;
        for (CompetitionNews event : events) {
            if (!event.isReleased()) continue;
            int age = second - event.getReleaseSecond();
            for (NewsStockImpact impact : event.getImpacts()) {
                if (!impact.getStock().getId().equals(stockId)) continue;
                double totalReturn = impact.getImpactPercent() / 100.0;
                if (event.getImpactStyle() == NewsImpactStyle.INSTANT && age == 0) total += totalReturn;
                if (event.getImpactStyle() == NewsImpactStyle.RAMP && age >= 0 && age < event.getImpactDurationSeconds()) {
                    total += Math.expm1(Math.log1p(totalReturn) / event.getImpactDurationSeconds());
                }
            }
        }
        return total;
    }

    public double participantReturn(Competition competition, CompetitionStock stock, int second) {
        int from = Math.max(0, second - competition.getOrderFlowLookbackSeconds() + 1);
        double net = 0;
        for (CompetitionTrade trade : tradeRepository
                .findByCompetitionIdAndStockIdAndCompetitionSecondGreaterThanEqual(competition.getId(), stock.getId(), from)) {
            int age = Math.max(0, second - trade.getCompetitionSecond());
            double weight = Math.exp(-3.0 * age / Math.max(1.0, competition.getOrderFlowLookbackSeconds()));
            double signed = trade.getGrossValue().doubleValue() * (trade.getSide() == TradeSide.BUY ? 1 : -1);
            net += signed * weight;
        }
        double pressure = Math.tanh(net / stock.getLiquidityScale());
        double impact = pressure * competition.getParticipantImpactMultiplier();
        return Math.max(-competition.getMaxOrderFlowImpactPerTick(),
                Math.min(competition.getMaxOrderFlowImpactPerTick(), impact));
    }

    private BigDecimal safePrice(double raw) {
        if (!Double.isFinite(raw) || raw <= 0.01) return PRICE_FLOOR;
        return BigDecimal.valueOf(raw).setScale(6, RoundingMode.HALF_UP);
    }
}
