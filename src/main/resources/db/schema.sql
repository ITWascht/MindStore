-- KEIN BEGIN/COMMIT hier!

CREATE TABLE IF NOT EXISTS idea (
  id             INTEGER PRIMARY KEY,
  title          TEXT    NOT NULL,
  body           TEXT,
  priority       INTEGER NOT NULL DEFAULT 2,
  status         TEXT    NOT NULL DEFAULT 'inbox',
  effort_minutes INTEGER,
  created_at     INTEGER NOT NULL DEFAULT (strftime('%s','now')),
  updated_at     INTEGER,
  CHECK (priority IN (1,2,3,4)),
  CHECK (status IN ('inbox','draft','doing','done','archived'))
);

CREATE INDEX IF NOT EXISTS idx_idea_status_priority ON idea(status, priority);
CREATE INDEX IF NOT EXISTS idx_idea_created_at     ON idea(created_at);

CREATE TRIGGER IF NOT EXISTS trg_idea_set_updated_at
AFTER UPDATE ON idea
FOR EACH ROW
BEGIN
  UPDATE idea SET updated_at = strftime('%s','now') WHERE id = NEW.id;
END;
