import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Info } from 'lucide-react';
import { documents } from '../lib/api';

interface EditorSession {
  url: string;
  token: string;
}

function getToken(response: Record<string, unknown>): string {
  const value = response.accessToken ?? response.access_token ?? response.token;

  if (typeof value !== 'string' || !value) {
    throw new Error('Invalid editor session response');
  }

  return value;
}

function getEditorUrl(xml: string, extension: string): string {
  const discovery = new DOMParser().parseFromString(xml, 'application/xml');

  if (discovery.querySelector('parsererror')) {
    throw new Error('Invalid Collabora discovery response');
  }

  const actions = Array.from(discovery.getElementsByTagName('action'));
  const matching = actions.filter(
    action => action.getAttribute('ext')?.toLowerCase() === extension
  );

  const action =
    matching.find(item => item.getAttribute('name') === 'edit') ??
    matching.find(item => item.getAttribute('name') === 'view');

  const url = action?.getAttribute('urlsrc');

  if (!url) {
    throw new Error(`No Collabora editor available for .${extension}`);
  }

  return url;
}

export default function Editor() {
  const { id: param } = useParams();
  const id = Number(param);

  const [name, setName] = useState('Document');
  const [session, setSession] = useState<EditorSession | null>(null);
  const [loading, setLoading] = useState(true);

  const form = useRef<HTMLFormElement>(null);
  const submitted = useRef(false);

  useEffect(() => {
    let active = true;

    submitted.current = false;
    setSession(null);
    setLoading(true);

    async function start() {
      try {
        if (!Number.isSafeInteger(id) || id <= 0) {
          throw new Error('Invalid document ID');
        }

        const document = await documents.get(id);
        if (!active) return;

        setName(document.name);

        const extension = document.name.split('.').pop()?.toLowerCase() || '';

        if (!/^[a-z0-9]{1,12}$/.test(extension)) {
          throw new Error('Unsupported document extension');
        }

        const discovery = await fetch('/collabora/hosting/discovery', {
          cache: 'no-store',
          credentials: 'same-origin'
        });

        if (!discovery.ok) {
          throw new Error(`Collabora unavailable (${discovery.status})`);
        }

        const action = getEditorUrl(await discovery.text(), extension);
        const collabora = new URL(
          import.meta.env.COLLABORA_URL || 'http://localhost:9981'
        );
        const editor = new URL(action);

        if (editor.origin !== collabora.origin) {
          throw new Error('Unexpected Collabora editor origin');
        }

        const wopi = new URL(
          import.meta.env.WOPI_PUBLIC_URL || 'http://backend:8080'
        );

        if (wopi.protocol !== 'http:' && wopi.protocol !== 'https:') {
          throw new Error('Invalid WOPI server URL');
        }

        editor.searchParams.set(
          'WOPISrc',
          new URL(`/wopi/files/${id}`, wopi).toString()
        );

        const token = getToken(await documents.wopiToken(id));

        if (active) {
          setSession({ url: editor.toString(), token });
        }
      } catch {
      } finally {
        if (active) setLoading(false);
      }
    }

    void start();

    return () => {
      active = false;
    };
  }, [id]);

  useEffect(() => {
    if (!session || !form.current || submitted.current) return;

    submitted.current = true;
    form.current.submit();
  }, [session]);

  return (
    <>
      <Link className="back-link" to={`/documents/${id}`}>
        <ArrowLeft size={17} />
        Back to document
      </Link>

      <div className="page-heading">
        <div>
          <span className="eyebrow">COLLABORA ONLINE</span>
          <h1>{name}</h1>
          <p>Edit your document.</p>
        </div>
      </div>

      {loading && <div className="panel empty">Preparing editor…</div>}

      {session && (
        <>
          <div className="notice">
            <Info size={17} />
            Changes are saved through the document server.
          </div>

          <form
            ref={form}
            method="POST"
            action={session.url}
            target="collabora-frame"
            style={{ display: 'none' }}
          >
            <input
              type="hidden"
              name="access_token"
              value={session.token}
              readOnly
            />
            <input
              type="hidden"
              name="access_token_ttl"
              value="0"
              readOnly
            />
          </form>

          <div className="editor-frame">
            <iframe
              name="collabora-frame"
              title="Collabora document editor"
              allow="clipboard-read; clipboard-write"
              referrerPolicy="no-referrer"
            />
          </div>
        </>
      )}
    </>
  );
}
