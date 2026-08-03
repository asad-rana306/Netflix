import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    discardResponseBodies: true,

    stages: [
        { duration: '5s',  target: 1 },    // 1. Warmup stage: Populates Redis cache smoothly
        { duration: '15s', target: 2500 }, // 2. Spike to 2,500 VUs against WARM Redis cache
        { duration: '20s', target: 2500 }, // 3. Hold load
        { duration: '5s',  target: 0 },    // 4. Ramp down
    ],

    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8082/api/v1/catalog/titles';

export default function () {
    // 🎲 Select pages 1 to 50 with size=20 (matching your Redis cache key format)
    const randomPage = Math.floor(Math.random() * 50) + 1;
    const url = `${BASE_URL}?query=all&page=${randomPage}&size=20`;

    const res = http.get(url, {
        headers: {
            'Accept': 'application/json',
            'Accept-Encoding': 'gzip',
        },
        tags: { name: 'GetCatalogTitles' },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(Math.random() * 0.5 + 0.5);
}