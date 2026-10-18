CREATE TABLE account (
  id            BIGSERIAL PRIMARY KEY,
  login         VARCHAR(32)  NOT NULL UNIQUE,            -- stored lowercase
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'PARTNER' CHECK (role IN ('PARTNER','ADMIN')),
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  balance       BIGINT       NOT NULL DEFAULT 0,          -- available, nano PARTNER_CURRENCY; may go negative after a rollback
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE session (
  token_hash   VARCHAR(64) PRIMARY KEY,                   -- hex SHA-256 of the cookie value; the raw token is never stored
  account_id   BIGINT      NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX session_account ON session(account_id);
CREATE TABLE company (
  id          BIGSERIAL PRIMARY KEY,
  identity    VARCHAR(48)    NOT NULL UNIQUE,
  account_id  BIGINT         NOT NULL REFERENCES account(id),
  type        VARCHAR(24)    NOT NULL CHECK (type IN ('CPA','REVSHARE_DEPOSIT','REVSHARE_TURNOVER')),
  amount      NUMERIC(20,9)  NOT NULL CHECK (amount >= 0),  -- CPA: PARTNER_CURRENCY amount; REVSHARE_*: percent
  status      VARCHAR(16)    NOT NULL DEFAULT 'IN_REVIEW' CHECK (status IN ('IN_REVIEW','ACTIVE','DISABLED')),
  created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
  CHECK (type = 'CPA' OR amount <= 100)
);
CREATE INDEX company_account ON company(account_id);
CREATE INDEX company_status ON company(status);
CREATE TABLE postback (
  id           BIGSERIAL PRIMARY KEY,
  company_id   BIGINT        NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  type         VARCHAR(16)   NOT NULL,                    -- 'KEITARO' (more trackers later)
  url          VARCHAR(2048) NOT NULL,                    -- normalized base URL the adapter builds requests from
  active       BOOLEAN       NOT NULL DEFAULT TRUE,
  last_sent_at TIMESTAMPTZ,
  last_status  INTEGER,                                   -- HTTP status of the last attempt, null when none/failed before a response
  last_error   VARCHAR(300),                              -- short reason when the last attempt failed or was skipped
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX postback_company ON postback(company_id);
CREATE TABLE referral (                                   -- "Players": a player attributed to a company
  player_id                BIGINT      PRIMARY KEY,
  company_id               BIGINT      NOT NULL REFERENCES company(id),
  click_id                 VARCHAR(128),                  -- affiliateAttribution["click_id"], validated
  attribution              JSONB       NOT NULL DEFAULT '{}',
  country                  VARCHAR(2),
  registered_at            TIMESTAMPTZ NOT NULL,
  first_deposit_at         TIMESTAMPTZ,
  first_deposit_amount     BIGINT,                        -- nano PARTNER_CURRENCY
  first_deposit_invoice_id BIGINT,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX referral_company_registered ON referral(company_id, registered_at);
CREATE INDEX referral_company_ftd ON referral(company_id, first_deposit_at);
CREATE TABLE commission (                                 -- the partner's earnings ledger (owner called it "Transactions")
  id          BIGSERIAL PRIMARY KEY,
  account_id  BIGINT      NOT NULL REFERENCES account(id),
  company_id  BIGINT      NOT NULL REFERENCES company(id),
  player_id   BIGINT      NOT NULL,
  type        VARCHAR(24) NOT NULL CHECK (type IN ('CPA','REVSHARE_DEPOSIT','REVSHARE_TURNOVER')),
  amount      BIGINT      NOT NULL,                       -- nano PARTNER_CURRENCY, signed
  source      VARCHAR(80) NOT NULL UNIQUE,                -- idempotency: cpa:<player> | deposit:<invoice> | turnover:<company>:<player>:<yyyy-mm-dd>
  day         DATE        NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX commission_account_day ON commission(account_id, day);
CREATE INDEX commission_company_day ON commission(company_id, day);
CREATE TABLE activity_daily (                             -- per player per UTC day, for analytics
  player_id       BIGINT  NOT NULL,
  day             DATE    NOT NULL,
  company_id      BIGINT  NOT NULL REFERENCES company(id),
  deposit_count   INTEGER NOT NULL DEFAULT 0,
  deposit_amount  BIGINT  NOT NULL DEFAULT 0,             -- nano PARTNER_CURRENCY
  turnover_amount BIGINT  NOT NULL DEFAULT 0,             -- real-money stakes net of rollbacks, nano PARTNER_CURRENCY
  PRIMARY KEY (player_id, day)
);
CREATE INDEX activity_company_day ON activity_daily(company_id, day);
CREATE TABLE processed_fact (                             -- at-least-once delivery: act once per fact
  key               VARCHAR(64) PRIMARY KEY,              -- register:<player> | invoice:<id> | spin:<id> | bet:<id>
  turnover          BIGINT      NOT NULL DEFAULT 0,       -- what a stake added to turnover, so a ROLLBACK reverses it exactly
  commission        BIGINT      NOT NULL DEFAULT 0,       -- what a stake added to commission
  commission_source VARCHAR(80),
  company_id        BIGINT,
  player_id         BIGINT,
  day               DATE        NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX processed_fact_created ON processed_fact(created_at);
CREATE TABLE payout (
  id          BIGSERIAL PRIMARY KEY,
  account_id  BIGINT       NOT NULL REFERENCES account(id),
  amount      BIGINT       NOT NULL CHECK (amount > 0),   -- nano PARTNER_CURRENCY
  currency    VARCHAR(10)  NOT NULL,
  network     VARCHAR(16)  NOT NULL,
  address     VARCHAR(128) NOT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','REJECTED')),
  tx_hash     VARCHAR(128),                               -- written by hand when paid
  comment     VARCHAR(300),                               -- written by hand (e.g. rejection reason); shown to the partner
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX payout_account ON payout(account_id, created_at);
CREATE INDEX payout_status ON payout(status);
