// 页面初始化
document.addEventListener('DOMContentLoaded', function() {
    // 初始化粒子背景
    initParticles();

    // 初始化页面特定功能
    initPageSpecificFeatures();

    // 设置全局事件监听
    setupGlobalEventListeners();

    // 检查URL参数
    checkUrlParams();

    // 初始化导航
    initNavigation();
});

// 初始化粒子背景
function initParticles() {
    if (typeof particlesJS !== 'undefined' && document.getElementById('particles-js')) {
        // 配置已在HTML中定义，这里只需初始化
        console.log('粒子背景已初始化');
    }
}

// 初始化页面特定功能
function initPageSpecificFeatures() {
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';

    switch(currentPage) {
        case 'index.html':
        case '':
            initHomePage();
            break;
        case 'share.html':
            initSharePage();
            break;
        case 'view.html':
            initViewPage();
            break;
    }
}

// 设置全局事件监听
function setupGlobalEventListeners() {
    // 全局键盘快捷键
    document.addEventListener('keydown', function(e) {
        // Ctrl/Cmd + / 打开帮助
        if ((e.ctrlKey || e.metaKey) && e.key === '/') {
            e.preventDefault();
            showHelpModal();
        }

        // Esc 关闭模态框
        if (e.key === 'Escape') {
            const modals = document.querySelectorAll('.modal.show');
            modals.forEach(modal => {
                const modalInstance = bootstrap.Modal.getInstance(modal);
                if (modalInstance) modalInstance.hide();
            });
        }
    });

    // 全局点击处理
    document.body.addEventListener('click', function(e) {
        // 处理链接在新标签页打开
        if (e.target.closest('a[target="_blank"]')) {
            e.preventDefault();
            const href = e.target.closest('a').href;
            window.open(href, '_blank');
        }
    });
}

// 检查URL参数
function checkUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);

    // 显示通知
    if (urlParams.has('message')) {
        const type = urlParams.get('type') || 'info';
        Utils.showNotification(decodeURIComponent(urlParams.get('message')), type);
    }

    // 清除URL参数
    if (urlParams.toString()) {
        const cleanUrl = window.location.pathname + window.location.hash;
        window.history.replaceState({}, document.title, cleanUrl);
    }
}

// 初始化导航
function initNavigation() {
    // 移动端导航切换
    const navbarToggles = document.querySelectorAll('.navbar-toggler');
    navbarToggles.forEach(toggle => {
        toggle.addEventListener('click', function() {
            document.body.classList.toggle('navbar-open');
        });
    });

    // 导航链接点击
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(link => {
        link.addEventListener('click', function() {
            // 关闭移动端导航
            if (document.body.classList.contains('navbar-open')) {
                document.body.classList.remove('navbar-open');
                const navbarCollapse = document.querySelector('.navbar-collapse');
                if (navbarCollapse && navbarCollapse.classList.contains('show')) {
                    const bsCollapse = bootstrap.Collapse.getInstance(navbarCollapse);
                    if (bsCollapse) bsCollapse.hide();
                }
            }
        });
    });
}

// 初始化首页
function initHomePage() {
    console.log('初始化首页');

    // 显示欢迎动画
    showWelcomeAnimation();

    // 设置文件上传事件
    setupFileUploadEvents();

    // 设置文本编辑事件
    setupTextEditorEvents();

    // 设置分享设置事件
    setupShareSettingsEvents();
}

// 初始化分享页
function initSharePage() {
    console.log('初始化分享页');

    // 从URL获取分享ID
    const urlParams = new URLSearchParams(window.location.search);
    const shareId = urlParams.get('id');

    if (shareId) {
        // 设置分享ID显示
        const shareIdDisplay = document.getElementById('shareIdDisplay');
        if (shareIdDisplay) {
            animateShareIdDisplay(shareId);
        }

        // 设置分享URL
        const shareUrl = `${window.location.origin}/view.html?id=${shareId}`;
        const shareUrlInput = document.getElementById('shareUrl');
        if (shareUrlInput) {
            shareUrlInput.value = shareUrl;
        }

        // 设置复制按钮
        const copyBtn = document.getElementById('copyUrlBtn');
        if (copyBtn) {
            copyBtn.addEventListener('click', function() {
                Utils.copyToClipboard(shareUrl, this);
            });
        }

        // 删除按钮已移除，不再需要设置
    }

    // 启动倒计时
    startShareCountdown();

    // 生成二维码
    generateQRCode(shareId);
}

// 初始化查看页
function initViewPage() {
    console.log('初始化查看页');

    // 从URL获取分享ID
    let shareId = '';
    
    // 检查是否有查询参数
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('id')) {
        shareId = urlParams.get('id');
    } else {
        // 尝试从路径获取（兼容旧URL格式）
        const pathParts = window.location.pathname.split('/');
        shareId = pathParts[pathParts.length - 1];
        if (shareId === 'view.html') {
            shareId = '';
        }
    }

    if (shareId) {
        // 加载分享内容
        loadShareContent(shareId);

        // 设置分享ID显示
        document.getElementById('shareIdDisplay').textContent = shareId;
    } else {
        // 无效分享ID
        document.getElementById('contentContainer').innerHTML = `
            <div class="alert alert-danger text-center py-5">
                <i class="fas fa-exclamation-triangle fa-3x mb-3"></i>
                <h4>无效的分享ID</h4>
                <p class="mb-3">无法找到对应的分享内容</p>
                <button class="btn btn-primary" onclick="window.location.href='/'">
                    <i class="fas fa-home me-1"></i>返回首页
                </button>
            </div>
        `;
        document.getElementById('contentLoader').classList.add('d-none');
        document.getElementById('contentContainer').classList.remove('d-none');
    }
}

// 显示欢迎动画
function showWelcomeAnimation() {
    const shareIdDisplay = document.querySelector('.share-id-display');
    if (shareIdDisplay) {
        shareIdDisplay.style.opacity = '0';
        shareIdDisplay.style.transform = 'scale(0.8)';

        setTimeout(() => {
            shareIdDisplay.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
            shareIdDisplay.style.opacity = '1';
            shareIdDisplay.style.transform = 'scale(1)';
        }, 300);
    }
}

// 动画显示分享ID
function animateShareIdDisplay(shareId) {
    const shareIdDisplay = document.getElementById('shareIdDisplay');
    if (!shareIdDisplay) return;

    // 打字机效果
    let i = 0;
    const typeInterval = setInterval(() => {
        if (i <= shareId.length) {
            shareIdDisplay.textContent = shareId.substring(0, i).padEnd(4, '0');
            i++;
        } else {
            clearInterval(typeInterval);

            // 完成后添加脉冲动画
            shareIdDisplay.classList.add('animate-pulse');
        }
    }, 150);
}

// 启动分享倒计时
function startShareCountdown() {
    const hoursEl = document.getElementById('hours');
    const minutesEl = document.getElementById('minutes');
    const secondsEl = document.getElementById('seconds');

    if (!hoursEl || !minutesEl || !secondsEl) return;

    let totalSeconds = 24 * 60 * 60; // 24小时

    // 初始化显示
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

// 更新倒计时显示
function updateCountdownDisplay(totalSeconds) {
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    document.getElementById('hours').textContent = hours.toString().padStart(2, '0');
    document.getElementById('minutes').textContent = minutes.toString().padStart(2, '0');
    document.getElementById('seconds').textContent = seconds.toString().padStart(2, '0');
}

// 生成二维码
function generateQRCode(shareId) {
    const qrcodeContainer = document.getElementById('qrcode');
    if (!qrcodeContainer || !shareId) return;

    const shareUrl = `${window.location.origin}/view/${shareId}`;

    // 清空容器
    qrcodeContainer.innerHTML = '';

    // 创建二维码
    new QRCode(qrcodeContainer, {
        text: shareUrl,
        width: 160,
        height: 160,
        colorDark: "#5e35b1",
        colorLight: "#ffffff",
        correctLevel: QRCode.CorrectLevel.H
    });

    // 添加动画
    qrcodeContainer.style.opacity = '0';
    qrcodeContainer.style.transform = 'scale(0.8)';

    setTimeout(() => {
        qrcodeContainer.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        qrcodeContainer.style.opacity = '1';
        qrcodeContainer.style.transform = 'scale(1)';
    }, 500);
}

// 设置文件上传事件
function setupFileUploadEvents() {
    // 拖拽区域事件已在upload.js中处理
    console.log('文件上传事件已设置');
}

// 设置文本编辑事件
function setupTextEditorEvents() {
    // 文本编辑事件已在upload.js中处理
    console.log('文本编辑事件已设置');
}

// 设置分享设置事件
function setupShareSettingsEvents() {
    // 密码切换事件已在upload.js中处理
    console.log('分享设置事件已设置');
}

// 显示帮助模态框
function showHelpModal() {
    let helpModal = document.getElementById('helpModal');

    if (!helpModal) {
        // 创建模态框
        helpModal = document.createElement('div');
        helpModal.id = 'helpModal';
        helpModal.className = 'modal fade';
        helpModal.tabIndex = '-1';
        helpModal.setAttribute('aria-hidden', 'true');

        helpModal.innerHTML = `
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header bg-gradient text-white">
                        <h5 class="modal-title"><i class="fas fa-question-circle me-2"></i>帮助中心</h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <div class="row mb-4">
                            <div class="col-md-4 mb-3">
                                <div class="card h-100 border-primary">
                                    <div class="card-body text-center">
                                        <div class="bg-primary bg-opacity-10 rounded-circle p-3 mb-3 d-inline-block">
                                            <i class="fas fa-cloud-upload-alt fa-2x text-primary"></i>
                                        </div>
                                        <h5 class="card-title">上传文件</h5>
                                        <p class="card-text">拖拽文件或点击按钮上传，支持最大500MB</p>
                                    </div>
                                </div>
                            </div>
                            <div class="col-md-4 mb-3">
                                <div class="card h-100 border-success">
                                    <div class="card-body text-center">
                                        <div class="bg-success bg-opacity-10 rounded-circle p-3 mb-3 d-inline-block">
                                            <i class="fas fa-font fa-2x text-success"></i>
                                        </div>
                                        <h5 class="card-title">分享文本</h5>
                                        <p class="card-text">支持富文本、代码高亮和Markdown预览</p>
                                    </div>
                                </div>
                            </div>
                            <div class="col-md-4 mb-3">
                                <div class="card h-100 border-info">
                                    <div class="card-body text-center">
                                        <div class="bg-info bg-opacity-10 rounded-circle p-3 mb-3 d-inline-block">
                                            <i class="fas fa-share-alt fa-2x text-info"></i>
                                        </div>
                                        <h5 class="card-title">安全分享</h5>
                                        <p class="card-text">可设置访问密码，24小时自动清理</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <h5 class="mb-3"><i class="fas fa-keyboard me-2 text-muted"></i>键盘快捷键</h5>
                        <table class="table table-sm">
                            <thead>
                                <tr>
                                    <th>快捷键</th>
                                    <th>功能</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><kbd>Ctrl</kbd> + <kbd>/</kbd></td>
                                    <td>打开/关闭帮助</td>
                                </tr>
                                <tr>
                                    <td><kbd>Esc</kbd></td>
                                    <td>关闭对话框</td>
                                </tr>
                                <tr>
                                    <td><kbd>Tab</kbd></td>
                                    <td>切换焦点</td>
                                </tr>
                            </tbody>
                        </table>

                        <div class="alert alert-info mt-4">
                            <i class="fas fa-lightbulb me-2"></i>
                            <strong>提示：</strong> 分享链接包含4位数字访问码，请妥善保管。所有内容将在24小时后自动删除。
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">知道了</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(helpModal);
    }

    // 显示模态框
    const modalInstance = new bootstrap.Modal(helpModal);
    modalInstance.show();
}

// 删除分享 (通用函数)
function deleteShare(shareId, password) {
    if (!shareId || !password) return;

    Utils.showLoading(document.body, '删除中...');

    fetch(`/api/share/${shareId}`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
            'X-Share-Password': password
        }
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                try {
                    const data = JSON.parse(text);
                    throw new Error(data.message || `删除失败: ${response.status}`);
                } catch (e) {
                    throw new Error(`删除失败: ${response.status}`);
                }
            });
        }
        // 删除成功可能没有响应体
        return response.text().then(text => {
            try {
                return JSON.parse(text);
            } catch (e) {
                return {};
            }
        });
    })
    .then(() => {
        Utils.showNotification('分享已成功删除', 'success');

        // 重定向到首页
        setTimeout(() => {
            window.location.href = '/?message=分享已成功删除&type=success';
        }, 1500);
    })
    .catch(error => {
        console.error('删除分享失败:', error);
        Utils.showNotification(`删除失败: ${error.message || '未知错误'}`, 'danger');
    })
    .finally(() => {
        // 移除加载状态
        document.body.removeChild(document.querySelector('.spinner-border').closest('div'));
    });
}