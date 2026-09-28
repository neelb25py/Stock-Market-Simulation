package org.api.stockmarket.modules.competition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.api.stockmarket.modules.competition.CompetitionDtos.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompetitionPlatformIntegrationTest {
    @Autowired CompetitionService service;
    @Autowired CompetitionAuthService auth;
    @Autowired CompetitionMarketEngine engine;
    @Autowired ReferencePricePointRepository references;

    private StockInput stock(String ticker) {
        return new StockInput(ticker, ticker + " Ltd", "Technology", "Large", 0.0003, 5_000_000d, new BigDecimal("100"));
    }

    @Test
    void completeCompetitionLifecycleAndTrading() {
        CompetitionView created = service.create(new CreateCompetitionRequest("Test", 10, 1000L, new BigDecimal("10000"), 42L));
        service.importStocks(created.id(), List.of(stock("TEST")));
        TeamCreated team = service.createTeams(created.id(), List.of(new TeamRequest("Alpha", null))).get(0);
        assertEquals(10, service.generateReferenceMarket(created.id()));
        service.createNews(created.id(), new NewsRequest("Launch", "A product launched", 0, 2,
                NewsImpactStyle.RAMP, List.of(new ImpactRequest("TEST", 6))));
        assertEquals(CompetitionStatus.LOCKED, service.lock(created.id()).status());
        LoginResponse login = auth.login(new LoginRequest(created.id(), "Alpha", team.accessCode()));
        assertEquals(CompetitionStatus.RUNNING, service.start(created.id()).status());
        CompetitionTeam authenticated = auth.requireTeam("Bearer " + login.token(), created.id());
        service.trade(created.id(), authenticated, new OrderRequest("TEST", 5), TradeSide.BUY);
        PortfolioView afterBuy = service.portfolio(authenticated);
        assertEquals(new BigDecimal("9500.00"), afterBuy.cashBalance());
        assertEquals(5, afterBuy.holdings().get(0).quantity());
        engine.tick(created.id());
        assertFalse(service.publicNews(created.id()).isEmpty());
        assertNotEquals(new BigDecimal("100.0000"), service.stocks(created.id()).get(0).livePrice());
        service.pause(created.id());
        assertThrows(CompetitionException.class,
                () -> service.trade(created.id(), authenticated, new OrderRequest("TEST", 1), TradeSide.SELL));
        service.resume(created.id());
        service.trade(created.id(), authenticated, new OrderRequest("TEST", 2), TradeSide.SELL);
        assertEquals(3, service.portfolio(authenticated).holdings().get(0).quantity());
        service.end(created.id());
        assertEquals(CompetitionStatus.FINISHED, service.get(created.id()).status());
        assertEquals(1, service.leaderboard(created.id()).size());
        assertThrows(CompetitionException.class,
                () -> service.trade(created.id(), authenticated, new OrderRequest("TEST", 1), TradeSide.BUY));
    }

    @Test
    void referenceMarketIsDeterministicAndLiveHistoryHidesFuture() {
        CompetitionView a = service.create(new CreateCompetitionRequest("A", 10, 1000L, BigDecimal.TEN, 7L));
        CompetitionView b = service.create(new CreateCompetitionRequest("B", 10, 1000L, BigDecimal.TEN, 7L));
        service.importStocks(a.id(), List.of(stock("SAME"))); service.importStocks(b.id(), List.of(stock("SAME")));
        service.generateReferenceMarket(a.id()); service.generateReferenceMarket(b.id());
        List<PricePointView> pathA = service.adminReference(a.id(), "SAME");
        List<PricePointView> pathB = service.adminReference(b.id(), "SAME");
        assertEquals(pathA.stream().map(PricePointView::price).toList(), pathB.stream().map(PricePointView::price).toList());
        assertEquals(1, service.liveHistory(a.id(), "SAME").size(), "team history exposes only starting/live observations");
        assertEquals(20, references.countByCompetitionId(a.id()) + references.countByCompetitionId(b.id()));
    }

    @Test
    void invalidStateAndOrderInputsAreRejected() {
        CompetitionView c = service.create(new CreateCompetitionRequest("Invalid", 10, 1000L, new BigDecimal("100"), 1L));
        assertThrows(CompetitionException.class, () -> service.start(c.id()));
        assertThrows(CompetitionException.class, () -> service.lock(c.id()));
    }
}
