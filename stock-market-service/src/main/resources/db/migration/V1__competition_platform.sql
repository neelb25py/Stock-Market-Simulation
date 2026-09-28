CREATE TABLE IF NOT EXISTS competitions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, status VARCHAR(32) NOT NULL,
  duration_seconds INT NOT NULL, tick_interval_ms BIGINT NOT NULL, default_starting_budget DECIMAL(19,2) NOT NULL,
  random_seed BIGINT NOT NULL, allow_short_selling BOOLEAN NOT NULL, participant_impact_multiplier DOUBLE NOT NULL,
  order_flow_lookback_seconds INT NOT NULL, max_order_flow_impact_per_tick DOUBLE NOT NULL, elapsed_seconds INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL, ready_at TIMESTAMP(6), locked_at TIMESTAMP(6), started_at TIMESTAMP(6),
  paused_at TIMESTAMP(6), ended_at TIMESTAMP(6), lock_hash VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS competition_stocks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, ticker VARCHAR(64) NOT NULL,
  company_name VARCHAR(255) NOT NULL, sector VARCHAR(128) NOT NULL, market_cap VARCHAR(32) NOT NULL,
  volatility DOUBLE NOT NULL, liquidity_scale DOUBLE NOT NULL, starting_price DECIMAL(19,4) NOT NULL,
  live_price DECIMAL(19,4) NOT NULL, enabled BOOLEAN NOT NULL,
  CONSTRAINT fk_cs_comp FOREIGN KEY (competition_id) REFERENCES competitions(id), UNIQUE KEY uk_cs_ticker (competition_id,ticker)
);
CREATE TABLE IF NOT EXISTS competition_teams (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, name VARCHAR(255) NOT NULL,
  access_code_hash VARCHAR(255) NOT NULL, session_token_hash VARCHAR(255), starting_budget DECIMAL(19,2) NOT NULL,
  cash_balance DECIMAL(19,2) NOT NULL, active BOOLEAN NOT NULL, created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_ct_comp FOREIGN KEY (competition_id) REFERENCES competitions(id), UNIQUE KEY uk_ct_name (competition_id,name)
);
CREATE TABLE IF NOT EXISTS team_holdings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, team_id BIGINT NOT NULL, stock_id BIGINT NOT NULL, quantity BIGINT NOT NULL,
  average_purchase_price DECIMAL(19,4) NOT NULL, realized_pnl DECIMAL(19,2) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_th_team FOREIGN KEY (team_id) REFERENCES competition_teams(id),
  CONSTRAINT fk_th_stock FOREIGN KEY (stock_id) REFERENCES competition_stocks(id), UNIQUE KEY uk_th_position (team_id,stock_id)
);
CREATE TABLE IF NOT EXISTS competition_trades (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, team_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
  side VARCHAR(16) NOT NULL, quantity BIGINT NOT NULL, execution_price DECIMAL(19,4) NOT NULL,
  gross_value DECIMAL(19,2) NOT NULL, slippage DECIMAL(19,4) NOT NULL, executed_at TIMESTAMP(6) NOT NULL,
  competition_second INT NOT NULL, CONSTRAINT fk_tr_comp FOREIGN KEY (competition_id) REFERENCES competitions(id),
  CONSTRAINT fk_tr_team FOREIGN KEY (team_id) REFERENCES competition_teams(id),
  CONSTRAINT fk_tr_stock FOREIGN KEY (stock_id) REFERENCES competition_stocks(id), INDEX idx_trade_stock_time (stock_id,executed_at)
);
CREATE TABLE IF NOT EXISTS reference_price_points (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
  second_offset INT NOT NULL, reference_price DECIMAL(19,6) NOT NULL, reference_return DOUBLE NOT NULL,
  CONSTRAINT fk_ref_comp FOREIGN KEY (competition_id) REFERENCES competitions(id),
  CONSTRAINT fk_ref_stock FOREIGN KEY (stock_id) REFERENCES competition_stocks(id),
  UNIQUE KEY uk_ref_point (stock_id,second_offset), INDEX idx_reference_lookup (competition_id,second_offset)
);
CREATE TABLE IF NOT EXISTS live_price_points (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
  second_offset INT NOT NULL, price DECIMAL(19,6) NOT NULL, reference_return DOUBLE NOT NULL,
  news_return DOUBLE NOT NULL, participant_return DOUBLE NOT NULL, combined_return DOUBLE NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL, CONSTRAINT fk_live_comp FOREIGN KEY (competition_id) REFERENCES competitions(id),
  CONSTRAINT fk_live_stock FOREIGN KEY (stock_id) REFERENCES competition_stocks(id), INDEX idx_live_history (stock_id,second_offset)
);
CREATE TABLE IF NOT EXISTS competition_news (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, headline VARCHAR(255) NOT NULL,
  description VARCHAR(4000) NOT NULL, release_second INT NOT NULL, impact_duration_seconds INT NOT NULL,
  impact_style VARCHAR(16) NOT NULL, released BOOLEAN NOT NULL, released_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_news_comp FOREIGN KEY (competition_id) REFERENCES competitions(id)
);
CREATE TABLE IF NOT EXISTS news_stock_impacts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, news_id BIGINT NOT NULL, stock_id BIGINT NOT NULL, impact_percent DOUBLE NOT NULL,
  CONSTRAINT fk_nsi_news FOREIGN KEY (news_id) REFERENCES competition_news(id),
  CONSTRAINT fk_nsi_stock FOREIGN KEY (stock_id) REFERENCES competition_stocks(id)
);
CREATE TABLE IF NOT EXISTS audit_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, actor VARCHAR(255) NOT NULL,
  action VARCHAR(255) NOT NULL, details VARCHAR(4000) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_audit_comp FOREIGN KEY (competition_id) REFERENCES competitions(id)
);
CREATE TABLE IF NOT EXISTS final_leaderboard_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, competition_id BIGINT NOT NULL, team_id BIGINT NOT NULL,
  rank_position INT NOT NULL, starting_budget DECIMAL(19,2) NOT NULL, ending_value DECIMAL(19,2) NOT NULL,
  profit_loss DECIMAL(19,2) NOT NULL, return_percent DOUBLE NOT NULL, finalized_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_final_comp FOREIGN KEY (competition_id) REFERENCES competitions(id),
  CONSTRAINT fk_final_team FOREIGN KEY (team_id) REFERENCES competition_teams(id)
);
