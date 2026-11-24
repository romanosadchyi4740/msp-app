import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { CORE_SETTINGS, FONT_DIRECTORY } from './alphaTabSettings.js';

describe('alphaTab core settings', () => {
  // Without an explicit directory, alphaTab looks next to its script, which in the build is /assets/ (KAN-108)
  test('load the music font from the site root, where Vite serves public/font/', () => {
    assert.equal(CORE_SETTINGS.fontDirectory, '/font/');
    assert.equal(FONT_DIRECTORY, CORE_SETTINGS.fontDirectory);
  });

  test('name a folder that alphaTab ships the Bravura font in, which the Vite plugin copies', () => {
    const require = createRequire(import.meta.url);
    const alphaTabDist = dirname(require.resolve('@coderline/alphatab'));
    const folder = FONT_DIRECTORY.replaceAll('/', '');
    assert.ok(existsSync(join(alphaTabDist, folder, 'Bravura.woff2')), `no ${folder}/Bravura.woff2 in ${alphaTabDist}`);
  });

  test('keep rendering on the main thread with every measure laid out', () => {
    assert.equal(CORE_SETTINGS.useWorkers, false);
    assert.equal(CORE_SETTINGS.enableLazyLoading, false);
  });
});
