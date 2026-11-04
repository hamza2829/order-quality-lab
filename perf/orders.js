import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const orderRequests = new Counter('order_requests');
const orderErrors = new Rate('order_errors');
const orderLatency = new Trend('order_latency', true);
const base = __ENV.ORDER_URL || 'http://localhost:8080';
const inventory = __ENV.INVENTORY_URL || 'http://localhost:8081';
const thresholds = { order_errors: ['rate<0.01'], checks: ['rate>0.99'] };
if (__ENV.LATENCY_BUDGET_MS) thresholds.order_latency = [`p(95)<${__ENV.LATENCY_BUDGET_MS}`];
export const options = {
  stages: [{ duration: '10s', target: 2 }, { duration: '15s', target: 8 }, { duration: '15s', target: 16 }, { duration: '10s', target: 0 }],
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};
const headers = { 'Content-Type': 'application/json' };
export function setup() {
  const seed = http.put(`${inventory}/api/stock/PERF`, JSON.stringify({ available: 1000000000 }), { headers });
  if (seed.status !== 200) throw new Error('Stock setup failed');
  const rejected = http.post(`${base}/api/orders`, JSON.stringify({ lines: [{ sku: 'PERF-EMPTY', quantity: 1, unitPrice: 12.50, currency: 'EUR' }] }), { headers });
  if (rejected.status !== 409) throw new Error('Rejected order probe failed');
}
export default function () {
  const response = http.post(`${base}/api/orders`, JSON.stringify({ lines: [{ sku: 'PERF', quantity: 1, unitPrice: 12.50, currency: 'EUR' }] }), { headers, tags: { name: 'Place order' } });
  const success = check(response, { 'order placed': r => r.status === 201 && r.json('status') === 'PLACED' });
  orderRequests.add(1); orderErrors.add(!success); orderLatency.add(response.timings.duration);
  if (success) {
    const read = http.get(`${base}/api/orders/${response.json('id')}`, { tags: { name: 'Read order' } });
    check(read, { 'order readable': r => r.status === 200 && r.json('status') === 'PLACED' });
  }
  sleep(0.1);
}
