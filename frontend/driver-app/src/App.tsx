import { useEffect, useRef, useState } from "react";
import {
  Portal,
  FleetMap,
  Money,
  api,
  message,
  subscribe,
  usePolling,
  type Session,
  type Position,
} from "../../shared/portal";

type DriverState = {
  status: string;
  pendingTrip: string | null;
  activeTrip: string | null;
  rideStatus?: string;
};
function Dashboard({ session }: { session: Session }) {
  const [tab, setTab] = useState("drive");
  const [state, setState] = useState<DriverState>({
    status: "OFFLINE",
    pendingTrip: null,
    activeTrip: null,
  });
  const [position, setPosition] = useState<Position | null>(null);
  const [demo, setDemo] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const watch = useRef<number | null>(null);
  const online = state.status !== "OFFLINE";
  const positionRef = useRef<Position | null>(null);
  const sending = useRef(false);
  const earnings = usePolling(
    () => api("/api/drivers/earnings/summary"),
    10000,
  );
  const ledger = usePolling(
    () => api("/api/drivers/earnings/trips?size=20"),
    10000,
  );
  const [started, setStarted] = useState(false);
  useEffect(() => {
    let live = true;
    const refresh = async () => {
      try {
        const next = await api<DriverState>("/api/drivers/me/state");
        if (live) {
          setState(next);
          setStarted(next.rideStatus === "IN_PROGRESS");
        }
      } catch (e) {
        if (live) setError(message(e));
      }
    };
    void refresh();
    const timer = setInterval(refresh, 2500);
    const stop = subscribe(`/topic/drivers/${session.id}/offers`, () => {
      void refresh();
      setNotice("A new ride is waiting for you.");
    });
    return () => {
      live = false;
      clearInterval(timer);
      stop();
    };
  }, [session.id]);
  async function sendPosition(p: Position) {
    if (sending.current) return;
    sending.current = true;
    try {
      await api("/api/drivers/location", {
        method: "POST",
        body: JSON.stringify({ lat: p.lat, lon: p.lon, speed: 0 }),
      });
      setError("");
    } catch (e) {
      setError(message(e));
    } finally {
      sending.current = false;
    }
  }
  useEffect(() => {
    if (!online) return;
    if (demo) {
      const p = positionRef.current || { lat: 33.5731, lon: -7.5898 };
      positionRef.current = p;
      setPosition(p);
      void sendPosition(p);
      const timer = setInterval(() => {
        if (positionRef.current) void sendPosition(positionRef.current);
      }, 5000);
      return () => clearInterval(timer);
    }
    if (!navigator.geolocation) {
      setError(
        "Geolocation is unavailable. Use the explicit Casablanca demo mode.",
      );
      return;
    }
    watch.current = navigator.geolocation.watchPosition(
      (p) => {
        const next = { lat: p.coords.latitude, lon: p.coords.longitude };
        positionRef.current = next;
        setPosition(next);
        void sendPosition(next);
      },
      (e) => {
        setError(
          `Location access: ${e.message}. Enable location in browser settings, or go offline and choose demo mode.`,
        );
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 10000 },
    );
    return () => {
      if (watch.current !== null)
        navigator.geolocation.clearWatch(watch.current);
      watch.current = null;
    };
  }, [online, demo]);
  async function toggle() {
    setBusy(true);
    setError("");
    try {
      if (!online && !demo && navigator.permissions) {
        const permission = await navigator.permissions.query({
          name: "geolocation",
        });
        if (permission.state === "denied")
          throw new Error(
            "Location permission is denied. Enable it in browser settings or select Casablanca demo mode.",
          );
      }
      const next = await api<DriverState>(
        `/api/drivers/me/availability?online=${!online}`,
        { method: "PUT" },
      );
      setState(next);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function action(name: string) {
    setBusy(true);
    setError("");
    try {
      await api(`/api/drivers/trips/${session.id}/${name}`, { method: "PUT" });
      if (name === "start") setStarted(true);
      if (name === "complete") setStarted(false);
      setState(await api("/api/drivers/me/state"));
      setNotice(
        name === "complete"
          ? "Ride complete. Your fare is being calculated."
          : "",
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const pick =
    demo && online
      ? (lat: number, lon: number) => {
          const p = { lat, lon };
          positionRef.current = p;
          setPosition(p);
          void sendPosition(p);
        }
      : undefined;
  return (
    <>
      <div className="heading">
        <div>
          <span className="eyebrow">YOUR SHIFT, AT A GLANCE</span>
          <h1>Ready for the next ride?</h1>
          <p>Drive Casablanca. Keep your city moving.</p>
        </div>
        <div>
          <span className={`pill ${state.status}`}>{state.status}</span>{" "}
          <button
            disabled={busy || !!state.activeTrip || !!state.pendingTrip}
            onClick={toggle}
          >
            {online ? "Go offline" : "Go online"}
          </button>
        </div>
      </div>
      <nav className="tabs" aria-label="Driver workspace">
        <button
          className={tab === "drive" ? "active" : ""}
          onClick={() => setTab("drive")}
        >
          Drive
        </button>
        <button
          className={tab === "earnings" ? "active" : ""}
          onClick={() => setTab("earnings")}
        >
          Earnings
        </button>
      </nav>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      <div className="stats">
        {[
          ["today", "Today"],
          ["thisWeek", "This week"],
          ["thisMonth", "This month"],
        ].map(([key, label]) => (
          <div className="stat" key={key}>
            <span>{label}</span>
            <strong>
              <Money value={earnings.data?.[key]?.earnings || 0} />
            </strong>
            <small>
              {earnings.data?.[key]?.trips || 0} trips · calculated fares
            </small>
          </div>
        ))}
      </div>
      {(earnings.error || ledger.error) && (
        <p className="error">{earnings.error || ledger.error}</p>
      )}
      {tab === "drive" ? (
        <div className="grid">
          <section className="panel">
            <h2>
              Your position{" "}
              <span className="eyebrow">
                {demo ? "DEMO LOCATION" : "LIVE GPS"}
              </span>
            </h2>
            <FleetMap
              positions={
                position
                  ? [
                      {
                        ...position,
                        driverId: session.id,
                        status: state.status,
                      },
                    ]
                  : []
              }
              pick={pick}
            />
            <p className="hint">
              {demo
                ? "Demo mode sends real GPS API requests. Click the map to move your taxi; the client sees the updates."
                : "Allow location access to share your current position. Location stops when you go offline or close this app."}
            </p>
          </section>
          <aside>
            <section className={`panel ${state.pendingTrip ? "offer" : ""}`}>
              <span className="eyebrow">DISPATCH</span>
              <h2>
                {state.pendingTrip
                  ? "Your next passenger"
                  : state.activeTrip
                    ? "Ride in progress"
                    : online
                      ? "You’re on the radar"
                      : "Start your shift"}
              </h2>
              {state.pendingTrip ? (
                <>
                  <p>
                    Trip <code>{state.pendingTrip.slice(0, 8)}</code>
                  </p>
                  <div className="actions">
                    <button disabled={busy} onClick={() => action("accept")}>
                      Accept ride
                    </button>
                    <button
                      className="quiet"
                      disabled={busy}
                      onClick={() => action("reject")}
                    >
                      Decline
                    </button>
                  </div>
                </>
              ) : state.activeTrip ? (
                <>
                  <p>
                    Trip <code>{state.activeTrip.slice(0, 8)}</code>
                  </p>
                  <div className="actions">
                    <button
                      disabled={busy || started}
                      onClick={() => action("start")}
                    >
                      Start ride
                    </button>
                    <button
                      disabled={busy || !started}
                      onClick={() => action("complete")}
                    >
                      Complete ride
                    </button>
                  </div>
                </>
              ) : (
                <p>
                  {online
                    ? "Stay online. Nearby ride offers will appear here automatically."
                    : "Go online to receive nearby ride offers."}
                </p>
              )}
            </section>
            <section className="panel ledger">
              <h3>Location source</h3>
              <label>
                <span>
                  <input
                    type="checkbox"
                    checked={demo}
                    disabled={online}
                    onChange={(e) => setDemo(e.target.checked)}
                  />{" "}
                  Casablanca demo mode
                </span>
              </label>
              <p className="hint">
                Use browser GPS for real driving. For a desktop demo outside
                Casablanca, select demo mode before going online.
              </p>
            </section>
          </aside>
        </div>
      ) : (
        <section className="panel">
          <h2>Trip earnings</h2>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Trip</th>
                  <th>Fare</th>
                  <th>Commission</th>
                  <th>Your payout</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {ledger.data?.trips?.map((t: any) => (
                  <tr key={t.tripId}>
                    <td>
                      <code>{t.tripId.slice(0, 8)}</code>
                    </td>
                    <td>
                      <Money value={t.totalFare} />
                    </td>
                    <td>
                      <Money value={t.commission} />
                    </td>
                    <td>
                      <Money value={t.payout} />
                    </td>
                    <td>{t.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!ledger.data?.trips?.length && (
              <p className="empty">Your completed rides will appear here.</p>
            )}
          </div>
        </section>
      )}
    </>
  );
}
export default function App() {
  return (
    <Portal role="DRIVER">
      {(session) => <Dashboard session={session} />}
    </Portal>
  );
}
