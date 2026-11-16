(function () {
  "use strict";

  // Pure Xray exportFunction + cloneInto WebCompat injection (zero eval / zero inline <script>):
  // Works cleanly on strict nonce-CSP sites (x.com, accounts.google.com, github.com, etc.)
  // without triggering Content-Security-Policy script-src violations or Xray compartment errors.
  const pageWin = window.wrappedJSObject;
  if (pageWin && typeof exportFunction === "function" && typeof cloneInto === "function") {
    try {
      installSegmenter(pageWin);
      installPromiseWithResolvers(pageWin);
      installGroupBy(pageWin);
      installUrlParse(pageWin);
      installSetMethods(pageWin);
    } catch (_) {}
  }

  // Automatic transient-failure recovery for X / Twitter CDN images (pbs.twimg.com / abs.twimg.com)
  installTwimgAutoRetry();

  function installSegmenter(pw) {
    if (!pw.Intl || typeof pw.Intl.Segmenter === "function") return;

    const VALID_GRANULARITIES = new Set(["grapheme", "word", "sentence"]);
    const GRAPHEME_RE = /(?:\p{Regional_Indicator}{2})|(?:\p{Extended_Pictographic}(?:\p{Emoji_Modifier}|\uFE0F|\u20E3)*(?:\u200D\p{Extended_Pictographic}(?:\p{Emoji_Modifier}|\uFE0F|\u20E3)*)*)|(?:\r\n|[\r\n]|[\s\S][\p{Mark}\u200D\uFE0E\uFE0F]*)/gu;
    const WORD_RE = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Thai}\p{Script=Lao}\p{Script=Khmer}\p{Script=Myanmar}]|[\p{L}\p{N}_]+(?:['\u2019\-][\p{L}\p{N}_]+)*|\s+|[^\s\p{L}\p{N}_]/gu;
    const WORD_LIKE_RE = /[\p{L}\p{N}_]/u;
    const SENTENCE_RE = /[^.!?\u3002\uFF01\uFF1F\r\n]+(?:[.!?\u3002\uFF01\uFF1F]+['"\u201D\u2019)\]]*\s*|\r?\n|$)|\r?\n/gu;

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

    function makeSegmentsObject(str, granularity) {
      let cached = null;
      function ensure() {
        if (!cached) cached = computeSegments(str, granularity);
        return cached;
      }

      const segsObj = new pw.Object();

      const containingFn = exportFunction(function containing(codeUnitIndex) {
        let n = codeUnitIndex === undefined ? 0 : Math.trunc(Number(codeUnitIndex));
        if (Number.isNaN(n)) n = 0;
        if (n < 0 || n >= str.length) return undefined;
        const arr = ensure();
        let lo = 0;
        let hi = arr.length - 1;
        while (lo <= hi) {
          const mid = (lo + hi) >>> 1;
          const item = arr[mid];
          const end = item.index + item.segment.length;
          if (n < item.index) {
            hi = mid - 1;
          } else if (n >= end) {
            lo = mid + 1;
          } else {
            return cloneInto(item, pw);
          }
        }
        return undefined;
      }, pw);

      const iterSym = pw.Symbol && pw.Symbol.iterator ? pw.Symbol.iterator : Symbol.iterator;
      const iteratorFn = exportFunction(function iterator() {
        const arr = ensure();
        let idx = 0;
        const iterObj = new pw.Object();
        const nextFn = exportFunction(function next() {
          if (idx < arr.length) {
            return cloneInto({ value: arr[idx++], done: false }, pw);
          }
          return cloneInto({ value: undefined, done: true }, pw);
        }, pw);
        pw.Object.defineProperty(iterObj, "next", {
          value: nextFn,
          writable: true,
          configurable: true
        });
        pw.Object.defineProperty(iterObj, iterSym, {
          value: exportFunction(function selfIter() { return iterObj; }, pw),
          writable: true,
          configurable: true
        });
        return iterObj;
      }, pw);

      pw.Object.defineProperty(segsObj, "containing", {
        value: containingFn,
        writable: true,
        configurable: true
      });
      pw.Object.defineProperty(segsObj, iterSym, {
        value: iteratorFn,
        writable: true,
        configurable: true
      });
      return segsObj;
    }

    const SegmenterCtor = exportFunction(function Segmenter(locales, options) {
      let gran = "grapheme";
      let localeMatcher = "best fit";
      if (options !== undefined && options !== null) {
        if (options.localeMatcher !== undefined) {
          localeMatcher = String(options.localeMatcher);
        }
        if (options.granularity !== undefined) {
          gran = String(options.granularity);
          if (!VALID_GRANULARITIES.has(gran)) {
            throw new pw.RangeError("Invalid granularity: " + gran);
          }
        }
      }
      let resolvedLocale = "zh-CN";
      try {
        resolvedLocale = new Intl.Collator(locales, { localeMatcher: localeMatcher }).resolvedOptions().locale;
      } catch (_) {}

      const inst = new pw.Object();
      try {
        if (SegmenterCtor && SegmenterCtor.prototype) {
          pw.Object.setPrototypeOf(inst, SegmenterCtor.prototype);
        }
      } catch (_) {}
      pw.Object.defineProperty(inst, "segment", {
        value: exportFunction(function segment(input) {
          return makeSegmentsObject(String(input), gran);
        }, pw),
        writable: true,
        configurable: true
      });
      pw.Object.defineProperty(inst, "resolvedOptions", {
        value: exportFunction(function resolvedOptions() {
          return cloneInto({ locale: resolvedLocale, granularity: gran }, pw);
        }, pw),
        writable: true,
        configurable: true
      });
      return inst;
    }, pw);

    pw.Object.defineProperty(SegmenterCtor, "supportedLocalesOf", {
      value: exportFunction(function supportedLocalesOf(locales, options) {
        try {
          const res = Intl.Collator.supportedLocalesOf(locales, options ? cloneInto(options, window) : undefined);
          return cloneInto(res, pw);
        } catch (_) {
          return cloneInto(["zh-CN", "en-US"], pw);
        }
      }, pw),
      writable: true,
      configurable: true
    });

    pw.Object.defineProperty(pw.Intl, "Segmenter", {
      value: SegmenterCtor,
      writable: true,
      configurable: true
    });
  }

  function installPromiseWithResolvers(pw) {
    if (!pw.Promise || typeof pw.Promise.withResolvers === "function") return;
    const fn = exportFunction(function withResolvers() {
      let resolveFn, rejectFn;
      const promise = new pw.Promise(exportFunction(function executor(res, rej) {
        resolveFn = res;
        rejectFn = rej;
      }, pw));
      const out = new pw.Object();
      out.promise = promise;
      out.resolve = resolveFn;
      out.reject = rejectFn;
      return out;
    }, pw);
    pw.Object.defineProperty(pw.Promise, "withResolvers", {
      value: fn,
      writable: true,
      configurable: true
    });
  }

  function installGroupBy(pw) {
    if (pw.Object && typeof pw.Object.groupBy !== "function") {
      const objGroupBy = exportFunction(function groupBy(items, callbackFn) {
        if (items == null) throw new pw.TypeError("Object.groupBy called on null or undefined");
        if (typeof callbackFn !== "function") throw new pw.TypeError("callbackFn must be a function");
        const out = pw.Object.create(null);
        const arr = pw.Array.from(items);
        for (let i = 0; i < arr.length; i++) {
          const item = arr[i];
          const rawKey = callbackFn(item, i);
          const key = typeof rawKey === "symbol" ? rawKey : String(rawKey);
          if (out[key] === undefined) {
            const bucket = new pw.Array();
            bucket.push(item);
            out[key] = bucket;
          } else {
            out[key].push(item);
          }
        }
        return out;
      }, pw);
      pw.Object.defineProperty(pw.Object, "groupBy", {
        value: objGroupBy,
        writable: true,
        configurable: true
      });
    }

    if (pw.Map && typeof pw.Map.groupBy !== "function") {
      const mapGroupBy = exportFunction(function groupBy(items, callbackFn) {
        if (items == null) throw new pw.TypeError("Map.groupBy called on null or undefined");
        if (typeof callbackFn !== "function") throw new pw.TypeError("callbackFn must be a function");
        const map = new pw.Map();
        const arr = pw.Array.from(items);
        for (let i = 0; i < arr.length; i++) {
          const item = arr[i];
          const key = callbackFn(item, i);
          let bucket = map.get(key);
          if (bucket === undefined) {
            bucket = new pw.Array();
            bucket.push(item);
            map.set(key, bucket);
          } else {
            bucket.push(item);
          }
        }
        return map;
      }, pw);
      pw.Object.defineProperty(pw.Map, "groupBy", {
        value: mapGroupBy,
        writable: true,
        configurable: true
      });
    }
  }

  function installUrlParse(pw) {
    if (!pw.URL || typeof pw.URL.parse === "function") return;
    const fn = exportFunction(function parse(url, base) {
      try {
        return base !== undefined ? new pw.URL(url, base) : new pw.URL(url);
      } catch (_) {
        return null;
      }
    }, pw);
    pw.Object.defineProperty(pw.URL, "parse", {
      value: fn,
      writable: true,
      configurable: true
    });
  }

  function installSetMethods(pw) {
    if (!pw.Set || !pw.Set.prototype || typeof pw.Set.prototype.union === "function") return;
    const proto = pw.Set.prototype;
    const defs = {
      union: function union(other) {
        const res = new pw.Set(this);
        const iter = pw.Array.from(other.keys());
        for (let i = 0; i < iter.length; i++) res.add(iter[i]);
        return res;
      },
      intersection: function intersection(other) {
        const res = new pw.Set();
        const iter = pw.Array.from(this);
        for (let i = 0; i < iter.length; i++) {
          if (other.has(iter[i])) res.add(iter[i]);
        }
        return res;
      },
      difference: function difference(other) {
        const res = new pw.Set(this);
        const iter = pw.Array.from(other.keys());
        for (let i = 0; i < iter.length; i++) res.delete(iter[i]);
        return res;
      },
      symmetricDifference: function symmetricDifference(other) {
        const res = new pw.Set(this);
        const iter = pw.Array.from(other.keys());
        for (let i = 0; i < iter.length; i++) {
          const v = iter[i];
          if (this.has(v)) res.delete(v);
          else res.add(v);
        }
        return res;
      },
      isSubsetOf: function isSubsetOf(other) {
        if (this.size > other.size) return false;
        const iter = pw.Array.from(this);
        for (let i = 0; i < iter.length; i++) {
          if (!other.has(iter[i])) return false;
        }
        return true;
      },
      isSupersetOf: function isSupersetOf(other) {
        if (this.size < other.size) return false;
        const iter = pw.Array.from(other.keys());
        for (let i = 0; i < iter.length; i++) {
          if (!this.has(iter[i])) return false;
        }
        return true;
      },
      isDisjointFrom: function isDisjointFrom(other) {
        const iter = pw.Array.from(this);
        for (let i = 0; i < iter.length; i++) {
          if (other.has(iter[i])) return false;
        }
        return true;
      }
    };
    const names = Object.keys(defs);
    for (let i = 0; i < names.length; i++) {
      const k = names[i];
      pw.Object.defineProperty(proto, k, {
        value: exportFunction(defs[k], pw),
        writable: true,
        configurable: true
      });
    }
  }

  function installTwimgAutoRetry() {
    let host = "";
    try {
      host = location.hostname || "";
    } catch (_) {
      return;
    }
    if (!/(^|\.)(x|twitter)\.com$/i.test(host)) return;

    window.addEventListener("error", function onImgError(ev) {
      const el = ev && ev.target;
      if (!el || el.tagName !== "IMG") return;
      const src = el.getAttribute("src") || el.src || "";
      if (!/^https:\/\/([a-z0-9-]+\.)?twimg\.com\//i.test(src)) return;

      const count = parseInt(el.getAttribute("data-gp-retry") || "0", 10) || 0;
      if (count >= 2) return;
      el.setAttribute("data-gp-retry", String(count + 1));

      const origSrcset = el.getAttribute("srcset");
      const delay = (count + 1) * 900;
      setTimeout(function () {
        if (!el.isConnected) return;
        if (el.complete && el.naturalWidth > 0) return;

        el.addEventListener("load", function syncBgOnce() {
          try {
            const parent = el.parentElement;
            if (parent) {
              const prev = el.previousElementSibling;
              if (prev && prev.tagName === "DIV") {
                const bg = prev.style && prev.style.backgroundImage;
                if (!bg || bg === "none" || bg === "") {
                  prev.style.backgroundImage = 'url("' + (el.currentSrc || src).replace(/"/g, "%22") + '")';
                }
              }
            }
          } catch (_) {}
        }, { once: true });

        if (origSrcset) el.removeAttribute("srcset");
        el.removeAttribute("src");
        requestAnimationFrame(function () {
          if (origSrcset) el.setAttribute("srcset", origSrcset);
          el.setAttribute("src", src);
        });
      }, delay);
    }, true);
  }
})();
