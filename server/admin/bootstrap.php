<?php
declare(strict_types=1);

require_once __DIR__ . '/../db.php';

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'self'");

date_default_timezone_set('UTC');

if (session_status() !== PHP_SESSION_ACTIVE) {
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/server/admin',
        'secure' => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

function ensureAdminSchema(PDO $pdo): void
{
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS admin_users (
            id SERIAL PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
    ");

    $pdo->exec("
        CREATE TABLE IF NOT EXISTS admin_login_attempts (
            id SERIAL PRIMARY KEY,
            email TEXT,
            ip TEXT,
            user_agent TEXT,
            attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            success BOOLEAN NOT NULL DEFAULT FALSE,
            honeypot_hit BOOLEAN NOT NULL DEFAULT FALSE
        );
    ");
}

function syncEnvAdmin(PDO $pdo): void
{
    $adminEmail = trim((string)(getenv('ADMIN_EMAIL') ?: ''));
    $adminHash = trim((string)(getenv('ADMIN_PASSWORD_HASH') ?: ''));

    if ($adminEmail === '' || $adminHash === '') {
        return;
    }

    $stmt = $pdo->prepare("
        INSERT INTO admin_users (email, password_hash, is_active, updated_at)
        VALUES (?, ?, TRUE, CURRENT_TIMESTAMP)
        ON CONFLICT (email)
        DO UPDATE SET
            password_hash = EXCLUDED.password_hash,
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP
    ");
    $stmt->execute([mb_strtolower($adminEmail), $adminHash]);
}

function clientIp(): string
{
    $forwarded = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
    if ($forwarded !== '') {
        $parts = explode(',', $forwarded);
        return trim($parts[0]);
    }
    return (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown');
}

function recordAttempt(PDO $pdo, string $email, bool $success, bool $honeypotHit = false): void
{
    $stmt = $pdo->prepare("
        INSERT INTO admin_login_attempts (email, ip, user_agent, success, honeypot_hit)
        VALUES (:email, :ip, :ua, :success, :honeypot)
    ");
    $stmt->bindValue(':email', mb_strtolower(trim($email)), PDO::PARAM_STR);
    $stmt->bindValue(':ip', clientIp(), PDO::PARAM_STR);
    $stmt->bindValue(':ua', substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 400), PDO::PARAM_STR);
    $stmt->bindValue(':success', $success, PDO::PARAM_BOOL);
    $stmt->bindValue(':honeypot', $honeypotHit, PDO::PARAM_BOOL);
    $stmt->execute();
}

function isRateLimited(PDO $pdo, string $email): bool
{
    $email = mb_strtolower(trim($email));
    $ip = clientIp();

    $stmt = $pdo->prepare("
        SELECT
            SUM(CASE WHEN ip = ? THEN 1 ELSE 0 END) AS ip_attempts,
            SUM(CASE WHEN email = ? AND ip = ? THEN 1 ELSE 0 END) AS email_ip_attempts,
            SUM(CASE WHEN email = ? THEN 1 ELSE 0 END) AS email_attempts
        FROM admin_login_attempts
        WHERE success = FALSE
          AND attempted_at > (CURRENT_TIMESTAMP - INTERVAL '15 minutes')
    ");
    $stmt->execute([$ip, $email, $ip, $email]);
    $row = $stmt->fetch() ?: [];

    $ipAttempts = (int)($row['ip_attempts'] ?? 0);
    $emailIpAttempts = (int)($row['email_ip_attempts'] ?? 0);
    $emailAttempts = (int)($row['email_attempts'] ?? 0);

    return $ipAttempts >= 25 || $emailIpAttempts >= 8 || $emailAttempts >= 12;
}

function csrfToken(): string
{
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return (string)$_SESSION['csrf_token'];
}

function validateCsrf(?string $token): bool
{
    $current = (string)($_SESSION['csrf_token'] ?? '');
    return $current !== '' && $token !== null && hash_equals($current, $token);
}

function requireAdmin(): void
{
    $ok = !empty($_SESSION['admin_auth'])
        && !empty($_SESSION['admin_email'])
        && !empty($_SESSION['admin_ua_hash']);

    $uaHash = hash('sha256', (string)($_SERVER['HTTP_USER_AGENT'] ?? ''));
    if (!$ok || !hash_equals((string)$_SESSION['admin_ua_hash'], $uaHash)) {
        session_unset();
        session_destroy();
        header('Location: /server/admin/login.php');
        exit;
    }
}

ensureAdminSchema($pdo);
syncEnvAdmin($pdo);
