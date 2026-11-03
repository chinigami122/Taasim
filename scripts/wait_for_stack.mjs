const base = process.env.BASE || "http://localhost:8080";
const deadline = Date.now() + 240000;
while (Date.now() < deadline) {
  try {
    const response = await fetch(base + "/actuator/health", {
      signal: AbortSignal.timeout(4000),
    });
    const result = await response.json();
    if (response.ok && result.status === "UP") {
      console.log("Gateway ready");
      process.exit(0);
    }
  } catch {}
  await new Promise((r) => setTimeout(r, 2000));
}
throw new Error("Gateway did not become healthy in 240 seconds");
