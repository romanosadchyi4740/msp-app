/**
 * WCAG 2.x contrast between colours, for checking that the theme's colours stay readable (KAN-53).
 * Colours are `#rrggbb` or `rgba(r,g,b,a)`; a translucent colour is first blended onto the background.
 */

// WCAG AA: normal text, and large text or graphical objects such as staff lines
export const AA_TEXT = 4.5;
export const AA_GRAPHICS = 3;

/**
 * `{ r, g, b, a }` with channels 0 to 255 and alpha 0 to 1.
 */
export function parseColor(color) {
  const hex = /^#([0-9a-f]{6})$/i.exec(color);
  if (hex) {
    const value = parseInt(hex[1], 16);
    return { r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255, a: 1 };
  }
  const rgba = /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(color);
  if (rgba) {
    return { r: Number(rgba[1]), g: Number(rgba[2]), b: Number(rgba[3]), a: rgba[4] === undefined ? 1 : Number(rgba[4]) };
  }
  throw new Error(`Not a colour: ${color}`);
}

function channel(value) {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminance({ r, g, b }) {
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/**
 * The contrast ratio of a foreground colour on an opaque background, from 1 to 21.
 */
export function contrastRatio(foreground, background) {
  const back = parseColor(background);
  const front = parseColor(foreground);
  const blended = {
    r: front.r * front.a + back.r * (1 - front.a),
    g: front.g * front.a + back.g * (1 - front.a),
    b: front.b * front.a + back.b * (1 - front.a),
  };
  const [light, dark] = [luminance(blended), luminance(back)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}
