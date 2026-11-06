/**
 * Where to go after signing in. The page a signed-out user wanted travels as `?next=` through the sign-in and
 * registration pages (and survives a full reload after a 401), so an invite link opened while signed out ends in the
 * room. Only paths inside the app are accepted, so a crafted link cannot send the user to another site.
 */
export function safeNextPath(next) {
  // Browsers drop tabs and line breaks from URLs and read a backslash as a slash, so "/\t/host" would become
  // "//host", another site
  if (typeof next !== 'string' || !next.startsWith('/') || next.startsWith('//') || next.includes('\\')
    || [...next].some((char) => char.charCodeAt(0) < 0x20 || char.charCodeAt(0) === 0x7f)) {
    return '/';
  }
  return new URL(next, window.location.origin).origin === window.location.origin ? next : '/';
}

/**
 * The sign-in or registration page that leads back to `path` afterwards.
 */
export function authPath(page, path) {
  const next = safeNextPath(path);
  return next === '/' ? page : `${page}?next=${encodeURIComponent(next)}`;
}
