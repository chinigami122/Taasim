import { useState } from 'react';
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { CreditCard, CheckCircle2, AlertCircle, ShieldCheck, Sparkles } from 'lucide-react';

const stripePk = import.meta.env.VITE_STRIPE_PK || '';
const isRealStripeKey = Boolean(stripePk && stripePk.startsWith('pk_') && !stripePk.includes('placeholder'));
const stripePromise = isRealStripeKey ? loadStripe(stripePk) : null;

interface InnerFormProps {
  tripId: string;
  amountMinor?: number;
  onPaymentSuccess?: () => void;
}

function StripeRealForm({ tripId, amountMinor, onPaymentSuccess }: InnerFormProps) {
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!stripe || !elements) return;

    setLoading(true);
    setErrorMsg(null);

    try {
      const result = await stripe.confirmPayment({
        elements,
        redirect: 'if_required',
      });

      if (result.error) {
        setErrorMsg(result.error.message || 'Payment confirmation failed');
      } else {
        setSuccess(true);
        onPaymentSuccess?.();
      }
    } catch (err: any) {
      setErrorMsg(err?.message || 'Unexpected payment error occurred');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="payment-success-card">
        <CheckCircle2 size={44} className="text-emerald animate-bounce" />
        <h4 style={{ margin: '8px 0 4px', color: '#10b981' }}>Fare Settled Successfully</h4>
        <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
          Stripe Payment Completed for Trip #{tripId.slice(0, 8)}
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="payment-form">
      <PaymentElement />
      {errorMsg && (
        <div className="form-error" style={{ marginTop: '12px' }}>
          <AlertCircle size={16} />
          <span>{errorMsg}</span>
        </div>
      )}
      <button
        type="submit"
        disabled={!stripe || loading}
        className="btn btn-primary"
        style={{ width: '100%', marginTop: '16px' }}
      >
        {loading ? 'Processing Payment...' : `Pay ${amountMinor ? (amountMinor / 100).toFixed(2) : '25.00'} MAD`}
      </button>
    </form>
  );
}

function MockPaymentForm({ tripId, amountMinor, onPaymentSuccess }: InnerFormProps) {
  const [cardNumber, setCardNumber] = useState('4242 4242 4242 4242');
  const [expiry, setExpiry] = useState('12/28');
  const [cvc, setCvc] = useState('123');
  const [loading, setLoading] = useState(false);
  const [paid, setPaid] = useState(false);

  const handlePay = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setPaid(true);
      onPaymentSuccess?.();
    }, 900);
  };

  if (paid) {
    return (
      <div className="payment-success-card" style={{
        padding: '24px',
        textAlign: 'center',
        background: 'rgba(16, 185, 129, 0.08)',
        border: '1px solid rgba(16, 185, 129, 0.25)',
        borderRadius: '12px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '8px'
      }}>
        <div style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          background: 'rgba(16, 185, 129, 0.15)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#10b981'
        }}>
          <CheckCircle2 size={32} />
        </div>
        <h4 style={{ margin: '8px 0 2px', color: '#10b981', fontSize: '1.1rem' }}>Payment Confirmed ✅</h4>
        <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
          Stripe mock charge settled for Trip #{tripId.slice(0, 8)}
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handlePay} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        fontSize: '0.78rem',
        padding: '8px 12px',
        borderRadius: '8px',
        background: 'rgba(255, 184, 0, 0.08)',
        border: '1px solid rgba(255, 184, 0, 0.2)',
        color: '#f59e0b'
      }}>
        <Sparkles size={16} />
        <span>Stripe Sandbox Mode — Test Card Loaded</span>
      </div>

      <div className="input-group">
        <label className="label">Card Number</label>
        <div style={{ position: 'relative' }}>
          <input
            className="input"
            value={cardNumber}
            onChange={(e) => setCardNumber(e.target.value)}
            placeholder="4242 4242 4242 4242"
            required
            style={{ paddingLeft: '38px', fontFamily: 'monospace' }}
          />
          <CreditCard
            size={18}
            style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', opacity: 0.5 }}
          />
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
        <div className="input-group">
          <label className="label">Expires</label>
          <input
            className="input"
            value={expiry}
            onChange={(e) => setExpiry(e.target.value)}
            placeholder="MM/YY"
            required
            style={{ fontFamily: 'monospace' }}
          />
        </div>
        <div className="input-group">
          <label className="label">CVC</label>
          <input
            className="input"
            value={cvc}
            onChange={(e) => setCvc(e.target.value)}
            placeholder="123"
            required
            style={{ fontFamily: 'monospace' }}
          />
        </div>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="btn btn-primary"
        style={{ width: '100%', marginTop: '6px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px' }}
      >
        <ShieldCheck size={18} />
        {loading ? 'Authorizing Payment...' : `Pay ${amountMinor ? (amountMinor / 100).toFixed(2) : '25.00'} MAD`}
      </button>
    </form>
  );
}

export interface PaymentPanelProps {
  tripId: string;
  clientSecret: string;
  amountMinor?: number;
  onPaymentSuccess?: () => void;
}

export default function PaymentPanel({
  tripId,
  clientSecret,
  amountMinor = 2500,
  onPaymentSuccess,
}: PaymentPanelProps) {
  const useRealElements = isRealStripeKey && clientSecret && !clientSecret.startsWith('pi_mock_');

  return (
    <div className="card" style={{
      padding: '24px',
      background: 'var(--card-bg, #181c24)',
      border: '1px solid var(--border-color, rgba(255,255,255,0.08))',
      borderRadius: '16px',
      boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '36px',
            height: '36px',
            borderRadius: '8px',
            background: 'rgba(99, 102, 241, 0.15)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--primary-color, #6366f1)'
          }}>
            <CreditCard size={20} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 600 }}>Trip Fare Settlement</h3>
            <p style={{ margin: 0, fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
              Secure Payment via Stripe
            </p>
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            {(amountMinor / 100).toFixed(2)} MAD
          </div>
          <span style={{
            fontSize: '0.7rem',
            padding: '2px 8px',
            borderRadius: '999px',
            background: 'rgba(16, 185, 129, 0.12)',
            color: '#10b981',
            fontWeight: 500
          }}>
            Fare Ready
          </span>
        </div>
      </div>

      {useRealElements && stripePromise ? (
        <Elements stripe={stripePromise} options={{ clientSecret }}>
          <StripeRealForm
            tripId={tripId}
            amountMinor={amountMinor}
            onPaymentSuccess={onPaymentSuccess}
          />
        </Elements>
      ) : (
        <MockPaymentForm
          tripId={tripId}
          amountMinor={amountMinor}
          onPaymentSuccess={onPaymentSuccess}
        />
      )}
    </div>
  );
}
