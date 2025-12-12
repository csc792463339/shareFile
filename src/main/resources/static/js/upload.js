// 文件上传和分享功能
let selectedFile = null;
let uploadInProgress = false;

console.log('upload.js: 脚本已加载');

// 安全的 Utils 访问函数
function safeUtils(callback, fallback) {
    if (typeof Utils !== 'undefined') {
        return callback(Utils);
    } else {
        console.warn('upload.js: Utils 未定义，使用备用方案');
        if (fallback) {
            return fallback();
        }
        return null;
    }
}

// 显示通知的安全方法
function showNotification(message, type) {
    safeUtils(
        (Utils) => Utils.showNotification(message, type),
        () => {
            console.log(`[${type}] ${message}`);
            alert(message);
        }
    );
}

// 格式化文件大小的安全方法
function formatFileSize(bytes) {
    return safeUtils(
        (Utils) => Utils.formatFileSize(bytes),
        () => {
            if (bytes === 0) return '0 Bytes';
            const k = 1024;
            const sizes = ['Bytes', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }
    );
}

// 格式化网络速度
function formatSpeed(bytesPerSecond) {
    if (bytesPerSecond === 0) return '0 B/s';
    const k = 1024;
    const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s'];
    const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k));
    return parseFloat((bytesPerSecond / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// 格式化剩余时间
function formatTime(seconds) {
    if (!seconds || seconds === Infinity || isNaN(seconds) || seconds <= 0) return '--';

    if (seconds < 10) {
        return '即将完成';
    } else if (seconds < 60) {
        return Math.ceil(seconds) + '秒';
    } else if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = Math.floor(seconds % 60);
        if (remainingSeconds < 30) {
            return minutes + '分钟';
        } else {
            return minutes + '分' + remainingSeconds + '秒';
        }
    } else {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        if (minutes === 0) {
            return hours + '小时';
        } else {
            return hours + '小时' + minutes + '分钟';
        }
    }
}

// HTML 转义的安全方法
function escapeHtml(text) {
    return safeUtils(
        (Utils) => Utils.escapeHtml(text),
        () => {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    );
}

// 初始化函数
function initUploadModule() {
    console.log('upload.js: 开始初始化，Utils 状态:', typeof Utils !== 'undefined');

    setTimeout(function() {
        try {
            initFileUpload();
            initTextShare();
            initPreviewToggle();
            console.log('upload.js: 初始化完成');
        } catch (error) {
            console.error('upload.js: 初始化失败', error);
        }
    }, 300);
}

// 使用 DOMContentLoaded 事件确保 DOM 已加载
document.addEventListener('DOMContentLoaded', function() {
    initUploadModule();
});

if (document.readyState !== 'loading') {
    initUploadModule();
}

// 初始化文件上传功能
function initFileUpload() {
    const fileInput = document.getElementById('fileInput');
    const dropZone = document.getElementById('dropZone');
    const shareFileBtn = document.getElementById('shareFileBtn');

    if (!fileInput || !dropZone || !shareFileBtn) return;

    // 文件选择标签点击事件
    const selectFileLabel = dropZone.querySelector('label[for="fileInput"]');
    if (selectFileLabel) {
        selectFileLabel.addEventListener('click', function(e) {
            // label 的 for 属性会自动触发 input
        });
    }

    // 文件输入变化事件
    fileInput.addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (file) {
            handleFileSelect(file);
        }
    });

    // 拖拽上传
    dropZone.addEventListener('dragover', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragleave', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove('drag-over');
    });

    dropZone.addEventListener('drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove('drag-over');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleFileSelect(files[0]);
        }
    });

    // 创建分享按钮事件
    shareFileBtn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();

        if (!selectedFile) {
            showNotification('请先选择要分享的文件', 'warning');
            return;
        }

        if (uploadInProgress) return;

        uploadFile();
    });
}

// 处理文件选择
function handleFileSelect(file) {
    if (uploadInProgress) uploadInProgress = false;

    const maxSize = 500 * 1024 * 1024; // 500MB
    if (file.size > maxSize) {
        showNotification('文件大小超过500MB限制', 'danger');
        return;
    }

    selectedFile = file;
    const fileInfo = document.getElementById('fileInfo');
    const fileName = document.getElementById('fileName');
    const fileSize = document.getElementById('fileSize');
    const shareFileBtn = document.getElementById('shareFileBtn');
    const dropZone = document.getElementById('dropZone');

    if (fileName) fileName.textContent = file.name;
    if (fileSize) fileSize.textContent = formatFileSize(file.size);
    if (fileInfo) fileInfo.classList.remove('d-none');

    if (dropZone) {
        const textCenter = dropZone.querySelector('.text-center');
        if (textCenter) {
            textCenter.innerHTML = `
                <i class="fas fa-file display-1 text-primary mb-3"></i>
                <h4 class="mb-2">${escapeHtml(file.name)}</h4>
                <p class="text-muted">${formatFileSize(file.size)}</p>
            `;
        }
    }

    if (shareFileBtn) shareFileBtn.disabled = false;
}

// 上传文件
function uploadFile() {
    if (!selectedFile) return;
    if (uploadInProgress) return;

    const shareFileBtn = document.getElementById('shareFileBtn');
    const uploadProgressContainer = document.getElementById('uploadProgressContainer');
    const progressBar = document.getElementById('progressBar');
    const progressPercentage = document.getElementById('progressPercentage');
    const uploadSpeed = document.getElementById('uploadSpeed');
    const remainingTime = document.getElementById('remainingTime');

    uploadInProgress = true;
    if (shareFileBtn) {
        shareFileBtn.disabled = true;
        shareFileBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>上传中...';
    }

    // 显示进度容器
    if (uploadProgressContainer) {
        uploadProgressContainer.classList.remove('d-none');
    }

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('isRichText', 'false');

    const xhr = new XMLHttpRequest();

    // 用于计算网络速度和剩余时间的变量
    let uploadStartTime = Date.now();
    let lastTime = uploadStartTime;
    let lastLoaded = 0;
    const speedHistory = []; // 存储最近几次的速度，用于平滑显示
    const maxHistoryLength = 5;

    xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            const currentTime = Date.now();

            // 更新进度条
            if (progressBar) {
                progressBar.style.width = percent + '%';
                progressBar.setAttribute('aria-valuenow', percent);
            }

            // 更新百分比
            if (progressPercentage) {
                progressPercentage.textContent = Math.round(percent) + '%';
            }

            // 计算网络速度和剩余时间
            const timeDiff = currentTime - lastTime;
            const loadedDiff = e.loaded - lastLoaded;

            if (timeDiff > 300 && loadedDiff > 0) { // 每300ms更新一次，更及时
                // 计算当前瞬时速度 (字节/秒)
                const instantSpeed = loadedDiff / (timeDiff / 1000);

                // 将速度添加到历史记录中
                speedHistory.push(instantSpeed);
                if (speedHistory.length > maxHistoryLength) {
                    speedHistory.shift();
                }

                // 计算平均速度，使显示更平滑
                const avgSpeed = speedHistory.reduce((a, b) => a + b, 0) / speedHistory.length;

                // 计算总体平均速度
                const totalElapsed = (currentTime - uploadStartTime) / 1000;
                const overallSpeed = e.loaded / totalElapsed;

                // 使用平均速度计算剩余时间
                const remainingBytes = e.total - e.loaded;
                const remainingSeconds = overallSpeed > 0 ? remainingBytes / overallSpeed : 0;

                // 格式化显示
                const speedText = formatSpeed(avgSpeed);
                const timeText = formatTime(remainingSeconds);

                // 更新显示
                if (uploadSpeed) {
                    uploadSpeed.textContent = speedText;
                }
                if (remainingTime) {
                    remainingTime.textContent = '剩余 ' + timeText;
                }

                lastTime = currentTime;
                lastLoaded = e.loaded;
            }
        }
    };

    xhr.onload = function() {
        if (xhr.status >= 200 && xhr.status < 300) {
            try {
                const response = JSON.parse(xhr.responseText);
                uploadInProgress = false;
                showNotification('文件分享创建成功！正在跳转...', 'success');

                // 延迟跳转，优化体验
                setTimeout(() => {
                    window.location.href = `/share.html?id=${response.shareId}`;
                }, 1500);
            } catch (e) {
                console.error('解析响应失败', e);
                showNotification('创建分享失败，请重试', 'danger');
                resetUploadState();
            }
        } else {
            try {
                const error = JSON.parse(xhr.responseText);
                showNotification(error.message || '上传失败', 'danger');
            } catch (e) {
                showNotification('上传失败: ' + xhr.status, 'danger');
            }
            resetUploadState();
        }
    };

    xhr.onerror = function() {
        showNotification('网络错误，请检查连接', 'danger');
        resetUploadState();
    };

    xhr.onabort = function() {
        resetUploadState();
    };

    xhr.open('POST', '/api/share/file');
    xhr.send(formData);
}

// 重置上传状态
function resetUploadState() {
    uploadInProgress = false;
    const shareFileBtn = document.getElementById('shareFileBtn');
    if (shareFileBtn) {
        shareFileBtn.disabled = false;
        shareFileBtn.innerHTML = '<i class="fas fa-paper-plane me-2"></i>立即创建分享';
    }

    const uploadProgressContainer = document.getElementById('uploadProgressContainer');
    const progressBar = document.getElementById('progressBar');
    const progressPercentage = document.getElementById('progressPercentage');
    const uploadSpeed = document.getElementById('uploadSpeed');
    const remainingTime = document.getElementById('remainingTime');

    if (uploadProgressContainer) {
        uploadProgressContainer.classList.add('d-none');
    }

    if (progressBar) {
        progressBar.style.width = '0%';
        progressBar.setAttribute('aria-valuenow', '0');
    }

    if (progressPercentage) {
        progressPercentage.textContent = '0%';
    }

    if (uploadSpeed) {
        uploadSpeed.textContent = '准备上传...';
    }

    if (remainingTime) {
        remainingTime.textContent = '--';
    }
}

// 初始化文本分享功能
function initTextShare() {
    const textContent = document.getElementById('textContent');
    const charCount = document.getElementById('charCount');
    const shareTextBtn = document.getElementById('shareTextBtn');
    const richTextToggle = document.getElementById('richTextToggle');
    const previewToggle = document.getElementById('previewToggle');
    const previewPanel = document.getElementById('previewPanel');

    if (!textContent || !shareTextBtn) return;

    // 字符计数
    textContent.addEventListener('input', function() {
        const length = this.value.length;
        if (charCount) {
            charCount.textContent = length;
            if (length > 50000) {
                charCount.classList.add('text-danger');
                this.value = this.value.substring(0, 50000);
                showNotification('文本内容不能超过50000字符', 'warning');
            } else {
                charCount.classList.remove('text-danger');
            }
        }

        if (previewToggle?.checked && previewPanel) {
            updatePreview(this.value, richTextToggle?.checked);
        }
    });

    // 富文本切换
    if (richTextToggle) {
        richTextToggle.addEventListener('change', function() {
            if (previewToggle?.checked && previewPanel && textContent.value) {
                updatePreview(textContent.value, this.checked);
            }
        });
    }

    // 预览切换
    if (previewToggle) {
        previewToggle.addEventListener('change', function() {
            if (previewPanel) {
                if (this.checked && textContent.value) {
                    previewPanel.classList.remove('d-none');
                    updatePreview(textContent.value, richTextToggle?.checked);
                } else {
                    previewPanel.classList.add('d-none');
                }
            }
        });
    }

    // 创建分享按钮事件
    shareTextBtn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        if (uploadInProgress) return;
        shareText();
    });
}

// 更新预览
function updatePreview(content, isRichText) {
    const previewContent = document.getElementById('previewContent');
    if (!previewContent) return;

    if (!isRichText || !content) {
        previewContent.innerHTML = `<pre>${escapeHtml(content || '')}</pre>`;
        return;
    }

    safeUtils((Utils) => {
        const textType = Utils.detectTextType(content);
        if (textType === 'markdown' && typeof marked !== 'undefined') {
            previewContent.innerHTML = marked.parse(content);
        } else if (textType === 'json') {
            try {
                const formatted = JSON.stringify(JSON.parse(content), null, 2);
                previewContent.innerHTML = `<pre><code class="language-json">${escapeHtml(formatted)}</code></pre>`;
                if (typeof hljs !== 'undefined') hljs.highlightElement(previewContent.querySelector('code'));
            } catch (e) {
                previewContent.innerHTML = `<pre><code class="language-text">${escapeHtml(content)}</code></pre>`;
            }
        } else if (textType === 'html') {
            previewContent.innerHTML = `<div class="border rounded p-3" style="max-height: 400px; overflow: auto;">${content}</div>`;
        } else {
            previewContent.innerHTML = `<pre><code class="language-${textType}">${escapeHtml(content)}</code></pre>`;
            if (typeof hljs !== 'undefined') {
                const codeElement = previewContent.querySelector('code');
                if (codeElement) hljs.highlightElement(codeElement);
            }
        }
    }, () => {
        previewContent.innerHTML = `<pre>${escapeHtml(content)}</pre>`;
    });
}

// 分享文本
function shareText() {
    const textContent = document.getElementById('textContent');
    const shareTextBtn = document.getElementById('shareTextBtn');
    const richTextToggle = document.getElementById('richTextToggle');

    if (!textContent || !textContent.value.trim()) {
        showNotification('请输入要分享的文本内容', 'warning');
        return;
    }

    const content = textContent.value.trim();
    if (content.length > 50000) {
        showNotification('文本内容不能超过50000字符', 'danger');
        return;
    }

    uploadInProgress = true;
    if (shareTextBtn) {
        shareTextBtn.disabled = true;
        shareTextBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>创建中...';
    }

    const requestData = {
        textContent: content,
        isRichText: richTextToggle?.checked || false
    };

    fetch('/api/share/text', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData)
    })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || `请求失败: ${response.status}`);
                });
            }
            return response.json();
        })
        .then(data => {
            showNotification('文本分享创建成功！正在跳转...', 'success');
            setTimeout(() => {
                window.location.href = `/share.html?id=${data.shareId}`;
            }, 1500);
        })
        .catch(error => {
            showNotification(`创建分享失败: ${error.message || '未知错误'}`, 'danger');
            uploadInProgress = false;
            if (shareTextBtn) {
                shareTextBtn.disabled = false;
                shareTextBtn.innerHTML = '<i class="fas fa-share-alt me-2"></i>创建分享';
            }
        });
}

// 初始化预览切换
function initPreviewToggle() {
    const previewToggle = document.getElementById('previewToggle');
    const previewPanel = document.getElementById('previewPanel');
    const textContent = document.getElementById('textContent');
    const richTextToggle = document.getElementById('richTextToggle');

    if (previewToggle && previewPanel) {
        previewToggle.addEventListener('change', function() {
            if (this.checked && textContent && textContent.value) {
                previewPanel.classList.remove('d-none');
                updatePreview(textContent.value, richTextToggle?.checked);
            } else {
                previewPanel.classList.add('d-none');
            }
        });
    }
}