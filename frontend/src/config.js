// Build-time configuration from Vite env vars (see .env.example).
// An empty VITE_API_URL means the API is served from the same origin as the frontend.
export const API_URL = import.meta.env.VITE_API_URL ?? '';
export const WS_URL = import.meta.env.VITE_WS_URL || `${API_URL}/ws`;
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '';
