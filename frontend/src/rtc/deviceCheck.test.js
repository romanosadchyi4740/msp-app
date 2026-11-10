import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  availableDevice, browserSupport, groupDevices, HEARD_LEVEL, isCompatible, levelOf, microphoneProblem,
  requiredChecks,
} from './deviceCheck.js';

function modernBrowser() {
  function RTCPeerConnection() {}
  function AudioContext() {}
  function HTMLMediaElement() {}
  HTMLMediaElement.prototype.setSinkId = () => {};
  return {
    isSecureContext: true,
    RTCPeerConnection,
    AudioContext,
    HTMLMediaElement,
    navigator: { mediaDevices: { getUserMedia: () => {} } },
  };
}

describe('browserSupport', () => {
  test('a current browser supports everything', () => {
    const support = browserSupport(modernBrowser());

    assert.deepEqual(support, {
      secureContext: true, webrtc: true, microphoneApi: true, webAudio: true, outputSelection: true,
    });
    assert.equal(isCompatible(support), true);
  });

  test('an older Safari has Web Audio under its prefixed name', () => {
    const { AudioContext, ...safari } = modernBrowser();

    assert.equal(browserSupport({ ...safari, webkitAudioContext: AudioContext }).webAudio, true);
  });

  test('a page that is not secure has no mediaDevices, and both are reported', () => {
    const support = browserSupport({ ...modernBrowser(), isSecureContext: false, navigator: {} });

    assert.equal(support.secureContext, false);
    assert.equal(support.microphoneApi, false);
    assert.equal(isCompatible(support), false);
  });

  test('a browser with WebRTC blocked is not compatible', () => {
    const { RTCPeerConnection: _blocked, ...browser } = modernBrowser();

    const support = browserSupport(browser);

    assert.equal(support.webrtc, false);
    assert.equal(isCompatible(support), false);
  });

  test('choosing the output is optional: Firefox and Safari stay compatible without it', () => {
    const { HTMLMediaElement: _none, ...browser } = modernBrowser();

    const support = browserSupport(browser);

    assert.equal(support.outputSelection, false);
    assert.equal(isCompatible(support), true);
  });

  test('nothing at all is supported without a browser', () => {
    assert.equal(isCompatible(browserSupport(undefined)), false);
    assert.equal(isCompatible(browserSupport({})), false);
  });
});

describe('requiredChecks', () => {
  test('lists every required check with its outcome and a hint for the failed ones', () => {
    const { AudioContext: _none, ...browser } = modernBrowser();

    const checks = requiredChecks(browserSupport(browser));

    assert.deepEqual(checks.map((check) => [check.id, check.ok]),
      [['secureContext', true], ['webrtc', true], ['microphoneApi', true], ['webAudio', false]]);
    assert.ok(checks.every((check) => check.label && check.hint));
  });
});

describe('microphoneProblem', () => {
  test('says what happened and what to do, by the error', () => {
    assert.match(microphoneProblem('NotAllowedError').message, /did not allow/);
    assert.match(microphoneProblem('NotAllowedError').hint, /address bar/);
    assert.match(microphoneProblem('NotFoundError').message, /No microphone/);
    assert.match(microphoneProblem('OverconstrainedError').message, /No microphone/);
    assert.match(microphoneProblem('NotReadableError').message, /another application/);
  });

  test('an unknown error still gets a message and a hint', () => {
    for (const name of ['TypeError', undefined]) {
      const problem = microphoneProblem(name);
      assert.ok(problem.message && problem.hint);
    }
  });
});

describe('levelOf', () => {
  const sine = (amplitude) => Array.from({ length: 480 }, (_, i) => amplitude * Math.sin((2 * Math.PI * i) / 48));

  test('silence is 0 and full scale is 1', () => {
    assert.equal(levelOf(new Float32Array(480)), 0);
    assert.equal(levelOf(Array(480).fill(1)), 1);
  });

  test('is on a decibel scale: a tenth of the amplitude is a third of the meter lower', () => {
    // A full-scale sine has an RMS of -3 dB; -3/60 below the top
    assert.ok(Math.abs(levelOf(sine(1)) - 0.95) < 0.01);
    // 20 dB quieter is 20/60 of the meter lower
    assert.ok(Math.abs(levelOf(sine(1)) - levelOf(sine(0.1)) - 1 / 3) < 0.01);
  });

  test('a quiet but audible signal counts as heard, noise at the floor does not', () => {
    assert.ok(levelOf(sine(0.02)) > HEARD_LEVEL);
    assert.ok(levelOf(sine(0.001)) < HEARD_LEVEL);
    assert.equal(levelOf(sine(0.0001)), 0);
  });

  test('no samples is silence', () => {
    assert.equal(levelOf([]), 0);
    assert.equal(levelOf(null), 0);
  });
});

describe('groupDevices', () => {
  const device = (kind, deviceId, label = '') => ({ kind, deviceId, label });

  test('separates microphones from outputs and leaves cameras out', () => {
    const devices = [device('audioinput', 'm1', 'USB microphone'), device('videoinput', 'c1', 'Camera'),
      device('audiooutput', 'o1', 'Headphones')];

    assert.deepEqual(groupDevices(devices), {
      inputs: [{ deviceId: 'm1', label: 'USB microphone' }],
      outputs: [{ deviceId: 'o1', label: 'Headphones' }],
    });
  });

  test('numbers the devices the browser does not name yet', () => {
    const devices = [device('audioinput', 'm1'), device('audioinput', 'm2'), device('audiooutput', 'o1')];

    const { inputs, outputs } = groupDevices(devices);

    assert.deepEqual(inputs.map((input) => input.label), ['Microphone 1', 'Microphone 2']);
    assert.deepEqual(outputs.map((output) => output.label), ['Output 1']);
  });

  test('leaves out the placeholder entries browsers give before permission', () => {
    assert.deepEqual(groupDevices([device('audioinput', ''), device('audiooutput', '')]),
      { inputs: [], outputs: [] });
    assert.deepEqual(groupDevices(undefined), { inputs: [], outputs: [] });
  });
});

describe('availableDevice', () => {
  const devices = [{ deviceId: 'a', label: 'A' }, { deviceId: 'b', label: 'B' }];

  test('keeps a saved device that is still there', () => {
    assert.equal(availableDevice('b', devices), 'b');
  });

  test('falls back to the default when the saved device is gone or none was saved', () => {
    assert.equal(availableDevice('unplugged', devices), '');
    assert.equal(availableDevice('', devices), '');
    assert.equal(availableDevice(null, devices), '');
    assert.equal(availableDevice('a', []), '');
  });
});
