import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useToast } from '../components/Toast.jsx';
import { dateTime } from '../format.js';

const ACCEPT = '.xlsx,.xls';

export default function UploadPurchases() {
  const notify = useToast();
  const inputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState(null); // dry-run result
  const [imported, setImported] = useState(null); // final result
  const [error, setError] = useState('');
  const [history, setHistory] = useState([]);
  const [emailInvoices, setEmailInvoices] = useState(false);

  const loadHistory = () => api.uploads().then(setHistory).catch(() => {});
  useEffect(() => { loadHistory(); }, []);

  function choose(f) {
    setError('');
    setPreview(null);
    setImported(null);
    if (!f) return;
    if (!/\.xlsx?$/i.test(f.name)) {
      setError('Please choose an Excel file (.xlsx or .xls).');
      return;
    }
    if (f.size > 10 * 1024 * 1024) {
      setError('File is larger than 10 MB.');
      return;
    }
    setFile(f);
    validate(f);
  }

  async function validate(f) {
    setBusy(true);
    try {
      setPreview(await api.uploadExcel(f, true));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function runImport() {
    setBusy(true);
    try {
      const result = await api.uploadExcel(file, false, emailInvoices);
      setImported(result);
      setPreview(null);
      notify(`Imported ${result.importedRows} of ${result.totalRows} rows`);
      loadHistory();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    setFile(null);
    setPreview(null);
    setImported(null);
    setError('');
    if (inputRef.current) inputRef.current.value = '';
  }

  const result = imported || preview;

  return (
    <div className="stack">
      <div className="steps">
        <Step n={1} title="Download the template" active={!file}>
          <p className="muted">Fill one row per purchase. Gold headers are required; column order doesn't matter.</p>
          <button className="btn btn-secondary" onClick={() => api.downloadTemplate().catch((e) => notify(e.message, 'error'))}>
            ⤓ Download Excel template
          </button>
        </Step>
        <Step n={2} title="Upload & validate" active={file && !imported}>
          <p className="muted">Each row is checked before anything is saved.</p>
        </Step>
        <Step n={3} title="Import" active={!!imported}>
          <p className="muted">Valid rows are saved; rows with errors are skipped and listed.</p>
        </Step>
      </div>

      <section
        className={`card dropzone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); choose(e.dataTransfer.files[0]); }}
        onClick={() => !busy && inputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && inputRef.current?.click()}
      >
        <input ref={inputRef} type="file" accept={ACCEPT} hidden onChange={(e) => choose(e.target.files[0])} />
        <div className="dropzone-icon">📄</div>
        {file ? (
          <>
            <div className="dropzone-title">{file.name}</div>
            <div className="muted">{(file.size / 1024).toFixed(1)} KB · click or drop to replace</div>
          </>
        ) : (
          <>
            <div className="dropzone-title">Drag &amp; drop your purchase sheet here</div>
            <div className="muted">or click to browse · .xlsx or .xls · max 10 MB</div>
          </>
        )}
      </section>

      {busy && <div className="loading">{preview ? 'Importing…' : 'Validating…'}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      {result && !busy && (
        <section className="card">
          <div className="card-head">
            <h3>{imported ? 'Import complete' : 'Validation result'}</h3>
            <span className="muted">{result.fileName}</span>
          </div>

          <div className="result-stats">
            <div><strong>{result.totalRows}</strong><span>rows in file</span></div>
            <div className="ok"><strong>{result.importedRows}</strong><span>{imported ? 'imported' : 'ready to import'}</span></div>
            <div className={result.failedRows ? 'bad' : ''}><strong>{result.failedRows}</strong><span>{imported ? 'skipped' : 'with errors'}</span></div>
          </div>

          {result.errors.length > 0 && (
            <>
              <h4 className="section-title">Rows with errors {imported ? '(not imported)' : '(will be skipped)'}</h4>
              <div className="table-wrap errors-table">
                <table className="table">
                  <thead><tr><th>Row</th><th>Invoice</th><th>Problem</th></tr></thead>
                  <tbody>
                    {result.errors.map((e) => (
                      <tr key={e.row}>
                        <td className="mono">{e.row}</td>
                        <td className="mono">{e.invoiceNumber || '—'}</td>
                        <td>
                          <ul className="problems">
                            {e.message.split('; ').map((m) => <li key={m}>{m}</li>)}
                          </ul>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <div className="result-actions">
            {preview && (
              <>
                <label className="checkbox result-check">
                  <input type="checkbox" checked={emailInvoices} onChange={(e) => setEmailInvoices(e.target.checked)} />
                  Email each customer their invoice (rows with an email address)
                </label>
                <button className="btn btn-ghost" onClick={reset}>Cancel</button>
                <button className="btn btn-primary" disabled={preview.importedRows === 0} onClick={runImport}>
                  Import {preview.importedRows} valid row{preview.importedRows === 1 ? '' : 's'}
                </button>
              </>
            )}
            {imported && (
              <>
                <button className="btn btn-ghost" onClick={reset}>Upload another file</button>
                <Link to="/sales" className="btn btn-primary">View sales records</Link>
              </>
            )}
          </div>
        </section>
      )}

      <section className="card no-pad">
        <div className="card-head padded"><h3>Upload history</h3></div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>File</th><th>Uploaded</th><th className="num">Rows</th><th className="num">Imported</th><th className="num">Skipped</th></tr>
            </thead>
            <tbody>
              {history.map((h) => (
                <tr key={h.id}>
                  <td>{h.fileName}</td>
                  <td>{dateTime(h.uploadedAt)}</td>
                  <td className="num">{h.totalRows}</td>
                  <td className="num text-green">{h.importedRows}</td>
                  <td className={`num ${h.failedRows ? 'text-red' : ''}`}>{h.failedRows}</td>
                </tr>
              ))}
              {history.length === 0 && <tr><td colSpan={5} className="empty">No uploads yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function Step({ n, title, active, children }) {
  return (
    <div className={`card step ${active ? 'step-active' : ''}`}>
      <div className="step-num">{n}</div>
      <div>
        <h3>{title}</h3>
        {children}
      </div>
    </div>
  );
}
