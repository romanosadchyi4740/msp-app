-- Pending integration requests whose proposed tracks were all deleted before KAN-80; the cleanup now rejects them
UPDATE integration_requests r
SET status = 'REJECTED'
WHERE r.status = 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM integration_request_tracks t WHERE t.request_id = r.id);
