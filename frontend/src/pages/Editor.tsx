
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  AlertCircle,
  Info,
  RefreshCw
} from 'lucide-react';
import { documents } from '../lib/api';

interface EditorSession {
  url: string;
  token: string;
}

const DEFAULT_COLLABORA_URL = 'http://localhost:9981';
const INTERNAL_COLLABORA_URL = 'http://collabora:9980';
const DEFAULT_WOPI_URL = 'http://backend:8080';

function getToken(response: Record<string, unknown>): string {
  const value =
    response.accessToken ??
    response.access_token ??
    response.token;

  if (typeof value !== 'string' || !value) {
    throw new Error('Invalid WOPI token response from backend');
  }

  return value;
}

function getEditorUrl(xml: string, extension: string): string {
  const discovery = new DOMParser().parseFromString(
    xml,
    'application/xml'
  );

  if (discovery.querySelector('parsererror')) {
    throw new Error('Invalid Collabora discovery XML response');
  }

  const actions = Array.from(
    discovery.getElementsByTagName('action')
  );

  const matching = actions.filter(
    action =>
      action.getAttribute('ext')?.toLowerCase() === extension
  );

  const action =
    matching.find(item => item.getAttribute('name') === 'edit') ??
    matching.find(item => item.getAttribute('name') === 'view');

  const url = action?.getAttribute('urlsrc');

  if (!url) {
    throw new Error(
      `Collabora does not support editing or viewing .${extension} files`
    );
  }

  return url;
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function resolveEditorUrl(actionUrl: string): URL {
  const browserCollabora = new URL(
    import.meta.env.VITE_COLLABORA_URL ||
      DEFAULT_COLLABORA_URL
  );

  const internalCollabora = new URL(INTERNAL_COLLABORA_URL);
  const editor = new URL(actionUrl);

  const allowedOrigins = [
    browserCollabora.origin,
    internalCollabora.origin
  ];

  if (!allowedOrigins.includes(editor.origin)) {
    throw new Error(
      `Unexpected Collabora origin: ${editor.origin}. ` +
      `Allowed origins: ${allowedOrigins.join(', ')}`
    );
  }

  // Discovery may return a Docker-internal address.
  // The browser must connect through the published port.
  editor.protocol = browserCollabora.protocol;
  editor.host = browserCollabora.host;

  return editor;
}

export default function Editor() {
  const { id: param } = useParams();
  const id = Number(param);

  const [name, setName] = useState('Document');
  const [session, setSession] = useState<EditorSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [step, setStep] = useState('Initializing editor');
  const [retryCount, setRetryCount] = useState(0);

  const form = useRef<HTMLFormElement>(null);
  const submitted = useRef(false);

  useEffect(() => {
    let active = true;
    let currentStep = 'Initializing editor';

    const updateStep = (value: string) => {
      currentStep = value;
      if (active) setStep(value);
    };

    submitted.current = false;
    setSession(null);
    setLoading(true);
    setError(null);
    updateStep('Initializing editor');

    async function start() {
      try {
        if (!Number.isSafeInteger(id) || id <= 0) {
          throw new Error('Invalid document ID');
        }

        // 1. Load document metadata
        updateStep('Loading document information');

        const document = await documents.get(id);

        if (!active) return;

        setName(document.name);

        const extension =
          document.name.split('.').pop()?.toLowerCase() || '';

        if (!/^[a-z0-9]{1,12}$/.test(extension)) {
          throw new Error('Unsupported document extension');
        }

        // 2. Get Collabora discovery information
        updateStep('Connecting to Collabora');

        const discovery = await fetch(
          '/collabora/hosting/discovery',
          {
            cache: 'no-store',
            credentials: 'same-origin'
          }
        );

        if (!discovery.ok) {
          throw new Error(
            `Collabora discovery failed: HTTP ${discovery.status}`
          );
        }

        const xml = await discovery.text();
        const actionUrl = getEditorUrl(xml, extension);

        // 3. Resolve the browser-accessible editor URL
        updateStep('Resolving Collabora editor URL');

        const editor = resolveEditorUrl(actionUrl);

        // 4. Configure the WOPI URL
        updateStep('Configuring WOPI connection');

        const wopi = new URL(
          import.meta.env.VITE_WOPI_PUBLIC_URL ||
            DEFAULT_WOPI_URL
        );

        if (
          wopi.protocol !== 'http:' &&
          wopi.protocol !== 'https:'
        ) {
          throw new Error('Invalid WOPI server URL');
        }

        const wopiFileUrl = new URL(
          `/wopi/files/${id}`,
          wopi
        ).toString();

        editor.searchParams.set('WOPISrc', wopiFileUrl);

        // 5. Request a document-scoped WOPI token
        updateStep('Requesting WOPI access token');

        const tokenResponse = await documents.wopiToken(id);
        const token = getToken(tokenResponse);

        if (!active) return;

        updateStep('Loading Collabora editor');

        setSession({
          url: editor.toString(),
          token
        });
      } catch (err) {
        if (!active) return;

        const message = getErrorMessage(err);

        console.error(
          '[Collabora Editor] Initialization failed',
          {
            documentId: id,
            step: currentStep,
            message
          }
        );

        setError(message);
        setSession(null);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void start();

    return () => {
      active = false;
    };
  }, [id, retryCount]);

  useEffect(() => {
    if (!session || !form.current || submitted.current) {
      return;
    }

    submitted.current = true;

    try {
      form.current.submit();
    } catch (err) {
      setError(
        `Unable to submit Collabora editor form: ${getErrorMessage(err)}`
      );
    }
  }, [session]);

  const retry = () => {
    setRetryCount(count => count + 1);
  };

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

      {loading && (
        <div className="panel empty">
          <RefreshCw size={20} />
          <p>Preparing editor…</p>
          <p>{step}</p>
        </div>
      )}

      {error && (
        <div
          className="panel"
          role="alert"
          style={{
            padding: 24,
            border: '1px solid #dc2626',
            borderRadius: 10
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              color: '#dc2626',
              marginBottom: 12
            }}
          >
            <AlertCircle size={24} />
            <h2 style={{ margin: 0 }}>
              Collabora failed to load
            </h2>
          </div>

          <p>
            <strong>Failed step:</strong> {step}
          </p>

          <p>
            <strong>Error:</strong> {error}
          </p>

          <p>
            <strong>Document ID:</strong> {id}
          </p>

          <button
            type="button"
            onClick={retry}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginTop: 16,
              padding: '10px 16px',
              cursor: 'pointer'
            }}
          >
            <RefreshCw size={16} />
            Retry Editor
          </button>
        </div>
      )}

      {session && !error && (
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
              key={retryCount}
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
