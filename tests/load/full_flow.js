import http from "k6/http";
import { check, sleep } from "k6";
export const options = {
  stages: __ENV.SMOKE
    ? [{ duration: "20s", target: 2 }]
    : [
        { duration: "30s", target: 20 },
        { duration: "1m", target: 20 },
        { duration: "30s", target: 0 },
      ],
  thresholds: {
    "http_req_duration{name:request-trip}": ["p(95)<800"],
    "http_req_duration{name:get-status}": ["p(95)<200"],
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "med", "p(95)", "max"],
};
const BASE = __ENV.BASE || "http://localhost:8080";
export function setup() {
  const email = `load-${Date.now()}@taasim.demo`,
    password = "LoadPassword123!";
  const headers = { "Content-Type": "application/json" };
  const register = http.post(
    BASE + "/api/auth/register",
    JSON.stringify({ email, password, fullName: "Load test", role: "CLIENT" }),
    { headers },
  );
  if (register.status !== 200)
    throw new Error(`Registration failed: ${register.status}`);
  const login = http.post(
    BASE + "/api/auth/login",
    JSON.stringify({ email, password }),
    { headers },
  );
  if (login.status !== 200) throw new Error(`Login failed: ${login.status}`);
  return { token: login.json("accessToken") };
}
export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    "Content-Type": "application/json",
  };
  const trip = http.post(
    BASE + "/api/trips/request",
    JSON.stringify({
      originLat: 33.5731,
      originLon: -7.5898,
      destinationLat: 33.5831,
      destinationLon: -7.5898,
    }),
    { headers, tags: { name: "request-trip" } },
  );
  check(trip, { "trip created": (r) => r.status === 200 });
  if (trip.status !== 200) return;
  sleep(1);
  const status = http.get(BASE + "/api/trips/" + trip.json("tripId"), {
    headers,
    tags: { name: "get-status" },
  });
  check(status, { "status returned": (r) => r.status === 200 });
  sleep(1);
}
