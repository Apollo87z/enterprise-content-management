
export interface DocumentItem {
  id: number;
  name: string;
  contentType?: string;
  size?: number;
  createdAt?: string;
  updatedAt?: string;
  uploadedAt?: string;
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

const TOKEN_KEY = 'ecm_access_token';

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const base64 = parts[1]
      .replace(/-/g, '+')
      .replace(/_/g, '/');

    const padded = base64.padEnd(
      Math.ceil(base64.length / 4) * 4,
      '='
    );

    const payload: unknown = JSON.parse(atob(padded));

    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return null;
    }

    return payload as Record<string, unknown>;
  } catch {
    return null;
  }
}

function isTokenUsable(token: string): boolean {
  const payload = decodeJwtPayload(token);

  if (!payload) return false;

  if (typeof payload.sub !== 'string' || !payload.sub.trim()) {
    return false;
  }

  if (typeof payload.exp !== 'number') {
    return false;
  }

  return payload.exp > Math.floor(Date.now() / 1000);
}

function restoreAccessToken(): string | null {
  const token = sessionStorage.getItem(TOKEN_KEY);

  if (!token) return null;

  if (!isTokenUsable(token)) {
    sessionStorage.removeItem(TOKEN_KEY);
    return null;
  }

  return token;
}

let accessToken: string | null = restoreAccessToken();
let unauthorizedHandler: (() => void) | null = null;

export function getAccessToken(): string | null {
  if (accessToken && !isTokenUsable(accessToken)) {
    setAccessToken(null);
  }

  return accessToken;
}

export function getAuthenticatedUsername(): string | null {
  const token = getAccessToken();

  if (!token) return null;

  const payload = decodeJwtPayload(token);
  return typeof payload?.sub === 'string' ? payload.sub : null;
}

export function setAccessToken(token: string | null) {
  if (token && !isTokenUsable(token)) {
    throw new Error('Invalid or expired authentication token');
  }

  accessToken = token;

  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}

export function setUnauthorizedHandler(
  handler: (() => void) | null
) {
  unauthorizedHandler = handler;
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getAccessToken();

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  if (
    options.body &&
    !(options.body instanceof FormData) &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
    referrerPolicy: 'no-referrer'
  });

  if (response.status === 401 && token && token === getAccessToken()) {
    unauthorizedHandler?.();
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    const body = await response.text();

    if (body) {
      try {
        const data: unknown = JSON.parse(body);

        if (data && typeof data === 'object') {
          const result = data as Record<string, unknown>;
          const detail = result.message ?? result.error ?? result.detail;

          if (typeof detail === 'string' && detail.length < 250) {
            message = detail;
          }
        }
      } catch {
        message = `Request failed (${response.status})`;
      }
    }

    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') || '';

  if (!contentType.includes('json')) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const authApi = {
  login(username: string, password: string) {
    return request<{ accessToken: string; tokenType?: string }>(
      '/api/v1/auth/login',
      {
        method: 'POST',
        body: JSON.stringify({ username, password })
      }
    );
  }
};

function parseDocuments(value: unknown): DocumentItem[] {
  if (Array.isArray(value)) {
    return value as DocumentItem[];
  }

  if (value && typeof value === 'object') {
    const result = value as Record<string, unknown>;

    if (Array.isArray(result.content)) {
      return result.content as DocumentItem[];
    }

    if (Array.isArray(result.documents)) {
      return result.documents as DocumentItem[];
    }
  }

  throw new Error('Unexpected document list response');
}

export const documents = {
  async list() {
    return parseDocuments(
      await request<unknown>('/api/v1/documents')
    );
  },

  get(id: number) {
    return request<DocumentItem>(`/api/v1/documents/${id}`);
  },

  upload(file: File) {
    const data = new FormData();
    data.append('file', file);

    return request<DocumentItem>('/api/v1/documents/upload', {
      method: 'POST',
      body: data
    });
  },

  remove(id: number) {
    return request<void>(`/api/v1/documents/${id}`, {
      method: 'DELETE'
    });
  },

  wopiToken(id: number) {
    return request<Record<string, unknown>>(
      `/api/v1/documents/${id}/wopi-token`,
      { method: 'POST' }
    );
  },

  async download(id: number, name: string) {
    const headers = new Headers();
    const token = getAccessToken();

    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    const response = await fetch(
      `/api/v1/documents/${id}/download`,
      {
        headers,
        credentials: 'same-origin',
        cache: 'no-store',
        referrerPolicy: 'no-referrer'
      }
    );

    if (response.status === 401 && token && token === getAccessToken()) {
      unauthorizedHandler?.();
    }

    if (!response.ok) {
      throw new ApiError(
        `Download failed (${response.status})`,
        response.status
      );
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);

    try {
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = name;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
    } finally {
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
  }
};
