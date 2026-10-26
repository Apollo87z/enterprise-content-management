# Content Hub frontend

React, TypeScript, Vite, and Collabora Online frontend for the Java ECM backend.

## Run

```bash
npm install
cp .env.example .env
npm run dev
```

Open http://127.0.0.1:5173.

## Expected backend routes

- `POST /api/v1/auth/login` with `{ "username": "...", "password": "..." }`, returning `{ "accessToken": "..." }`
- `GET /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `POST /api/v1/documents` with multipart field `file`
- `DELETE /api/v1/documents/{id}`
- `GET /api/v1/documents/{id}/download`
- `POST /api/v1/documents/{id}/wopi-token` returning `accessToken`, `access_token`, or `token`
- `GET /wopi/files/{id}` and WOPI contents endpoints

Verify route and response compatibility against your backend before using upload, download, or delete. Document list accepts an array or a paginated object with `content` or `documents`.
