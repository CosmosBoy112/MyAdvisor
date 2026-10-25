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
-- Advisor agent run log (docs/advisor-tools.md section 9). Logs hold academic questions, so they expire after 90 days
-- and are removed with their report.
CREATE TABLE IF NOT EXISTS agent_runs (
 id uuid NOT NULL,
 started_at timestamptz NOT NULL,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 report_id uuid REFERENCES reports(id) ON DELETE CASCADE,
 model text NOT NULL,
 status text NOT NULL CHECK (status IN ('ok','error','rate_limited','validation_failed')),
 latency_ms integer,
 llm_calls smallint NOT NULL DEFAULT 0,
 tool_calls smallint NOT NULL DEFAULT 0,
 view_type text,
 error_code text,
 PRIMARY KEY (id, started_at)
);
SELECT create_hypertable('agent_runs', 'started_at', chunk_time_interval => INTERVAL '7 days', if_not_exists => true);
CREATE INDEX IF NOT EXISTS agent_runs_subject_idx ON agent_runs(subject, started_at DESC);
CREATE TABLE IF NOT EXISTS agent_steps (
 run_id uuid NOT NULL,
 step_no smallint NOT NULL,
 created_at timestamptz NOT NULL,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 report_id uuid REFERENCES reports(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK (kind IN ('llm','tool','validation')),
 tool_name text,
 arguments jsonb,
 result jsonb,
 latency_ms integer,
 error_code text,
 PRIMARY KEY (run_id, step_no, created_at)
);
SELECT create_hypertable('agent_steps', 'created_at', chunk_time_interval => INTERVAL '7 days', if_not_exists => true);
CREATE INDEX IF NOT EXISTS agent_steps_tool_idx ON agent_steps(tool_name, created_at DESC);
SELECT add_retention_policy('agent_runs', INTERVAL '90 days', if_not_exists => true);
SELECT add_retention_policy('agent_steps', INTERVAL '90 days', if_not_exists => true);
-- Long-term memory the student stated themselves (docs/advisor-tools.md section 7); kept across reports.
CREATE TABLE IF NOT EXISTS student_memories (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 category text NOT NULL CHECK (category IN ('preference','goal','constraint','other')),
 content text NOT NULL CHECK (length(content) <= 500),
 report_id uuid REFERENCES reports(id) ON DELETE SET NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS student_memories_subject_idx ON student_memories(subject);
