// Core settings of the tab canvas's alphaTab instance (KAN-32, KAN-108). Kept apart from TabCanvas.jsx so a node test
// can check them without loading alphaTab.

/**
 * Where alphaTab loads its music font (Bravura) from. The alphaTab Vite plugin copies the fonts into `public/font/`,
 * which Vite serves at `/font/` under the dev server and in the build. Without this setting alphaTab derives the
 * directory from its own script URL: that works under the dev server, but in the build the script is a hashed bundle
 * in `/assets/`, so it asked for `/assets/font/` (404) and no tab rendered (KAN-108).
 */
export const FONT_DIRECTORY = '/font/';

export const CORE_SETTINGS = Object.freeze({
  fontDirectory: FONT_DIRECTORY,
  // The whole tab is rendered up front, so every measure has bounds for the cursor. Rendering runs on the main
  // thread: it happens once per load, and alphaTab's web worker does not start under the Vite dev server.
  // (With workers, the bounds are only ready on postRenderFinished.)
  enableLazyLoading: false,
  useWorkers: false,
});
