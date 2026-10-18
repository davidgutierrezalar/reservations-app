-- btree_gist lets the exclusion constraint combine "=" on resource_id with "&&" on time ranges
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE resources (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type        VARCHAR(30)  NOT NULL,
    capacity    INT          NOT NULL CHECK (capacity > 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE reservations (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT      NOT NULL REFERENCES resources (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    -- Last line of defense against double booking, even under concurrent requests
    CONSTRAINT reservations_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status = 'CONFIRMED')
);

CREATE INDEX idx_reservations_user ON reservations (user_id);
