/** "Join as": a request only; an admin decides the actual role. */
export default function RoleChoice({ value, onChange }) {
  const options = [
    { id: 'USER', title: 'Sales team', hint: 'Record sales and follow installations' },
    { id: 'AGENT', title: 'Installation agent', hint: 'Pick up and carry out installation jobs' },
  ];
  return (
    <fieldset className="role-choice">
      <legend>Join as</legend>
      <div className="role-options">
        {options.map((o) => (
          <label key={o.id} className={`role-option ${value === o.id ? 'selected' : ''}`}>
            <input type="radio" name="requestedRole" value={o.id} checked={value === o.id} onChange={() => onChange(o.id)} />
            <span className="role-option-title">{o.title}</span>
            <span className="muted small">{o.hint}</span>
          </label>
        ))}
      </div>
      <small className="muted">An admin approves your account and confirms your role.</small>
    </fieldset>
  );
}
