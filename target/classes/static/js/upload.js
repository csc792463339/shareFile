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
    
    // 延迟执行，确保 DOM 和依赖都已准备好
    setTimeout(function() {
        try {
            console.log('upload.js: 开始绑定事件');
    initFileUpload();
    initTextShare();
    initPreviewToggle();
            console.log('upload.js: 初始化完成');
        } catch (error) {
            console.error('upload.js: 初始化失败', error);
            console.error('错误堆栈:', error.stack);
        }
    }, 300);
}

// 使用 DOMContentLoaded 事件确保 DOM 已加载
document.addEventListener('DOMContentLoaded', function() {
    console.log('upload.js: DOMContentLoaded 事件触发');
    initUploadModule();
});

// 如果 DOM 已经加载完成，立即执行
if (document.readyState !== 'loading') {
    console.log('upload.js: DOM 已加载，立即初始化');
    initUploadModule();
}

// 初始化文件上传功能
function initFileUpload() {
    console.log('upload.js: 初始化文件上传功能');
    
    const fileInput = document.getElementById('fileInput');
    const dropZone = document.getElementById('dropZone');
    const shareFileBtn = document.getElementById('shareFileBtn');
    
    console.log('upload.js: 元素检查', {
        fileInput: !!fileInput,
        dropZone: !!dropZone,
        shareFileBtn: !!shareFileBtn
    });

    if (!fileInput || !dropZone || !shareFileBtn) {
        console.error('upload.js: 缺少必要的元素');
        return;
    }

    // 文件选择标签点击事件
    const selectFileLabel = dropZone.querySelector('label[for="fileInput"]');
    if (selectFileLabel) {
        selectFileLabel.addEventListener('click', function(e) {
            console.log('upload.js: 文件选择标签被点击');
            // label 的 for 属性会自动触发 input
        });
    }

    // 文件输入变化事件
    fileInput.addEventListener('change', function(e) {
        console.log('upload.js: 文件选择变化', e.target.files);
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
            console.log('upload.js: 文件拖拽', files[0]);
            handleFileSelect(files[0]);
        }
    });

    // 创建分享按钮事件 - 使用事件委托确保绑定成功
    shareFileBtn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        console.log('upload.js: 创建分享按钮被点击', {
            selectedFile: !!selectedFile,
            uploadInProgress: uploadInProgress
        });
        
        if (!selectedFile) {
            showNotification('请先选择要分享的文件', 'warning');
            return;
        }
        
        if (uploadInProgress) {
            console.log('upload.js: 上传正在进行中，忽略点击');
            return;
        }
        
        console.log('upload.js: 开始上传文件');
        uploadFile();
    });
    
    console.log('upload.js: 文件上传事件绑定完成');
}

// 处理文件选择
function handleFileSelect(file) {
    console.log('upload.js: 处理文件选择', file.name, file.size);
    
    // 重置上传状态（如果之前有上传失败的情况）
    if (uploadInProgress) {
        console.log('upload.js: 检测到上传状态异常，重置状态');
        uploadInProgress = false;
    }
    
    // 检查文件大小（500MB限制）
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

    // 更新文件信息显示
    if (fileName) fileName.textContent = file.name;
    if (fileSize) fileSize.textContent = formatFileSize(file.size);
    if (fileInfo) fileInfo.classList.remove('d-none');

    // 更新上传区域显示
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

    // 启用分享按钮
    if (shareFileBtn) {
        shareFileBtn.disabled = false;
        console.log('upload.js: 分享按钮已启用');
    }
}

// 上传文件
function uploadFile() {
    console.log('upload.js: uploadFile 函数被调用');
    
    if (!selectedFile) {
        console.error('upload.js: 没有选择文件');
        showNotification('请先选择要分享的文件', 'warning');
        return;
    }
    
    if (uploadInProgress) {
        console.log('upload.js: 上传正在进行中');
        return;
    }

    const shareFileBtn = document.getElementById('shareFileBtn');
    const uploadProgress = document.getElementById('uploadProgress');
    const progressBar = uploadProgress?.querySelector('.progress-bar');
    const progressText = uploadProgress?.querySelector('.progress-text');

    console.log('upload.js: 开始上传，文件名:', selectedFile.name, '大小:', selectedFile.size);
    
    uploadInProgress = true;
    if (shareFileBtn) {
        shareFileBtn.disabled = true;
        shareFileBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>上传中...';
    }
    if (uploadProgress) uploadProgress.classList.remove('d-none');

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('isRichText', 'false');

    // 使用 XMLHttpRequest 以便显示上传进度
    const xhr = new XMLHttpRequest();

    xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            if (progressBar) {
                progressBar.style.width = percent + '%';
                progressBar.setAttribute('aria-valuenow', percent);
            }
            if (progressText) {
                progressText.textContent = Math.round(percent) + '%';
            }
        }
    };

    xhr.onload = function() {
        console.log('upload.js: 上传响应', xhr.status, xhr.responseText);
        
        if (xhr.status >= 200 && xhr.status < 300) {
            try {
                const response = JSON.parse(xhr.responseText);
                console.log('upload.js: 上传成功', response);
                // 重置状态（在跳转前）
                uploadInProgress = false;
                showNotification('文件分享创建成功！', 'success');
                
                // 跳转到分享页面
                setTimeout(() => {
                    window.location.href = `/share.html?id=${response.shareId}`;
                }, 1000);
            } catch (e) {
                console.error('upload.js: 解析响应失败', e, xhr.responseText);
                showNotification('创建分享失败，请重试', 'danger');
                resetUploadState();
            }
        } else {
            try {
                const error = JSON.parse(xhr.responseText);
                console.error('upload.js: 上传失败', error);
                showNotification(error.message || '上传失败', 'danger');
            } catch (e) {
                console.error('upload.js: 上传失败，无法解析错误', xhr.status, xhr.responseText);
                showNotification('上传失败: ' + xhr.status, 'danger');
            }
            resetUploadState();
        }
    };

    xhr.onerror = function() {
        console.error('upload.js: 网络错误');
        showNotification('网络错误，请检查连接', 'danger');
        resetUploadState();
    };

    xhr.onabort = function() {
        console.log('upload.js: 上传已取消');
        resetUploadState();
    };

    console.log('upload.js: 发送上传请求到 /api/share/file');
    xhr.open('POST', '/api/share/file');
    xhr.send(formData);
}

// 重置上传状态
function resetUploadState() {
    uploadInProgress = false;
    const shareFileBtn = document.getElementById('shareFileBtn');
    if (shareFileBtn) {
        shareFileBtn.disabled = false;
        shareFileBtn.innerHTML = '<i class="fas fa-share-alt me-2"></i>创建分享';
    }
    
    const uploadProgress = document.getElementById('uploadProgress');
    if (uploadProgress) {
        uploadProgress.classList.add('d-none');
        const progressBar = uploadProgress.querySelector('.progress-bar');
        if (progressBar) {
            progressBar.style.width = '0%';
            progressBar.setAttribute('aria-valuenow', '0');
        }
        const progressText = uploadProgress.querySelector('.progress-text');
        if (progressText) progressText.textContent = '0%';
    }
}

// 初始化文本分享功能
function initTextShare() {
    console.log('upload.js: 初始化文本分享功能');
    
    const textContent = document.getElementById('textContent');
    const charCount = document.getElementById('charCount');
    const shareTextBtn = document.getElementById('shareTextBtn');
    const richTextToggle = document.getElementById('richTextToggle');
    const previewToggle = document.getElementById('previewToggle');
    const previewPanel = document.getElementById('previewPanel');

    if (!textContent || !shareTextBtn) {
        console.error('upload.js: 文本分享元素缺失');
        return;
    }

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

        // 实时预览
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
        console.log('upload.js: 文本分享按钮被点击', {
            uploadInProgress: uploadInProgress,
            textLength: textContent.value.length
        });
        
        if (uploadInProgress) {
            console.log('upload.js: 上传正在进行中，忽略点击');
            return;
        }
        
        console.log('upload.js: 开始分享文本');
        shareText();
    });
    
    console.log('upload.js: 文本分享事件绑定完成');
}

// 更新预览
function updatePreview(content, isRichText) {
    const previewContent = document.getElementById('previewContent');
    if (!previewContent) return;

    if (!isRichText || !content) {
        previewContent.innerHTML = `<pre>${escapeHtml(content || '')}</pre>`;
        return;
    }

    const textType = safeUtils(
        (Utils) => Utils.detectTextType(content),
        () => {
            // 简单的文本类型检测
            if (/^\s*[\{\[]/.test(content)) return 'json';
            if (/<\/?[a-z][\s\S]*>/i.test(content)) return 'html';
            if (/^<\?xml/.test(content)) return 'xml';
            if (/# |\*|\_|\[.*\]\(.*\)/.test(content)) return 'markdown';
            return 'text';
        }
    );

    if (textType === 'markdown' && typeof marked !== 'undefined') {
        previewContent.innerHTML = marked.parse(content);
    } else if (textType === 'json') {
        try {
            const formatted = JSON.stringify(JSON.parse(content), null, 2);
            previewContent.innerHTML = `<pre><code class="language-json">${escapeHtml(formatted)}</code></pre>`;
            if (typeof hljs !== 'undefined') {
                hljs.highlightElement(previewContent.querySelector('code'));
            }
        } catch (e) {
            previewContent.innerHTML = `<pre><code class="language-text">${escapeHtml(content)}</code></pre>`;
        }
    } else if (textType === 'html') {
        previewContent.innerHTML = `
            <div class="border rounded p-3" style="max-height: 400px; overflow: auto;">
                ${content}
            </div>
        `;
    } else {
        previewContent.innerHTML = `<pre><code class="language-${textType}">${escapeHtml(content)}</code></pre>`;
        if (typeof hljs !== 'undefined') {
            const codeElement = previewContent.querySelector('code');
            if (codeElement) hljs.highlightElement(codeElement);
        }
    }
}

// 分享文本
function shareText() {
    console.log('upload.js: shareText 函数被调用');
    
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

    console.log('upload.js: 发送文本分享请求', requestData);

    fetch('/api/share/text', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestData)
    })
    .then(response => {
        console.log('upload.js: 文本分享响应', response.status);
        if (!response.ok) {
            return response.json().then(data => {
                throw new Error(data.message || `请求失败: ${response.status}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('upload.js: 文本分享成功', data);
        showNotification('文本分享创建成功！', 'success');
        
        // 跳转到分享页面
        setTimeout(() => {
            window.location.href = `/share.html?id=${data.shareId}`;
        }, 1000);
    })
    .catch(error => {
        console.error('upload.js: 分享文本失败', error);
        showNotification(`创建分享失败: ${error.message || '未知错误'}`, 'danger');
        uploadInProgress = false;
        if (shareTextBtn) {
            shareTextBtn.disabled = false;
            shareTextBtn.innerHTML = '<i class="fas fa-share-alt me-2"></i>创建分享';
        }
    });
}

// 初始化密码切换功能（已移除密码功能，保留空函数以兼容）
function initPasswordToggles() {
    // 密码功能已移除
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
