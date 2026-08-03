import http from 'k6/http';
import { check } from 'k6';

export const options = {
    stages: [
        { duration: '15s', target: 1000 }, // Rapid ramp-up to 1,000 users
        { duration: '30s', target: 3000 }, // Push to 3,000 users
        { duration: '30s', target: 5000 }, // Extreme Stress: 5,000 concurrent users!
        { duration: '15s', target: 0 },    // Ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'], // Alert if error rate exceeds 5%
    },
};

const BASE_URL = 'http://localhost:8083/api/v1/stream';

export default function () {
    // Generate a unique profile UUID per VU to test dynamic database indexing
    const vuId = String(__VU).padStart(12, '0');
    const profileId = `00000000-0000-0000-0000-${vuId}`;
    const titleId = "b2c3d4e5-0000-0000-0000-222233334444";

    const headers = {
        'X-User-Id': 'user-test-123',
        'Content-Type': 'application/json'
    };

    // 1. Fetch Manifest
    const manifestRes = http.get(`${BASE_URL}/hls/sample1/master.m3u8`, { headers });
    check(manifestRes, { 'manifest 200 OK': (r) => r.status === 200 });

    // 2. Fetch Segment
    const segmentRes = http.get(`${BASE_URL}/hls/sample1/segment_000.ts`, { headers });
    check(segmentRes, { 'segment 200 OK': (r) => r.status === 200 });

    // 3. Post Watch Progress (Direct DB Hit)
    const progressPayload = JSON.stringify({
        profileId: profileId,
        titleId: titleId,
        progressSeconds: Math.floor(Math.random() * 1200),
        durationSeconds: 3600
    });

    const progressRes = http.post(`${BASE_URL}/progress`, progressPayload, { headers });
    check(progressRes, { 'progress update 200 OK': (r) => r.status === 200 });

    // Removed sleep(1) to drive maximum continuous throughput to PostgreSQL
}