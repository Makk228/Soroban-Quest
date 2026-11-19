create index concurrently refresh_tokens_user_idx on identity.refresh_tokens (user_id) where revoked_at is null;
