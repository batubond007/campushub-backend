-- COD-9 / COD-10 (US-02): the one active e-mail verification code per account.
-- A new code replaces the old one; the code itself is stored only as a SHA-256 hash.
CREATE TABLE otp_codes (
    user_id    BIGINT                   PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts   INT                      NOT NULL,
    sent_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
