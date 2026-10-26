import { useEffect, useState } from 'react';
import { ArrowLeft, Download, FilePenLine, FileText, Trash2 } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { documents } from '../lib/api';
import type { DocumentItem } from '../lib/api';

export default function DocumentDetail() {
  const { id: param } = useParams();
  const id = Number(param);
  const navigate = useNavigate();

  const [item, setItem] = useState<DocumentItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;

    if (!Number.isSafeInteger(id) || id <= 0) {
        setLoading(false);
      return;
    }

    documents.get(id)
      .then(data => {
        if (active) setItem(data);
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id]);

  async function download() {
    if (!item) return;

    setBusy(true);

    try {
      await documents.download(id, item.name);
    } catch {
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!item || !window.confirm(`Delete ${item.name}? This cannot be undone.`)) {
      return;
    }

    setBusy(true);

    try {
      await documents.remove(id);
      navigate('/documents', { replace: true });
    } catch {
      setBusy(false);
    }
  }

  return (
    <>
      <Link className="back-link" to="/documents">
        <ArrowLeft size={17} />
        Back to documents
      </Link>

      <div className="page-heading">
        <div>
          <span className="eyebrow">DOCUMENT DETAILS</span>
          <h1>{item?.name || 'Document'}</h1>
          <p>View document information and available actions.</p>
        </div>
      </div>

      {loading ? (
        <div className="panel empty">Loading document…</div>
      ) : item ? (
        <div className="panel detail-panel">
          <div className="detail-header">
            <span className="detail-file-icon"><FileText size={32} /></span>
            <div>
              <h2>{item.name}</h2>
              <p>Document ID {item.id}</p>
            </div>
          </div>

          <div className="detail-grid">
            <div>
              <span>Content type</span>
              <strong>{item.contentType || 'Unknown'}</strong>
            </div>
            <div>
              <span>File size</span>
              <strong>
                {typeof item.size === 'number'
                  ? `${(item.size / 1024).toFixed(1)} KB`
                  : '—'}
              </strong>
            </div>
          </div>

          <div className="actions">
            <Link to={`/documents/${id}/edit`} className="primary">
              <FilePenLine size={17} />
              Open editor
            </Link>

            <button className="secondary" disabled={busy} onClick={download}>
              <Download size={17} />
              Download
            </button>

            <button className="danger" disabled={busy} onClick={remove}>
              <Trash2 size={17} />
              Delete
            </button>
          </div>
        </div>
      ) : null}
    </>
  );
}
