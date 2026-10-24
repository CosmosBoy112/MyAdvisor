CREATE TABLE IF NOT EXISTS app_users (
 subject text PRIMARY KEY,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS reports (
 id uuid PRIMARY KEY,
 owner_subject text NOT NULL REFERENCES app_users(subject),
 filename text NOT NULL,
 analysis jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS reports_owner_idx ON reports(owner_subject, created_at DESC);
CREATE TABLE IF NOT EXISTS chat_messages (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 report_id uuid NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
 role text NOT NULL CHECK (role IN ('user','assistant')),
 content text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
