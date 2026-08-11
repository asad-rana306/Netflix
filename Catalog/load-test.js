import http from 'k6/http';
import { check } from 'k6';

export const options = {
    discardResponseBodies: true,

    scenarios: {
        find_breaking_point: {
            executor: 'ramping-arrival-rate',
            startRate: 1000,            // Start at 1,000 req/sec
            timeUnit: '1s',
            preAllocatedVUs: 1000,      // Pre-allocate memory
            maxVUs: 4000,               // Cap max OS thread pool
            stages: [
                { duration: '15s', target: 2000 },  // Step 1: 2k RPS
                { duration: '30s', target: 5000 },  // Step 2: Push to 5k RPS
                { duration: '30s', target: 8000 },  // Step 3: Push to 8k RPS (Stress Phase)
                { duration: '15s', target: 0 },     // Ramp down
            ],
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8000/api/v1/catalog/titles';
const JWT_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhc2FkY29kZWNyYWZ0QGdtYWlsLmNvbSIsImlhdCI6MTc4NjAwOTUyNywiZXhwIjoxNzg2MDk1OTI3fQ.F9nSAn297O5PP644Xebjh2MZgKb75VHYcBegBEd_Z5P49JqWtPi7GRCCwNaR4AFpY9h7Ypn08knv3sXTW-VjPg';

export default function () {
    const randomPage = Math.floor(Math.random() * 50) + 1;
    const url = `${BASE_URL}?query=all&page=${randomPage}&size=20`;

    const res = http.get(url, {
        headers: {
            'Accept': 'application/json',
            'Accept-Encoding': 'gzip',
            'Authorization': `Bearer ${JWT_TOKEN}`,
        },
        tags: { name: 'GetCatalogTitles' },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}