create index concurrently practice_sessions_active_idx
    on training.practice_sessions (user_id)
    where status = 'IN_PROGRESS';
