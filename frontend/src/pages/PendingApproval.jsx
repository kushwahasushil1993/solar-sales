import { useEffect } from 'react';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

const WANTS = { USER: 'the sales team', AGENT: 'an installation agent' };

/** Signed in, but an admin still has to choose this account's role (sales or agent). */
export default function PendingApproval() {
  const { user, refreshUser, logout } = useAuth();

  // let them in as soon as an admin approves the account
  useEffect(() => {
    const timer = setInterval(() => refreshUser().catch(() => {}), 15000);
    return () => clearInterval(timer);
  }, [refreshUser]);

  return (
    <AuthLayout title="Waiting for approval" subtitle={`Thanks for signing up, ${user.fullName.split(' ')[0]}!`}>
      <div className="auth-form">
        <div className="alert alert-info">
          An admin needs to approve your account and choose whether you work as part of the sales team or as an
          installation agent.
          {user.requestedRole && <> You asked to join as <strong>{WANTS[user.requestedRole]}</strong>.</>}
        </div>
        <p className="muted small">
          This page checks automatically. You'll get in as soon as you're approved, so there's no need to sign in again.
          Signed in as {user.email || user.phone}.
        </p>
        <button className="btn btn-secondary btn-block" onClick={() => refreshUser()}>Check again</button>
        <button className="btn btn-ghost btn-block" onClick={logout}>Sign out</button>
      </div>
    </AuthLayout>
  );
}
