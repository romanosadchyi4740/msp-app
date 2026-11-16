// Contrast and focus audit for the a11y-audit skill (KAN-53, KAN-97, KAN-98). Paste the whole file into
// Claude in Chrome's javascript_tool on a page of the app; it defines window.__a11y and returns a short summary.
// It is page code, not part of the app build. All measurements follow WCAG 2.x relative luminance.
//
// window.__a11y.audit()            every visible text (AA 4.5:1, 3:1 for large text) and lucide icon (3:1)
// window.__a11y.sweep(paths)       audit() on each path, navigating inside the SPA so this script stays loaded
// window.__a11y.focusInfo(el, box) the focus indicator of el (outline, or the border of box) against what is
//                                  painted around it; use it after moving focus with the real Tab key
// window.__a11y.textOn(el)         one element's text contrast against what is painted behind it
// window.__a11y.settle(ms)         waits for CSS transitions; the automated window is throttled, so 0.15 s
//                                  transitions can take over a second
(() => {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 1;
  const context = canvas.getContext('2d', { willReadFrequently: true });

  // Any CSS colour, including Tailwind 4's oklch(), as { r, g, b, a }: the canvas converts it
  const parse = (color) => {
    context.clearRect(0, 0, 1, 1);
    context.fillStyle = '#000';
    context.fillStyle = color;
    context.fillRect(0, 0, 1, 1);
    const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
    return { r, g, b, a: a / 255 };
  };
  const channel = (v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const luminance = (c) => 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
  const blend = (front, back) => ({
    r: front.r * front.a + back.r * (1 - front.a),
    g: front.g * front.a + back.g * (1 - front.a),
    b: front.b * front.a + back.b * (1 - front.a),
    a: 1,
  });
  const ratio = (front, back) => {
    const [light, dark] = [luminance(blend(front, back)), luminance(back)].sort((x, y) => y - x);
    return +((light + 0.05) / (dark + 0.05)).toFixed(2);
  };

  // What is painted at a point: the stacked backgrounds there, bottom layer first. Pages often paint their
  // background on a sibling layer, so walking up the element's own ancestors would find nothing.
  const paintedAt = (x, y, skip) => {
    const layers = [];
    for (const element of document.elementsFromPoint(x, y)) {
      if (skip && skip.contains(element)) continue;
      const color = parse(getComputedStyle(element).backgroundColor);
      if (color.a > 0) {
        layers.push(color);
        if (color.a === 1) break;
      }
    }
    let back = { r: 255, g: 255, b: 255, a: 1 };
    for (const layer of layers.reverse()) back = blend(layer, back);
    return back;
  };
  const opacityOf = (element) => {
    let opacity = 1;
    for (let e = element; e; e = e.parentElement) opacity *= Number(getComputedStyle(e).opacity);
    return opacity;
  };
  const centre = (element) => {
    const r = element.getBoundingClientRect();
    return [r.left + r.width / 2, r.top + r.height / 2];
  };
  // Rendered and seen: not hidden, not transparent, and not visually hidden for screen readers only (sr-only clips
  // the element to a 1 px box)
  const visible = (element) => {
    const r = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    const clipped = style.clip === 'rect(0px, 0px, 0px, 0px)' || style.clipPath === 'inset(50%)'
      || (r.width <= 1 && r.height <= 1 && style.overflow === 'hidden');
    return r.width > 0 && r.height > 0 && style.visibility !== 'hidden' && !clipped && opacityOf(element) > 0;
  };

  // The background behind an element along its ancestors, for elements that are not on screen at their centre
  // (scrolled out of the window or out of a scrolling sidebar), where elementsFromPoint sees something else
  const ancestorBackground = (element) => {
    const layers = [];
    for (let e = element; e; e = e.parentElement) {
      const color = parse(getComputedStyle(e).backgroundColor);
      if (color.a > 0) {
        layers.push(color);
        if (color.a === 1) break;
      }
    }
    let back = { r: 255, g: 255, b: 255, a: 1 };
    for (const layer of layers.reverse()) back = blend(layer, back);
    return back;
  };
  const backgroundOf = (element) => {
    const [x, y] = centre(element);
    const top = x >= 0 && y >= 0 && x < innerWidth && y < innerHeight ? document.elementFromPoint(x, y) : null;
    const onScreen = top && (top === element || element.contains(top) || top.contains(element));
    return onScreen ? paintedAt(x, y) : ancestorBackground(element);
  };

  const textOn = (element) => {
    const color = parse(getComputedStyle(element).color);
    return ratio({ ...color, a: color.a * opacityOf(element) }, backgroundOf(element));
  };

  const audit = () => {
    const texts = [];
    let textCount = 0;
    for (const element of document.body.querySelectorAll('*')) {
      const text = [...element.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent.trim())
        .join(' ').trim();
      // aria-hidden text is still seen, so it is measured; disabled controls are exempt in WCAG
      if (!text || element.closest('[disabled],[aria-disabled=true]') || !visible(element)) continue;
      textCount++;
      const style = getComputedStyle(element);
      const size = parseFloat(style.fontSize);
      const large = size >= 24 || (size >= 18.66 && Number(style.fontWeight) >= 700);
      const value = textOn(element);
      if (value < (large ? 3 : 4.5)) texts.push(`${value} "${text.slice(0, 50)}"`);
    }
    const icons = [];
    let iconCount = 0;
    for (const svg of document.querySelectorAll('svg.lucide')) {
      if (!visible(svg) || svg.closest('[disabled]')) continue;
      iconCount++;
      const value = textOn(svg);
      if (value < 3) icons.push(`${value} ${[...svg.classList].filter((c) => c.startsWith('lucide-')).join(' ')}`);
    }
    return { page: location.pathname, theme: document.documentElement.className, texts: textCount,
      textFailures: texts, icons: iconCount, iconFailures: icons };
  };

  const settle = (ms = 1500) => new Promise((resolve) => setTimeout(resolve, ms));

  const sweep = async (paths) => {
    const lines = [];
    for (const path of paths) {
      history.pushState({}, '', path);
      dispatchEvent(new PopStateEvent('popstate'));
      await settle(2000);
      const a = audit();
      const failures = [...a.textFailures.map((f) => `TEXT ${f}`), ...a.iconFailures.map((f) => `ICON ${f}`)];
      lines.push(`${a.theme} ${path}: ${a.texts} texts, ${a.icons} icons ${failures.length ? JSON.stringify(failures) : 'ok'}`);
    }
    return lines.join('\n');
  };

  const focusInfo = (element, indicator = element) => {
    const style = getComputedStyle(indicator);
    const r = indicator.getBoundingClientRect();
    // Tailwind 4's focus:outline-hidden draws a transparent outline (shown only in forced-colors mode)
    const hiddenOutline = style.outlineStyle !== 'none' && parse(style.outlineColor).a === 0;
    const result = {
      theme: document.documentElement.className,
      focused: document.activeElement === element,
      focusVisible: element.matches(':focus-visible'),
      outline: style.outlineStyle === 'none' ? 'none'
        : hiddenOutline ? 'hidden' : `${style.outlineWidth} ${style.outlineStyle}`,
    };
    if (style.outlineStyle !== 'none' && !hiddenOutline) {
      const gap = parseFloat(style.outlineOffset) + parseFloat(style.outlineWidth) + 3;
      result.outlineVsBackground = ratio(parse(style.outlineColor),
        paintedAt(Math.max(r.left - gap, 0), r.top + r.height / 2, indicator));
    }
    if (parseFloat(style.borderTopWidth) > 0) {
      result.borderVsBackground = ratio(parse(style.borderTopColor),
        paintedAt(Math.max(r.left - 3, 0), r.top + r.height / 2, indicator));
    }
    return result;
  };

  window.__a11y = { audit, sweep, focusInfo, textOn, settle, ratio: (front, back) => ratio(parse(front), parse(back)) };
  return `a11y audit ready on ${location.pathname} (${document.documentElement.className || 'no theme class yet'})`;
})();
