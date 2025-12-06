// 页面初始化
document.addEventListener('DOMContentLoaded', function () {
    // 初始化页面特定功能
    initPageSpecificFeatures();

    // 设置全局事件监听
    setupGlobalEventListeners();

    // 检查URL参数
    checkUrlParams();

    // 初始化导航
    initNavigation();

    console.log('FlashShare: 浅色主题已加载');
});

// 初始化页面特定功能
function initPageSpecificFeatures() {
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';

    // 简单路由逻辑
    if (currentPage === 'index.html' || currentPage === '') {
        initHomePage();
    } else if (currentPage === 'share.html') {
        initSharePage();
    } else if (currentPage === 'view.html') {
        initViewPage();
    }
}

// 设置全局事件监听
function setupGlobalEventListeners() {
    // 全局键盘快捷键
    document.addEventListener('keydown', function (e) {
        // Esc 关闭模态框
        if (e.key === 'Escape') {
            const modals = document.querySelectorAll('.modal.show');
            modals.forEach(modal => {
                const modalInstance = bootstrap.Modal.getInstance(modal);
                if (modalInstance) modalInstance.hide();
            });
        }
    });
}

// 检查URL参数
function checkUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('message')) {
        const type = urlParams.get('type') || 'info';
        if (typeof Utils !== 'undefined') {
            Utils.showNotification(decodeURIComponent(urlParams.get('message')), type);
        }
        // 清除URL参数，保持地址栏干净
        window.history.replaceState({}, document.title, window.location.pathname);
    }
}

// 初始化导航
function initNavigation() {
    const navbarToggles = document.querySelectorAll('.navbar-toggler');
    navbarToggles.forEach(toggle => {
        toggle.addEventListener('click', function () {
            document.body.classList.toggle('navbar-open');
        });
    });
}

// 初始化首页
function initHomePage() {
    // 首页的事件绑定主要在 index.html 的 inline script 和 upload.js 中处理
    // 这里可以添加额外的首页特有逻辑
}

// 初始化分享页 (share.html)
function initSharePage() {
    const urlParams = new URLSearchParams(window.location.search);
    const shareId = urlParams.get('id');

    if (shareId) {
        // 动态打字机效果显示ID
        const shareIdDisplay = document.getElementById('shareIdDisplay');
        if (shareIdDisplay) {
            shareIdDisplay.textContent = shareId;
            shareIdDisplay.classList.add('animate-pulse');
        }

        const shareUrl = `${window.location.origin}/view.html?id=${shareId}`;
        const shareUrlInput = document.getElementById('shareUrl');
        if (shareUrlInput) shareUrlInput.value = shareUrl;

        const copyBtn = document.getElementById('copyUrlBtn');
        if (copyBtn) {
            copyBtn.addEventListener('click', function () {
                Utils.copyToClipboard(shareUrl, this);
            });
        }

        // 生成二维码
        generateQRCode(shareId);
    }

    startShareCountdown();
}

// 初始化查看页 (view.html)
function initViewPage() {
    // 逻辑主要在 view.js 中，这里保留作为入口扩展
}

// 启动倒计时
function startShareCountdown() {
    const hoursEl = document.getElementById('hours');
    if (!hoursEl) return;

    let totalSeconds = 24 * 60 * 60; // 24小时
    updateCountdownDisplay(totalSeconds);

    const countdownInterval = setInterval(() => {
        totalSeconds--;
        if (totalSeconds <= 0) {
            clearInterval(countdownInterval);
            return;
        }
        updateCountdownDisplay(totalSeconds);
    }, 1000);
}

function updateCountdownDisplay(totalSeconds) {
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const hEl = document.getElementById('hours');
    const mEl = document.getElementById('minutes');
    const sEl = document.getElementById('seconds');

    if (hEl) hEl.textContent = hours.toString().padStart(2, '0');
    if (mEl) mEl.textContent = minutes.toString().padStart(2, '0');
    if (sEl) sEl.textContent = seconds.toString().padStart(2, '0');
}

// 生成二维码
function generateQRCode(shareId) {
    const qrcodeContainer = document.getElementById('qrcode');
    if (!qrcodeContainer || !shareId) return;

    const shareUrl = `${window.location.origin}/view/${shareId}`;
    qrcodeContainer.innerHTML = '';

    // 使用 qrcode.js 生成
    if (typeof QRCode !== 'undefined') {
        new QRCode(qrcodeContainer, {
            text: shareUrl,
            width: 140,
            height: 140,
            colorDark: "#4f46e5", // 使用新的主色调
            colorLight: "#ffffff",
            correctLevel: QRCode.CorrectLevel.H
        });
    }
}