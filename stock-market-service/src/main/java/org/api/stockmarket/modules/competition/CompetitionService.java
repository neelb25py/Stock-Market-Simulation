package org.api.stockmarket.modules.competition;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.api.stockmarket.modules.stocks.entity.Stock;
import org.api.stockmarket.modules.stocks.service.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.api.stockmarket.modules.competition.CompetitionDtos.*;

@Service
@RequiredArgsConstructor
public class CompetitionService {
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private final CompetitionRepository competitionRepository;
    private final CompetitionStockRepository stockRepository;
    private final CompetitionTeamRepository teamRepository;
    private final TeamHoldingRepository holdingRepository;
    private final CompetitionTradeRepository tradeRepository;
    private final ReferencePricePointRepository referenceRepository;
    private final LivePricePointRepository livePriceRepository;
    private final CompetitionNewsRepository newsRepository;
    private final AuditEventRepository auditRepository;
    private final FinalLeaderboardResultRepository finalResultRepository;
    private final StockService legacyStockService;
    private final CompetitionAuthService authService;
    private final EntityManager entityManager;

    @Transactional
    public CompetitionView create(CreateCompetitionRequest request) {
        String name = request.name() == null || request.name().isBlank() ? "Trading Challenge" : request.name().trim();
        int duration = request.durationSeconds() == null ? 3600 : request.durationSeconds();
        long tick = request.tickIntervalMs() == null ? 1000 : request.tickIntervalMs();
        BigDecimal budget = request.defaultStartingBudget() == null ? new BigDecimal("1000000") : request.defaultStartingBudget();
        if (duration < 10 || duration > 86400 || tick < 100 || budget.signum() <= 0) {
            throw bad("Invalid duration, tick interval, or starting budget");
        }
        long seed = request.randomSeed() == null ? new SecureRandom().nextLong() : request.randomSeed();
        Competition competition = competitionRepository.save(new Competition(name, duration, tick, money(budget), seed));
        audit(competition, "ADMIN", "COMPETITION_CREATED", name);
        return view(competition);
    }

    @Transactional(readOnly = true)
    public List<CompetitionView> list() {
        return competitionRepository.findAll().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public CompetitionView get(Long id) { return view(requireCompetition(id)); }

    @Transactional
    public List<StockView> loadDefaultStocks(Long competitionId) {
        Competition competition = requireEditable(competitionId);
        prepareStockReplacement(competition);
        stockRepository.deleteByCompetitionId(competitionId);
        List<Stock> source = legacyStockService.getAllStocks();
        if (source.isEmpty()) throw bad("The default stock universe is empty");
        List<CompetitionStock> created = new ArrayList<>();
        for (Stock stock : source) {
            double vol = 0.00015 + stock.getPricingModel().getAttributes().getVolatility().getMagnitude() * 0.00025;
            String cap = stock.getCompany().getMarketCap().getName();
            created.add(new CompetitionStock(competition, stock.getTicker(), stock.getCompany().getCompanyName(),
                    stock.getCompany().getSector(), cap, vol, liquidityFor(cap),
                    stock.getPricingModel().getPrice().setScale(4, RoundingMode.HALF_UP)));
        }
        stockRepository.saveAll(created);
        audit(competition, "ADMIN", "DEFAULT_STOCKS_LOADED", "stocks=" + created.size());
        return created.stream().map(this::stockView).toList();
    }

    @Transactional
    public List<StockView> importStocks(Long competitionId, List<StockInput> inputs) {
        Competition competition = requireEditable(competitionId);
        if (inputs == null || inputs.isEmpty()) throw bad("CSV contains no stock rows");
        Set<String> tickers = new HashSet<>();
        List<CompetitionStock> rows = new ArrayList<>();
        for (StockInput input : inputs) {
            String ticker = required(input.ticker(), "ticker").toUpperCase(Locale.ROOT);
            if (!tickers.add(ticker)) throw bad("Duplicate ticker: " + ticker);
            BigDecimal price = input.startingPrice();
            if (price == null || price.signum() <= 0) throw bad("Invalid starting price for " + ticker);
            double volatility = input.volatility() == null ? 0.0003 : input.volatility();
            double liquidity = input.liquidity() == null ? liquidityFor(input.marketCap()) : input.liquidity();
            if (!Double.isFinite(volatility) || volatility < 0 || !Double.isFinite(liquidity) || liquidity <= 0) {
                throw bad("Invalid volatility or liquidity for " + ticker);
            }
            rows.add(new CompetitionStock(competition, ticker, required(input.companyName(), "companyName"),
                    required(input.sector(), "sector"), required(input.marketCap(), "marketCap"),
                    volatility, liquidity, price.setScale(4, RoundingMode.HALF_UP)));
        }
        prepareStockReplacement(competition);
        stockRepository.deleteByCompetitionId(competitionId);
        stockRepository.saveAll(rows);
        audit(competition, "ADMIN", "STARTING_PRICES_IMPORTED", "stocks=" + rows.size());
        return rows.stream().map(this::stockView).toList();
    }

    @Transactional(readOnly = true)
    public List<StockView> stocks(Long competitionId) {
        requireCompetition(competitionId);
        return stockRepository.findByCompetitionIdOrderByTicker(competitionId).stream().map(this::stockView).toList();
    }

    @Transactional
    public List<TeamCreated> createTeams(Long competitionId, List<TeamRequest> requests) {
        Competition competition = requireEditable(competitionId);
        if (requests == null || requests.isEmpty()) throw bad("At least one team is required");
        List<TeamCreated> result = new ArrayList<>();
        for (TeamRequest request : requests) {
            String name = required(request.name(), "team name");
            if (teamRepository.findByCompetitionIdAndNameIgnoreCase(competitionId, name).isPresent()) {
                throw new CompetitionException(HttpStatus.CONFLICT, "Duplicate team: " + name);
            }
            BigDecimal budget = request.startingBudget() == null ? competition.getDefaultStartingBudget() : money(request.startingBudget());
            if (budget.signum() <= 0) throw bad("Team budget must be positive");
            String code = authService.newAccessCode();
            CompetitionTeam team = teamRepository.save(new CompetitionTeam(competition, name, authService.hash(code), budget));
            result.add(new TeamCreated(team.getId(), name, code, budget));
        }
        audit(competition, "ADMIN", "TEAMS_CREATED", "count=" + result.size());
        return result;
    }

    @Transactional
    public List<TeamCreated> createTeams(Long competitionId, BulkTeamRequest request) {
        if (request.names() == null) throw bad("Team names are required");
        return createTeams(competitionId, request.names().stream().map(n -> new TeamRequest(n, request.startingBudget())).toList());
    }

    @Transactional(readOnly = true)
    public List<TeamView> teams(Long competitionId) {
        requireCompetition(competitionId);
        return teamRepository.findByCompetitionIdOrderByName(competitionId).stream()
                .map(t -> new TeamView(t.getId(), t.getName(), t.getStartingBudget(), t.getCashBalance(), t.isActive())).toList();
    }

    @Transactional
    public TeamView renameTeam(Long competitionId, Long teamId, RenameTeamRequest request) {
        Competition c = requireEditable(competitionId);
        CompetitionTeam team = teamRepository.findById(teamId).filter(t -> t.getCompetition().getId().equals(competitionId))
                .orElseThrow(() -> notFound("Team"));
        String name = required(request.name(), "team name");
        teamRepository.findByCompetitionIdAndNameIgnoreCase(competitionId, name)
                .filter(other -> !other.getId().equals(teamId)).ifPresent(other -> { throw new CompetitionException(HttpStatus.CONFLICT, "Duplicate team: " + name); });
        team.setName(name); teamRepository.save(team); audit(c, "ADMIN", "TEAM_RENAMED", "team=" + teamId + ", name=" + name);
        return new TeamView(team.getId(), team.getName(), team.getStartingBudget(), team.getCashBalance(), team.isActive());
    }

    @Transactional
    public TeamCreated regenerateCode(Long competitionId, Long teamId) {
        Competition c = requireEditable(competitionId);
        CompetitionTeam team = teamRepository.findById(teamId).filter(t -> t.getCompetition().getId().equals(competitionId))
                .orElseThrow(() -> notFound("Team"));
        String code = authService.newAccessCode(); team.setAccessCodeHash(authService.hash(code)); team.setSessionTokenHash(null);
        teamRepository.save(team); audit(c, "ADMIN", "TEAM_ACCESS_CODE_REGENERATED", "team=" + teamId);
        return new TeamCreated(team.getId(), team.getName(), code, team.getStartingBudget());
    }

    @Transactional
    public void deleteTeam(Long competitionId, Long teamId) {
        Competition c = requireEditable(competitionId);
        CompetitionTeam team = teamRepository.findById(teamId).filter(t -> t.getCompetition().getId().equals(competitionId))
                .orElseThrow(() -> notFound("Team"));
        teamRepository.delete(team); audit(c, "ADMIN", "TEAM_DELETED", "team=" + teamId);
    }

    @Transactional
    public void setBudget(Long competitionId, BudgetRequest request) {
        Competition competition = requireEditable(competitionId);
        BigDecimal amount = money(request.amount());
        if (amount.signum() <= 0) throw bad("Budget must be positive");
        List<CompetitionTeam> targets = request.teamId() == null ? teamRepository.findByCompetitionIdOrderByName(competitionId)
                : List.of(teamRepository.findById(request.teamId()).filter(t -> t.getCompetition().getId().equals(competitionId))
                .orElseThrow(() -> notFound("Team")));
        targets.forEach(team -> { team.setStartingBudget(amount); team.setCashBalance(amount); });
        teamRepository.saveAll(targets);
        audit(competition, "ADMIN", "BUDGET_CHANGED", "teams=" + targets.size() + ", amount=" + amount);
    }

    @Transactional
    public long generateReferenceMarket(Long competitionId) {
        Competition competition = requireEditable(competitionId);
        List<CompetitionStock> stocks = stockRepository.findByCompetitionIdOrderByTicker(competitionId);
        if (stocks.isEmpty()) throw bad("Load the stock universe before generating the reference market");
        referenceRepository.deleteForCompetition(competitionId);
        entityManager.flush();
        long count = 0;
        for (CompetitionStock stock : stocks) {
            Random random = new Random(competition.getRandomSeed() ^ ((long) stock.getTicker().hashCode() << 32));
            BigDecimal referencePrice = stock.getStartingPrice();
            List<ReferencePricePoint> batch = new ArrayList<>(competition.getDurationSeconds());
            for (int second = 0; second < competition.getDurationSeconds(); second++) {
                double referenceReturn = second == 0 ? 0.0 : random.nextGaussian() * stock.getVolatility();
                referenceReturn = Math.max(-0.02, Math.min(0.02, referenceReturn));
                if (second > 0) referencePrice = safePrice(referencePrice.doubleValue() * (1.0 + referenceReturn));
                batch.add(new ReferencePricePoint(competition, stock, second, referencePrice, referenceReturn));
            }
            referenceRepository.saveAll(batch);
            entityManager.flush();
            entityManager.clear();
            count += batch.size();
        }
        Competition managed = requireCompetition(competitionId);
        managed.setReadyAt(Instant.now());
        managed.setStatus(CompetitionStatus.READY);
        competitionRepository.save(managed);
        audit(managed, "ADMIN", "REFERENCE_MARKET_GENERATED", "points=" + count + ", seed=" + managed.getRandomSeed());
        return count;
    }

    @Transactional
    public NewsView createNews(Long competitionId, NewsRequest request) {
        Competition competition = requireEditable(competitionId);
        if (request.releaseSecond() < 0 || request.releaseSecond() >= competition.getDurationSeconds()) {
            throw bad("News release second must be inside the competition duration");
        }
        CompetitionNews news = new CompetitionNews();
        news.setCompetition(competition);
        news.setHeadline(required(request.headline(), "headline"));
        news.setDescription(required(request.description(), "description"));
        news.setReleaseSecond(request.releaseSecond());
        news.setImpactDurationSeconds(request.impactDurationSeconds() == null ? 60 : Math.max(1, request.impactDurationSeconds()));
        news.setImpactStyle(request.impactStyle() == null ? NewsImpactStyle.RAMP : request.impactStyle());
        for (ImpactRequest impact : request.impacts() == null ? List.<ImpactRequest>of() : request.impacts()) {
            CompetitionStock stock = stockRepository.findByCompetitionIdAndTickerIgnoreCase(competitionId, impact.ticker())
                    .orElseThrow(() -> notFound("Stock " + impact.ticker()));
            if (!Double.isFinite(impact.impactPercent()) || impact.impactPercent() <= -100) throw bad("Invalid news impact");
            news.getImpacts().add(new NewsStockImpact(news, stock, impact.impactPercent()));
        }
        CompetitionNews saved = newsRepository.save(news);
        audit(competition, "ADMIN", "NEWS_CREATED", saved.getHeadline() + " at second " + saved.getReleaseSecond());
        return newsView(saved, true);
    }

    @Transactional(readOnly = true)
    public List<NewsView> adminNews(Long competitionId) {
        requireCompetition(competitionId);
        return newsRepository.findByCompetitionIdOrderByReleaseSecond(competitionId).stream().map(n -> newsView(n, true)).toList();
    }

    @Transactional
    public void deleteNews(Long competitionId, Long newsId) {
        Competition c = requireEditable(competitionId);
        CompetitionNews news = newsRepository.findById(newsId).filter(n -> n.getCompetition().getId().equals(competitionId))
                .orElseThrow(() -> notFound("News"));
        newsRepository.delete(news); audit(c, "ADMIN", "NEWS_DELETED", "news=" + newsId);
    }

    @Transactional(readOnly = true)
    public List<PublicNewsView> publicNews(Long competitionId) {
        requireCompetition(competitionId);
        return newsRepository.findByCompetitionIdAndReleasedTrueOrderByReleaseSecond(competitionId).stream()
                .map(n -> new PublicNewsView(n.getId(), n.getHeadline(), n.getDescription(), n.getReleaseSecond(), n.getReleasedAt())).toList();
    }

    @Transactional
    public CompetitionView lock(Long id) {
        Competition competition = requireCompetition(id);
        if (competition.getStatus() != CompetitionStatus.READY) throw state("Only READY competitions can be locked");
        long stocks = stockRepository.countByCompetitionId(id);
        if (stocks == 0 || teamRepository.countByCompetitionId(id) == 0) throw bad("Stocks and teams are required before lock");
        long expected = stocks * competition.getDurationSeconds();
        if (referenceRepository.countByCompetitionId(id) != expected) throw bad("Reference market is missing or incomplete");
        competition.setLockHash(calculateLockHash(competition));
        competition.setStatus(CompetitionStatus.LOCKED);
        competition.setLockedAt(Instant.now());
        competitionRepository.save(competition);
        audit(competition, "ADMIN", "COMPETITION_LOCKED", "sha256=" + competition.getLockHash());
        return view(competition);
    }

    @Transactional
    public CompetitionView start(Long id) {
        Competition c = requireCompetition(id);
        if (c.getStatus() != CompetitionStatus.LOCKED) throw state("Only LOCKED competitions can start");
        c.setStatus(CompetitionStatus.RUNNING); c.setStartedAt(Instant.now());
        competitionRepository.save(c); audit(c, "ADMIN", "COMPETITION_STARTED", "elapsed=" + c.getElapsedSeconds());
        return view(c);
    }

    @Transactional
    public CompetitionView pause(Long id) {
        Competition c = requireCompetition(id);
        if (c.getStatus() != CompetitionStatus.RUNNING) throw state("Only RUNNING competitions can pause");
        c.setStatus(CompetitionStatus.PAUSED); c.setPausedAt(Instant.now());
        competitionRepository.save(c); audit(c, "ADMIN", "COMPETITION_PAUSED", "elapsed=" + c.getElapsedSeconds());
        return view(c);
    }

    @Transactional
    public CompetitionView resume(Long id) {
        Competition c = requireCompetition(id);
        if (c.getStatus() != CompetitionStatus.PAUSED) throw state("Only PAUSED competitions can resume");
        c.setStatus(CompetitionStatus.RUNNING); c.setPausedAt(null);
        competitionRepository.save(c); audit(c, "ADMIN", "COMPETITION_RESUMED", "elapsed=" + c.getElapsedSeconds());
        return view(c);
    }

    @Transactional
    public CompetitionView end(Long id) {
        Competition c = requireCompetition(id);
        if (c.getStatus() != CompetitionStatus.RUNNING && c.getStatus() != CompetitionStatus.PAUSED) {
            throw state("Only RUNNING or PAUSED competitions can end");
        }
        finish(c, "ADMIN_MANUAL_END");
        return view(c);
    }

    @Transactional
    public TradeView trade(Long competitionId, CompetitionTeam authenticatedTeam, OrderRequest request, TradeSide side) {
        Competition competition = requireCompetition(competitionId);
        if (competition.getStatus() != CompetitionStatus.RUNNING) throw state("Trading is allowed only while the competition is RUNNING");
        if (request.quantity() <= 0) throw bad("Quantity must be a positive whole number");
        CompetitionStock stock = stockRepository.findByCompetitionIdAndTickerIgnoreCase(competitionId, request.ticker())
                .orElseThrow(() -> notFound("Stock"));
        if (!stock.isEnabled()) throw bad("Stock is disabled");
        CompetitionTeam team = teamRepository.findByIdForUpdate(authenticatedTeam.getId()).orElseThrow(() -> notFound("Team"));
        TeamHolding holding = holdingRepository.findForUpdate(team.getId(), stock.getId()).orElseGet(() -> new TeamHolding(team, stock));
        BigDecimal price = stock.getLivePrice();
        BigDecimal gross = price.multiply(BigDecimal.valueOf(request.quantity())).setScale(2, RoundingMode.HALF_UP);
        if (side == TradeSide.BUY) {
            if (team.getCashBalance().compareTo(gross) < 0) throw bad("Insufficient cash");
            BigDecimal priorCost = holding.getAveragePurchasePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
            long newQuantity = Math.addExact(holding.getQuantity(), request.quantity());
            holding.setAveragePurchasePrice(priorCost.add(gross).divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP));
            holding.setQuantity(newQuantity);
            team.setCashBalance(money(team.getCashBalance().subtract(gross)));
        } else {
            if (!competition.isAllowShortSelling() && holding.getQuantity() < request.quantity()) throw bad("Insufficient shares");
            BigDecimal realized = price.subtract(holding.getAveragePurchasePrice()).multiply(BigDecimal.valueOf(request.quantity()));
            holding.setRealizedPnL(money(holding.getRealizedPnL().add(realized)));
            holding.setQuantity(holding.getQuantity() - request.quantity());
            if (holding.getQuantity() == 0) holding.setAveragePurchasePrice(BigDecimal.ZERO);
            team.setCashBalance(money(team.getCashBalance().add(gross)));
        }
        holding.setUpdatedAt(Instant.now());
        holdingRepository.save(holding); teamRepository.save(team);
        CompetitionTrade trade = new CompetitionTrade();
        trade.setCompetition(competition); trade.setTeam(team); trade.setStock(stock); trade.setSide(side);
        trade.setQuantity(request.quantity()); trade.setExecutionPrice(price); trade.setGrossValue(gross);
        trade.setCompetitionSecond(competition.getElapsedSeconds());
        return tradeView(tradeRepository.save(trade));
    }

    @Transactional(readOnly = true)
    public PortfolioView portfolio(CompetitionTeam team) { return portfolioFor(team); }

    @Transactional(readOnly = true)
    public List<TradeView> trades(CompetitionTeam team) {
        return tradeRepository.findByTeamIdOrderByTimestampDesc(team.getId()).stream().map(this::tradeView).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardRow> leaderboard(Long competitionId) {
        Competition c = requireCompetition(competitionId);
        if (c.getStatus() == CompetitionStatus.FINISHED) {
            List<FinalLeaderboardResult> saved = finalResultRepository.findByCompetitionIdOrderByRankPosition(competitionId);
            if (!saved.isEmpty()) return saved.stream().map(r -> new LeaderboardRow(r.getRankPosition(), r.getTeam().getId(),
                    r.getTeam().getName(), r.getStartingBudget(), r.getTeam().getCashBalance(), r.getEndingValue(),
                    r.getProfitLoss(), r.getReturnPercent())).toList();
        }
        List<PortfolioView> values = teamRepository.findByCompetitionIdOrderByName(competitionId).stream().map(this::portfolioFor)
                .sorted(Comparator.comparingDouble(PortfolioView::returnPercent).reversed().thenComparing(PortfolioView::teamName)).toList();
        List<LeaderboardRow> rows = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            PortfolioView p = values.get(i);
            rows.add(new LeaderboardRow(i + 1, p.teamId(), p.teamName(), p.startingBudget(), p.cashBalance(),
                    p.totalPortfolioValue(), p.totalPnL(), p.returnPercent()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public MarketView market(Long competitionId) {
        return new MarketView(get(competitionId), stocks(competitionId), publicNews(competitionId), leaderboard(competitionId));
    }

    @Transactional(readOnly = true)
    public List<PricePointView> liveHistory(Long competitionId, String ticker) {
        Competition c = requireCompetition(competitionId);
        CompetitionStock stock = stockRepository.findByCompetitionIdAndTickerIgnoreCase(competitionId, ticker)
                .orElseThrow(() -> notFound("Stock"));
        List<PricePointView> history = new ArrayList<>();
        history.add(new PricePointView(0, stock.getStartingPrice(), 0, 0, 0, 0));
        livePriceRepository.findByCompetitionIdAndStockIdAndSecondOffsetLessThanEqualOrderBySecondOffset(
                        competitionId, stock.getId(), c.getElapsedSeconds())
                .forEach(p -> history.add(new PricePointView(p.getSecondOffset(), p.getPrice(), p.getReferenceReturn(),
                        p.getNewsReturn(), p.getParticipantReturn(), p.getCombinedReturn())));
        return history;
    }

    @Transactional(readOnly = true)
    public List<PricePointView> adminReference(Long competitionId, String ticker) {
        CompetitionStock stock = stockRepository.findByCompetitionIdAndTickerIgnoreCase(competitionId, ticker)
                .orElseThrow(() -> notFound("Stock"));
        return referenceRepository.findByCompetitionIdAndStockIdOrderBySecondOffset(competitionId, stock.getId()).stream()
                .map(p -> new PricePointView(p.getSecondOffset(), p.getReferencePrice(), p.getReferenceReturn(), 0, 0, p.getReferenceReturn())).toList();
    }

    @Transactional
    public StockView emergencyPrice(Long competitionId, EmergencyPriceRequest request) {
        Competition c = requireCompetition(competitionId);
        if (request.price() == null || request.price().compareTo(MIN_PRICE) < 0 || request.reason() == null || request.reason().isBlank()) {
            throw bad("A valid price and reason are required");
        }
        CompetitionStock stock = stockRepository.findByCompetitionIdAndTickerIgnoreCase(competitionId, request.ticker())
                .orElseThrow(() -> notFound("Stock"));
        BigDecimal previous = stock.getLivePrice(); stock.setLivePrice(request.price().setScale(4, RoundingMode.HALF_UP));
        stockRepository.save(stock);
        audit(c, "ADMIN", "EMERGENCY_PRICE_OVERRIDE", stock.getTicker() + ": " + previous + " -> " + stock.getLivePrice() + "; " + request.reason());
        return stockView(stock);
    }

    @Transactional(readOnly = true)
    public List<AuditView> auditLog(Long competitionId) {
        requireCompetition(competitionId);
        return auditRepository.findByCompetitionIdOrderByCreatedAtDesc(competitionId).stream()
                .map(a -> new AuditView(a.getId(), a.getActor(), a.getAction(), a.getDetails(), a.getCreatedAt())).toList();
    }

    @Transactional
    public void finish(Competition c, String reason) {
        if (c.getStatus() == CompetitionStatus.FINISHED) return;
        List<LeaderboardRow> rows = leaderboard(c.getId());
        finalResultRepository.deleteByCompetitionId(c.getId());
        for (LeaderboardRow row : rows) {
            FinalLeaderboardResult result = new FinalLeaderboardResult();
            result.setCompetition(c);
            result.setTeam(teamRepository.getReferenceById(row.teamId()));
            result.setRankPosition(row.rank()); result.setStartingBudget(row.startingBudget());
            result.setEndingValue(row.totalPortfolioValue()); result.setProfitLoss(row.profitLoss());
            result.setReturnPercent(row.returnPercent());
            finalResultRepository.save(result);
        }
        c.setStatus(CompetitionStatus.FINISHED); c.setEndedAt(Instant.now()); competitionRepository.save(c);
        audit(c, "SYSTEM", "COMPETITION_FINISHED", reason);
    }

    Competition requireCompetition(Long id) {
        return competitionRepository.findById(id).orElseThrow(() -> notFound("Competition"));
    }

    private Competition requireEditable(Long id) {
        Competition c = requireCompetition(id);
        if (c.getStatus() != CompetitionStatus.DRAFT && c.getStatus() != CompetitionStatus.READY) {
            throw state("Competition configuration is immutable after lock");
        }
        return c;
    }

    private void prepareStockReplacement(Competition competition) {
        if (newsRepository.countByCompetitionId(competition.getId()) > 0) {
            throw state("Delete scheduled news before replacing its stock universe");
        }
        referenceRepository.deleteForCompetition(competition.getId());
        competition.setStatus(CompetitionStatus.DRAFT);
        competition.setReadyAt(null);
        competitionRepository.save(competition);
    }

    private CompetitionView view(Competition c) {
        return new CompetitionView(c.getId(), c.getName(), c.getStatus(), c.getDurationSeconds(), c.getTickIntervalMs(),
                c.getDefaultStartingBudget(), c.getRandomSeed(), c.getElapsedSeconds(),
                Math.max(0, c.getDurationSeconds() - c.getElapsedSeconds()), c.isAllowShortSelling(),
                c.getParticipantImpactMultiplier(), c.getOrderFlowLookbackSeconds(), c.getMaxOrderFlowImpactPerTick(),
                c.getLockHash(), c.getCreatedAt(), c.getStartedAt(), c.getEndedAt(),
                stockRepository.countByCompetitionId(c.getId()), teamRepository.countByCompetitionId(c.getId()));
    }

    private StockView stockView(CompetitionStock s) {
        double change = s.getStartingPrice().signum() == 0 ? 0 : s.getLivePrice().subtract(s.getStartingPrice())
                .divide(s.getStartingPrice(), 8, RoundingMode.HALF_UP).doubleValue() * 100;
        return new StockView(s.getId(), s.getTicker(), s.getCompanyName(), s.getSector(), s.getMarketCap(),
                s.getVolatility(), s.getLiquidityScale(), s.getStartingPrice(), s.getLivePrice(), s.isEnabled(), change);
    }

    private NewsView newsView(CompetitionNews n, boolean includeImpacts) {
        List<ImpactRequest> impacts = includeImpacts ? n.getImpacts().stream()
                .map(i -> new ImpactRequest(i.getStock().getTicker(), i.getImpactPercent())).toList() : List.of();
        return new NewsView(n.getId(), n.getHeadline(), n.getDescription(), n.getReleaseSecond(),
                n.getImpactDurationSeconds(), n.getImpactStyle(), n.isReleased(), n.getReleasedAt(), impacts);
    }

    private TradeView tradeView(CompetitionTrade t) {
        return new TradeView(t.getId(), t.getStock().getTicker(), t.getSide(), t.getQuantity(), t.getExecutionPrice(),
                t.getGrossValue(), t.getTimestamp(), t.getCompetitionSecond());
    }

    private PortfolioView portfolioFor(CompetitionTeam team) {
        List<HoldingView> rows = new ArrayList<>();
        BigDecimal marketValue = BigDecimal.ZERO, realized = BigDecimal.ZERO, unrealized = BigDecimal.ZERO;
        for (TeamHolding h : holdingRepository.findByTeamId(team.getId())) {
            BigDecimal value = h.getStock().getLivePrice().multiply(BigDecimal.valueOf(h.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal openPnl = h.getStock().getLivePrice().subtract(h.getAveragePurchasePrice())
                    .multiply(BigDecimal.valueOf(h.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            marketValue = marketValue.add(value); realized = realized.add(h.getRealizedPnL()); unrealized = unrealized.add(openPnl);
            rows.add(new HoldingView(h.getStock().getTicker(), h.getQuantity(), h.getAveragePurchasePrice(),
                    h.getStock().getLivePrice(), value, h.getRealizedPnL(), openPnl));
        }
        BigDecimal total = money(team.getCashBalance().add(marketValue));
        BigDecimal pnl = money(total.subtract(team.getStartingBudget()));
        double returnPct = pnl.divide(team.getStartingBudget(), 8, RoundingMode.HALF_UP).doubleValue() * 100;
        return new PortfolioView(team.getId(), team.getName(), team.getStartingBudget(), team.getCashBalance(), money(marketValue),
                total, money(realized), money(unrealized), pnl, returnPct, rows);
    }

    private String calculateLockHash(Competition c) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, c.getRandomSeed() + "|" + c.getDurationSeconds() + "|" + c.getDefaultStartingBudget());
            for (CompetitionStock s : stockRepository.findByCompetitionIdOrderByTicker(c.getId())) {
                update(digest, s.getTicker() + "|" + s.getStartingPrice() + "|" + s.getLiquidityScale());
                for (ReferencePricePoint p : referenceRepository.findByCompetitionIdAndStockIdOrderBySecondOffset(c.getId(), s.getId())) {
                    update(digest, p.getSecondOffset() + "|" + p.getReferencePrice() + "|" + p.getReferenceReturn());
                }
            }
            for (CompetitionTeam t : teamRepository.findByCompetitionIdOrderByName(c.getId())) update(digest, t.getName() + "|" + t.getStartingBudget());
            for (CompetitionNews n : newsRepository.findByCompetitionIdOrderByReleaseSecond(c.getId())) {
                update(digest, n.getHeadline() + "|" + n.getReleaseSecond() + "|" + n.getImpactStyle());
                n.getImpacts().stream().sorted(Comparator.comparing(i -> i.getStock().getTicker()))
                        .forEach(i -> update(digest, i.getStock().getTicker() + "|" + i.getImpactPercent()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); }
    private void audit(Competition c, String actor, String action, String details) { auditRepository.save(new AuditEvent(c, actor, action, details)); }
    private BigDecimal safePrice(double raw) { return !Double.isFinite(raw) || raw < 0.01 ? MIN_PRICE : BigDecimal.valueOf(raw).setScale(6, RoundingMode.HALF_UP); }
    private BigDecimal money(BigDecimal amount) { if (amount == null) throw bad("Amount is required"); return amount.setScale(2, RoundingMode.HALF_UP); }
    private String required(String value, String label) { if (value == null || value.isBlank()) throw bad(label + " is required"); return value.trim(); }
    private double liquidityFor(String cap) {
        if (cap == null) return 2_000_000;
        return switch (cap.toLowerCase(Locale.ROOT)) { case "mega" -> 10_000_000; case "large" -> 5_000_000; case "mid" -> 2_000_000; default -> 750_000; };
    }
    private CompetitionException bad(String message) { return new CompetitionException(HttpStatus.BAD_REQUEST, message); }
    private CompetitionException state(String message) { return new CompetitionException(HttpStatus.CONFLICT, message); }
    private CompetitionException notFound(String what) { return new CompetitionException(HttpStatus.NOT_FOUND, what + " not found"); }
}
