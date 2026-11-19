create index concurrently practice_sessions_user_started_idx
    on training.practice_sessions (user_id, started_at desc);
