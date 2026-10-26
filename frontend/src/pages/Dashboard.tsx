import { useEffect, useState } from 'react';
import { ArrowRight, FileText, FolderOpen, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { documents } from '../lib/api';
import type { DocumentItem } from '../lib/api';

export default function Dashboard() {
  const [items, setItems] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    documents.list()
      .then(data => {
        if (active) setItems(data);
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">OVERVIEW</span>
          <h1>Dashboard</h1>
          <p>Access and manage your documents.</p>
        </div>

        <Link to="/documents" className="primary">
          <UploadCloud size={17} />
          Manage documents
        </Link>
      </div>

      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-icon"><FolderOpen /></div>
          <span>Total documents</span>
          <strong>{loading ? '—' : items.length}</strong>
        </div>

        <div className="stat-card">
          <div className="stat-icon"><FileText /></div>
          <span>Document library</span>
          <strong>Active</strong>
        </div>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Recent documents</h2>
            <p>Your latest available files</p>
          </div>

          <Link className="text-link" to="/documents">
            View all
            <ArrowRight size={16} />
          </Link>
        </div>

        {loading ? (
          <div className="empty">Loading documents…</div>
        ) : items.length > 0 ? (
          <div className="document-list">
            {items.slice(0, 5).map(item => (
              <Link className="document-row" key={item.id} to={`/documents/${item.id}`}>
                <span className="file-icon"><FileText size={20} /></span>
                <span className="file-title">{item.name}</span>
                <ArrowRight size={17} />
              </Link>
            ))}
          </div>
        ) : (
          <div className="empty">No documents uploaded yet.</div>
        )}
      </section>
    </>
  );
}
