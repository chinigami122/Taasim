import assert from "node:assert/strict";
const base = process.env.BASE || "http://localhost:8080";
const suffix = Date.now();
async function request(path, token, body, method = body ? "POST" : "GET") {
  const response = await fetch(base + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(10000),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(
      `${method} ${path}: ${response.status} ${JSON.stringify(result)}`,
    );
  return result;
}
async function account(role, index = "") {
  const email = `smoke-${role}-${suffix}-${index}@taasim.demo`,
    password = "SmokePassword123!";
  await request("/api/auth/register", null, {
    email,
    password,
    fullName: `Smoke ${role}`,
    role,
  });
  const login = await request("/api/auth/login", null, { email, password });
  const token = login.accessToken || login.token;
  return {
    token,
    id: JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString())
      .sub,
  };
}
async function until(fn, predicate, label) {
  let last;
  for (let i = 0; i < 60; i++) {
    last = await fn();
    if (predicate(last)) return last;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`Timed out: ${label}; last=${JSON.stringify(last)}`);
}
const client = await account("CLIENT"),
  driver = await account("DRIVER"),
  admin = await account("ADMIN");
await request(
  "/api/drivers/me/availability?online=true",
  driver.token,
  null,
  "PUT",
);
await request("/api/drivers/location", driver.token, {
  lat: 33.5731,
  lon: -7.5898,
  speed: 0,
});
const trip = await request("/api/trips/request", client.token, {
  originLat: 33.5731,
  originLon: -7.5898,
  destinationLat: 33.5831,
  destinationLon: -7.5898,
});
await until(
  () => request("/api/drivers/me/state", driver.token),
  (s) => s.pendingTrip === trip.tripId,
  "driver offer",
);
await request(
  `/api/drivers/trips/${driver.id}/accept`,
  driver.token,
  null,
  "PUT",
);
await request(
  `/api/drivers/trips/${driver.id}/start`,
  driver.token,
  null,
  "PUT",
);
await request("/api/drivers/location", driver.token, {
  lat: 33.5731,
  lon: -7.5898,
  speed: 5,
});
await request("/api/drivers/location", driver.token, {
  lat: 33.5831,
  lon: -7.5898,
  speed: 5,
});
await request(
  `/api/drivers/trips/${driver.id}/complete`,
  driver.token,
  null,
  "PUT",
);
await until(
  () => request(`/api/trips/${trip.tripId}`, client.token),
  (s) => s.status === "COMPLETED",
  "trip complete",
);
const bill = await until(
  async () => {
    try {
      return await request(`/api/billing/trips/${trip.tripId}`, client.token);
    } catch {
      return {};
    }
  },
  (s) => s.totalFare > 0,
  "billing record",
);
assert.equal(bill.status, "CALCULATED");
const earnings = await request("/api/drivers/earnings/summary", driver.token);
assert.ok(earnings.today.trips >= 1);
const series = await request("/api/admin/billing/summary?days=7", admin.token);
assert.equal(series.length, 7);
const funnel = await request("/api/admin/trips/funnel", admin.token);
assert.ok(funnel.completed >= 1);
const forbidden = await fetch(base + `/api/drivers/trips/${driver.id}/accept`, {
  method: "PUT",
  headers: { Authorization: `Bearer ${client.token}` },
});
assert.equal(forbidden.status, 403);
await request(
  "/api/drivers/me/availability?online=false",
  driver.token,
  null,
  "PUT",
);
console.log(
  JSON.stringify(
    {
      result: "PASS",
      tripId: trip.tripId,
      fare: bill.totalFare,
      checks: [
        "dispatch",
        "accept",
        "start",
        "GPS",
        "complete",
        "billing",
        "earnings",
        "admin charts",
        "RBAC",
        "offline",
      ],
    },
    null,
    2,
  ),
);
