<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !validateCsrf($_POST['csrf_token'] ?? null)) {
    http_response_code(400);
    echo 'Bad request';
    exit;
}

session_unset();
session_destroy();
header('Location: ' . adminUrl('login.php'));
exit;
