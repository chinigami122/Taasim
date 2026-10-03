import { useState, useEffect, useRef } from 'react';
import { TripsService } from '../api';
import Navbar from '../components/Navbar';
import {
  Car,
  MapPin,
  Navigation,
  Clock,
  CheckCircle,
  AlertTriangle,
  RotateCcw,
  History,
  Copy,
  Check,
  Compass,
  ArrowRight
} from 'lucide-react';

const CASABLANCA_ZONES = [
  { id: 1, name: 'Zone 1 — Centre Ville / Maarif' },
  { id: 2, name: 'Zone 2 — Sidi Belyout / Port' },
  { id: 3, name: 'Zone 3 — Bourgogne / Anfa' },
  { id: 4, name: 'Zone 4 — Gauthier / Mers Sultan' },
  { id: 5, name: 'Zone 5 — Ain Diab / Corniche' },
  { id: 6, name: 'Zone 6 — Hay Hassani' },
  { id: 7, name: 'Zone 7 — Oulfa' },
  { id: 8, name: 'Zone 8 — Roches Noires' },
  { id: 9, name: 'Zone 9 — Ain Sebaa' },
  { id: 10, name: 'Zone 10 — Sidi Bernoussi' },
  { id: 11, name: 'Zone 11 — Sbata / Ben M\'sik' },
  { id: 12, name: 'Zone 12 — Sidi Maarouf / Technopark' },
  { id: 13, name: 'Zone 13 — Californie' },
  { id: 14, name: 'Zone 14 — Bouskoura Ville Verte' },
  { id: 15, name: 'Zone 15 — Mediouna' },
  { id: 16, name: 'Zone 16 — Mohammed V Airport (CMN)' },
];

export default function RequestRidePage() {
  const [originZone, setOriginZone] = useState<number>(5);
  const [destZone, setDestZone] = useState<number>(12);
  const [useExactCoordinates, setUseExactCoordinates] = useState<boolean>(false);
  const [originLat, setOriginLat] = useState<number>(33.5898);
  const [originLon, setOriginLon] = useState<number>(-7.6631);
  const [destLat, setDestLat] = useState<number>(33.5284);
  const [destLon, setDestLon] = useState<number>(-7.6415);

  const [tripId, setTripId] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('IDLE');
  const [driverId, setDriverId] = useState<string | null>(null);
  const [etaSeconds, setEtaSeconds] = useState<number | null>(null);
  const [error, setError] = useState<string>('');
  const [copied, setCopied] = useState<boolean>(false);

  const [recentTrips, setRecentTrips] = useState<any[]>([]);
  const [historyLoading, setHistoryLoading] = useState<boolean>(false);

  const pollIntervalRef = useRef<any>(null);

  // Clean up polling interval on unmount
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    };
  }, []);

  // Fetch recent trips on mount
  useEffect(() => {
    fetchTripHistory();
  }, []);

  const fetchTripHistory = async () => {
    setHistoryLoading(true);
    try {
      const res: any = await TripsService.getMyTrips({ limit: 5 });
      if (Array.isArray(res)) {
        setRecentTrips(res);
      } else if (res?.trips && Array.isArray(res.trips)) {
        setRecentTrips(res.trips);
      }
    } catch (err) {
      console.warn('Could not load trip history:', err);
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleRequestRide = async () => {
    setError('');
    setStatus('SUBMITTING');
    setDriverId(null);
    setEtaSeconds(null);

    try {
      const payload: any = {
        originZone,
        destinationZone: destZone,
      };

      if (useExactCoordinates) {
        payload.originLat = originLat;
        payload.originLon = originLon;
        payload.destinationLat = destLat;
        payload.destinationLon = destLon;
      }

      const res: any = await TripsService.requestTrip({
        requestBody: payload
      });

      const assignedTripId = res.trip_id || res.tripId || res.id;
      const initialStatus = res.status || 'REQUESTED';

      setTripId(assignedTripId);
      setStatus(initialStatus);

      // Start status polling
      startPolling(assignedTripId);
      // Refresh history list
      fetchTripHistory();
    } catch (err: any) {
      console.error('Request ride failed:', err);
      setError(err?.body?.message || err?.body?.error || 'Failed to submit trip request. Ensure you are signed in.');
      setStatus('IDLE');
    }
  };

  const startPolling = (currentTripId: string) => {
    if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);

    pollIntervalRef.current = setInterval(async () => {
      try {
        const trip: any = await TripsService.getTrip({ tripId: currentTripId });
        const currentStatus = trip.status || trip.tripStatus;
        setStatus(currentStatus);

        if (trip.driver_id || trip.driverId) {
          setDriverId(trip.driver_id || trip.driverId);
        }
        if (trip.eta_seconds || trip.etaSeconds) {
          setEtaSeconds(trip.eta_seconds || trip.etaSeconds);
        }

        // Stop polling on terminal states
        if (['ACCEPTED', 'COMPLETED', 'CANCELLED'].includes(currentStatus)) {
          clearInterval(pollIntervalRef.current);
          fetchTripHistory();
        }
      } catch (pollErr) {
        console.warn('Polling error for trip ' + currentTripId, pollErr);
      }
    }, 1500);
  };

  const handleReset = () => {
    if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    setTripId(null);
    setStatus('IDLE');
    setDriverId(null);
    setEtaSeconds(null);
    setError('');
  };

  const copyTripId = () => {
    if (tripId) {
      navigator.clipboard.writeText(tripId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  // Helper for dynamic badge rendering
  const renderStatusBadge = () => {
    switch (status) {
      case 'SUBMITTING':
        return (
          <span className="badge badge-indigo">
            <div className="pulse-dot" style={{ backgroundColor: 'currentColor' }} />
            Submitting Booking
          </span>
        );
      case 'REQUESTED':
        return (
          <span className="badge badge-amber" style={{ animation: 'pulse-dot 2s infinite ease' }}>
            <div className="pulse-dot" style={{ backgroundColor: '#fbbf24' }} />
            Matching Taxi...
          </span>
        );
      case 'MATCHED':
        return (
          <span className="badge badge-emerald">
            <CheckCircle size={13} />
            Taxi Matched
          </span>
        );
      case 'ACCEPTED':
        return (
          <span className="badge badge-emerald">
            <Car size={13} />
            Driver Accepted & En Route
          </span>
        );
      case 'IN_PROGRESS':
        return (
          <span className="badge badge-indigo">
            <Navigation size={13} />
            Ride In Progress
          </span>
        );
      case 'COMPLETED':
        return (
          <span className="badge badge-emerald">
            <CheckCircle size={13} />
            Trip Completed
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="badge badge-rose">
            <AlertTriangle size={13} />
            Trip Cancelled
          </span>
        );
      default:
        return (
          <span className="badge" style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-muted)' }}>
            Ready
          </span>
        );
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Navbar />

      <main style={{
        flex: 1,
        maxWidth: '1200px',
        width: '100%',
        margin: '0 auto',
        padding: '36px 24px',
      }}>
        {/* Page Hero */}
        <div style={{ marginBottom: '32px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <Compass size={20} color="var(--taxi-amber)" />
            <span style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--taxi-amber)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
              Casablanca Dispatch & Telematics
            </span>
          </div>
          <h1 style={{ fontSize: '2.4rem', fontWeight: 800, marginBottom: '8px' }}>
            Request a Ride
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '1rem', maxWidth: '600px' }}>
            Select your Casablanca pickup zone and destination. Our intelligent matching engine pairs you with the closest taxi in real-time.
          </p>
        </div>

        {error && (
          <div className="alert-error" style={{ maxWidth: '800px', marginBottom: '24px' }}>
            <AlertTriangle size={18} style={{ flexShrink: 0 }} />
            <span>{error}</span>
          </div>
        )}

        {/* 2-Column Layout */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: '28px',
          alignItems: 'start',
        }}>
          
          {/* Booking Form Card */}
          <div className="glass-panel" style={{ padding: '32px' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <MapPin size={20} color="var(--taxi-amber)" />
              Route Configuration
            </h2>

            <div className="input-group">
              <label className="input-label" htmlFor="origin-zone-select">Pickup Location</label>
              <select
                id="origin-zone-select"
                className="input-field"
                value={originZone}
                disabled={status !== 'IDLE'}
                onChange={e => setOriginZone(Number(e.target.value))}
              >
                {CASABLANCA_ZONES.map(z => (
                  <option key={`origin-${z.id}`} value={z.id}>
                    {z.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="input-group">
              <label className="input-label" htmlFor="dest-zone-select">Destination</label>
              <select
                id="dest-zone-select"
                className="input-field"
                value={destZone}
                disabled={status !== 'IDLE'}
                onChange={e => setDestZone(Number(e.target.value))}
              >
                {CASABLANCA_ZONES.map(z => (
                  <option key={`dest-${z.id}`} value={z.id}>
                    {z.name}
                  </option>
                ))}
              </select>
            </div>

            {/* GPS toggle */}
            <div style={{
              margin: '18px 0 24px 0',
              padding: '14px 16px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid var(--border-subtle)',
            }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600 }}>
                <input
                  type="checkbox"
                  checked={useExactCoordinates}
                  disabled={status !== 'IDLE'}
                  onChange={e => setUseExactCoordinates(e.target.checked)}
                  style={{ accentColor: 'var(--taxi-amber)', width: '16px', height: '16px' }}
                />
                <span>Include Exact GPS Coordinates</span>
              </label>

              {useExactCoordinates && (
                <div style={{ marginTop: '14px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Origin Lat/Lon</span>
                    <input
                      className="input-field"
                      style={{ padding: '8px 12px', fontSize: '0.85rem', marginTop: '4px' }}
                      type="number"
                      step="0.0001"
                      value={originLat}
                      disabled={status !== 'IDLE'}
                      onChange={e => setOriginLat(parseFloat(e.target.value))}
                    />
                    <input
                      className="input-field"
                      style={{ padding: '8px 12px', fontSize: '0.85rem', marginTop: '6px' }}
                      type="number"
                      step="0.0001"
                      value={originLon}
                      disabled={status !== 'IDLE'}
                      onChange={e => setOriginLon(parseFloat(e.target.value))}
                    />
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Dest Lat/Lon</span>
                    <input
                      className="input-field"
                      style={{ padding: '8px 12px', fontSize: '0.85rem', marginTop: '4px' }}
                      type="number"
                      step="0.0001"
                      value={destLat}
                      disabled={status !== 'IDLE'}
                      onChange={e => setDestLat(parseFloat(e.target.value))}
                    />
                    <input
                      className="input-field"
                      style={{ padding: '8px 12px', fontSize: '0.85rem', marginTop: '6px' }}
                      type="number"
                      step="0.0001"
                      value={destLon}
                      disabled={status !== 'IDLE'}
                      onChange={e => setDestLon(parseFloat(e.target.value))}
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Action buttons */}
            {status === 'IDLE' ? (
              <button
                id="request-ride-btn"
                type="button"
                className="btn-primary"
                onClick={handleRequestRide}
              >
                <Car size={20} />
                <span>Confirm & Request Ride</span>
              </button>
            ) : (
              <button
                type="button"
                className="btn-secondary"
                style={{ width: '100%' }}
                onClick={handleReset}
              >
                <RotateCcw size={16} />
                <span>Book Another Ride</span>
              </button>
            )}
          </div>

          {/* Real-time Status Card */}
          <div className="glass-panel-elevated" style={{ padding: '32px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Navigation size={20} color="var(--indigo-accent)" />
                Live Trip Telematics
              </h2>
              {renderStatusBadge()}
            </div>

            {tripId ? (
              <div>
                {/* Trip ID Block */}
                <div style={{
                  padding: '16px',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(15, 23, 42, 0.7)',
                  border: '1px solid var(--border-subtle)',
                  marginBottom: '20px',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                      Trip Reference
                    </span>
                    <button
                      onClick={copyTripId}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: copied ? 'var(--success)' : 'var(--text-secondary)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        fontSize: '0.78rem',
                      }}
                    >
                      {copied ? <Check size={14} /> : <Copy size={14} />}
                      <span>{copied ? 'Copied' : 'Copy'}</span>
                    </button>
                  </div>
                  <code style={{ fontSize: '0.9rem', color: 'var(--taxi-amber)', wordBreak: 'break-all', fontFamily: 'monospace' }}>
                    {tripId}
                  </code>
                </div>

                {/* Progress Timeline */}
                <div style={{
                  padding: '20px 16px',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid var(--border-subtle)',
                  marginBottom: '20px',
                }}>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '14px' }}>
                    Trip Progression
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
                    
                    {/* Step 1: Requested */}
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', zIndex: 2 }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        background: ['REQUESTED', 'MATCHED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(status)
                          ? 'var(--taxi-amber)' : 'rgba(255,255,255,0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#090d16',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                      }}>
                        1
                      </div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>Requested</span>
                    </div>

                    {/* Step 2: Matched */}
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', zIndex: 2 }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        background: ['MATCHED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(status)
                          ? 'var(--success)' : 'rgba(255,255,255,0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#090d16',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                      }}>
                        2
                      </div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>Matched</span>
                    </div>

                    {/* Step 3: Accepted */}
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', zIndex: 2 }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        background: ['ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(status)
                          ? 'var(--indigo-accent)' : 'rgba(255,255,255,0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#ffffff',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                      }}>
                        3
                      </div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>Accepted</span>
                    </div>

                    {/* Step 4: Completed */}
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', zIndex: 2 }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        background: status === 'COMPLETED' ? 'var(--success)' : 'rgba(255,255,255,0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#090d16',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                      }}>
                        4
                      </div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>Completed</span>
                    </div>

                  </div>
                </div>

                {/* Driver Match Info */}
                {driverId && (
                  <div style={{
                    padding: '16px',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(16, 185, 129, 0.08)',
                    border: '1px solid rgba(16, 185, 129, 0.25)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <div style={{
                        width: '38px',
                        height: '38px',
                        borderRadius: '10px',
                        background: 'rgba(16, 185, 129, 0.2)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}>
                        <Car size={20} color="var(--success)" />
                      </div>
                      <div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Assigned Taxi Driver</div>
                        <div style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-main)' }}>{driverId}</div>
                      </div>
                    </div>

                    {etaSeconds !== null && (
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Clock size={12} /> ETA
                        </div>
                        <div style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--success)' }}>
                          {Math.round(etaSeconds / 60)} min
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {status === 'REQUESTED' && (
                  <div style={{
                    marginTop: '16px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    fontSize: '0.85rem',
                    color: 'var(--taxi-amber)',
                  }}>
                    <div className="pulse-dot" style={{ backgroundColor: 'var(--taxi-amber)' }} />
                    <span>Searching for available drivers near Casablanca Zone {originZone}...</span>
                  </div>
                )}

              </div>
            ) : (
              <div style={{
                padding: '48px 24px',
                textAlign: 'center',
                color: 'var(--text-muted)',
              }}>
                <Car size={42} strokeWidth={1.5} style={{ opacity: 0.3, marginBottom: '14px' }} />
                <p style={{ fontSize: '0.95rem', marginBottom: '6px' }}>No active trip selected</p>
                <p style={{ fontSize: '0.82rem' }}>Configure pickup and dropoff on the left to start booking.</p>
              </div>
            )}
          </div>

        </div>

        {/* Recent Trip History Section */}
        <section style={{ marginTop: '48px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <History size={18} color="var(--text-secondary)" />
              Recent Booking History
            </h3>
            <button
              onClick={fetchTripHistory}
              className="btn-secondary"
              disabled={historyLoading}
              style={{ fontSize: '0.8rem', padding: '6px 12px' }}
            >
              {historyLoading ? 'Refreshing...' : 'Refresh History'}
            </button>
          </div>

          {recentTrips.length > 0 ? (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: '16px',
            }}>
              {recentTrips.map((t: any, idx: number) => {
                const tripRef = t.trip_id || t.tripId || `trip-${idx}`;
                const tripStat = t.status || t.tripStatus || 'UNKNOWN';
                const originZ = t.origin_zone || t.originZone || '-';
                const destZ = t.destination_zone || t.destinationZone || '-';
                const driver = t.driver_id || t.driverId;

                return (
                  <div
                    key={tripRef}
                    className="glass-panel"
                    style={{
                      padding: '16px 20px',
                      borderRadius: 'var(--radius-md)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--taxi-amber)' }}>
                        {tripRef.length > 18 ? tripRef.substring(0, 18) + '...' : tripRef}
                      </span>
                      <span className={`badge ${tripStat === 'COMPLETED' ? 'badge-emerald' : tripStat === 'MATCHED' ? 'badge-amber' : 'badge-indigo'}`} style={{ fontSize: '0.68rem', padding: '2px 8px' }}>
                        {tripStat}
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem', color: 'var(--text-main)', marginTop: '4px' }}>
                      <span>Zone {originZ}</span>
                      <ArrowRight size={14} color="var(--text-muted)" />
                      <span>Zone {destZ}</span>
                    </div>

                    {driver && (
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        Driver: <strong style={{ color: 'var(--text-secondary)' }}>{driver}</strong>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="glass-panel" style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.88rem' }}>
              No previous trips found in your account history.
            </div>
          )}
        </section>

      </main>
    </div>
  );
}
