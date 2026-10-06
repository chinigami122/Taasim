import { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import {
  Navigation,
  Compass,
  RotateCcw,
  Radio,
  Zap,
} from 'lucide-react';
import { TripsService } from '../api';
import { subscribeToTrip, type Loc } from '../ws/trackingClient';
import PaymentPanel from './PaymentPanel';

import Navbar from '../components/Navbar';

const CASA_CENTER: [number, number] = [33.5731, -7.5898];

// Custom HTML DivIcons with modern styles (no broken png asset issues)
const originIcon = L.divIcon({
  className: 'custom-leaflet-marker origin-marker',
  html: `<div style="
    background: #10b981;
    color: white;
    width: 32px;
    height: 32px;
    border-radius: 50% 50% 50% 0;
    transform: rotate(-45deg);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 0 15px rgba(16, 185, 129, 0.6);
    border: 2px solid white;
  "><span style="transform: rotate(45deg); font-weight: 800; font-size: 13px;">A</span></div>`,
  iconSize: [32, 32],
  iconAnchor: [16, 32],
  popupAnchor: [0, -32],
});

const destIcon = L.divIcon({
  className: 'custom-leaflet-marker dest-marker',
  html: `<div style="
    background: #ef4444;
    color: white;
    width: 32px;
    height: 32px;
    border-radius: 50% 50% 50% 0;
    transform: rotate(-45deg);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.6);
    border: 2px solid white;
  "><span style="transform: rotate(45deg); font-weight: 800; font-size: 13px;">B</span></div>`,
  iconSize: [32, 32],
  iconAnchor: [16, 32],
  popupAnchor: [0, -32],
});

const driverIcon = L.divIcon({
  className: 'custom-leaflet-marker driver-marker',
  html: `<div style="
    background: #f59e0b;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    box-shadow: 0 0 20px rgba(245, 158, 11, 0.8);
    border: 2px solid #ffffff;
    animation: pulse 1.8s infinite;
  ">🚕</div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18],
});

function MapClickHandler({ onPick, disabled }: { onPick: (lat: number, lon: number) => void; disabled: boolean }) {
  useMapEvents({
    click: (e) => {
      if (!disabled) {
        onPick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

export default function RideMapPage() {
  const [zones, setZones] = useState<any>(null);
  const [origin, setOrigin] = useState<[number, number] | null>(null);
  const [dest, setDest] = useState<[number, number] | null>(null);
  const [tripId, setTripId] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('IDLE');
  const [driverId, setDriverId] = useState<string | null>(null);
  const [driverPos, setDriverPos] = useState<[number, number] | null>(null);
  const [driverSpeed, setDriverSpeed] = useState<number | null>(null);
  const [lastPingTime, setLastPingTime] = useState<string | null>(null);
  const [isRequesting, setIsRequesting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Stripe Payment State
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [fareAmount, setFareAmount] = useState<number>(2500);

  const unsubRef = useRef<(() => void) | null>(null);

  // Load Casablanca zones GeoJSON
  useEffect(() => {
    fetch('/casablanca_zones.geojson')
      .then((res) => {
        if (!res.ok) throw new Error('Failed to load Casablanca zones');
        return res.json();
      })
      .then((data) => setZones(data))
      .catch((err) => console.warn('Could not load zones GeoJSON:', err));

    return () => {
      if (unsubRef.current) {
        unsubRef.current();
        unsubRef.current = null;
      }
    };
  }, []);

  const fetchPaymentIntent = async (tId: string) => {
    try {
      const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
      const token = localStorage.getItem('token');
      const res = await fetch(`${apiBase}/api/billing/trips/${tId}/intent`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      if (res.ok) {
        const data = await res.json();
        setClientSecret(data.clientSecret || `pi_mock_${tId}_secret_mock`);
        if (data.amount) setFareAmount(data.amount);
      } else {
        // Fallback to mock secret if billing service returns non-200
        setClientSecret(`pi_mock_${tId}_secret_mock`);
      }
    } catch (err) {
      console.warn('Error fetching payment intent:', err);
      setClientSecret(`pi_mock_${tId}_secret_mock`);
    }
  };

  // Poll trip status if in REQUESTED/MATCHED/IN_PROGRESS to track completion
  useEffect(() => {
    if (!tripId || status === 'COMPLETED' || status === 'CANCELLED') return;

    const interval = setInterval(async () => {
      try {
        const trip: any = await TripsService.getTrip({ tripId });
        if (trip && trip.status && trip.status !== status) {
          setStatus(trip.status);
          if (trip.driverId) setDriverId(trip.driverId);

          if (trip.status === 'COMPLETED') {
            fetchPaymentIntent(tripId);
          }
        }
      } catch (e) {
        console.debug('Trip polling check:', e);
      }
    }, 2500);

    return () => clearInterval(interval);
  }, [tripId, status]);

  const handleMapPick = (lat: number, lon: number) => {
    if (!origin) {
      setOrigin([lat, lon]);
    } else if (!dest) {
      setDest([lat, lon]);
    }
  };

  const handleReset = () => {
    if (unsubRef.current) {
      unsubRef.current();
      unsubRef.current = null;
    }
    setOrigin(null);
    setDest(null);
    setTripId(null);
    setStatus('IDLE');
    setDriverId(null);
    setDriverPos(null);
    setDriverSpeed(null);
    setLastPingTime(null);
    setClientSecret(null);
    setErrorMsg(null);
  };

  const handleRequestRide = async () => {
    if (!origin || !dest) return;
    setIsRequesting(true);
    setErrorMsg(null);

    try {
      const riderId = localStorage.getItem('user_email') || 'client@taasim.ma';
      // Default to Casablanca zones (e.g. Zone 5: Moulay Rachid -> Zone 12: Assoukhour Assawda)
      const res: any = await TripsService.requestTrip({
        requestBody: {
          riderId,
          originZone: 5,
          destinationZone: 12,
          originLat: origin[0],
          originLon: origin[1],
          destinationLat: dest[0],
          destinationLon: dest[1],
        },
      });

      const assignedTripId = res.tripId || res.id;
      const initialStatus = res.status || 'REQUESTED';

      setTripId(assignedTripId);
      setStatus(initialStatus);

      // Open STOMP WebSocket tracking subscription
      if (unsubRef.current) {
        unsubRef.current();
      }

      unsubRef.current = subscribeToTrip(assignedTripId, (loc: Loc) => {
        setDriverPos([loc.lat, loc.lon]);
        if (loc.driverId) setDriverId(loc.driverId);
        if (loc.speed !== undefined) setDriverSpeed(loc.speed);
        if (loc.status) setStatus(loc.status);
        setLastPingTime(new Date(loc.ts || Date.now()).toLocaleTimeString());

        // If ride completed via WebSocket telemetry
        if (loc.status === 'COMPLETED') {
          fetchPaymentIntent(assignedTripId);
        }
      });
    } catch (err: any) {
      console.error('Request ride failed:', err);
      setErrorMsg(err?.body?.message || err?.message || 'Failed to dispatch ride request');
    } finally {
      setIsRequesting(false);
    }
  };

  const [isSimulating, setIsSimulating] = useState(false);

  const simulateDriverDrive = async () => {
    if (!tripId) return;
    setIsSimulating(true);
    setErrorMsg(null);

    try {
      const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

      // 1. Determine assigned driver
      let activeDriver = driverId;
      if (!activeDriver) {
        try {
          const tripInfo: any = await TripsService.getTrip({ tripId });
          activeDriver = tripInfo.driverId || tripInfo.driver_id;
        } catch {}
      }
      if (!activeDriver) activeDriver = 'taxi_casablanca_001';
      setDriverId(activeDriver);

      // 2. Authenticate simulator driver
      let dToken = localStorage.getItem('driver_sim_token');
      if (!dToken) {
        const dEmail = 'driver_sim_map@taasim.ma';
        const dPass = 'Password123!';
        try {
          await fetch(`${apiBase}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: dEmail, password: dPass, fullName: 'Simulated Driver', role: 'DRIVER' })
          });
        } catch {}
        const loginRes = await fetch(`${apiBase}/api/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: dEmail, password: dPass })
        });
        if (loginRes.ok) {
          const data = await loginRes.json();
          dToken = data.accessToken;
          if (dToken) localStorage.setItem('driver_sim_token', dToken);
        }
      }

      // 3. Accept trip
      try {
        await fetch(`${apiBase}/api/drivers/trips/${activeDriver}/accept`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${dToken}` }
        });
      } catch {}

      // 4. Start trip
      try {
        await fetch(`${apiBase}/api/drivers/trips/${activeDriver}/start`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${dToken}` }
        });
        setStatus('IN_PROGRESS');
      } catch {}

      // 5. Send location pings moving from origin to destination
      const startLat = origin ? origin[0] : 33.5735;
      const startLon = origin ? origin[1] : -7.5895;
      const endLat = dest ? dest[0] : 33.5900;
      const endLon = dest ? dest[1] : -7.6100;

      for (let i = 0; i <= 5; i++) {
        const ratio = i / 5;
        const curLat = startLat + (endLat - startLat) * ratio;
        const curLon = startLon + (endLon - startLon) * ratio;

        await fetch(`${apiBase}/api/drivers/location`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${dToken}`
          },
          body: JSON.stringify({
            driverId: activeDriver,
            lat: curLat,
            lon: curLon,
            speed: 8.0 + i * 2.5
          })
        });

        // Set local pos as well
        setDriverPos([curLat, curLon]);
        setDriverSpeed(8.0 + i * 2.5);
        setLastPingTime(new Date().toLocaleTimeString());

        await new Promise((r) => setTimeout(r, 1200));
      }

      // 6. Complete trip
      try {
        await fetch(`${apiBase}/api/drivers/trips/${activeDriver}/complete`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${dToken}` }
        });
        setStatus('COMPLETED');
        fetchPaymentIntent(tripId);
      } catch {}
    } catch (err: any) {
      console.error('Simulation failed:', err);
      setErrorMsg(err?.message || 'Driver simulation encountered an error');
    } finally {
      setIsSimulating(false);
    }
  };

  // Status Badge color
  const getStatusBadge = () => {
    switch (status) {
      case 'REQUESTED':
        return { label: 'Matching Taxi...', color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.15)' };
      case 'MATCHED':
        return { label: 'Taxi Matched', color: '#10b981', bg: 'rgba(16, 185, 129, 0.15)' };
      case 'DRIVER_ARRIVING':
        return { label: 'Driver Arriving', color: '#06b6d4', bg: 'rgba(6, 182, 212, 0.15)' };
      case 'IN_PROGRESS':
        return { label: 'Ride in Progress', color: '#6366f1', bg: 'rgba(99, 102, 241, 0.15)' };
      case 'COMPLETED':
        return { label: 'Trip Completed', color: '#10b981', bg: 'rgba(16, 185, 129, 0.2)' };
      default:
        return { label: 'Select Pick & Drop', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.1)' };
    }
  };

  const badge = getStatusBadge();

  return (
    <div style={{ width: '100vw', height: '100vh', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Navbar />
      <div style={{ position: 'relative', flex: 1, width: '100%', overflow: 'hidden' }}>
        {/* Floating Control Panel */}
      <div
        style={{
          position: 'absolute',
          top: 16,
          left: 16,
          zIndex: 1000,
          width: '380px',
          maxWidth: 'calc(100vw - 32px)',
          background: 'rgba(15, 23, 42, 0.92)',
          backdropFilter: 'blur(16px)',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: '16px',
          padding: '20px',
          color: '#f8fafc',
          boxShadow: '0 12px 40px rgba(0, 0, 0, 0.5)',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Compass size={22} color="#6366f1" />
            <h2 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700, letterSpacing: '-0.02em' }}>
              Casablanca Live Dispatch
            </h2>
          </div>
          <span
            style={{
              padding: '4px 10px',
              borderRadius: '999px',
              fontSize: '0.75rem',
              fontWeight: 600,
              color: badge.color,
              background: badge.bg,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <span
              style={{
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                background: badge.color,
                boxShadow: `0 0 8px ${badge.color}`,
              }}
            />
            {badge.label}
          </span>
        </div>

        {/* Origin / Destination Status */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '10px 12px',
              background: 'rgba(255, 255, 255, 0.04)',
              borderRadius: '10px',
              border: origin ? '1px solid rgba(16, 185, 129, 0.4)' : '1px dashed rgba(255, 255, 255, 0.15)',
            }}
          >
            <div
              style={{
                width: '28px',
                height: '28px',
                borderRadius: '50%',
                background: origin ? '#10b981' : 'rgba(255,255,255,0.1)',
                color: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.78rem',
                fontWeight: 700,
              }}
            >
              A
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '0.72rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Pickup Point (Origin)
              </div>
              <div style={{ fontSize: '0.85rem', fontWeight: 500, fontFamily: 'monospace' }}>
                {origin ? `${origin[0].toFixed(4)}, ${origin[1].toFixed(4)}` : 'Click anywhere on map'}
              </div>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '10px 12px',
              background: 'rgba(255, 255, 255, 0.04)',
              borderRadius: '10px',
              border: dest ? '1px solid rgba(239, 68, 68, 0.4)' : '1px dashed rgba(255, 255, 255, 0.15)',
            }}
          >
            <div
              style={{
                width: '28px',
                height: '28px',
                borderRadius: '50%',
                background: dest ? '#ef4444' : 'rgba(255,255,255,0.1)',
                color: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.78rem',
                fontWeight: 700,
              }}
            >
              B
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '0.72rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Dropoff Point (Destination)
              </div>
              <div style={{ fontSize: '0.85rem', fontWeight: 500, fontFamily: 'monospace' }}>
                {dest ? `${dest[0].toFixed(4)}, ${dest[1].toFixed(4)}` : origin ? 'Click map for destination' : 'Waiting for pickup'}
              </div>
            </div>
          </div>
        </div>

        {errorMsg && (
          <div
            style={{
              padding: '10px',
              borderRadius: '8px',
              background: 'rgba(239, 68, 68, 0.12)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              color: '#f87171',
              fontSize: '0.8rem',
            }}
          >
            {errorMsg}
          </div>
        )}

        {/* Action Controls */}
        <div style={{ display: 'flex', gap: '8px' }}>
          {!tripId ? (
            <button
              onClick={handleRequestRide}
              disabled={!origin || !dest || isRequesting}
              className="btn btn-primary"
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                padding: '12px',
                fontSize: '0.95rem',
              }}
            >
              <Navigation size={18} />
              {isRequesting ? 'Requesting Ride...' : 'Request Ride'}
            </button>
          ) : (
            <button
              onClick={handleReset}
              className="btn btn-secondary"
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                padding: '10px',
              }}
            >
              <RotateCcw size={16} />
              New Ride
            </button>
          )}

          {(origin || dest) && !tripId && (
            <button
              onClick={handleReset}
              className="btn btn-secondary"
              style={{ padding: '10px 14px' }}
              title="Reset points"
            >
              <RotateCcw size={16} />
            </button>
          )}
        </div>

        {/* Live Trip & Telematics Panel */}
        {tripId && (
          <div
            style={{
              padding: '14px',
              background: 'rgba(255, 255, 255, 0.03)',
              borderRadius: '12px',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Trip ID</span>
              <span style={{ fontSize: '0.82rem', fontFamily: 'monospace', color: '#6366f1', fontWeight: 600 }}>
                {tripId.slice(0, 13)}…
              </span>
            </div>

            {driverId && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Assigned Driver</span>
                <span style={{ fontSize: '0.82rem', fontWeight: 600, color: '#f8fafc' }}>
                  {driverId}
                </span>
              </div>
            )}

            {driverPos && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  fontSize: '0.78rem',
                  color: '#10b981',
                  background: 'rgba(16, 185, 129, 0.08)',
                  padding: '6px 10px',
                  borderRadius: '6px',
                }}
              >
                <Radio size={14} className="animate-pulse" />
                <span>
                  Live GPS: {driverPos[0].toFixed(4)}, {driverPos[1].toFixed(4)}
                  {driverSpeed !== null ? ` • ${(driverSpeed * 3.6).toFixed(1)} km/h` : ''}
                  {lastPingTime ? ` (${lastPingTime})` : ''}
                </span>
              </div>
            )}

            {tripId && (status === 'MATCHED' || status === 'IN_PROGRESS') && (
              <button
                onClick={simulateDriverDrive}
                disabled={isSimulating}
                className="btn btn-primary"
                style={{
                  marginTop: '4px',
                  background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                  color: '#090d16',
                  fontWeight: 700,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  boxShadow: '0 0 15px rgba(245, 158, 11, 0.4)',
                }}
              >
                <Zap size={18} />
                {isSimulating ? 'Simulating Taxi Drive...' : 'Simulate Taxi Drive 🚕'}
              </button>
            )}
          </div>
        )}

        {/* Step 5 Payment Panel when Trip is COMPLETED */}
        {status === 'COMPLETED' && (
          <div style={{ marginTop: '8px' }}>
            <PaymentPanel
              tripId={tripId || ''}
              clientSecret={clientSecret || `pi_mock_${tripId}_secret_mock`}
              amountMinor={fareAmount}
              onPaymentSuccess={() => {
                setStatus('PAID');
              }}
            />
          </div>
        )}
      </div>

      {/* Full-Screen Leaflet Map */}
      <MapContainer
        center={CASA_CENTER}
        zoom={12}
        style={{ width: '100%', height: '100%', zIndex: 1 }}
        zoomControl={false}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {/* Casablanca Prefectures & Zones Overlay */}
        {zones && (
          <GeoJSON
            data={zones}
            style={() => ({
              color: '#6366f1',
              weight: 1.5,
              opacity: 0.65,
              fillColor: '#818cf8',
              fillOpacity: 0.06,
            })}
            onEachFeature={(feature, layer) => {
              if (feature.properties && feature.properties.zone_name) {
                layer.bindTooltip(
                  `<strong>Zone ${feature.properties.zone_id}</strong>: ${feature.properties.zone_name}`,
                  { permanent: false, direction: 'center' }
                );
              }
            }}
          />
        )}

        {/* Map Click Picker for Origin and Destination */}
        {!tripId && <MapClickHandler onPick={handleMapPick} disabled={Boolean(origin && dest)} />}

        {/* Pickup Marker */}
        {origin && (
          <Marker position={origin} icon={originIcon}>
            <Popup>
              <strong>Pickup Point A</strong>
              <br />
              {origin[0].toFixed(5)}, {origin[1].toFixed(5)}
            </Popup>
          </Marker>
        )}

        {/* Destination Marker */}
        {dest && (
          <Marker position={dest} icon={destIcon}>
            <Popup>
              <strong>Dropoff Point B</strong>
              <br />
              {dest[0].toFixed(5)}, {dest[1].toFixed(5)}
            </Popup>
          </Marker>
        )}

        {/* Live Driver Taxi Icon Marker */}
        {driverPos && (
          <Marker position={driverPos} icon={driverIcon}>
            <Popup>
              <strong>🚕 {driverId || 'Assigned Driver'}</strong>
              <br />
              Speed: {driverSpeed !== null ? `${(driverSpeed * 3.6).toFixed(1)} km/h` : 'N/A'}
              <br />
              Status: {status}
            </Popup>
          </Marker>
        )}
      </MapContainer>
      </div>
    </div>
  );
}
