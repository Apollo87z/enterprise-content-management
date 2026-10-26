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

let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
    referrerPolicy: 'no-referrer'
  });

  if (response.status === 401 && accessToken) {
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
    return parseDocuments(await request<unknown>('/api/v1/documents'));
  },

  get(id: number) {
    return request<DocumentItem>(`/api/v1/documents/${id}`);
  },

  upload(file: File) {
    const data = new FormData();
    data.append('file', file);

    return request<DocumentItem>('/api/v1/documents', {
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

    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`);
    }

    const response = await fetch(`/api/v1/documents/${id}/download`, {
      headers,
      cache: 'no-store',
      referrerPolicy: 'no-referrer'
    });

    if (response.status === 401) {
      unauthorizedHandler?.();
    }

    if (!response.ok) {
      throw new ApiError(`Download failed (${response.status})`, response.status);
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

