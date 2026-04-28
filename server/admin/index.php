<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

if (!empty($_SESSION['admin_auth'])) {
    header('Location: /server/admin/dashboard.php');
    exit;
}

header('Location: /server/admin/login.php');
exit;
