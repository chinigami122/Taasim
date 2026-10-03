import { useAuth } from '../auth/AuthContext';
import { Car, LogOut, User, Shield } from 'lucide-react';

export default function Navbar() {
  const { email, role, logout } = useAuth();

  return (
    <header style={{
      width: '100%',
      borderBottom: '1px solid var(--border-subtle)',
      backgroundColor: 'rgba(9, 13, 22, 0.85)',
      backdropFilter: 'blur(16px)',
      position: 'sticky',
      top: 0,
      zIndex: 50,
    }}>
      <div style={{
        maxWidth: '1200px',
        margin: '0 auto',
        padding: '14px 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '12px',
            background: 'linear-gradient(135deg, #fbbf24 0%, #d97706 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 15px rgba(245, 158, 11, 0.4)',
          }}>
            <Car size={22} color="#090d16" strokeWidth={2.5} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontFamily: 'var(--font-heading)', fontSize: '1.25rem', fontWeight: 800, color: '#f8fafc', letterSpacing: '-0.03em' }}>
                TaaSim
              </span>
              <span style={{
                fontSize: '0.65rem',
                fontWeight: 800,
                color: '#f59e0b',
                backgroundColor: 'rgba(245, 158, 11, 0.15)',
                padding: '2px 6px',
                borderRadius: '4px',
                border: '1px solid rgba(245, 158, 11, 0.3)',
                letterSpacing: '0.05em'
              }}>
                CASABLANCA
              </span>
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Smart Ride-Hailing Platform
            </div>
          </div>
        </div>

        {/* User Info & Actions */}
        {email && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '6px 14px',
              borderRadius: '9999px',
              background: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid var(--border-subtle)',
            }}>
              <User size={15} color="var(--text-secondary)" />
              <span style={{ fontSize: '0.85rem', color: 'var(--text-main)', fontWeight: 500 }}>
                {email}
              </span>
              <span className="badge badge-indigo" style={{ padding: '2px 8px', fontSize: '0.68rem' }}>
                <Shield size={10} />
                {role?.replace('ROLE_', '') || 'CLIENT'}
              </span>
            </div>

            <button
              onClick={logout}
              className="btn-secondary"
              title="Logout"
              style={{ padding: '8px 14px', fontSize: '0.82rem' }}
            >
              <LogOut size={15} />
              <span>Logout</span>
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
