<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
requireAdmin();

$totals = $pdo->query("
    SELECT
        COUNT(*)::INT AS total_plays,
        COALESCE(AVG(completion_time), 0)::FLOAT AS avg_time,
        COALESCE(SUM(completion_time), 0)::FLOAT AS total_time
    FROM play_logs
")->fetch() ?: ['total_plays' => 0, 'avg_time' => 0, 'total_time' => 0];

$devices = $pdo->query("
    SELECT
        COUNT(*)::INT AS total_devices,
        COALESCE(SUM(puzzles_played), 0)::INT AS puzzles_played_sum
    FROM devices
")->fetch() ?: ['total_devices' => 0, 'puzzles_played_sum' => 0];

$topDevicesStmt = $pdo->query("
    SELECT device_id, puzzles_played, total_play_time_seconds, last_seen_at
    FROM devices
    ORDER BY puzzles_played DESC, total_play_time_seconds DESC
    LIMIT 20
");
$topDevices = $topDevicesStmt ? $topDevicesStmt->fetchAll() : [];
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Arrow Game Admin Dashboard</title>
    <style>
        body { margin: 0; font-family: Arial, sans-serif; background: #0b1020; color: #fff; }
        .container { max-width: 1100px; margin: 26px auto; padding: 0 16px; }
        .top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
        .cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
        .card { background: #111a33; border-radius: 12px; padding: 16px; }
        .label { color: #93c5fd; font-size: 13px; margin-bottom: 6px; }
        .value { font-size: 26px; font-weight: 700; }
        .panel { background: #111a33; border-radius: 12px; overflow: hidden; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #253454; }
        th { color: #bfdbfe; font-size: 13px; }
        .btn { background: #1d4ed8; color: #fff; border: 0; padding: 10px 12px; border-radius: 8px; font-weight: 700; cursor: pointer; }
        .small { color: #93a5c6; font-size: 12px; }
        @media (max-width: 900px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
        @media (max-width: 560px) { .cards { grid-template-columns: 1fr; } }
    </style>
</head>
<body>
<div class="container">
    <div class="top">
        <div>
            <h1>Arrow Game Stats</h1>
            <div class="small">Signed in as <?= htmlspecialchars((string)$_SESSION['admin_email'], ENT_QUOTES, 'UTF-8') ?></div>
        </div>
        <form method="post" action="<?= htmlspecialchars(adminUrl('logout.php'), ENT_QUOTES, 'UTF-8') ?>">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(csrfToken(), ENT_QUOTES, 'UTF-8') ?>">
            <button class="btn" type="submit">Logout</button>
        </form>
    </div>

    <div class="cards">
        <div class="card">
            <div class="label">Total Plays</div>
            <div class="value"><?= (int)$totals['total_plays'] ?></div>
        </div>
        <div class="card">
            <div class="label">Average Completion Time (s)</div>
            <div class="value"><?= number_format((float)$totals['avg_time'], 2) ?></div>
        </div>
        <div class="card">
            <div class="label">Unique Devices</div>
            <div class="value"><?= (int)$devices['total_devices'] ?></div>
        </div>
        <div class="card">
            <div class="label">Total Play Time (s)</div>
            <div class="value"><?= number_format((float)$totals['total_time'], 0) ?></div>
        </div>
    </div>

    <div class="panel">
        <table>
            <thead>
            <tr>
                <th>Device ID</th>
                <th>Puzzles Played</th>
                <th>Total Play Time (s)</th>
                <th>Last Seen</th>
            </tr>
            </thead>
            <tbody>
            <?php if (empty($topDevices)): ?>
                <tr><td colspan="4">No device stats available yet.</td></tr>
            <?php else: ?>
                <?php foreach ($topDevices as $row): ?>
                    <tr>
                        <td><?= htmlspecialchars((string)$row['device_id'], ENT_QUOTES, 'UTF-8') ?></td>
                        <td><?= (int)$row['puzzles_played'] ?></td>
                        <td><?= number_format((float)$row['total_play_time_seconds'], 2) ?></td>
                        <td><?= htmlspecialchars((string)$row['last_seen_at'], ENT_QUOTES, 'UTF-8') ?></td>
                    </tr>
                <?php endforeach; ?>
            <?php endif; ?>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>
