<?php
declare(strict_types=1);

/**
 * Railway production path:
 * - Uses PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD env vars (internal connection).
 *
 * Localhost path:
 * - If server/db.local.php exists, use it as public DB credentials for local testing.
 * - db.local.php must return an array with host, port, dbname, user, pass, sslmode.
 */
function isLocalRequest(): bool
{
    $host = strtolower((string)($_SERVER['HTTP_HOST'] ?? ''));
    $remote = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    return str_contains($host, 'localhost')
        || str_contains($host, '127.0.0.1')
        || $remote === '127.0.0.1'
        || $remote === '::1';
}

$cfg = [
    'host' => getenv('PGHOST') ?: 'localhost',
    'port' => getenv('PGPORT') ?: '5432',
    'dbname' => getenv('PGDATABASE') ?: 'railway',
    'user' => getenv('PGUSER') ?: 'postgres',
    'pass' => getenv('PGPASSWORD') ?: '',
    'sslmode' => getenv('PGSSLMODE') ?: 'require',
];

if (isLocalRequest()) {
    $localCfgFile = __DIR__ . '/db.local.php';
    if (is_file($localCfgFile)) {
        $localCfg = require $localCfgFile;
        if (is_array($localCfg)) {
            $cfg = array_merge($cfg, $localCfg);
        }
    }
}

try {
    $dsn = sprintf(
        'pgsql:host=%s;port=%s;dbname=%s;sslmode=%s',
        $cfg['host'],
        $cfg['port'],
        $cfg['dbname'],
        $cfg['sslmode']
    );
    $pdo = new PDO($dsn, (string)$cfg['user'], (string)$cfg['pass'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (PDOException $e) {
    header('Content-Type: application/json', true, 500);
    echo json_encode(['error' => 'Database connection failed: ' . $e->getMessage()]);
    exit;
}
