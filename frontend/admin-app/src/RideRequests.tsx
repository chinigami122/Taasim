import { useState } from "react";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, usePolling } from "../../shared/portal";

type Report = {
  processedAt: string | null;
  counts: { hour: string; zone: number; count: number }[];
};

const casablancaTime = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Africa/Casablanca",
  dateStyle: "short",
  timeStyle: "short",
});

export default function RideRequests() {
  const [zone, setZone] = useState("all");
  const report = usePolling(() => {
    const to = Math.floor(Date.now() / 3600000) * 3600000;
    const from = new Date(to - 24 * 3600000).toISOString();
    return api<Report>(`/api/admin/analytics/ride-requests?from=${from}&to=${new Date(to).toISOString()}`);
  }, 60000);

  const byHour = new Map<string, number>();
  for (const row of report.data?.counts ?? []) {
    if (zone === "all" || row.zone === Number(zone)) {
      byHour.set(row.hour, (byHour.get(row.hour) ?? 0) + row.count);
    }
  }
  const series = [...byHour].sort(([a], [b]) => a.localeCompare(b)).map(([hour, count]) => ({
    hour: casablancaTime.format(new Date(hour)),
    count,
  }));

  return (
    <section className="panel">
      <h2>Hourly ride requests</h2>
      <label>
        Pickup zone{" "}
        <select value={zone} onChange={(event) => setZone(event.target.value)}>
          <option value="all">All zones</option>
          <option value="0">Unresolved</option>
          {Array.from({ length: 16 }, (_, index) => index + 1).map((id) => (
            <option value={id} key={id}>Zone {id}</option>
          ))}
        </select>
      </label>
      {report.error && <p role="status">Ride request analytics is temporarily unavailable.</p>}
      {report.data?.processedAt && (
        <p>Last processed {casablancaTime.format(new Date(report.data.processedAt))} · Casablanca time</p>
      )}
      {series.length ? (
        <>
          <p>{series.length} of the last 24 completed hours processed.</p>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={series}>
              <XAxis dataKey="hour" minTickGap={32} />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" name="Ride requests" fill="#f59e0b" />
            </BarChart>
          </ResponsiveContainer>
        </>
      ) : (
        !report.error && <p>{report.data ? "Waiting for the first analytics run." : "Loading ride requests…"}</p>
      )}
    </section>
  );
}
