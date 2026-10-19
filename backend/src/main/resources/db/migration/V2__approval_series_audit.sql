-- Approval: admins flag expensive resources; bookings on them start as PENDING
ALTER TABLE resources ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_resources_capacity ON resources (capacity);

-- Recurring bookings: occurrences of one series share an id
ALTER TABLE reservations ADD COLUMN series_id UUID;
CREATE INDEX idx_reservations_series ON reservations (series_id) WHERE series_id IS NOT NULL;
CREATE INDEX idx_reservations_status ON reservations (status);

-- A pending request holds its slot too, otherwise an approval could create a double booking.
-- Exclusion constraints are checked on UPDATE as well, so edits get the same protection.
ALTER TABLE reservations DROP CONSTRAINT reservations_no_overlap;
ALTER TABLE reservations ADD CONSTRAINT reservations_no_overlap EXCLUDE USING gist (
    resource_id WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(20)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    action      VARCHAR(30)  NOT NULL,
    actor_id    BIGINT       NOT NULL REFERENCES users (id),
    details     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log (created_at DESC);
