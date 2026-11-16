/**
 * The colours alphaTab draws a tab with, per app theme, and the page background they are drawn on (the main layout's
 * zinc-950 and zinc-50). Glyphs (notes, numbers, titles) are text and need WCAG AA's 4.5:1; staff lines and bar
 * lines are graphics and need 3:1 (canvasColors.test.js checks both).
 */
export const CANVAS_BACKGROUNDS = {
  dark: '#09090b',
  light: '#fafafa',
};

export const CANVAS_COLORS = {
  dark: {
    mainGlyphColor: '#fafafa',
    secondaryGlyphColor: 'rgba(250,250,250,0.7)',
    scoreInfoColor: '#fafafa',
    staffLineColor: '#a1a1aa',
    barSeparatorColor: '#d4d4d8',
    barNumberColor: '#a1a1aa',
  },
  light: {
    mainGlyphColor: '#09090b',
    secondaryGlyphColor: 'rgba(9,9,11,0.7)',
    scoreInfoColor: '#09090b',
    staffLineColor: '#52525b',
    barSeparatorColor: '#27272a',
    barNumberColor: '#52525b',
  },
};

export const TEXT_COLOR_KEYS = ['mainGlyphColor', 'secondaryGlyphColor', 'scoreInfoColor', 'barNumberColor'];
export const GRAPHIC_COLOR_KEYS = ['staffLineColor', 'barSeparatorColor'];

/**
 * The colours for a theme; an unknown theme gets the dark ones, the app's default.
 */
export function canvasColorsFor(theme) {
  return CANVAS_COLORS[theme] ?? CANVAS_COLORS.dark;
}
