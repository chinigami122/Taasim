import { useState } from 'react';
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { CreditCard, CheckCircle2, AlertCircle } from 'lucide-react';

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
      } else if (result.paymentIntent?.status === 'succeeded') {
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

interface PaymentPanelProps {
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
        <p role="status">Checkout is unavailable. Configure Stripe test keys and retry fetching the fare. No payment has been taken.</p>
      )}
    </div>
  );
}
