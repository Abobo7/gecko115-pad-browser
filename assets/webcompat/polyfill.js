(function () {
  "use strict";

  const PAGE_POLYFILL = `
(function () {
  "use strict";

  // 1. ECMA-402 Intl.Segmenter (Required by X / Twitter 2026 web login & composer)
  if (typeof Intl !== "undefined" && typeof Intl.Segmenter !== "function") {
    const VALID_GRANULARITIES = new Set(["grapheme", "word", "sentence"]);
    const GRAPHEME_RE = /(?:\\p{Regional_Indicator}{2})|(?:\\p{Extended_Pictographic}(?:\\p{Emoji_Modifier}|\\uFE0F|\\u20E3)*(?:\\u200D\\p{Extended_Pictographic}(?:\\p{Emoji_Modifier}|\\uFE0F|\\u20E3)*)*)|(?:\\r\\n|[\\r\\n]|[\\s\\S][\\p{Mark}\\u200D\\uFE0E\\uFE0F]*)/gu;
    const WORD_RE = /[\\p{Script=Han}\\p{Script=Hiragana}\\p{Script=Katakana}\\p{Script=Thai}\\p{Script=Lao}\\p{Script=Khmer}\\p{Script=Myanmar}]|[\\p{L}\\p{N}_]+(?:['\\u2019\\-][\\p{L}\\p{N}_]+)*|\\s+|[^\\s\\p{L}\\p{N}_]/gu;
    const WORD_LIKE_RE = /[\\p{L}\\p{N}_]/u;
    const SENTENCE_RE = /[^.!?\\u3002\\uFF01\\uFF1F\\r\\n]+(?:[.!?\\u3002\\uFF01\\uFF1F]+['"\\u201D\\u2019)\\]]*\\s*|\\r?\\n|$)|\\r?\\n/gu;

    function computeSegments(str, granularity) {
      const list = [];
      if (!str) return list;
      let re;
      if (granularity === "word") {
        re = new RegExp(WORD_RE.source, "gu");
      } else if (granularity === "sentence") {
        re = new RegExp(SENTENCE_RE.source, "gu");
      } else {
        re = new RegExp(GRAPHEME_RE.source, "gu");
      }
      let m;
      while ((m = re.exec(str)) !== null) {
        const seg = m[0];
        if (!seg.length) {
          re.lastIndex++;
          continue;
        }
        const entry = { segment: seg, index: m.index, input: str };
        if (granularity === "word") {
          entry.isWordLike = WORD_LIKE_RE.test(seg);
        }
        list.push(entry);
      }
      return list;
    }

    class Segments {
      constructor(str, granularity) {
        this._str = str;
        this._granularity = granularity;
        this._cache = null;
      }
      _ensure() {
        if (!this._cache) {
          this._cache = computeSegments(this._str, this._granularity);
        }
        return this._cache;
      }
      containing(codeUnitIndex = 0) {
        let n = Math.trunc(Number(codeUnitIndex));
        if (Number.isNaN(n)) n = 0;
        if (n < 0 || n >= this._str.length) return undefined;
        const arr = this._ensure();
        let lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
          const mid = (lo + hi) >>> 1;
          const item = arr[mid];
          const end = item.index + item.segment.length;
          if (n < item.index) {
            hi = mid - 1;
          } else if (n >= end) {
            lo = mid + 1;
          } else {
            const out = { segment: item.segment, index: item.index, input: this._str };
            if (this._granularity === "word") out.isWordLike = item.isWordLike;
            return out;
          }
        }
        return undefined;
      }
      *[Symbol.iterator]() {
        const arr = this._ensure();
        for (let i = 0; i < arr.length; i++) {
          const item = arr[i];
          const out = { segment: item.segment, index: item.index, input: this._str };
          if (this._granularity === "word") out.isWordLike = item.isWordLike;
          yield out;
        }
      }
    }

    class Segmenter {
      constructor(locales, options) {
        let gran = "grapheme";
        let localeMatcher = "best fit";
        if (options !== undefined) {
          const optObj = Object(options);
          if (optObj.localeMatcher !== undefined) {
            localeMatcher = String(optObj.localeMatcher);
          }
          if (optObj.granularity !== undefined) {
            gran = String(optObj.granularity);
            if (!VALID_GRANULARITIES.has(gran)) {
              throw new RangeError("Invalid granularity: " + gran);
            }
          }
        }
        let resolvedLocale = "zh-CN";
        try {
          resolvedLocale = new Intl.Collator(locales, { localeMatcher }).resolvedOptions().locale;
        } catch (_) {}
        this._locale = resolvedLocale;
        this._granularity = gran;
      }
      segment(input) {
        const str = String(input);
        return new Segments(str, this._granularity);
      }
      resolvedOptions() {
        return { locale: this._locale, granularity: this._granularity };
      }
      static supportedLocalesOf(locales, options) {
        return Intl.Collator.supportedLocalesOf(locales, options);
      }
    }

    Object.defineProperty(Segmenter.prototype, Symbol.toStringTag, {
      value: "Intl.Segmenter",
      configurable: true
    });
    Object.defineProperty(Intl, "Segmenter", {
      value: Segmenter,
      writable: true,
      configurable: true
    });
  }

  // 2. ES2024 Promise.withResolvers
  if (typeof Promise.withResolvers !== "function") {
    Object.defineProperty(Promise, "withResolvers", {
      value: function withResolvers() {
        let resolve, reject;
        const promise = new this((res, rej) => {
          resolve = res;
          reject = rej;
        });
        return { promise, resolve, reject };
      },
      writable: true,
      configurable: true
    });
  }

  // 3. ES2024 Object.groupBy & Map.groupBy
  if (typeof Object.groupBy !== "function") {
    Object.defineProperty(Object, "groupBy", {
      value: function groupBy(items, callbackFn) {
        if (items == null) throw new TypeError("Object.groupBy called on null or undefined");
        if (typeof callbackFn !== "function") throw new TypeError("callbackFn must be a function");
        const out = Object.create(null);
        let idx = 0;
        for (const item of items) {
          const key = PropertyKey(callbackFn(item, idx++));
          if (out[key] === undefined) out[key] = [item];
          else out[key].push(item);
        }
        return out;
      },
      writable: true,
      configurable: true
    });
    function PropertyKey(v) {
      return typeof v === "symbol" ? v : String(v);
    }
  }

  if (typeof Map !== "undefined" && typeof Map.groupBy !== "function") {
    Object.defineProperty(Map, "groupBy", {
      value: function groupBy(items, callbackFn) {
        if (items == null) throw new TypeError("Map.groupBy called on null or undefined");
        if (typeof callbackFn !== "function") throw new TypeError("callbackFn must be a function");
        const map = new Map();
        let idx = 0;
        for (const item of items) {
          const key = callbackFn(item, idx++);
          const list = map.get(key);
          if (list === undefined) map.set(key, [item]);
          else list.push(item);
        }
        return map;
      },
      writable: true,
      configurable: true
    });
  }

  // 4. WHATWG URL.parse (2024)
  if (typeof URL !== "undefined" && typeof URL.parse !== "function") {
    Object.defineProperty(URL, "parse", {
      value: function parse(url, base) {
        try {
          return base !== undefined ? new URL(url, base) : new URL(url);
        } catch (_) {
          return null;
        }
      },
      writable: true,
      configurable: true
    });
  }

  // 5. ES2025 Set methods
  if (typeof Set !== "undefined" && typeof Set.prototype.union !== "function") {
    const proto = Set.prototype;
    Object.defineProperties(proto, {
      union: {
        value: function union(other) {
          const res = new Set(this);
          for (const v of other.keys()) res.add(v);
          return res;
        },
        writable: true,
        configurable: true
      },
      intersection: {
        value: function intersection(other) {
          const res = new Set();
          for (const v of this) {
            if (other.has(v)) res.add(v);
          }
          return res;
        },
        writable: true,
        configurable: true
      },
      difference: {
        value: function difference(other) {
          const res = new Set(this);
          for (const v of other.keys()) res.delete(v);
          return res;
        },
        writable: true,
        configurable: true
      },
      symmetricDifference: {
        value: function symmetricDifference(other) {
          const res = new Set(this);
          for (const v of other.keys()) {
            if (this.has(v)) res.delete(v);
            else res.add(v);
          }
          return res;
        },
        writable: true,
        configurable: true
      },
      isSubsetOf: {
        value: function isSubsetOf(other) {
          if (this.size > other.size) return false;
          for (const v of this) {
            if (!other.has(v)) return false;
          }
          return true;
        },
        writable: true,
        configurable: true
      },
      isSupersetOf: {
        value: function isSupersetOf(other) {
          if (this.size < other.size) return false;
          for (const v of other.keys()) {
            if (!this.has(v)) return false;
          }
          return true;
        },
        writable: true,
        configurable: true
      },
      isDisjointFrom: {
        value: function isDisjointFrom(other) {
          for (const v of this) {
            if (other.has(v)) return false;
          }
          return true;
        },
        writable: true,
        configurable: true
      }
    });
  }
})();
`;

  try {
    // Firefox content-script window.eval executes directly in the page global compartment, bypassing page CSP
    window.eval(PAGE_POLYFILL);
  } catch (err) {
    try {
      const pageWin = window.wrappedJSObject;
      if (pageWin && pageWin.Intl && typeof pageWin.Intl.Segmenter !== "function" && typeof exportFunction === "function" && typeof cloneInto === "function") {
        // Fallback via Xray exportFunction + cloneInto if window.eval was ever restricted
        const SegmenterFallback = exportFunction(function (locales, options) {
          const gran = (options && options.granularity) ? String(options.granularity) : "grapheme";
          return cloneInto({
            segment: function (input) {
              const s = String(input);
              const chars = Array.from(s);
              let pos = 0;
              const items = chars.map(function (ch) {
                const it = gran === "word"
                  ? { segment: ch, index: pos, input: s, isWordLike: /[\p{L}\p{N}_]/u.test(ch) }
                  : { segment: ch, index: pos, input: s };
                pos += ch.length;
                return it;
              });
              return items;
            },
            resolvedOptions: function () {
              return { locale: "zh-CN", granularity: gran };
            }
          }, pageWin, { cloneFunctions: true });
        }, pageWin);
        pageWin.Intl.Segmenter = SegmenterFallback;
      }
    } catch (_) {}
  }
})();
