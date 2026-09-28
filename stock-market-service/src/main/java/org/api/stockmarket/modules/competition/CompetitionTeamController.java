package org.api.stockmarket.modules.competition;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.api.stockmarket.modules.competition.CompetitionDtos.*;

@RestController
@RequiredArgsConstructor
public class CompetitionTeamController {
    private final CompetitionService service;
    private final CompetitionAuthService auth;
    private final CompetitionEventBroadcaster broadcaster;

    @PostMapping("/api/team/login")
    public LoginResponse login(@RequestBody LoginRequest request) { return auth.login(request); }

    @GetMapping("/api/competitions/{id}/market")
    public MarketView market(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        auth.requireTeam(token, id); return service.market(id);
    }

    @GetMapping("/api/competitions/{id}/stocks")
    public List<StockView> stocks(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        auth.requireTeam(token, id); return service.stocks(id);
    }

    @GetMapping("/api/competitions/{id}/stocks/{ticker}/history")
    public List<PricePointView> history(@RequestHeader("Authorization") String token, @PathVariable Long id,
                                        @PathVariable String ticker) {
        auth.requireTeam(token, id); return service.liveHistory(id, ticker);
    }

    @PostMapping("/api/competitions/{id}/orders/buy")
    public TradeView buy(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestBody OrderRequest request) {
        return service.trade(id, auth.requireTeam(token, id), request, TradeSide.BUY);
    }

    @PostMapping("/api/competitions/{id}/orders/sell")
    public TradeView sell(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestBody OrderRequest request) {
        return service.trade(id, auth.requireTeam(token, id), request, TradeSide.SELL);
    }

    @GetMapping("/api/competitions/{id}/portfolio")
    public PortfolioView portfolio(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        return service.portfolio(auth.requireTeam(token, id));
    }

    @GetMapping("/api/competitions/{id}/trades")
    public List<TradeView> trades(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        return service.trades(auth.requireTeam(token, id));
    }

    @GetMapping("/api/competitions/{id}/news")
    public List<PublicNewsView> news(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        auth.requireTeam(token, id); return service.publicNews(id);
    }

    @GetMapping("/api/competitions/{id}/leaderboard")
    public List<LeaderboardRow> leaderboard(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        auth.requireTeam(token, id); return service.leaderboard(id);
    }

    @GetMapping(value = "/api/competitions/{id}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable Long id, @RequestParam String token) {
        auth.requireTeam("Bearer " + token, id); return broadcaster.subscribe(id);
    }
}
