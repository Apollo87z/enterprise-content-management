import { useState } from 'react';
import type { FormEvent } from 'react';
import { FileText } from 'lucide-react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';


interface QuickAccount {
  role: string;
  username: string;
  password: string;
}

const quickAccounts: QuickAccount[] = __QUICK_ACCOUNTS__.filter(
  account => account.username.trim() !== '' && account.password !== ''
);

export default function Login() {
  const { authenticated, login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  async function signIn(name: string, secret: string) {
    if (busy) return;

    setBusy(true);

    try {
      await login(name.trim(), secret);
      navigate('/', { replace: true });
    } catch {
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void signIn(username, password);
  }

  function selectAccount(account: QuickAccount) {
    setUsername(account.username);
    setPassword(account.password);
    void signIn(account.username, account.password);
  }

  if (authenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-icon"><FileText size={27} /></div>
        <div className="eyebrow">CONTENT HUB</div>
        <h1>Welcome back</h1>
        <p>Sign in to Content Hub.</p>

        <form onSubmit={submit} className="form">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoComplete="username"
            value={username}
            onChange={event => setUsername(event.target.value)}
            required
            maxLength={120}
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={event => setPassword(event.target.value)}
            required
          />

          <button className="primary full" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        {quickAccounts.length > 0 && (
          <div className="demo-login">
            <div className="demo-login-heading">Quick sign in</div>
            <div className="demo-login-actions">
              {quickAccounts.map(account => (
                <button
                  key={account.role}
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => selectAccount(account)}
                >
                  {account.role}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
