import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { ArrowRight, FileText, Search, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { documents } from '../lib/api';
import type { DocumentItem } from '../lib/api';

export default function Documents() {
  const [items, setItems] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [query, setQuery] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

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

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;

    setUploading(true);

    try {
      await documents.upload(file);
      setItems(await documents.list());
    } catch {
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  }

  const filtered = items.filter(item =>
    item.name.toLowerCase().includes(query.trim().toLowerCase())
  );

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CONTENT LIBRARY</span>
          <h1>Documents</h1>
          <p>Upload, organize, and edit your files.</p>
        </div>

        <button
          className="primary"
          disabled={uploading}
          onClick={() => fileInput.current?.click()}
        >
          <UploadCloud size={18} />
          {uploading ? 'Uploading…' : 'Upload document'}
        </button>

        <input ref={fileInput} type="file" hidden onChange={upload} />
      </div>

      <div className="panel">
        <div className="toolbar">
          <div className="search-field">
            <Search size={18} />
            <input
              aria-label="Search documents"
              placeholder="Search documents"
              value={query}
              onChange={event => setQuery(event.target.value)}
            />
          </div>

          <span className="muted">{filtered.length} documents</span>
        </div>

        {loading ? (
          <div className="empty">Loading documents…</div>
        ) : filtered.length > 0 ? (
          <div className="document-list">
            {filtered.map(item => (
              <Link
                key={item.id}
                className="document-row"
                to={`/documents/${item.id}`}
              >
                <span className="file-icon"><FileText size={20} /></span>
                <span className="file-info">
                  <strong>{item.name}</strong>
                  <small>{item.contentType || 'Document'} · ID {item.id}</small>
                </span>
                <ArrowRight size={18} />
              </Link>
            ))}
          </div>
        ) : (
          <div className="empty">
            {query ? 'No documents match your search.' : 'No documents yet. Upload a file to get started.'}
          </div>
        )}
      </div>
    </>
  );
}
