/* ============================================
   星芽花园 · 公共脚本
   包含：星空生成、植物表情映射、防抖工具
   ============================================ */

/** 生成星夜背景（星星 + 流星） */
function createStarBackground(containerId, opts) {
    opts = opts || {};
    const starCount = opts.count || 160;
    const container = document.getElementById(containerId);
    if (!container) return;

    const starElClass = opts.starClass || 'sg-star';
    container.innerHTML = '';

    for (let i = 0; i < starCount; i++) {
        const s = document.createElement('div');
        s.className = starElClass;
        const size = Math.random() * 2.6 + 0.8;
        s.style.width = size + 'px';
        s.style.height = size + 'px';
        s.style.left = Math.random() * 100 + '%';
        s.style.top = Math.random() * 100 + '%';
        s.style.animationDelay = (Math.random() * 5) + 's';
        s.style.animationDuration = (2 + Math.random() * 4) + 's';
        container.appendChild(s);
    }

    // 增加多颗随机流星，轨迹/时序随机，观感更自然流畅
    if (opts.shooting !== false) {
        const shootCount = opts.shootCount || 4;
        for (let i = 0; i < shootCount; i++) {
            const shoot = document.createElement('div');
            shoot.className = 'sg-shooting-star';
            shoot.style.top = (Math.random() * 45) + '%';
            shoot.style.animationDelay = (Math.random() * 9) + 's';
            shoot.style.animationDuration = (4.5 + Math.random() * 5) + 's';
            shoot.style.setProperty('--shoot-angle', (26 + Math.random() * 16) + 'deg');
            container.appendChild(shoot);
        }
    }
}

/** 植物名 → emoji 映射（覆盖 plant 表全部 18 株植物） */
function getPlantEmoji(plantName) {
    if (!plantName) return '🌱';
    const map = {
        '智慧藤': '📚🌿',
        '专注花': '🎯🌸',
        '活力草': '💪🍃',
        '晨光花': '🌅🌻',
        '宁静叶': '🧘🍀',
        '星芽草': '✨🌱',
        '灵感菇': '🍄',
        '韵律兰': '🌷',
        '逻辑松': '🌲',
        '思绪苇': '🪶',
        '记忆蕨': '🌾',
        '安眠星草': '✨',
        '自律棘': '🌵',
        '联结藤': '💮',
        '远见葵': '🌞',
        '静心茗': '🍵',
        '烟火椒': '🌶️',
        '秩序苔': '🪴'
    };
    return map[plantName] || '🌻';
}

/** 植物稀有度 → 颜色/样式 */
function getRarityStyle(rarity) {
    switch ((rarity || '').toUpperCase()) {
        case 'COMMON':    return { badge: 'sg-tag-success', glow: 'rgba(74,222,128,0.4)' };
        case 'RARE':      return { badge: 'sg-tag-info',    glow: 'rgba(96,165,250,0.4)' };
        case 'LEGENDARY': return { badge: 'sg-tag-warn',    glow: 'rgba(250,204,21,0.5)' };
        default:          return { badge: 'sg-tag-muted',   glow: 'rgba(255,255,255,0.3)' };
    }
}

/** 防抖函数 */
function debounce(fn, wait) {
    wait = wait || 300;
    let t;
    return function() {
        const ctx = this, args = arguments;
        clearTimeout(t);
        t = setTimeout(() => fn.apply(ctx, args), wait);
    };
}

/** URL 参数解析 */
function getUrlParam(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

/** 侧边栏高亮（根据当前 path） */
function highlightSidebar() {
    const path = window.location.pathname;
    document.querySelectorAll('.sg-menu a').forEach(function (a) {
        const href = a.getAttribute('href');
        if (href && href === path) {
            a.classList.add('active');
        }
    });
}

/** ECharts 深色星夜主题（统一色板） */
const sgChartTheme = {
    colors: ['#4ade80', '#FFD700', '#60a5fa', '#f87171', '#8b5cf6', '#f472b6'],
    axisLine: { color: 'rgba(255,255,255,0.2)' },
    axisLabel: { color: 'rgba(255,255,255,0.55)' },
    splitLine: { color: 'rgba(255,255,255,0.06)' },
    tooltipBg: 'rgba(15,23,42,0.9)',
    tooltipBorder: 'rgba(74,222,128,0.3)'
};

/* DOMContentLoaded 通用入口 */
document.addEventListener('DOMContentLoaded', function () {
    highlightSidebar();
});
