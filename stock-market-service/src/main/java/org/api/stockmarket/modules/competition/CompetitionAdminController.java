package org.api.stockmarket.modules.competition;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.api.stockmarket.modules.competition.CompetitionDtos.*;

@RestController
@RequestMapping("/api/admin/competitions")
@RequiredArgsConstructor
public class CompetitionAdminController {
    private final CompetitionService service;
    private final CompetitionAuthService auth;
    private final StartingPriceService startingPriceService;

    private void admin(String key) { auth.requireAdmin(key); }

    @PostMapping
    public CompetitionView create(@RequestHeader("X-Admin-Key") String key, @RequestBody CreateCompetitionRequest request) {
        admin(key); return service.create(request);
    }

    @GetMapping
    public List<CompetitionView> list(@RequestHeader("X-Admin-Key") String key) { admin(key); return service.list(); }

    @GetMapping("/{id}")
    public CompetitionView get(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.get(id); }

    @PostMapping("/{id}/stocks/defaults")
    public List<StockView> defaults(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) {
        admin(key); return service.loadDefaultStocks(id);
    }

    @PostMapping("/{id}/stocks")
    public List<StockView> importJson(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id,
                                      @RequestBody List<StockInput> rows) {
        admin(key); return service.importStocks(id, rows);
    }

    @PostMapping(value = "/{id}/stocks/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<StockView> importCsv(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        admin(key);
        List<StockInput> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line; boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (first) { first = false; if (line.toLowerCase().contains("ticker")) continue; }
                String[] p = line.split(",", -1);
                if (p.length < 7) throw new CompetitionException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "CSV columns: ticker,companyName,sector,marketCap,volatility,liquidity,startingPrice");
                rows.add(new StockInput(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(),
                        Double.valueOf(p[4].trim()), Double.valueOf(p[5].trim()), new BigDecimal(p[6].trim())));
            }
        }
        return service.importStocks(id, rows);
    }

    @PostMapping("/{id}/stocks/fetch-prices")
    public StartingPriceService.FetchResult fetchPrices(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) {
        admin(key); return startingPriceService.refresh(id);
    }

    @GetMapping("/{id}/stocks")
    public List<StockView> stocks(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.stocks(id); }

    @PostMapping("/{id}/teams")
    public List<TeamCreated> team(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @RequestBody TeamRequest request) {
        admin(key); return service.createTeams(id, List.of(request));
    }

    @PostMapping("/{id}/teams/bulk")
    public List<TeamCreated> teams(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @RequestBody BulkTeamRequest request) {
        admin(key); return service.createTeams(id, request);
    }

    @GetMapping("/{id}/teams")
    public List<TeamView> teams(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.teams(id); }

    @PutMapping("/{id}/teams/{teamId}")
    public TeamView renameTeam(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @PathVariable Long teamId,
                               @RequestBody RenameTeamRequest request) { admin(key); return service.renameTeam(id, teamId, request); }

    @PostMapping("/{id}/teams/{teamId}/access-code")
    public TeamCreated regenerateCode(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @PathVariable Long teamId) {
        admin(key); return service.regenerateCode(id, teamId);
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    public void deleteTeam(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @PathVariable Long teamId) {
        admin(key); service.deleteTeam(id, teamId);
    }

    @PutMapping("/{id}/budget")
    public void budget(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @RequestBody BudgetRequest request) {
        admin(key); service.setBudget(id, request);
    }

    @PostMapping("/{id}/reference/generate")
    public java.util.Map<String, Long> reference(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) {
        admin(key); return java.util.Map.of("points", service.generateReferenceMarket(id));
    }

    @GetMapping("/{id}/reference/{ticker}")
    public List<PricePointView> reference(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id,
                                          @PathVariable String ticker) { admin(key); return service.adminReference(id, ticker); }

    @PostMapping("/{id}/news")
    public NewsView news(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @RequestBody NewsRequest request) {
        admin(key); return service.createNews(id, request);
    }

    @GetMapping("/{id}/news")
    public List<NewsView> news(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.adminNews(id); }

    @DeleteMapping("/{id}/news/{newsId}")
    public void deleteNews(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id, @PathVariable Long newsId) {
        admin(key); service.deleteNews(id, newsId);
    }

    @PostMapping("/{id}/lock") public CompetitionView lock(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.lock(id); }
    @PostMapping("/{id}/start") public CompetitionView start(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.start(id); }
    @PostMapping("/{id}/pause") public CompetitionView pause(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.pause(id); }
    @PostMapping("/{id}/resume") public CompetitionView resume(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.resume(id); }
    @PostMapping("/{id}/end") public CompetitionView end(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.end(id); }

    @PostMapping("/{id}/emergency/price")
    public StockView emergencyPrice(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id,
                                    @RequestBody EmergencyPriceRequest request) { admin(key); return service.emergencyPrice(id, request); }

    @GetMapping("/{id}/leaderboard")
    public List<LeaderboardRow> leaderboard(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.leaderboard(id); }

    @GetMapping("/{id}/audit")
    public List<AuditView> audit(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) { admin(key); return service.auditLog(id); }

    @GetMapping(value = "/{id}/exports/leaderboard.csv", produces = "text/csv")
    public ResponseEntity<String> leaderboardCsv(@RequestHeader("X-Admin-Key") String key, @PathVariable Long id) {
        admin(key);
        StringBuilder csv = new StringBuilder("rank,team,startingBudget,endingValue,profitLoss,returnPercent\n");
        service.leaderboard(id).forEach(r -> csv.append(r.rank()).append(',').append(csv(r.teamName())).append(',')
                .append(r.startingBudget()).append(',').append(r.totalPortfolioValue()).append(',')
                .append(r.profitLoss()).append(',').append(r.returnPercent()).append('\n'));
        return download("leaderboard.csv", csv.toString());
    }

    private ResponseEntity<String> download(String filename, String body) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"").body(body);
    }
    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
