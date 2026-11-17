(function () {
  "use strict";

  var doc = document;
  doc.documentElement.classList.add("js");

  /* sticky nav shadow */
  var nav = doc.getElementById("nav");
  function onScroll() {
    if (!nav) return;
    nav.classList.toggle("scrolled", window.scrollY > 8);
  }
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* mobile menu */
  var toggle = doc.getElementById("navToggle");
  var links = doc.getElementById("navLinks");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    links.addEventListener("click", function (e) {
      if (e.target.tagName === "A") {
        links.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      }
    });
  }

  /* scroll reveal */
  var items = Array.prototype.slice.call(doc.querySelectorAll(".reveal"));
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("in");
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    items.forEach(function (el) { io.observe(el); });
  }
  /* safety net: never leave above-the-fold content invisible */
  setTimeout(function () {
    items.forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (r.top < window.innerHeight && r.bottom > 0) el.classList.add("in");
    });
  }, 700);

  /* year */
  var year = doc.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());

  /* copy checksum */
  var sha = doc.getElementById("sha");
  var copy = doc.getElementById("copySha");
  if (sha && copy) {
    copy.addEventListener("click", function () {
      var text = sha.textContent.trim();
      var done = function () {
        copy.textContent = "Copied";
        setTimeout(function () { copy.textContent = "Copy"; }, 1600);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done).catch(fallback);
      } else {
        fallback();
      }
      function fallback() {
        var ta = doc.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.opacity = "0";
        doc.body.appendChild(ta);
        ta.select();
        try { doc.execCommand("copy"); done(); } catch (e) { /* ignore */ }
        doc.body.removeChild(ta);
      }
    });
  }
  /* ---------- interactive demo ---------- */
  (function initDemo() {
    var stage = doc.getElementById("demoStage");
    var slider = doc.getElementById("demoSlider");
    var path = doc.getElementById("demoProgress");
    if (!stage || !slider || !path) return;

    var user = doc.getElementById("demoUser");
    var distEl = doc.getElementById("demoDist");
    var etaEl = doc.getElementById("demoEta");
    var statusEl = doc.getElementById("demoStatus");
    var statusText = doc.getElementById("demoStatusText");
    var MAX = 2800, RING = 500;
    var total = path.getTotalLength();

    function fmt(d) {
      return d >= 1000 ? (d / 1000).toFixed(1) + " km" : Math.round(d) + " m";
    }

    function render() {
      var d = Number(slider.value);
      var t = 1 - d / MAX;
      if (t < 0) t = 0;
      if (t > 1) t = 1;

      var pt = path.getPointAtLength(total * t);
      user.style.left = (pt.x / 300 * 100) + "%";
      user.style.top = (pt.y / 560 * 100) + "%";
      path.style.strokeDasharray = (total * t) + " " + total;

      slider.style.setProperty("--pct", (d / MAX * 100) + "%");
      distEl.textContent = fmt(d);
      etaEl.textContent = Math.max(0, Math.ceil(d / 6.94 / 60)) + " min";

      var ringing = d <= RING;
      stage.classList.toggle("ringing", ringing);
      statusEl.classList.toggle("hot", ringing);

      if (ringing) {
        statusText.textContent = "Alarm ringing — you’re at your stop";
      } else if (d <= 1200) {
        statusText.textContent = "Approaching — " + fmt(d) + " to go";
      } else {
        statusText.textContent = "Cruising — " + fmt(d) + " to go";
      }
    }

    slider.addEventListener("input", render);

    var reset = doc.getElementById("demoReset");
    if (reset) {
      reset.addEventListener("click", function () {
        slider.value = MAX;
        render();
      });
    }

    var overlay = doc.getElementById("alarmOverlay");
    if (overlay) {
      var buttons = overlay.querySelectorAll(".alarm-actions span");
      if (buttons[0]) {
        buttons[0].addEventListener("click", function () {
          slider.value = MAX;
          render();
        });
      }
      if (buttons[1]) {
        buttons[1].addEventListener("click", function () {
          slider.value = 800;
          render();
        });
      }
    }

    render();
  })();

  /* ---------- alarm sound preview (Web Audio, no files) ---------- */
  (function initSound() {
    var btn = doc.getElementById("soundBtn");
    if (!btn) return;
    var ctx = null;
    var label = btn.querySelector("span") || btn;

    function beep(freq, start, dur, level) {
      var osc = ctx.createOscillator();
      var gain = ctx.createGain();
      osc.type = "triangle";
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(level, start + 0.014);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + dur);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(start + dur + 0.03);
    }

    btn.addEventListener("click", function () {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) {
        label.textContent = "Audio not supported";
        return;
      }
      if (!ctx) ctx = new AC();
      if (ctx.state === "suspended") ctx.resume();

      var t0 = ctx.currentTime + 0.05;
      for (var cycle = 0; cycle < 3; cycle++) {
        var t = t0 + cycle * 0.46;
        beep(1046, t, 0.13, 0.13);
        beep(784, t + 0.16, 0.13, 0.11);
      }

      var old = label.textContent;
      label.textContent = "Ringing…";
      btn.disabled = true;
      setTimeout(function () {
        label.textContent = old;
        btn.disabled = false;
      }, 1550);
    });
  })();

  /* ---------- sticky mobile download bar ---------- */
  (function initSticky() {
    var cta = doc.getElementById("stickyCta");
    var target = doc.getElementById("download");
    if (!cta) return;
    function update() {
      var show = window.scrollY > 720;
      if (target) {
        var r = target.getBoundingClientRect();
        if (r.top < window.innerHeight * 0.92) show = false;
      }
      cta.classList.toggle("show", show);
    }
    window.addEventListener("scroll", update, { passive: true });
    update();
  })();

  /* ---------- card spotlight ---------- */
  if (window.matchMedia && window.matchMedia("(hover: hover)").matches) {
    doc.querySelectorAll(".card").forEach(function (card) {
      card.addEventListener("pointermove", function (e) {
        var r = card.getBoundingClientRect();
        card.style.setProperty("--mx", (e.clientX - r.left) + "px");
        card.style.setProperty("--my", (e.clientY - r.top) + "px");
      });
    });
  }
})();
