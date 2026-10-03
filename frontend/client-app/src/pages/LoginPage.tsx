import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Car, Lock, Mail, ArrowRight, CheckCircle2, AlertCircle, ShieldCheck } from 'lucide-react';

export default function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!email || !password) {
      setError('Please fill in all required fields.');
      return;
    }

    if (isRegisterMode && password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    setLoading(true);
    try {
      if (isRegisterMode) {
        await register(email, password, 'CLIENT');
        setSuccessMsg('Account created successfully! Redirecting...');
      } else {
        await login(email, password);
      }
      setTimeout(() => {
        navigate('/request-ride');
      }, 400);
    } catch (err: any) {
      console.error('Auth error:', err);
      const serverMsg = err?.body?.error || err?.body?.message || err?.message || 'Authentication failed. Please check your credentials.';
      setError(serverMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (testEmail: string, testPass: string) => {
    setEmail(testEmail);
    setPassword(testPass);
    if (isRegisterMode) setConfirmPassword(testPass);
    setError('');
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      padding: '24px 16px',
    }}>
      {/* Container */}
      <div style={{ width: '100%', maxWidth: '440px' }}>
        
        {/* Header Branding */}
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '64px',
            height: '64px',
            borderRadius: '20px',
            background: 'linear-gradient(135deg, #fbbf24 0%, #d97706 100%)',
            boxShadow: '0 0 30px rgba(245, 158, 11, 0.45)',
            marginBottom: '16px',
          }}>
            <Car size={34} color="#090d16" strokeWidth={2.5} />
          </div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: '6px' }}>
            TaaSim <span style={{ color: 'var(--taxi-amber)' }}>Casablanca</span>
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
            Next-Gen Ride-Hailing Client Portal
          </p>
        </div>

        {/* Card */}
        <div className="glass-panel-elevated" style={{ padding: '36px 32px' }}>
          
          {/* Tabs */}
          <div style={{
            display: 'flex',
            background: 'rgba(15, 23, 42, 0.6)',
            padding: '4px',
            borderRadius: 'var(--radius-md)',
            marginBottom: '28px',
            border: '1px solid var(--border-subtle)',
          }}>
            <button
              type="button"
              onClick={() => { setIsRegisterMode(false); setError(''); }}
              style={{
                flex: 1,
                padding: '9px 12px',
                border: 'none',
                borderRadius: '8px',
                fontFamily: 'var(--font-heading)',
                fontSize: '0.9rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: !isRegisterMode ? 'rgba(255, 255, 255, 0.1)' : 'transparent',
                color: !isRegisterMode ? 'var(--text-main)' : 'var(--text-muted)',
                transition: 'all 0.2s ease',
              }}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { setIsRegisterMode(true); setError(''); }}
              style={{
                flex: 1,
                padding: '9px 12px',
                border: 'none',
                borderRadius: '8px',
                fontFamily: 'var(--font-heading)',
                fontSize: '0.9rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: isRegisterMode ? 'rgba(255, 255, 255, 0.1)' : 'transparent',
                color: isRegisterMode ? 'var(--text-main)' : 'var(--text-muted)',
                transition: 'all 0.2s ease',
              }}
            >
              Create Account
            </button>
          </div>

          {/* Feedback Alerts */}
          {error && (
            <div className="alert-error">
              <AlertCircle size={18} style={{ flexShrink: 0 }} />
              <span>{error}</span>
            </div>
          )}

          {successMsg && (
            <div className="alert-success">
              <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
              <span>{successMsg}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit}>
            <div className="input-group">
              <label className="input-label" htmlFor="email-input">Email Address</label>
              <div style={{ position: 'relative' }}>
                <Mail size={17} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '15px' }} />
                <input
                  id="email-input"
                  className="input-field"
                  style={{ paddingLeft: '42px' }}
                  type="email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  required
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label" htmlFor="password-input">Password</label>
              <div style={{ position: 'relative' }}>
                <Lock size={17} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '15px' }} />
                <input
                  id="password-input"
                  className="input-field"
                  style={{ paddingLeft: '42px' }}
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            {isRegisterMode && (
              <div className="input-group">
                <label className="input-label" htmlFor="confirm-pass-input">Confirm Password</label>
                <div style={{ position: 'relative' }}>
                  <ShieldCheck size={17} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '15px' }} />
                  <input
                    id="confirm-pass-input"
                    className="input-field"
                    style={{ paddingLeft: '42px' }}
                    type="password"
                    value={confirmPassword}
                    onChange={e => setConfirmPassword(e.target.value)}
                    placeholder="••••••••"
                    required
                  />
                </div>
              </div>
            )}

            <button
              id="submit-auth-btn"
              type="submit"
              className="btn-primary"
              disabled={loading}
              style={{ marginTop: '12px' }}
            >
              {loading ? (
                <>
                  <div className="pulse-dot" style={{ background: '#090d16' }} />
                  <span>Processing...</span>
                </>
              ) : (
                <>
                  <span>{isRegisterMode ? 'Complete Registration' : 'Sign In to TaaSim'}</span>
                  <ArrowRight size={18} />
                </>
              )}
            </button>
          </form>

          {/* Quick Demo Credentials */}
          <div style={{
            marginTop: '28px',
            paddingTop: '20px',
            borderTop: '1px solid var(--border-subtle)',
            fontSize: '0.8rem',
            color: 'var(--text-muted)',
            textAlign: 'center',
          }}>
            <span style={{ display: 'block', marginBottom: '8px' }}>⚡ Quick Test Credentials:</span>
            <button
              type="button"
              onClick={() => handleQuickFill(`rider_${Math.floor(Math.random()*9000+1000)}@taasim.ma`, 'Secret123!')}
              style={{
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--taxi-amber)',
                padding: '6px 12px',
                borderRadius: '6px',
                cursor: 'pointer',
                fontFamily: 'monospace',
                fontSize: '0.78rem',
              }}
            >
              Random Client Demo + Secret123!
            </button>
          </div>

        </div>
      </div>
    </div>
  );
}
