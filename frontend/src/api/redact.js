// Keeps secrets out of the browser console (KAN-107). The app logs failed requests with console.error(message, error),
// and an axios error carries the request it came from: the Authorization header with the JWT and the request body
// (passwords, message text). Anyone who sees the console, e.g. during a screen share, could read and reuse them.

export const REDACTED = '[redacted]';

// Header names are case-insensitive: the token may sit under `authorization` as well
function redactHeaders(headers) {
  if (!headers || typeof headers !== 'object') return;
  for (const name of Object.keys(headers)) {
    if (name.toLowerCase() === 'authorization' && headers[name] !== undefined && headers[name] !== null) {
      headers[name] = REDACTED;
    }
  }
}

function redactConfig(config) {
  if (!config) return;
  redactHeaders(config.headers);
  if (config.data !== undefined && config.data !== null) {
    config.data = REDACTED;
  }
}

/**
 * Removes the token and the request body from an axios error before callers can log it. Everything a caller acts on
 * stays: the status and the server's error response, the URL and method, and flags such as `suppressErrorToast`.
 * Mutates and returns the error; anything that is not an object is returned unchanged.
 */
export function redactAxiosError(error) {
  if (!error || typeof error !== 'object') return error;
  redactConfig(error.config);
  if (error.response?.config !== error.config) {
    redactConfig(error.response?.config);
  }
  return error;
}

/**
 * The STOMP client's debug text without frame headers and bodies: only the first line, such as `>>> CONNECT` or
 * `<<< MESSAGE`. A frame's headers hold the token on CONNECT, and message bodies hold chat text and room events.
 */
export function stompDebugLine(text) {
  return String(text).split('\n', 1)[0];
}
