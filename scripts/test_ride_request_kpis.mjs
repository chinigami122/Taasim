import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

const base = process.env.BASE || "http://localhost:8080";
const hour = 3600000;
const start = Math.floor(Date.now() / hour) * hour;
const from = new Date(start - hour).toISOString();
const to = new Date(start + hour).toISOString();
const path = `/api/admin/analytics/ride-requests?from=${from}&to=${to}`;

function compose(args, input) {
  const result = spawnSync("docker", [
    "compose", "-f", "docker-compose.yml", "-f", "docker-compose.app.yml", ...args,
  ], { input, stdio: [input === undefined ? "inherit" : "pipe", "inherit", "inherit"] });
  assert.ifError(result.error);
  assert.equal(result.status, 0, args.join(" "));
}

async function request(path, token, body) {
  return fetch(base + path, {
    method: body ? "POST" : "GET",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body && JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
}

async function json(path, token, body) {
  const response = await request(path, token, body);
  assert.ok(response.ok, `${path}: ${response.status} ${response.ok ? "" : await response.text()}`);
  return response.json();
}

async function account(role) {
  const email = `analytics-${role.toLowerCase()}-${Date.now()}@taasim.demo`;
  const password = "SmokePassword123!";
  await json("/api/auth/register", null, { email, password, fullName: "Analytics Smoke", role });
  const login = await json("/api/auth/login", null, { email, password });
  return login.accessToken || login.token;
}

async function publish() {
  // Archiver flushes at most every 30 seconds.
  await sleep(40000);
  compose(["run", "--rm", "--no-deps", "ride-request-kpis", "--from", from, "--to", to]);
}

const client = await account("CLIENT");
const admin = await account("ADMIN");
assert.equal((await request(path, client)).status, 403);
assert.equal((await request("/api/admin/analytics/ride-requests?from=bad&to=bad", admin)).status, 400);

const zone = Number(process.env.KPI_TEST_ZONE || 16);
const beforeRequest = Date.now();
const trip = await json("/api/trips/request", client, { originZone: zone, destinationZone: 15 });
assert.equal(trip.originZone, zone);
const requestedHour = new Date(Math.floor(beforeRequest / hour) * hour).toISOString();
assert.equal(Math.floor(beforeRequest / hour), Math.floor(Date.now() / hour), "request crossed an hour boundary");

const report = () => json(path, admin);
const count = (data, at = requestedHour) => data.counts.find(
  (row) => Date.parse(row.hour) === Date.parse(at) && row.zone === zone,
)?.count ?? 0;
const baseline = count(await report());
await publish();
const first = await report();
assert.ok(first.processedAt);
assert.equal(count(first), baseline + 1, "new ride increments the zone-hour count");
assert.ok(first.counts.some((row) => row.count === 0), "processed zero counts are explicit");

const riderId = JSON.parse(Buffer.from(client.split(".")[1], "base64url").toString()).sub;
const replay = { trip_id: trip.tripId, rider_id: riderId, requested_at: beforeRequest, origin_zone: zone };
const late = { ...replay, trip_id: crypto.randomUUID(), requested_at: start - 1 };
const malformed = { ...late, trip_id: "invalid-trip-id" };
compose([
  "exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
  "--bootstrap-server", "kafka:29092", "--topic", "raw.trips",
], [replay, late, malformed].map((event) => JSON.stringify(event)).join("\n") + "\n");
await publish();
const afterReplay = await report();
assert.equal(count(afterReplay), count(first), "replay leaves the KPI unchanged");
assert.equal(count(afterReplay, from), count(first, from) + 1, "late valid event lands in its original hour");
console.log("Ride request KPI pipeline passed");
