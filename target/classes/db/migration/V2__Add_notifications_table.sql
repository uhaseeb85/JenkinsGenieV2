-- Add notifications table for tracking sent notifications

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    notification_type VARCHAR(32) NOT NULL, -- SUCCESS, FAILURE, MANUAL_INTERVENTION
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'SENT' -- SENT, FAILED
);

-- Index for querying notifications by build
CREATE INDEX idx_notifications_build_id ON notifications(build_id);
CREATE INDEX idx_notifications_type ON notifications(notification_type);
CREATE INDEX idx_notifications_sent_at ON notifications(sent_at);