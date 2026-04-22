-- ThreatSense 5G – Dashboard seed data (MySQL)
-- Run after the app has created tables (and at least once so admin user exists).
--
-- From project root:
--   mysql -u root -proot threatsense5g_db < src/main/resources/data/seed-dashboard-data.sql
-- Or paste/run in MySQL Workbench, DBeaver, etc. (database: threatsense5g_db)
--
-- This script uses variables so it works on an empty DB or one that already has rows.
-- You get: 24 traffic records, 25 threat detections (last 7 days), 11 alerts → charts and tables fill.

SET FOREIGN_KEY_CHECKS = 0;

-- ========== 1. NETWORK TRAFFIC ==========
INSERT INTO network_traffic (timestamp, src_ip, dst_ip, protocol, packet_size, packet_count, duration_ms, slice_type, upload_source, processed) VALUES
('2026-02-21 08:15:00', '10.0.1.101', '10.0.2.50', 'TCP', 1500, 100, 45, 'eMBB', 'pcap_upload', 1),
('2026-02-21 09:30:00', '10.0.1.102', '10.0.2.51', 'UDP', 512, 200, 120, 'URLLC', 'pcap_upload', 1),
('2026-02-21 10:45:00', '192.168.1.20', '192.168.2.10', 'TCP', 1400, 85, 60, 'mMTC', 'live_capture', 1),
('2026-02-22 07:00:00', '10.0.1.101', '10.0.2.52', 'TCP', 1500, 150, 90, 'eMBB', 'pcap_upload', 1),
('2026-02-22 11:20:00', '10.0.1.103', '10.0.2.50', 'ICMP', 64, 50, 10, 'URLLC', 'pcap_upload', 1),
('2026-02-22 14:00:00', '192.168.1.21', '192.168.2.11', 'TCP', 1200, 300, 200, 'mMTC', 'live_capture', 1),
('2026-02-23 08:30:00', '10.0.1.101', '10.0.2.53', 'UDP', 800, 180, 75, 'eMBB', 'pcap_upload', 1),
('2026-02-23 12:00:00', '10.0.1.104', '10.0.2.51', 'TCP', 1500, 220, 110, 'eMBB', 'pcap_upload', 1),
('2026-02-23 15:45:00', '192.168.1.20', '192.168.2.10', 'TCP', 1400, 95, 55, 'URLLC', 'live_capture', 1),
('2026-02-24 09:00:00', '10.0.1.102', '10.0.2.50', 'TCP', 1500, 130, 80, 'mMTC', 'pcap_upload', 1),
('2026-02-24 13:30:00', '10.0.1.105', '10.0.2.54', 'UDP', 400, 500, 250, 'eMBB', 'pcap_upload', 1),
('2026-02-24 16:00:00', '192.168.1.22', '192.168.2.12', 'TCP', 1500, 70, 40, 'URLLC', 'live_capture', 1),
('2026-02-25 08:00:00', '10.0.1.101', '10.0.2.55', 'TCP', 1400, 160, 95, 'eMBB', 'pcap_upload', 1),
('2026-02-25 10:15:00', '10.0.1.103', '10.0.2.51', 'TCP', 1500, 90, 50, 'mMTC', 'pcap_upload', 1),
('2026-02-25 14:30:00', '192.168.1.20', '192.168.2.10', 'UDP', 600, 400, 180, 'eMBB', 'live_capture', 1),
('2026-02-26 07:45:00', '10.0.1.106', '10.0.2.50', 'TCP', 1500, 110, 65, 'URLLC', 'pcap_upload', 1),
('2026-02-26 11:00:00', '10.0.1.102', '10.0.2.56', 'TCP', 1200, 250, 130, 'eMBB', 'pcap_upload', 1),
('2026-02-26 15:20:00', '192.168.1.21', '192.168.2.11', 'TCP', 1500, 88, 48, 'mMTC', 'live_capture', 1),
('2026-02-27 08:20:00', '10.0.1.101', '10.0.2.57', 'TCP', 1500, 140, 72, 'eMBB', 'pcap_upload', 1),
('2026-02-27 10:00:00', '10.0.1.104', '10.0.2.51', 'UDP', 900, 320, 160, 'URLLC', 'pcap_upload', 1),
('2026-02-27 12:30:00', '192.168.1.20', '192.168.2.10', 'TCP', 1400, 75, 42, 'mMTC', 'live_capture', 1),
('2026-02-27 14:00:00', '10.0.1.107', '10.0.2.58', 'TCP', 1500, 190, 100, 'eMBB', 'pcap_upload', 1),
('2026-02-28 09:10:00', '10.0.1.101', '10.0.2.59', 'TCP', 1500, 165, 88, 'eMBB', 'pcap_upload', 1),
('2026-02-28 11:45:00', '10.0.1.102', '10.0.2.50', 'UDP', 512, 280, 140, 'URLLC', 'pcap_upload', 1),
('2026-02-28 13:00:00', '192.168.1.22', '192.168.2.12', 'TCP', 1500, 92, 52, 'mMTC', 'live_capture', 1);

SET @tid = LAST_INSERT_ID();

-- ========== 2. THREAT DETECTIONS (linked to traffic above) ==========
INSERT INTO threat_detections (traffic_id, threat_type, severity, confidence_score, model_used, explanation, detected_at) VALUES
(@tid,   'NORMAL', 'LOW', 0.95, 'baseline', 'Normal pattern', '2026-02-21 08:16:00'),
(@tid+1, 'DDOS', 'HIGH', 0.88, 'ml_v1', 'High packet rate', '2026-02-21 09:31:00'),
(@tid+2, 'NORMAL', 'LOW', 0.92, 'baseline', 'Normal', '2026-02-21 10:46:00'),
(@tid+3, 'INTRUSION', 'CRITICAL', 0.91, 'ml_v1', 'Suspicious port scan', '2026-02-22 07:01:00'),
(@tid+4, 'ANOMALY', 'MEDIUM', 0.78, 'ml_v1', 'Unusual ICMP burst', '2026-02-22 11:21:00'),
(@tid+5, 'NORMAL', 'LOW', 0.94, 'baseline', 'Normal', '2026-02-22 14:01:00'),
(@tid+6, 'DDOS', 'HIGH', 0.85, 'ml_v1', 'Flood pattern', '2026-02-23 08:31:00'),
(@tid+7, 'NORMAL', 'LOW', 0.96, 'baseline', 'Normal', '2026-02-23 12:01:00'),
(@tid+8, 'INTRUSION', 'MEDIUM', 0.82, 'ml_v1', 'Probe activity', '2026-02-23 15:46:00'),
(@tid+9, 'ANOMALY', 'MEDIUM', 0.80, 'ml_v1', 'Traffic spike', '2026-02-24 09:01:00'),
(@tid+10, 'DDOS', 'CRITICAL', 0.93, 'ml_v1', 'DDoS signature', '2026-02-24 13:31:00'),
(@tid+11, 'NORMAL', 'LOW', 0.90, 'baseline', 'Normal', '2026-02-24 16:01:00'),
(@tid+12, 'NORMAL', 'LOW', 0.97, 'baseline', 'Normal', '2026-02-25 08:01:00'),
(@tid+13, 'INTRUSION', 'HIGH', 0.87, 'ml_v1', 'Brute-force attempt', '2026-02-25 10:16:00'),
(@tid+14, 'ANOMALY', 'LOW', 0.75, 'ml_v1', 'Minor deviation', '2026-02-25 14:31:00'),
(@tid+15, 'NORMAL', 'LOW', 0.93, 'baseline', 'Normal', '2026-02-26 07:46:00'),
(@tid+16, 'DDOS', 'MEDIUM', 0.81, 'ml_v1', 'Elevated rate', '2026-02-26 11:01:00'),
(@tid+17, 'NORMAL', 'LOW', 0.95, 'baseline', 'Normal', '2026-02-26 15:21:00'),
(@tid+18, 'INTRUSION', 'CRITICAL', 0.89, 'ml_v1', 'Malicious payload', '2026-02-27 08:21:00'),
(@tid+19, 'NORMAL', 'LOW', 0.94, 'baseline', 'Normal', '2026-02-27 10:01:00'),
(@tid+20, 'ANOMALY', 'MEDIUM', 0.77, 'ml_v1', 'Timing anomaly', '2026-02-27 12:31:00'),
(@tid+21, 'DDOS', 'HIGH', 0.86, 'ml_v1', 'Syn flood', '2026-02-27 14:01:00'),
(@tid+22, 'NORMAL', 'LOW', 0.91, 'baseline', 'Normal', '2026-02-28 09:11:00'),
(@tid+23, 'INTRUSION', 'HIGH', 0.84, 'ml_v1', 'Scanning activity', '2026-02-28 11:46:00'),
(@tid+23, 'ANOMALY', 'LOW', 0.72, 'ml_v1', 'Minor anomaly', '2026-02-28 13:01:00');

SET @did = LAST_INSERT_ID();

-- ========== 3. ALERTS (linked to detections above: 1st, 2nd, 4th, 5th, 7th, 9th, 11th, 14th, 19th, 22nd, 24th) ==========
INSERT INTO alerts (detection_id, status, assigned_to_id, notes, email_sent, created_at, resolved_at) VALUES
(@did,     'RESOLVED', NULL, 'False positive', 0, '2026-02-21 08:17:00', '2026-02-21 09:00:00'),
(@did+1,   'RESOLVED', NULL, 'Mitigated', 1, '2026-02-21 09:32:00', '2026-02-21 10:00:00'),
(@did+3,   'ACKNOWLEDGED', NULL, 'Under review', 1, '2026-02-22 07:02:00', NULL),
(@did+4,   'OPEN', NULL, NULL, 0, '2026-02-22 11:22:00', NULL),
(@did+6,   'RESOLVED', NULL, 'Blocked', 1, '2026-02-23 08:32:00', '2026-02-23 12:00:00'),
(@did+8,   'INVESTIGATING', NULL, 'Tracing source', 0, '2026-02-23 15:47:00', NULL),
(@did+10,  'OPEN', NULL, NULL, 1, '2026-02-24 13:32:00', NULL),
(@did+13,  'ACKNOWLEDGED', NULL, NULL, 0, '2026-02-25 10:17:00', NULL),
(@did+18,  'OPEN', NULL, 'Critical - escalate', 1, '2026-02-27 08:22:00', NULL),
(@did+21,  'OPEN', NULL, NULL, 0, '2026-02-27 14:02:00', NULL),
(@did+23,  'OPEN', NULL, NULL, 0, '2026-02-28 11:47:00', NULL);

SET FOREIGN_KEY_CHECKS = 1;
