import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { api } from '../api.js';
import { rememberReturnPath } from '../auth.jsx';
import ProviderIcon from './ProviderIcon.jsx';

/** "Continue with Google / GitHub" for whichever providers the backend has configured (renders nothing if none). */
export default function OAuthButtons() {
  const location = useLocation();
  const [providers, setProviders] = useState([]);

  useEffect(() => {
    api.providers().then(setProviders).catch(() => setProviders([]));
  }, []);

  if (providers.length === 0) return null;

  return (
    <>
      <div className="divider"><span>or</span></div>
      {providers.map((p) => (
        <a
          key={p.id}
          href={p.loginUrl}
          className={`btn btn-oauth btn-oauth-${p.id}`}
          onClick={() => rememberReturnPath(location.state?.from)}
        >
          <ProviderIcon id={p.id} />
          Continue with {p.name}
        </a>
      ))}
    </>
  );
}
