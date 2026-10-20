-- Refresh tokens are opaque random values. Only their SHA-256 is stored, so a leaked table cannot be replayed.
-- Each login starts a family; every refresh revokes the presented row (ROTATED) and adds a new one to the family.
-- Presenting a rotated token again means it was copied: the whole family is revoked (REUSED).
CREATE TABLE refresh_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      UUID        NOT NULL,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(20),
    CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
