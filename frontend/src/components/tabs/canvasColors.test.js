import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { AA_GRAPHICS, AA_TEXT, contrastRatio, parseColor } from './contrast.js';
import {
  CANVAS_BACKGROUNDS,
  CANVAS_COLORS,
  canvasColorsFor,
  GRAPHIC_COLOR_KEYS,
  TEXT_COLOR_KEYS,
} from './canvasColors.js';

describe('contrastRatio', () => {
  test('is 21 for black on white and 1 for a colour on itself', () => {
    assert.equal(Math.round(contrastRatio('#000000', '#ffffff') * 10) / 10, 21);
    assert.equal(contrastRatio('#71717a', '#71717a'), 1);
  });

  test('does not depend on which colour is the foreground when both are opaque', () => {
    assert.equal(contrastRatio('#a1a1aa', '#09090b'), contrastRatio('#09090b', '#a1a1aa'));
  });

  test('blends a translucent foreground onto the background first', () => {
    assert.ok(contrastRatio('rgba(255,255,255,0.5)', '#000000') < contrastRatio('#ffffff', '#000000'));
    assert.equal(contrastRatio('rgba(255,255,255,0)', '#000000'), 1);
  });

  test('matches the known AA failure of zinc-500 text on zinc-950', () => {
    assert.ok(contrastRatio('#71717a', '#09090b') < AA_TEXT);
    assert.ok(contrastRatio('#a1a1aa', '#09090b') >= AA_TEXT);
  });
});

describe('parseColor', () => {
  test('reads hex and rgba colours', () => {
    assert.deepEqual(parseColor('#ff8000'), { r: 255, g: 128, b: 0, a: 1 });
    assert.deepEqual(parseColor('rgba(9, 9, 11, 0.7)'), { r: 9, g: 9, b: 11, a: 0.7 });
    assert.deepEqual(parseColor('rgb(1,2,3)'), { r: 1, g: 2, b: 3, a: 1 });
  });

  test('rejects anything else', () => {
    assert.throws(() => parseColor('red'));
  });
});

describe('canvas colours', () => {
  for (const theme of Object.keys(CANVAS_COLORS)) {
    const colors = CANVAS_COLORS[theme];
    const background = CANVAS_BACKGROUNDS[theme];

    test(`${theme}: every glyph colour meets AA for text`, () => {
      for (const key of TEXT_COLOR_KEYS) {
        assert.ok(contrastRatio(colors[key], background) >= AA_TEXT, `${key} ${contrastRatio(colors[key], background)}`);
      }
    });

    test(`${theme}: staff and bar lines meet AA for graphics`, () => {
      for (const key of GRAPHIC_COLOR_KEYS) {
        assert.ok(contrastRatio(colors[key], background) >= AA_GRAPHICS, key);
      }
    });

    test(`${theme}: every colour alphaTab is given is checked`, () => {
      assert.deepEqual(Object.keys(colors).sort(), [...TEXT_COLOR_KEYS, ...GRAPHIC_COLOR_KEYS].sort());
    });
  }

  test('an unknown theme gets the dark colours', () => {
    assert.equal(canvasColorsFor('sepia'), CANVAS_COLORS.dark);
    assert.equal(canvasColorsFor('light'), CANVAS_COLORS.light);
  });
});
