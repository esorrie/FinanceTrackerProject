CREATE TABLE IF NOT EXISTS tbl_currency (
                                            currency_id INT AUTO_INCREMENT PRIMARY KEY,
                                            currency_code CHAR(3) NOT NULL UNIQUE,
    symbol VARCHAR(3) NOT NULL,
    base_unit BOOLEAN DEFAULT TRUE
    );

CREATE TABLE IF NOT EXISTS tbl_user (
                                        user_id INT AUTO_INCREMENT PRIMARY KEY,
                                        username VARCHAR(50) NOT NULL,
    currency_id INT NOT NULL,
    open_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_currency FOREIGN KEY (currency_id) REFERENCES tbl_currency(currency_id)
    );

CREATE TABLE IF NOT EXISTS tbl_asset (
                                         asset_id INT AUTO_INCREMENT PRIMARY KEY,
                                         asset_symbol VARCHAR(10) NOT NULL UNIQUE,
    asset_name VARCHAR(50) NOT NULL,
    price DECIMAL(19, 4),
    opening_price DECIMAL(19, 4),
    closing_price DECIMAL(19, 4),
    stock_exchange VARCHAR(100),
    currency_id INT NOT NULL,
    CONSTRAINT fk_asset_currency FOREIGN KEY (currency_id) REFERENCES tbl_currency(currency_id)
    );

CREATE TABLE IF NOT EXISTS tbl_portfolio (
                                             portfolio_id INT AUTO_INCREMENT PRIMARY KEY,
                                             user_id INT NOT NULL,
                                             portfolio_name VARCHAR(50) DEFAULT 'Portfolio',
    CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES tbl_user(user_id)
    );

CREATE TABLE IF NOT EXISTS tbl_holding (
                                           holding_id INT AUTO_INCREMENT PRIMARY KEY,
                                           user_id INT NOT NULL,
                                           asset_id INT NOT NULL,
                                           portfolio_id INT NOT NULL,
                                           units DECIMAL(19, 4) NOT NULL,
    avg_purchase_price DECIMAL(19, 4) NOT NULL,
    last_price DECIMAL(19, 4) NOT NULL,
    purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_holding_user FOREIGN KEY (user_id) REFERENCES tbl_user(user_id),
    CONSTRAINT fk_holding_asset FOREIGN KEY (asset_id) REFERENCES tbl_asset(asset_id),
    CONSTRAINT fk_holding_portfolio FOREIGN KEY (portfolio_id) REFERENCES tbl_portfolio(portfolio_id)
    );

CREATE TABLE IF NOT EXISTS tbl_exchange_rates (
                                                  exchange_id INT AUTO_INCREMENT PRIMARY KEY,
                                                  start_currency_id INT NOT NULL,
                                                  end_currency_id INT NOT NULL,
                                                  rate DECIMAL(19, 4) NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exchange_start_currency FOREIGN KEY (start_currency_id) REFERENCES tbl_currency(currency_id),
    CONSTRAINT fk_exchange_end_currency FOREIGN KEY (end_currency_id) REFERENCES tbl_currency(currency_id)
    );

CREATE TABLE IF NOT EXISTS tbl_favourites (
                                              favourite_id INT AUTO_INCREMENT PRIMARY KEY,
                                              user_id INT NOT NULL,
                                              asset_id INT NOT NULL,
                                              display INT CHECK (display BETWEEN 0 AND 3),
    CONSTRAINT fk_favourite_user FOREIGN KEY (user_id) REFERENCES tbl_user(user_id),
    CONSTRAINT fk_favourite_asset FOREIGN KEY (asset_id) REFERENCES tbl_asset(asset_id),
    CONSTRAINT uq_favourite_user_asset UNIQUE (user_id, asset_id),
    CONSTRAINT uq_favourite_user_display UNIQUE (user_id, display)
    );
