CREATE TABLE IF NOT EXISTS puzzles (
    seed INT PRIMARY KEY,
    difficulty INT DEFAULT 1,
    completions INT DEFAULT 0,
    avg_time_seconds FLOAT DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS play_logs (
    id SERIAL PRIMARY KEY,
    seed INT REFERENCES puzzles(seed),
    completion_time FLOAT,
    device_id TEXT,
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
