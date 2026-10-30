import { useEffect, useState, type ReactNode } from "react";
import {
  MapContainer,
  TileLayer,
  CircleMarker,
  Popup,
  useMapEvents,
} from "react-leaflet";
import { Client } from "@stomp/stompjs";
import "leaflet/dist/leaflet.css";
import "./portal.css";

export const API = import.meta.env.VITE_API_BASE || "http://localhost:8080";
export type Session = {
  token: string;
  id: string;
  email: string;
  role: string;
};
export type Position = {
  driverId?: string;
  lat: number;
  lon: number;
  status?: string;
};
export async function api<T = any>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = localStorage.getItem("portal-token");
  const response = await fetch(API + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(
      data.error || data.message || `Request failed (${response.status})`,
    );
  return data;
}
export function subscribe(topic: string, receive: (data: any) => void) {
  const client = new Client({
    brokerURL: (import.meta.env.VITE_WS_BASE || API + "/ws/tracking").replace(
      /^http/,
      "ws",
    ),
    connectHeaders: {
      Authorization: `Bearer ${localStorage.getItem("portal-token")}`,
    },
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(topic, (message) => {
        try {
          receive(JSON.parse(message.body));
        } catch (error) {
          console.error(error);
        }
      });
    },
  });
  client.activate();
  return () => {
    void client.deactivate();
  };
}
export function Portal({
  role,
  children,
}: {
  role: string;
  children: (session: Session) => ReactNode;
}) {
  const [session, setSession] = useState<Session | null>(() => {
    try {
      const token = localStorage.getItem("portal-token");
      if (!token) return null;
      const claims = JSON.parse(
        atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
      );
      if (claims.exp * 1000 < Date.now() || claims.role !== role) return null;
      return { token, id: claims.sub, email: claims.email, role: claims.role };
    } catch {
      return null;
    }
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function login(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const result = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({
          email: form.get("email"),
          password: form.get("password"),
        }),
      });
      const token = result.accessToken || result.token;
      const claims = JSON.parse(
        atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
      );
      if (claims.role !== role)
        throw new Error(`Sign in with a ${role.toLowerCase()} account.`);
      localStorage.setItem("portal-token", token);
      setSession({ token, id: claims.sub, email: claims.email, role });
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  if (!session)
    return (
      <main className="login">
        <div className="brand">
          T<span>aa</span>Sim{" "}
          <small>{role === "DRIVER" ? "DRIVER PARTNER" : "OPERATIONS"}</small>
        </div>
        <form className="panel login-card" onSubmit={login}>
          <span className="eyebrow">CASABLANCA MOBILITY</span>
          <h1>
            {role === "DRIVER"
              ? "Your city. Your next ride."
              : "A clear view of your city."}
          </h1>
          <p>Sign in to your {role.toLowerCase()} workspace.</p>
          <label>
            Email
            <input type="email" name="email" autoComplete="username" required />
          </label>
          <label>
            Password
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              required
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button disabled={busy}>{busy ? "Signing in…" : "Sign in →"}</button>
        </form>
      </main>
    );
  return (
    <>
      <header>
        <a className="brand" href="/">
          T<span>aa</span>Sim{" "}
          <small>{role === "DRIVER" ? "DRIVER PARTNER" : "OPERATIONS"}</small>
        </a>
        <div>
          {session.email}
          <button
            className="quiet"
            onClick={() => {
              localStorage.removeItem("portal-token");
              setSession(null);
            }}
          >
            Sign out
          </button>
        </div>
      </header>
      <main className="workspace">{children(session)}</main>
    </>
  );
}
function Picker({ pick }: { pick?: (lat: number, lon: number) => void }) {
  useMapEvents({ click: (e) => pick?.(e.latlng.lat, e.latlng.lng) });
  return null;
}
export function FleetMap({
  positions,
  pick,
}: {
  positions: Position[];
  pick?: (lat: number, lon: number) => void;
}) {
  return (
    <MapContainer center={[33.5731, -7.5898]} zoom={12} className="map">
      <TileLayer
        attribution="© OpenStreetMap"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Picker pick={pick} />
      {positions.map((p, i) => (
        <CircleMarker
          key={p.driverId || i}
          center={[p.lat, p.lon]}
          radius={9}
          pathOptions={{
            color: p.status === "BUSY" ? "#f59e0b" : "#10b981",
            fillOpacity: 0.9,
          }}
        >
          <Popup>
            {p.driverId || "Your position"} · {p.status || "AVAILABLE"}
          </Popup>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}
export function message(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Unexpected error. Please retry.";
}
export function Money({ value }: { value: number }) {
  return (
    <>
      {Number(value || 0).toLocaleString("fr-MA", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}{" "}
      <small>MAD</small>
    </>
  );
}
export function usePolling<T>(
  load: () => Promise<T>,
  interval: number,
  dependencies: unknown[] = [],
) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    let inFlight = false;
    const refresh = async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const result = await load();
        if (live) {
          setData(result);
          setError("");
        }
      } catch (e) {
        if (live) setError(message(e));
      } finally {
        inFlight = false;
      }
    };
    void refresh();
    const timer = setInterval(refresh, interval);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, dependencies);
  return { data, error };
}
