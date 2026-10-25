import React, { useState } from 'react';

type TokenResponse = {
  tokenType: string;
  accessToken: string;
  expiresInSeconds: number;
  refreshToken: string;
  role: string;
};

type Props = {
  onClose: () => void;
  onAuthenticated: (tokens: TokenResponse) => void;
};

export default function AuthModal({ onClose, onAuthenticated }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(base + '/api/v1/auth/' + mode, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mode === 'register' ? { email, password, displayName } : { email, password })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || 'Authentication failed.');
      onAuthenticated(body as TokenResponse);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Authentication failed. Please retry.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="backdrop" onClick={onClose}>
    <section className="modal glass" role="dialog" aria-modal="true" aria-label="Account access" onClick={e => e.stopPropagation()}>
      <button className="close" onClick={onClose} aria-label="Close">×</button>
      <h2>{mode === 'login' ? 'Welcome back' : 'Create your traveller account'}</h2>
      <p>Secure access powered by RailReserve authentication.</p>
      <form onSubmit={submit} className="authform">
        {mode === 'register' && <label className="field"><span>DISPLAY NAME</span><input value={displayName} onChange={e => setDisplayName(e.target.value)} minLength={2} maxLength={100} required /></label>}
        <label className="field"><span>EMAIL</span><input type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={254} autoComplete="email" required /></label>
        <label className="field"><span>PASSWORD {mode === 'register' ? '(12 CHARACTERS MINIMUM)' : ''}</span><input type="password" value={password} onChange={e => setPassword(e.target.value)} minLength={mode === 'register' ? 12 : 1} maxLength={72} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required /></label>
        {error && <p className="autherror" role="alert">{error}</p>}
        <button className="searchbtn" disabled={loading}>{loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
      </form>
      <p className="authswitch">{mode === 'login' ? 'New to RailReserve?' : 'Already registered?'} <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}> {mode === 'login' ? 'Create an account' : 'Sign in'} </button></p>
    </section>
  </div>;
}
