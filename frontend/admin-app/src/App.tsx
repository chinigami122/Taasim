import { useState } from "react";
import RideRequests from "./RideRequests";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import {
  Portal,
  FleetMap,
  Money,
  api,
  message,
  usePolling,
  type Position,
} from "../../shared/portal";
function Dashboard() {
  const [tab, setTab] = useState("fleet");
  const [date, setDate] = useState(
    new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Casablanca" }).format(
      new Date(),
    ),
  );
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<"createdAt" | "status">("createdAt");
  const [detail, setDetail] = useState<any>(null);
  const [detailError, setDetailError] = useState("");
  const fleet = usePolling(
    () => api<Position[]>("/api/admin/drivers/live-positions"),
    3000,
  );
  const trips = usePolling(
    () =>
      api<any[]>(
        `/api/admin/trips?date=${date}&limit=10000${status ? `&status=${status}` : ""}`,
      ),
    5000,
    [date, status],
  );
  const billing = usePolling(
    () => api(`/api/admin/billing/summary?date=${date}`),
    10000,
    [date],
  );
  const series = usePolling(
    () => api(`/api/admin/billing/summary?date=${date}&days=7`),
    15000,
    [date],
  );
  const top = usePolling(
    () => api(`/api/admin/billing/top-drivers?date=${date}&limit=10`),
    15000,
    [date],
  );
  const funnel = usePolling(
    () => api(`/api/admin/trips/funnel?date=${date}`),
    10000,
    [date],
  );
  const rows = [...(trips.data || [])].sort((a, b) =>
    String(b[sort]).localeCompare(String(a[sort])),
  );
  const pages = Math.max(1, Math.ceil(rows.length / 15));
  const effectivePage = Math.min(page, pages - 1);
  const errors = [
    ...new Set(
      [
        fleet.error,
        trips.error,
        billing.error,
        series.error,
        top.error,
        funnel.error,
      ].filter(Boolean),
    ),
  ];
  async function fare(id: string) {
    try {
      setDetailError("");
      setDetail(await api(`/api/billing/trips/${id}`));
    } catch (e) {
      setDetailError(message(e));
    }
  }
  return (
    <>
      <div className="heading">
        <div>
          <span className="eyebrow">CASABLANCA · CONTROL ROOM</span>
          <h1>Every ride. One clear view.</h1>
          <p>Fleet activity and business performance, together.</p>
        </div>
        <label>
          Reporting date
          <input
            aria-label="Reporting date"
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      <div className="stats">
        <div className="stat">
          <span>Total fares</span>
          <strong>
            <Money value={billing.data?.revenue || 0} />
          </strong>
          <small>Calculated + charged fares</small>
        </div>
        <div className="stat">
          <span>Platform commission</span>
          <strong>
            <Money value={billing.data?.commission || 0} />
          </strong>
          <small>For the selected date</small>
        </div>
        <div className="stat">
          <span>Completed rides</span>
          <strong>{billing.data?.completedTrips || 0}</strong>
          <small>{fleet.data?.length || 0} drivers reporting positions</small>
        </div>
      </div>
      <nav className="tabs" aria-label="Admin workspace">
        {["fleet", "trips", "billing", "analytics"].map((t) => (
          <button
            className={tab === t ? "active" : ""}
            key={t}
            onClick={() => setTab(t)}
          >
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </nav>
      {errors.map((e) => (
        <p key={e} className="error" role="alert">
          {e}
        </p>
      ))}
      {tab === "fleet" && (
        <div className="grid">
          <section className="panel">
            <div className="heading">
              <h2>Live fleet</h2>
              <span className="live">● Refreshes every 3 seconds</span>
            </div>
            <FleetMap
              positions={(fleet.data || []).filter(
                (p) => Number.isFinite(p.lat) && Number.isFinite(p.lon),
              )}
            />
            <p className="hint">
              Green · available &nbsp; Amber · busy. Last known locations;
              offline drivers are excluded.
            </p>
          </section>
          <section className="panel">
            <h2>On the road</h2>
            {!fleet.data?.length && (
              <p className="empty">No drivers online yet.</p>
            )}
            {fleet.data?.map((p) => (
              <div
                key={p.driverId}
                style={{
                  borderBottom: "1px solid #ffffff12",
                  padding: "16px 0",
                }}
              >
                <span className={`pill ${p.status}`}>{p.status}</span>
                <p style={{ fontSize: 12 }}>{p.driverId}</p>
                <small>
                  {p.lat?.toFixed(4)}, {p.lon?.toFixed(4)}
                </small>
              </div>
            ))}
          </section>
        </div>
      )}
      {tab === "trips" && (
        <section className="panel">
          <div className="filters">
            <label>
              Trip status
              <select
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All statuses</option>
                {[
                  "REQUESTED",
                  "MATCHED",
                  "ACCEPTED",
                  "IN_PROGRESS",
                  "COMPLETED",
                  "REJECTED",
                  "CANCELLED",
                ].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </label>
            <span>{rows.length} trips · selected date</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Trip</th>
                  <th>
                    <button onClick={() => setSort("status")}>Status ↕</button>
                  </th>
                  <th>Driver</th>
                  <th>Zones</th>
                  <th>
                    <button onClick={() => setSort("createdAt")}>
                      Requested ↕
                    </button>
                  </th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {rows
                  .slice(effectivePage * 15, (effectivePage + 1) * 15)
                  .map((t) => (
                    <tr key={t.tripId}>
                      <td>
                        <code>{t.tripId.slice(0, 8)}</code>
                      </td>
                      <td>
                        <span className={`pill ${t.status}`}>{t.status}</span>
                      </td>
                      <td>{t.driverId?.slice(0, 8) || "Awaiting match"}</td>
                      <td>
                        {t.originZone} → {t.destZone ?? t.destinationZone}
                      </td>
                      <td>{new Date(t.createdAt).toLocaleTimeString()}</td>
                      <td>
                        <button
                          className="quiet"
                          onClick={() => fare(t.tripId)}
                        >
                          View fare
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
            {!rows.length && (
              <p className="empty">No trips match these filters.</p>
            )}
          </div>
          <div className="actions">
            <button
              className="quiet"
              disabled={effectivePage === 0}
              onClick={() => setPage(effectivePage - 1)}
            >
              Previous
            </button>
            <span>
              Page {effectivePage + 1} / {pages}
            </span>
            <button
              className="quiet"
              disabled={effectivePage + 1 >= pages}
              onClick={() => setPage(effectivePage + 1)}
            >
              Next
            </button>
          </div>
          {detailError && <p className="error">{detailError}</p>}
          {detail && (
            <div className="panel ledger" role="status">
              <h3>Trip {detail.tripId?.slice(0, 8)}</h3>
              <p>
                Total <Money value={detail.totalFare} /> · Commission{" "}
                <Money value={detail.commission} /> · Driver{" "}
                <Money value={detail.driverPayout} />
              </p>
              <span className="pill">{detail.status}</span>
              <button className="quiet" onClick={() => setDetail(null)}>
                Close
              </button>
            </div>
          )}
        </section>
      )}
      {tab === "analytics" && <RideRequests />}
      {tab === "billing" && (
        <>
          <div className="grid">
            <section className="panel">
              <h2>Seven days of fares</h2>
              <div className="chart">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={series.data || []}>
                    <CartesianGrid stroke="#ffffff0b" vertical={false} />
                    <XAxis
                      dataKey="date"
                      tick={{ fill: "#94a3b8", fontSize: 11 }}
                    />
                    <YAxis tick={{ fill: "#94a3b8", fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        background: "#111827",
                        border: "1px solid #334155",
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey="revenue"
                      name="Fares (MAD)"
                      stroke="#f59e0b"
                      strokeWidth={3}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </section>
            <section className="panel">
              <h2>Top drivers by payout</h2>
              <div className="chart">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={(top.data || []).map((d: any) => ({
                      ...d,
                      label: d.driverId.slice(0, 8),
                    }))}
                  >
                    <XAxis
                      dataKey="label"
                      tick={{ fill: "#94a3b8", fontSize: 10 }}
                    />
                    <YAxis tick={{ fill: "#94a3b8", fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        background: "#111827",
                        border: "1px solid #334155",
                      }}
                    />
                    <Bar
                      dataKey="payout"
                      name="Payout (MAD)"
                      fill="#818cf8"
                      radius={[5, 5, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              {!top.data?.length && (
                <p className="hint">No payouts for this date yet.</p>
              )}
            </section>
          </div>
          <section className="panel ledger">
            <h2>Trip status distribution</h2>
            <p className="hint">
              Current status counts for trips requested on the selected date.
            </p>
            <div className="stats">
              {Object.entries(funnel.data || {}).map(([key, value]) => (
                <div className="stat" key={key}>
                  <span>{key.replace("_", " ")}</span>
                  <strong>{String(value)}</strong>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </>
  );
}
export default function App() {
  return <Portal role="ADMIN">{() => <Dashboard />}</Portal>;
}
