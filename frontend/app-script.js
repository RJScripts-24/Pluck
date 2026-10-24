/* =====================================================
   PLUCK — Application Shared Script
   Matrix Rain, Cmd+K Palette, Toasts & Common Utilities
   ===================================================== */

// ── 0. Universal Auth Fetch Interceptor ──────────────────
(function setupAuthFetch() {
  const originalFetch = window.fetch;
  window.fetch = function(resource, init = {}) {
    try {
      const token = localStorage.getItem('pluck_token') || localStorage.getItem('token');
      const url = typeof resource === 'string' ? resource : (resource && resource.url ? resource.url : '');
      if (url && (url.includes('/api/') || url.startsWith('/api'))) {
        init = init || {};
        init.headers = init.headers || {};
        if (token) {
          if (init.headers instanceof Headers) {
            if (!init.headers.has('Authorization')) {
              init.headers.set('Authorization', `Bearer ${token}`);
            }
          } else if (!init.headers['Authorization']) {
            init.headers['Authorization'] = `Bearer ${token}`;
          }
        }
        let activeWs = localStorage.getItem('pluck_active_workspace');
        if (activeWs === 'ws_personal') {
          localStorage.removeItem('pluck_active_workspace');
          activeWs = null;
        }
        if (activeWs) {
          if (init.headers instanceof Headers) {
            if (!init.headers.has('X-Workspace-Id')) {
              init.headers.set('X-Workspace-Id', activeWs);
            }
          } else if (!init.headers['X-Workspace-Id']) {
            init.headers['X-Workspace-Id'] = activeWs;
          }
        }
        if (!init.credentials) {
          init.credentials = 'include';
        }
      }
    } catch (e) {
      console.warn('Auth interceptor warning:', e);
    }
    return originalFetch.call(this, resource, init);
  };
})();

// ── 1. Matrix Rain Background (Optimized 30fps RAF) ───
(function initMatrix() {
  const canvas = document.getElementById('matrix-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  const CHARS = '0123456789ABCDEF{}()<>=+*!?;:_/~$#@';
  const FONT_SIZE = 14;
  let cols = 0;
  let drops = [];

  function resize() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    cols = Math.floor(canvas.width / FONT_SIZE);
    drops = Array.from({ length: cols }, () => Math.floor(Math.random() * -50));
    ctx.font = `${FONT_SIZE}px "Space Mono", monospace`;
  }
  resize();
  window.addEventListener('resize', resize, { passive: true });

  let lastTime = 0;
  const FRAME_INTERVAL = 38; // ~26 FPS: perfect nostalgic retro aesthetic, minimal CPU/GPU

  function draw() {
    ctx.fillStyle = 'rgba(10, 10, 10, 0.09)';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.font = `${FONT_SIZE}px "Space Mono", monospace`;
    for (let i = 0; i < cols; i++) {
      const ch = CHARS[Math.floor(Math.random() * CHARS.length)];
      ctx.fillStyle = Math.random() < 0.05 ? '#f472b6' : '#7c3aed';
      ctx.fillText(ch, i * FONT_SIZE, drops[i] * FONT_SIZE);

      if (drops[i] * FONT_SIZE > canvas.height && Math.random() > 0.975) {
        drops[i] = 0;
      }
      drops[i]++;
    }
  }

  function loop(now) {
    if (!document.hidden && now - lastTime >= FRAME_INTERVAL) {
      lastTime = now;
      draw();
    }
    requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);
})();

// ── 2. Toast System ───────────────────────────────────
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  // De-duplicate identical toasts already displaying
  const existingToasts = Array.from(container.children);
  const alreadyShowing = existingToasts.some(t => t.textContent && t.textContent.includes(message));
  if (alreadyShowing) return;

  const toast = document.createElement('div');
  toast.className = `pluck-toast toast-${type}`;
  const icon = type === 'success' ? '⚡' : type === 'error' ? '⚠️' : '✨';
  toast.innerHTML = `
    <span>${icon}</span>
    <span style="flex: 1;">${message}</span>
    <button onclick="this.parentElement.remove()" style="background: transparent; border: none; color: #94a3b8; cursor: pointer; font-size: 13px; padding: 0 0 0 6px; line-height: 1;" onmouseover="this.style.color='#fff'" onmouseout="this.style.color='#94a3b8'">✕</button>
  `;
  container.appendChild(toast);

  const duration = type === 'error' ? 6500 : 2500;
  setTimeout(() => {
    if (toast.parentElement) {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.25s ease';
      setTimeout(() => toast.remove(), 250);
    }
  }, duration);
}

// ── 3. Global Cmd+K Command Palette ────────────────────
(function initCmdK() {
  const overlay = document.getElementById('cmdk-modal');
  const input = document.getElementById('cmdk-input');
  const resultsContainer = document.getElementById('cmdk-results');

  function getCmdkItems() {
    const actionItems = [
      { title: '+ Create New Snippet', lang: 'Action', pkg: 'Studio', path: 'studio.html' },
      { title: 'Open Team Workspace (Acme Core Eng)', lang: 'Action', pkg: 'Teams', path: 'team.html' },
      { title: 'API Access Tokens & MCP Config', lang: 'Action', pkg: 'Settings', path: 'settings.html' },
      { title: 'Pluck Landing Page', lang: 'Action', pkg: 'Home', path: 'index.html' }
    ];

    let userSnippets = [];
    try {
      userSnippets = JSON.parse(localStorage.getItem('pluck_user_snippets') || '[]');
    } catch (e) {}

    const snippetItems = userSnippets.map(s => {
      const depName = (s.dependencies && s.dependencies.length > 0)
        ? (s.dependencies[0].name || s.dependencies[0])
        : (s.folderId || 'vault');
      return {
        title: s.title || 'Untitled Snippet',
        lang: (s.language || 'Code').toUpperCase(),
        pkg: depName,
        path: `snippet.html?id=${encodeURIComponent(s.id)}`
      };
    });

    return [...snippetItems, ...actionItems];
  }

  function openPalette() {
    if (!overlay) return;
    overlay.classList.add('open');
    if (input) {
      input.value = '';
      input.focus();
      renderResults(getCmdkItems());
    }
  }

  function closePalette() {
    if (!overlay) return;
    overlay.classList.remove('open');
  }

  function renderResults(items) {
    if (!resultsContainer) return;
    resultsContainer.innerHTML = '';
    if (items.length === 0) {
      resultsContainer.innerHTML = `<div style="padding: 20px; text-align: center; color: var(--muted); font-size: 12px;">No snippets match this query</div>`;
      return;
    }

    items.forEach((item, index) => {
      const el = document.createElement('div');
      el.className = `cmdk-item ${index === 0 ? 'selected' : ''}`;
      el.innerHTML = `
        <div style="display: flex; align-items: center; gap: 10px;">
          <span style="color: var(--cyan); font-size: 11px;">[${item.lang}]</span>
          <span style="font-weight: 700;">${item.title}</span>
          <span class="dep-chip" style="font-size: 9px;">${item.pkg}</span>
        </div>
        <span style="font-size: 10px; color: var(--muted);">↵ Open</span>
      `;
      el.addEventListener('click', () => {
        window.location.href = item.path;
      });
      resultsContainer.appendChild(el);
    });
  }

  // Global Keydown
  window.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      if (overlay && overlay.classList.contains('open')) {
        closePalette();
      } else {
        openPalette();
      }
    } else if (e.key === 'Escape' && overlay && overlay.classList.contains('open')) {
      closePalette();
    }
  });

  if (overlay) {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) closePalette();
    });
  }

  // Search input filtering
  if (input) {
    input.addEventListener('input', (e) => {
      const q = e.target.value.toLowerCase().trim();
      const allItems = getCmdkItems();
      const filtered = allItems.filter(it =>
        it.title.toLowerCase().includes(q) ||
        it.lang.toLowerCase().includes(q) ||
        it.pkg.toLowerCase().includes(q)
      );
      renderResults(filtered);
    });
  }

  // Trigger button clicks
  document.querySelectorAll('.js-open-cmdk').forEach(btn => {
    btn.addEventListener('click', openPalette);
  });
})();

// ── 4. Quick Copy Helper ───────────────────────────────
function copySnippetText(text, label = 'Code copied to clipboard!') {
  navigator.clipboard.writeText(text).then(() => {
    showToast(label, 'success');
  }).catch(() => {
    showToast('Failed to copy', 'error');
  });
}

// ── 5. React Bits Dock Magnification Engine for Primary Buttons (Zero-Reflow RAF) ──
(function initDockButtons() {
  const buttons = document.querySelectorAll('.btn-solid-pixel, .btn-outline-pixel, .topbar-actions a, .topbar-actions button');
  if (!buttons.length) return;

  const DISTANCE = 130;
  const MAX_SCALE = 1.08;
  const STIFFNESS = 0.24;
  const DAMPING = 0.75;

  const buttonStates = Array.from(buttons).map(btn => ({
    el: btn,
    scale: 1,
    targetScale: 1,
    transX: 0,
    targetTransX: 0,
    transY: 0,
    targetTransY: 0,
    vx: 0,
    vy: 0,
    vs: 0,
    isHovered: false,
    centerX: 0,
    centerY: 0
  }));

  function measureButtons() {
    buttonStates.forEach(b => {
      const rect = b.el.getBoundingClientRect();
      b.centerX = rect.left + rect.width / 2;
      b.centerY = rect.top + rect.height / 2;
    });
  }
  measureButtons();
  window.addEventListener('resize', measureButtons, { passive: true });
  window.addEventListener('scroll', measureButtons, { passive: true });

  let isTicking = false;
  let mouse = { x: -9999, y: -9999 };

  function update() {
    let active = false;

    for (let i = 0; i < buttonStates.length; i++) {
      const b = buttonStates[i];

      if (b.isHovered) {
        b.targetScale = MAX_SCALE;
        b.targetTransX = 0;
        b.targetTransY = -2;
      } else {
        const dx = mouse.x - b.centerX;
        const dy = mouse.y - b.centerY;
        const distSq = dx * dx + dy * dy;

        if (distSq < DISTANCE * DISTANCE) {
          const dist = Math.sqrt(distSq);
          const norm = (1 + Math.cos((dist / DISTANCE) * Math.PI)) / 2;
          b.targetScale = 1 + (MAX_SCALE - 1) * norm;
          b.targetTransX = (dx / DISTANCE) * 4 * norm;
          b.targetTransY = (dy / DISTANCE) * 4 * norm;
        } else {
          b.targetScale = 1;
          b.targetTransX = 0;
          b.targetTransY = 0;
        }
      }

      const forceS = (b.targetScale - b.scale) * STIFFNESS;
      b.vs = (b.vs + forceS) * DAMPING;
      b.scale += b.vs;

      const forceX = (b.targetTransX - b.transX) * STIFFNESS;
      b.vx = (b.vx + forceX) * DAMPING;
      b.transX += b.vx;

      const forceY = (b.targetTransY - b.transY) * STIFFNESS;
      b.vy = (b.vy + forceY) * DAMPING;
      b.transY += b.vy;

      if (
        Math.abs(b.scale - 1) > 0.002 ||
        Math.abs(b.transX) > 0.05 ||
        Math.abs(b.transY) > 0.05 ||
        Math.abs(b.vs) > 0.001
      ) {
        b.el.style.transform = `scale(${b.scale.toFixed(3)}) translate(${b.transX.toFixed(1)}px, ${b.transY.toFixed(1)}px)`;
        active = true;
      } else if (b.scale !== 1 || b.transX !== 0 || b.transY !== 0) {
        b.scale = 1;
        b.transX = 0;
        b.transY = 0;
        b.vs = 0;
        b.vx = 0;
        b.vy = 0;
        b.el.style.transform = '';
      }
    }

    if (active) {
      requestAnimationFrame(update);
    } else {
      isTicking = false;
    }
  }

  window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX;
    mouse.y = e.clientY;
    if (!isTicking) {
      isTicking = true;
      requestAnimationFrame(update);
    }
  }, { passive: true });

  window.addEventListener('mouseleave', () => {
    mouse.x = -9999;
    mouse.y = -9999;
  });

  buttonStates.forEach(b => {
    b.el.addEventListener('mouseenter', () => { b.isHovered = true; });
    b.el.addEventListener('mouseleave', () => { b.isHovered = false; });
  });
})();

// ── 6. Universal Pixel Sign Out Modal Engine ───────────
(function initSignOutModal() {
  function getModal() {
    let modal = document.getElementById('pluck-signout-modal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'pluck-signout-modal';
      modal.className = 'modal-backdrop';
      modal.style.display = 'none';
      modal.innerHTML = `
        <div class="modal-dialog pixel-border-box" onclick="event.stopPropagation()" style="max-width: 440px; border-color: rgba(239, 68, 68, 0.45); box-shadow: 0 24px 60px rgba(0, 0, 0, 0.95), 0 0 35px rgba(239, 68, 68, 0.25), 4px 4px 0 0 rgba(0, 0, 0, 0.9);">
          <div class="modal-header">
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 16px;">🚪</span>
              <h3 style="font-family: var(--mono); font-size: 13px; font-weight: 700; color: #fff; margin: 0; text-transform: uppercase;">
                Sign Out of Pluck
              </h3>
            </div>
            <button type="button" class="modal-close-btn" onclick="window.closeSignOutModal()" aria-label="Close modal">✕</button>
          </div>

          <p style="font-size: 11px; color: var(--muted); margin: 8px 0 22px 0; line-height: 1.6;">
            Are you sure you want to sign out? Your active session token will be invalidated and you will be returned to the sign-in screen.
          </p>

          <div style="display: flex; justify-content: flex-end; gap: 10px;">
            <button type="button" class="btn-outline-pixel" onclick="window.closeSignOutModal()" style="font-size: 11px; padding: 7px 16px;">
              Cancel
            </button>
            <button type="button" id="confirm-signout-btn" class="btn-danger-pixel" onclick="window.executeSignOut()" style="font-size: 11px; padding: 7px 18px; display: flex; align-items: center; gap: 6px;">
              <span>🚪</span> Sign Out
            </button>
          </div>
        </div>
      `;
      modal.addEventListener('click', (e) => {
        if (e.target === modal) window.closeSignOutModal();
      });
      document.body.appendChild(modal);
    }
    return modal;
  }

  window.openSignOutModal = function() {
    const modal = getModal();
    modal.style.display = 'flex';
  };

  window.closeSignOutModal = function() {
    const modal = document.getElementById('pluck-signout-modal');
    if (modal) modal.style.display = 'none';
  };

  window.executeSignOut = function() {
    const btn = document.getElementById('confirm-signout-btn');
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Signing out...';
    }
    localStorage.removeItem('pluck_user');
    localStorage.removeItem('pluck_token');
    localStorage.removeItem('pluck_active_workspace');
    localStorage.removeItem('pluck_user_snippets');
    document.cookie = 'pluck_session=; max-age=0; path=/;';
    fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    window.location.href = 'auth.html';
  };

  window.confirmSignOut = function(e) {
    if (e && e.preventDefault) e.preventDefault();
    window.openSignOutModal();
    return false;
  };

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      window.closeSignOutModal();
    }
  });
})();


