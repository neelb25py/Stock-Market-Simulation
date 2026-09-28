package org.api.stockmarket.modules.competition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StartingPriceService {
    public record QuoteError(String ticker, String message) {}
    public record FetchResult(int updated, int preserved, List<QuoteError> errors, List<CompetitionDtos.StockView> stocks) {}

    private final CompetitionRepository competitionRepository;
    private final CompetitionStockRepository stockRepository;
    private final AuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    @Transactional
    public FetchResult refresh(Long competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionException(HttpStatus.NOT_FOUND, "Competition not found"));
        if (competition.getStatus() != CompetitionStatus.DRAFT && competition.getStatus() != CompetitionStatus.READY) {
            throw new CompetitionException(HttpStatus.CONFLICT, "Starting prices cannot change after lock");
        }
        List<CompetitionStock> stocks = stockRepository.findByCompetitionIdOrderByTicker(competitionId);
        if (stocks.isEmpty()) throw new CompetitionException(HttpStatus.BAD_REQUEST, "Load stocks before fetching prices");
        List<QuoteError> errors = new ArrayList<>();
        int updated = 0;
        for (CompetitionStock stock : stocks) {
            try {
                String symbol = URLEncoder.encode(stock.getTicker(), StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?range=5d&interval=1d"))
                        .timeout(Duration.ofSeconds(6)).header("User-Agent", "MarketCompetition/1.0").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("provider status " + response.statusCode());
                JsonNode result = objectMapper.readTree(response.body()).path("chart").path("result").path(0);
                double price = result.path("meta").path("regularMarketPrice").asDouble(Double.NaN);
                if (!Double.isFinite(price) || price <= 0) throw new IllegalStateException("no current price returned");
                BigDecimal value = BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP);
                stock.setStartingPrice(value); stock.setLivePrice(value); updated++;
            } catch (Exception e) {
                errors.add(new QuoteError(stock.getTicker(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        stockRepository.saveAll(stocks);
        auditRepository.save(new AuditEvent(competition, "ADMIN", "STARTING_PRICES_FETCHED",
                "updated=" + updated + ", preserved=" + errors.size()));
        List<CompetitionDtos.StockView> views = stocks.stream().map(s -> new CompetitionDtos.StockView(s.getId(), s.getTicker(),
                s.getCompanyName(), s.getSector(), s.getMarketCap(), s.getVolatility(), s.getLiquidityScale(),
                s.getStartingPrice(), s.getLivePrice(), s.isEnabled(), 0)).toList();
        return new FetchResult(updated, errors.size(), errors, views);
    }
}
