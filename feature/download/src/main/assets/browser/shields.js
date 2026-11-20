/*
 * Brave Shields in a page - brave-core's farbling, Global Privacy Control, language reduction, cosmetic filtering and de-AMP,
 * Copyright The Brave Authors, licensed under the Mozilla Public License, Version 2.0: https://mozilla.org/MPL/2.0/ -
 * written again as a script every frame runs before its own. __BRIDGE__ is the app's, named anew each run. Run after
 * scriptlets.js - ScriptletLibrary - and procedural.js - compileProceduralSelector, applyCompiledSelector - in their scope.
 */
(function () {
  'use strict';
  var bridge = window['__BRIDGE__'];
  if (!bridge) return;

  // The top page's host - a frame's Shields, and its farbling, are its top page's, as Brave's are.
  var topHost = location.hostname;
  try {
    var ancestors = location.ancestorOrigins;
    if (ancestors && ancestors.length) topHost = new URL(ancestors[ancestors.length - 1]).hostname;
  } catch (e) {}
  var config;
  try {
    config = JSON.parse(bridge.config(topHost, location.hostname, location.href));
  } catch (e) {
    return;
  }
  if (!config.up) return;

  // uBO's scriptlets the lists give this page, first of all - before any of its own scripts can see what they change.
  runScriptlets(config.scriptlets || []);

  // A function put in a browser's own place reads as the browser's own, as Brave's - native - ones do.
  var nativeSources = new WeakMap();
  var toString = Function.prototype.toString;
  function asNative(replacement, original) {
    nativeSources.set(replacement, toString.call(original));
    return replacement;
  }
  Function.prototype.toString = asNative(function () {
    return nativeSources.has(this) ? nativeSources.get(this) : toString.call(this);
  }, toString);
  function replaceMethod(proto, name, make) {
    if (!proto || typeof proto[name] !== 'function') return;
    var original = proto[name];
    proto[name] = asNative(make(original), original);
  }
  function replaceGetter(proto, name, get) {
    var descriptor = proto && Object.getOwnPropertyDescriptor(proto, name);
    var original = descriptor && descriptor.get;
    try {
      Object.defineProperty(proto, name, {
        get: original ? asNative(function () { return get.call(this, original.call(this)); }, original) : get,
        configurable: true,
        enumerable: true,
      });
    } catch (e) {}
  }

  // Global Privacy Control: this browser asks every site not to sell or share what it learns of its user.
  replaceGetter(Navigator.prototype, 'globalPrivacyControl', function () { return true; });

  if (config.lang) {
    // One language only - the first - as Brave's language reduction gives.
    replaceGetter(Navigator.prototype, 'languages', function () { return Object.freeze([navigator.language]); });
  }

  if (config.fp) farble(config.fp === 2, config.seed >>> 0);
  hide();
  if (config.procedural && config.procedural.length) procedural(config.procedural);
  if (config.amp && window === window.top) deAmp();

  /** Each scriptlet - [trusted, name, ...args] - by its function; one uBO trusts only its own lists with, only from them. */
  function runScriptlets(calls) {
    for (var i = 0; i < calls.length; i++) {
      var call = calls[i];
      var entry = ScriptletLibrary[call[1]];
      if (!entry || (entry.trusted && !call[0])) continue;
      try {
        entry.fn.apply(null, call.slice(2));
      } catch (e) {}
    }
  }

  /**
   * Brave's procedural filters: what a stylesheet cannot find - by its text, its styles, an ancestor of what it finds - found
   * by Brave's operators, and hidden, restyled or removed; again as the page changes.
   */
  function procedural(filters) {
    function act(element, action) {
      if (!action) {
        element.style.setProperty('display', 'none', 'important');
        return;
      }
      switch (action.type) {
        case 'style':
          element.setAttribute('style', (element.getAttribute('style') || '') + ';' + action.arg);
          break;
        case 'remove':
          element.remove();
          break;
        case 'remove-attr':
          element.removeAttribute(action.arg);
          break;
        case 'remove-class':
          if (element.classList.contains(action.arg)) element.classList.remove(action.arg);
          break;
      }
    }
    var done = new WeakSet();
    function run() {
      for (var i = 0; i < filters.length; i++) {
        var filter = filters[i];
        try {
          var selector = filter.selector;
          var start = selector[0].type === 'css-selector' ? 1 : 0;
          var elements = Array.from(document.querySelectorAll(start ? selector[0].arg : '*'));
          var found = start === selector.length ? elements : applyCompiledSelector(compileProceduralSelector(selector.slice(start)), elements);
          for (var j = 0; j < found.length; j++) {
            // An element restyled once is not restyled again with every change of the page.
            if (filter.action && filter.action.type === 'style' && done.has(found[j])) continue;
            done.add(found[j]);
            act(found[j], filter.action);
          }
        } catch (e) {}
      }
    }
    var pending = false;
    function soon() {
      if (pending) return;
      pending = true;
      setTimeout(function () {
        pending = false;
        run();
      }, 200);
    }
    new MutationObserver(soon).observe(document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', run);
  }

  /** A run of numbers from [seed] - the same each time for the same site, this run of the app: mulberry32. */
  function random(seed) {
    var state = seed >>> 0;
    return function () {
      state = (state + 0x6d2b79f5) >>> 0;
      var t = state;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /**
   * Brave's farbling: what a page reads back of what it drew, played or was told - a canvas, audio, the processor count,
   * the devices - changed a little, the same way for one site all session and differently for every other, so none of it
   * tells this browser apart; [strict] changes it wholly, at the risk of sites that rely on it.
   */
  function farble(strict, seed) {
    function farbleBytes(data, salt) {
      var next = random(seed ^ salt ^ data.length);
      if (strict) {
        for (var i = 0; i < data.length; i += 4) {
          data[i] = next() * 256;
          data[i + 1] = next() * 256;
          data[i + 2] = next() * 256;
        }
        return;
      }
      // A pixel in a hundred, or so, its lowest bit flipped - never seen, always different to a fingerprint.
      for (var p = Math.floor(next() * 100) * 4; p < data.length; p += 4 * (1 + Math.floor(next() * 200))) {
        var channel = p + Math.floor(next() * 3);
        data[channel] = data[channel] ^ 1;
      }
    }

    var getImageData = CanvasRenderingContext2D.prototype.getImageData;
    replaceMethod(CanvasRenderingContext2D.prototype, 'getImageData', function (original) {
      return function () {
        var image = original.apply(this, arguments);
        farbleBytes(image.data, 1);
        return image;
      };
    });
    // A canvas read whole - as a picture's address or file - read from a farbled copy of itself.
    function farbledCopy(canvas) {
      if (!canvas.width || !canvas.height) return canvas;
      try {
        var copy = document.createElement('canvas');
        copy.width = canvas.width;
        copy.height = canvas.height;
        var context = copy.getContext('2d');
        context.drawImage(canvas, 0, 0);
        var image = getImageData.call(context, 0, 0, copy.width, copy.height);
        farbleBytes(image.data, 1);
        context.putImageData(image, 0, 0);
        return copy;
      } catch (e) {
        return canvas;
      }
    }
    replaceMethod(HTMLCanvasElement.prototype, 'toDataURL', function (original) {
      return function () { return original.apply(farbledCopy(this), arguments); };
    });
    replaceMethod(HTMLCanvasElement.prototype, 'toBlob', function (original) {
      return function () { return original.apply(farbledCopy(this), arguments); };
    });
    [window.WebGLRenderingContext, window.WebGL2RenderingContext].forEach(function (gl) {
      if (!gl) return;
      replaceMethod(gl.prototype, 'readPixels', function (original) {
        return function () {
          var result = original.apply(this, arguments);
          var pixels = arguments[6];
          if (pixels instanceof Uint8Array || pixels instanceof Uint8ClampedArray) farbleBytes(pixels, 2);
          return result;
        };
      });
      if (strict) {
        // The graphics card's own name, vendor and renderer, told as no card's.
        replaceMethod(gl.prototype, 'getParameter', function (original) {
          return function (name) {
            if (name === 0x9245 || name === 0x9246) return random(seed ^ name)().toString(36).slice(2, 10);
            return original.apply(this, arguments);
          };
        });
      }
    });

    // Audio read back, scaled by a factor of the site's - or, strict, its samples noise.
    var fudge = 0.99 + random(seed ^ 3)() * 0.01;
    var farbled = new WeakSet();
    function farbleSamples(samples, salt) {
      if (strict) {
        var next = random(seed ^ salt);
        for (var i = 0; i < samples.length; i++) samples[i] = next() * 2 - 1;
      } else {
        for (var j = 0; j < samples.length; j++) samples[j] *= fudge;
      }
    }
    if (window.AudioBuffer) {
      replaceMethod(AudioBuffer.prototype, 'getChannelData', function (original) {
        return function () {
          var samples = original.apply(this, arguments);
          if (!farbled.has(samples)) {
            farbled.add(samples);
            farbleSamples(samples, 4);
          }
          return samples;
        };
      });
      replaceMethod(AudioBuffer.prototype, 'copyFromChannel', function (original) {
        return function (destination) {
          var result = original.apply(this, arguments);
          farbleSamples(destination, 5);
          return result;
        };
      });
    }
    if (window.AnalyserNode) {
      ['getFloatFrequencyData', 'getFloatTimeDomainData'].forEach(function (name) {
        replaceMethod(AnalyserNode.prototype, name, function (original) {
          return function (array) {
            var result = original.apply(this, arguments);
            for (var i = 0; i < array.length; i++) array[i] *= fudge;
            return result;
          };
        });
      });
    }

    // How many processors: two, up to the device's own, as the site's seed picks.
    var cores = navigator.hardwareConcurrency;
    if (cores > 2) {
      var farbledCores = 2 + Math.floor(random(seed ^ 6)() * (cores - 1));
      replaceGetter(Navigator.prototype, 'hardwareConcurrency', function () { return farbledCores; });
    }

    // The devices - cameras, microphones - in an order of the site's own.
    if (window.MediaDevices) {
      replaceMethod(MediaDevices.prototype, 'enumerateDevices', function (original) {
        return function () {
          return original.apply(this, arguments).then(function (devices) {
            var next = random(seed ^ 7);
            return devices.map(function (device) { return [next(), device]; })
              .sort(function (a, b) { return a[0] - b[0]; })
              .map(function (pair) { return pair[1]; });
          });
        };
      });
    }
  }

  /**
   * Cosmetic filtering: what the lists hide on this page, by a stylesheet of the document's own - which no page's security
   * policy refuses - and, as elements of new classes and ids come, the generic selectors hiding those.
   */
  function hide() {
    var sheet;
    try {
      sheet = new CSSStyleSheet();
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
    } catch (e) {
      return;
    }
    var rules = [];
    var hidden = Object.create(null);
    function add(selectors) {
      var added = false;
      for (var i = 0; i < selectors.length; i++) {
        if (hidden[selectors[i]]) continue;
        hidden[selectors[i]] = true;
        rules.push(selectors[i] + '{display:none!important}');
        added = true;
      }
      // A selector the browser cannot read is dropped alone, the rest kept.
      if (added) sheet.replaceSync(rules.join('\n'));
    }
    add(config.hide || []);
    if (!config.byClassAndId) return;

    var seenClasses = Object.create(null);
    var seenIds = Object.create(null);
    var newClasses = [];
    var newIds = [];
    var pending = false;
    function note(element) {
      if (element.nodeType !== 1) return;
      var classes = element.classList;
      for (var i = 0; classes && i < classes.length; i++) {
        if (!seenClasses[classes[i]]) {
          seenClasses[classes[i]] = true;
          newClasses.push(classes[i]);
        }
      }
      if (element.id && !seenIds[element.id]) {
        seenIds[element.id] = true;
        newIds.push(element.id);
      }
    }
    function noteTree(root) {
      note(root);
      if (!root.querySelectorAll) return;
      var found = root.querySelectorAll('[class],[id]');
      for (var i = 0; i < found.length; i++) note(found[i]);
    }
    function ask() {
      pending = false;
      if (!newClasses.length && !newIds.length) return;
      var classes = newClasses;
      var ids = newIds;
      newClasses = [];
      newIds = [];
      try {
        add(JSON.parse(bridge.classIdSelectors(location.hostname, JSON.stringify(classes), JSON.stringify(ids))));
      } catch (e) {}
    }
    function soon() {
      if (pending) return;
      pending = true;
      setTimeout(ask, 50);
    }
    new MutationObserver(function (mutations) {
      for (var i = 0; i < mutations.length; i++) {
        var mutation = mutations[i];
        if (mutation.type === 'attributes') note(mutation.target);
        for (var j = 0; j < mutation.addedNodes.length; j++) noteTree(mutation.addedNodes[j]);
      }
      soon();
    }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
    if (document.documentElement) {
      noteTree(document.documentElement);
      soon();
    }
  }

  /**
   * De-AMP: an AMP page - its <html> marked `amp` or `⚡` - left for the page it stands for, its canonical link, before it is
   * shown; never back to the page that sent here, so a site sending its readers to AMP is no loop.
   */
  function deAmp() {
    function check() {
      var html = document.documentElement;
      if (!html) return false;
      if (!html.hasAttribute('amp') && !html.hasAttribute('⚡')) return true;
      var link = document.querySelector('link[rel~="canonical"]');
      if (!link) return false;
      var canonical = link.href;
      if (/^https?:/i.test(canonical) && canonical !== location.href && canonical !== document.referrer) location.replace(canonical);
      return true;
    }
    if (check()) return;
    var observer = new MutationObserver(function () {
      if (check()) observer.disconnect();
    });
    observer.observe(document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', function () {
      check();
      observer.disconnect();
    });
  }
})();
