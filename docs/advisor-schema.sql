-- LionPathway advisor schema (draft)
-- Catalog facts + user state, evaluated by a rule engine and exposed to the LLM as tools.
-- Not wired into DatabaseConfig yet; the live profile still runs only resources/schema.sql.
-- Section 11 needs the timescaledb extension (preinstalled on Tiger Cloud services).

-- ============================================================
-- 1. Reference: catalog years, terms, grades
-- ============================================================

CREATE TABLE IF NOT EXISTS catalog_years (
 code text PRIMARY KEY,                       -- '2025-26'
 starts_on date NOT NULL,
 ends_on date NOT NULL
);

CREATE TABLE IF NOT EXISTS terms (
 code text PRIMARY KEY,                       -- 'FA26', 'SP27', 'SU27'
 season text NOT NULL CHECK (season IN ('FA','SP','SU')),
 year smallint NOT NULL,
 starts_on date NOT NULL,
 ends_on date NOT NULL,
 catalog_year text NOT NULL REFERENCES catalog_years(code),
 UNIQUE (season, year)
);

-- Ordered grade scale so "min grade C" is a simple rank comparison.
CREATE TABLE IF NOT EXISTS grades (
 grade text PRIMARY KEY,                      -- 'A', 'A-', ..., 'D', 'F', 'P', 'TR'
 rank smallint NOT NULL,                      -- higher is better; non-letter grades get their own rank
 points numeric(2,1),                         -- null for P/TR/W; null grades are left out of GPA
 earns_credit boolean NOT NULL
);

-- ============================================================
-- 2. Catalog: courses and prerequisites
-- ============================================================

CREATE TABLE IF NOT EXISTS courses (
 code text PRIMARY KEY,                       -- 'STAT 414'
 subject text NOT NULL,                       -- 'STAT'
 number text NOT NULL,                        -- '414', '497A'
 level smallint NOT NULL,                     -- 400; derived from number when seeding
 UNIQUE (subject, number)
);
CREATE INDEX IF NOT EXISTS courses_subject_level_idx ON courses(subject, level);

-- Title, credits and description can change between catalog years.
CREATE TABLE IF NOT EXISTS course_versions (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 course_code text NOT NULL REFERENCES courses(code),
 catalog_year text NOT NULL REFERENCES catalog_years(code),
 title text NOT NULL,
 credits_min numeric(3,1) NOT NULL,
 credits_max numeric(3,1) NOT NULL,
 description text,
 prereq_text text,                            -- original bulletin wording, kept for citation
 repeatable boolean NOT NULL DEFAULT false,
 typical_terms text[],                        -- {FA,SP}; used to predict terms that are not collected yet
 source_url text,
 UNIQUE (course_code, catalog_year),
 CHECK (credits_min <= credits_max),
 CHECK (typical_terms <@ ARRAY['FA','SP','SU'])
);

-- GenEd and other attributes: GN, GH, GS, GQ, GWS, GHW, US, IL, WCC, ...
CREATE TABLE IF NOT EXISTS course_attributes (
 course_version_id bigint NOT NULL REFERENCES course_versions(id) ON DELETE CASCADE,
 attribute text NOT NULL,
 PRIMARY KEY (course_version_id, attribute)
);
CREATE INDEX IF NOT EXISTS course_attributes_attr_idx ON course_attributes(attribute);

-- Cross-listed courses count as the same course for audits.
CREATE TABLE IF NOT EXISTS course_crosslists (
 course_code text NOT NULL REFERENCES courses(code),
 crosslisted_code text NOT NULL REFERENCES courses(code),
 PRIMARY KEY (course_code, crosslisted_code),
 CHECK (course_code < crosslisted_code)
);

-- Prerequisite expression tree, one tree per course version.
--   ALL/ANY  combine children
--   COURSE   required_course completed before the term (or concurrently when concurrent_ok)
--   COREQ    required_course completed before or taken in the same term
--   STANDING minimum semester standing
--   TEXT     wording the parser could not structure; the engine reports unknown instead of guessing
CREATE TABLE IF NOT EXISTS prereq_nodes (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 course_version_id bigint NOT NULL REFERENCES course_versions(id) ON DELETE CASCADE,
 parent_id bigint REFERENCES prereq_nodes(id) ON DELETE CASCADE,
 sort_order smallint NOT NULL DEFAULT 0,
 node_type text NOT NULL CHECK (node_type IN ('ALL','ANY','COURSE','COREQ','STANDING','TEXT')),
 required_course text REFERENCES courses(code),
 min_grade text REFERENCES grades(grade),
 concurrent_ok boolean NOT NULL DEFAULT false,
 min_semester smallint,
 raw_text text,
 CHECK (node_type NOT IN ('COURSE','COREQ') OR required_course IS NOT NULL),
 CHECK (node_type <> 'STANDING' OR min_semester IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS prereq_nodes_version_idx ON prereq_nodes(course_version_id);
-- Reverse lookup: "what does STAT 200 unlock?"
CREATE INDEX IF NOT EXISTS prereq_nodes_required_idx ON prereq_nodes(required_course);

-- ============================================================
-- 3. Catalog: programs and requirement trees
-- ============================================================

-- program_type 'entrance' holds Entrance to Major requirements; entrance_for names the major it gates.
CREATE TABLE IF NOT EXISTS programs (
 code text PRIMARY KEY,                       -- 'DATSC_BS', 'STAT_MINOR', 'GENED', 'DATSC_ETM'
 name text NOT NULL,
 program_type text NOT NULL CHECK (program_type IN ('major','minor','certificate','gened','university','entrance')),
 college text,
 entrance_for text REFERENCES programs(code),
 CHECK ((program_type = 'entrance') = (entrance_for IS NOT NULL))
);

-- Students follow the requirements of the catalog year they entered under.
CREATE TABLE IF NOT EXISTS program_versions (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 program_code text NOT NULL REFERENCES programs(code),
 catalog_year text NOT NULL REFERENCES catalog_years(code),
 min_total_credits numeric(4,1),
 source_url text,
 UNIQUE (program_code, catalog_year)
);

CREATE TABLE IF NOT EXISTS program_options (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 program_version_id bigint NOT NULL REFERENCES program_versions(id) ON DELETE CASCADE,
 code text NOT NULL,                          -- 'APPLIED', 'COMPUTATIONAL', 'STATISTICAL'
 name text NOT NULL,
 UNIQUE (program_version_id, code)
);

-- Requirement tree. option_id null = common to every option.
--   GROUP_ALL : every child must be satisfied
--   GROUP_ANY : at least min_count children must be satisfied
--   POOL      : courses matching requirement_rules, satisfied by min_count and/or min_credits
--   GPA_MIN   : GPA over courses matching requirement_rules (no rules = all graded courses) >= min_gpa
-- A single required course is a POOL with one COURSE rule and min_count 1.
CREATE TABLE IF NOT EXISTS requirement_nodes (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 program_version_id bigint NOT NULL REFERENCES program_versions(id) ON DELETE CASCADE,
 option_id bigint REFERENCES program_options(id) ON DELETE CASCADE,
 parent_id bigint REFERENCES requirement_nodes(id) ON DELETE CASCADE,
 sort_order smallint NOT NULL DEFAULT 0,
 label text NOT NULL,                         -- 'Prescribed Courses', 'Supporting Courses'
 node_type text NOT NULL CHECK (node_type IN ('GROUP_ALL','GROUP_ANY','POOL','GPA_MIN')),
 min_count smallint,
 min_credits numeric(4,1),
 min_grade text REFERENCES grades(grade),
 min_gpa numeric(3,2),
 exclusive boolean NOT NULL DEFAULT true,     -- false = a course used here may also count elsewhere
 source_text text,                            -- bulletin wording, kept for citation
 CHECK (node_type <> 'GROUP_ANY' OR min_count IS NOT NULL),
 CHECK (node_type <> 'POOL' OR min_count IS NOT NULL OR min_credits IS NOT NULL),
 CHECK (node_type <> 'GPA_MIN' OR min_gpa IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS requirement_nodes_program_idx ON requirement_nodes(program_version_id, parent_id);

-- Filters for POOL and GPA_MIN nodes. Within one row the non-null filters are ANDed;
-- a course matches if it matches any include row and no exclude row.
--   ('STAT', null, 400, 499) = any 400-level STAT course
--   (null, 'GQ', null, null) = any GQ course
CREATE TABLE IF NOT EXISTS requirement_rules (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 node_id bigint NOT NULL REFERENCES requirement_nodes(id) ON DELETE CASCADE,
 include boolean NOT NULL DEFAULT true,
 course_code text REFERENCES courses(code),
 subject text,
 attribute text,
 level_min smallint,
 level_max smallint,
 CHECK (course_code IS NOT NULL OR subject IS NOT NULL OR attribute IS NOT NULL OR level_min IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS requirement_rules_node_idx ON requirement_rules(node_id);
CREATE INDEX IF NOT EXISTS requirement_rules_course_idx ON requirement_rules(course_code);

-- Enrollment restrictions printed with a course ("DS majors only", "5th semester or higher").
-- include = true: only students matching may enroll; false: students matching may not.
CREATE TABLE IF NOT EXISTS course_restrictions (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 course_version_id bigint NOT NULL REFERENCES course_versions(id) ON DELETE CASCADE,
 restriction_type text NOT NULL CHECK (restriction_type IN ('program','college','min_semester','campus','text')),
 include boolean NOT NULL DEFAULT true,
 program_code text REFERENCES programs(code),
 college text,
 min_semester smallint,
 campus text,
 raw_text text,
 CHECK (restriction_type <> 'program' OR program_code IS NOT NULL),
 CHECK (restriction_type <> 'college' OR college IS NOT NULL),
 CHECK (restriction_type <> 'min_semester' OR min_semester IS NOT NULL),
 CHECK (restriction_type <> 'campus' OR campus IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS course_restrictions_version_idx ON course_restrictions(course_version_id);

-- ============================================================
-- 4. Offerings (collected per term)
-- ============================================================

CREATE TABLE IF NOT EXISTS course_offerings (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 term_code text NOT NULL REFERENCES terms(code),
 course_code text NOT NULL REFERENCES courses(code),
 campus text NOT NULL,                        -- 'UP', 'WORLD', ...
 section text NOT NULL,
 instructor text,
 meetings jsonb,                              -- [{"days":"MWF","start":"10:10","end":"11:00"}]
 capacity smallint,
 enrolled smallint,
 fetched_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (term_code, course_code, campus, section)
);
CREATE INDEX IF NOT EXISTS course_offerings_course_idx ON course_offerings(course_code, term_code);

-- ============================================================
-- 5. Credit by exam and transfer equivalencies
-- ============================================================

CREATE TABLE IF NOT EXISTS credit_sources (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 kind text NOT NULL CHECK (kind IN ('exam','institution')),
 code text NOT NULL UNIQUE,                   -- 'AP', 'IB', 'CLEP', or an institution code
 name text NOT NULL,
 location text
);

-- One row per accepted source course or exam score band.
-- psu_course_code is null for generic credit; psu_label always holds what is awarded ('MATH 140', 'STAT 2XX').
CREATE TABLE IF NOT EXISTS credit_equivalencies (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 source_id bigint NOT NULL REFERENCES credit_sources(id) ON DELETE CASCADE,
 source_item text NOT NULL,                   -- 'Calculus BC' or 'MATH 201'
 min_score numeric(4,1),                      -- exams only
 max_score numeric(4,1),
 psu_course_code text REFERENCES courses(code),
 psu_label text NOT NULL,
 credits numeric(3,1) NOT NULL,
 attributes text[],                           -- GenEd attributes awarded with generic credit
 effective_from date,
 effective_to date,
 source_url text,
 fetched_at timestamptz NOT NULL DEFAULT now(),
 CHECK (min_score IS NULL OR max_score IS NULL OR min_score <= max_score)
);
CREATE INDEX IF NOT EXISTS credit_equivalencies_lookup_idx ON credit_equivalencies(source_id, source_item);

-- ============================================================
-- 6. University policy: numeric rules and academic calendar
-- ============================================================

-- Numeric or structured rules the engine reads instead of hard-coding, e.g.
--   'semester_classification' -> [{"min_credits":0,"semester":1}, ...]
--   'max_credits_without_approval' -> {"credits":19}
-- scope_code is null for university-wide rules, otherwise a college or program code.
-- More specific scopes override broader ones.
CREATE TABLE IF NOT EXISTS policy_rules (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 key text NOT NULL,
 catalog_year text NOT NULL REFERENCES catalog_years(code),
 scope text NOT NULL CHECK (scope IN ('university','college','program')),
 scope_code text,
 value jsonb NOT NULL,
 description text NOT NULL,
 policy_ref text,                             -- policy number or section title
 source_url text NOT NULL,
 fetched_at timestamptz NOT NULL DEFAULT now(),
 CHECK ((scope = 'university') = (scope_code IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS policy_rules_key_idx ON policy_rules(key, catalog_year, scope, coalesce(scope_code, ''));

CREATE TABLE IF NOT EXISTS academic_calendar (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 term_code text NOT NULL REFERENCES terms(code),
 event_key text NOT NULL,                     -- 'regular_add', 'regular_drop', 'late_drop', 'withdrawal', 'graduation_application'
 label text NOT NULL,
 starts_on date,
 ends_on date NOT NULL,                       -- the deadline
 campus text,                                 -- null = all campuses
 source_url text NOT NULL,
 fetched_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS academic_calendar_event_idx ON academic_calendar(term_code, event_key, coalesce(campus, ''));

-- ============================================================
-- 7. User state
-- ============================================================

CREATE TABLE IF NOT EXISTS app_users (
 subject text PRIMARY KEY,
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS student_profiles (
 subject text PRIMARY KEY REFERENCES app_users(subject) ON DELETE CASCADE,
 catalog_year text REFERENCES catalog_years(code),
 entry_term text REFERENCES terms(code),
 expected_grad_term text REFERENCES terms(code),
 campus text,
 college text,
 max_credits_per_term numeric(3,1) NOT NULL DEFAULT 18,
 updated_at timestamptz NOT NULL DEFAULT now()
);

-- declared = on record, intended = what the student is aiming for, exploring = what-if only.
CREATE TABLE IF NOT EXISTS student_programs (
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 program_version_id bigint NOT NULL REFERENCES program_versions(id),
 option_id bigint REFERENCES program_options(id),
 status text NOT NULL CHECK (status IN ('declared','intended','exploring')),
 added_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (subject, program_version_id)
);

-- Academic record. The What-If PDF is parsed once into these rows; afterwards the student edits them directly.
-- Planned courses live in plan_courses, not here.
CREATE TABLE IF NOT EXISTS student_courses (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 course_code text REFERENCES courses(code),   -- null for unmatched transfer credit
 term_code text REFERENCES terms(code),
 status text NOT NULL CHECK (status IN ('completed','in_progress','transfer','exam')),
 grade text REFERENCES grades(grade),
 credits numeric(3,1) NOT NULL,
 raw_label text,                              -- as printed on the report, e.g. 'STAT 2XX'
 equivalency_id bigint REFERENCES credit_equivalencies(id),
 source text NOT NULL CHECK (source IN ('pdf','manual')),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS student_courses_subject_idx ON student_courses(subject);

-- Adviser-approved substitutions and waivers that the catalog cannot express.
CREATE TABLE IF NOT EXISTS student_exceptions (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 node_id bigint NOT NULL REFERENCES requirement_nodes(id),
 kind text NOT NULL CHECK (kind IN ('substitute','waive')),
 student_course_id bigint REFERENCES student_courses(id) ON DELETE CASCADE,
 note text,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (kind <> 'substitute' OR student_course_id IS NOT NULL)
);

-- ============================================================
-- 8. Plans
-- ============================================================

CREATE TABLE IF NOT EXISTS plans (
 id uuid PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 name text NOT NULL,
 is_primary boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS plans_one_primary_idx ON plans(subject) WHERE is_primary;

CREATE TABLE IF NOT EXISTS plan_courses (
 plan_id uuid NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
 term_code text NOT NULL REFERENCES terms(code),
 course_code text NOT NULL REFERENCES courses(code),
 PRIMARY KEY (plan_id, term_code, course_code)
);

-- ============================================================
-- 9. Audit cache
-- ============================================================

-- Rule engine output, keyed by a hash of its inputs (record + plan + program + catalog version).
-- Recomputed whenever the hash changes; the agent reads this instead of recalculating per message.
CREATE TABLE IF NOT EXISTS audit_snapshots (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 program_version_id bigint NOT NULL REFERENCES program_versions(id),
 plan_id uuid REFERENCES plans(id) ON DELETE CASCADE,  -- null = record only
 input_hash text NOT NULL,
 result jsonb NOT NULL,                       -- per-node status, applied courses, remaining credits
 computed_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS audit_snapshots_key_idx
 ON audit_snapshots(subject, program_version_id, coalesce(plan_id, '00000000-0000-0000-0000-000000000000'::uuid), input_hash);

-- ============================================================
-- 10. Conversations and long-term memory
-- ============================================================

CREATE TABLE IF NOT EXISTS conversations (
 id uuid PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 title text,
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversation_messages (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 role text NOT NULL CHECK (role IN ('user','assistant','tool')),
 content text,
 tool_name text,
 tool_payload jsonb,                          -- tool call arguments or result
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversation_messages_conv_idx ON conversation_messages(conversation_id, id);

-- Facts that outlive a conversation ("avoids 8am classes", "aiming for data engineering").
-- Loaded into context every turn; the student can view and delete them.
CREATE TABLE IF NOT EXISTS student_memories (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 category text NOT NULL CHECK (category IN ('preference','goal','constraint','other')),
 content text NOT NULL CHECK (length(content) <= 500),
 source_message_id bigint REFERENCES conversation_messages(id) ON DELETE SET NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS student_memories_subject_idx ON student_memories(subject);

-- ============================================================
-- 11. Agent run log (TimescaleDB hypertables)
-- ============================================================
-- Written by the server for every turn, not exposed as tools.
-- Hypertable unique keys must include the time column, and agent_steps links to agent_runs by
-- run_id without a foreign key because foreign keys that reference hypertables depend on the version.

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS agent_runs (
 id uuid NOT NULL,
 started_at timestamptz NOT NULL,
 subject text NOT NULL REFERENCES app_users(subject) ON DELETE CASCADE,
 conversation_id uuid REFERENCES conversations(id) ON DELETE CASCADE,
 model text NOT NULL,
 status text NOT NULL CHECK (status IN ('ok','error','rate_limited','validation_failed')),
 latency_ms integer,
 llm_calls smallint NOT NULL DEFAULT 0,
 tool_calls smallint NOT NULL DEFAULT 0,
 input_tokens integer,
 output_tokens integer,
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

-- Logs hold academic records and questions, so they expire.
SELECT add_retention_policy('agent_runs', INTERVAL '90 days', if_not_exists => true);
SELECT add_retention_policy('agent_steps', INTERVAL '90 days', if_not_exists => true);

-- Optional: daily tool latency and error rate for a dashboard.
-- CREATE MATERIALIZED VIEW IF NOT EXISTS agent_tool_daily WITH (timescaledb.continuous) AS
--  SELECT time_bucket(INTERVAL '1 day', created_at) AS day, tool_name,
--         count(*) AS calls, count(error_code) AS errors, avg(latency_ms) AS avg_latency_ms
--  FROM agent_steps WHERE kind = 'tool' GROUP BY day, tool_name WITH NO DATA;
-- SELECT add_continuous_aggregate_policy('agent_tool_daily', start_offset => INTERVAL '3 days',
--  end_offset => INTERVAL '1 hour', schedule_interval => INTERVAL '1 hour', if_not_exists => true);

-- ============================================================
-- 12. Policy text for retrieval (optional, needs pgvector)
-- ============================================================
-- Prose policies that do not fit the tables above (repeats, pass/fail, academic standing, ...).
-- Uncomment once the vector extension is enabled on the TigerData service.
--
-- CREATE EXTENSION IF NOT EXISTS vector;
-- CREATE TABLE IF NOT EXISTS policy_chunks (
--  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
--  catalog_year text REFERENCES catalog_years(code),
--  scope text NOT NULL CHECK (scope IN ('university','college','program')),
--  scope_code text,                                   -- null for university-wide
--  policy_ref text,                                   -- policy number or section title
--  title text NOT NULL,
--  heading text,
--  content text NOT NULL,
--  effective_on date,
--  source_url text NOT NULL,
--  fetched_at timestamptz NOT NULL DEFAULT now(),
--  embedding vector(768) NOT NULL,
--  CHECK ((scope = 'university') = (scope_code IS NULL))
-- );
