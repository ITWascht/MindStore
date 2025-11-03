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
  UPDATE idea
  SET updated_at = strftime('%s','now')
  WHERE id = NEW.id;
END;

-- Tags
CREATE TABLE IF NOT EXISTS tag (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL UNIQUE,
  color      TEXT,                       -- optional hex wie "#8BC34A"
  created_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

-- Idea <-> Tag (M:N)
CREATE TABLE IF NOT EXISTS idea_tag (
  idea_id INTEGER NOT NULL,
  tag_id  INTEGER NOT NULL,
  PRIMARY KEY (idea_id, tag_id),
  FOREIGN KEY (idea_id) REFERENCES idea(id) ON DELETE CASCADE,
  FOREIGN KEY (tag_id)  REFERENCES tag(id)  ON DELETE CASCADE
);

-- Erinnerungstabelle (eine optionale Erinnerung pro Idee)
CREATE TABLE IF NOT EXISTS reminder (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  idea_id    INTEGER NOT NULL UNIQUE,             -- genau eine Erinnerung pro Idee
  due_at     INTEGER NOT NULL,                    -- Unix Sekunden
  note       TEXT,                                -- optional
  is_done    INTEGER NOT NULL DEFAULT 0,          -- 0/1
  created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')),
  updated_at INTEGER
  ,FOREIGN KEY(idea_id) REFERENCES idea(id) ON DELETE CASCADE
);

CREATE TRIGGER IF NOT EXISTS trg_reminder_updated
AFTER UPDATE ON reminder
FOR EACH ROW
BEGIN
  UPDATE reminder SET updated_at = strftime('%s','now') WHERE id = NEW.id;
END;
