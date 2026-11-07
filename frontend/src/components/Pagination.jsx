export default function Pagination({ page, totalPages, totalElements, size, onPage, onSize }) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="pagination">
      <span className="muted">
        Showing {from}–{to} of {totalElements}
      </span>
      <div className="pagination-controls">
        <select value={size} onChange={(e) => onSize(Number(e.target.value))} aria-label="Rows per page">
          {[10, 25, 50, 100].map((s) => (
            <option key={s} value={s}>{s} / page</option>
          ))}
        </select>
        <button className="btn btn-ghost" disabled={page === 0} onClick={() => onPage(page - 1)}>‹ Prev</button>
        <span className="muted">Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
        <button className="btn btn-ghost" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next ›</button>
      </div>
    </div>
  );
}
