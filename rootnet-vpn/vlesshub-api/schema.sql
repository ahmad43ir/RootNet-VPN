-- VlessHub D1 schema — Cloudflare-side data plane, fully separated from
-- RootNet's Supabase project.

CREATE TABLE IF NOT EXISTS servers (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL,
  flag          TEXT NOT NULL DEFAULT '🌐',
  country       TEXT NOT NULL DEFAULT 'Cloud',
  config        TEXT NOT NULL UNIQUE,
  host          TEXT DEFAULT '',
  port          INTEGER DEFAULT 0,
  type          TEXT NOT NULL DEFAULT 'vless',
  config_format TEXT NOT NULL DEFAULT 'link',
  source_channel TEXT DEFAULT '',
  is_active     INTEGER NOT NULL DEFAULT 1,
  created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS proxies (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  host         TEXT NOT NULL,
  port         INTEGER NOT NULL,
  secret       TEXT DEFAULT '',
  source       TEXT DEFAULT '',
  link         TEXT NOT NULL UNIQUE,
  working      INTEGER NOT NULL DEFAULT 1,
  last_checked TEXT,
  is_active    INTEGER NOT NULL DEFAULT 1,
  added_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS vpn_files (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  filename       TEXT NOT NULL,
  mime_type      TEXT,
  size_bytes     INTEGER NOT NULL DEFAULT 0,
  content        TEXT NOT NULL,          -- base64 TEXT payload
  source_channel TEXT DEFAULT '',
  is_encrypted   INTEGER NOT NULL DEFAULT 0,
  config_count   INTEGER NOT NULL DEFAULT 0,
  uploaded_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS app_config (
  id              INTEGER PRIMARY KEY,
  latest_version  TEXT NOT NULL DEFAULT '0.1.0',
  latest_build    INTEGER NOT NULL DEFAULT 1,
  minimum_version TEXT NOT NULL DEFAULT '0.1.0',
  update_url      TEXT NOT NULL DEFAULT 'https://chobgroup.pages.dev',
  release_notes   TEXT DEFAULT '',
  force_update    INTEGER NOT NULL DEFAULT 0,
  updated_at      TEXT
);
INSERT OR IGNORE INTO app_config (id) VALUES (1);

CREATE TABLE IF NOT EXISTS bot_state (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL DEFAULT ''
);
INSERT OR IGNORE INTO bot_state (key, value) VALUES ('vless_channels', '');
INSERT OR IGNORE INTO bot_state (key, value) VALUES ('last_scrape_time', '');

CREATE INDEX IF NOT EXISTS idx_servers_active ON servers (is_active);
CREATE INDEX IF NOT EXISTS idx_files_uploaded ON vpn_files (uploaded_at);
