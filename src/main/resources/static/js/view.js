// 全局变量
let currentShareId = null;
let shareData = null;
let downloadInProgress = false;

// 加载分享内容
function loadShareContent(shareId) {
    currentShareId = shareId;
    const contentLoader = document.getElementById('contentLoader');
    const contentContainer = document.getElementById('contentContainer');
    const textContent = document.getElementById('textContent');
    const fileContent = document.getElementById('fileContent');

    // 显示加载状态
    contentLoader.classList.remove('d-none');
    contentContainer.classList.add('d-none');
    textContent.classList.add('d-none');
    fileContent.classList.add('d-none');

    API.get(`/api/share?shareId=${shareId}`)
        .then(data => {
            shareData = data;
            renderShareContent(data);
        })
        .catch(error => {
            console.error('加载分享内容失败:', error);
            contentContainer.innerHTML = `
                <div class="alert alert-warning text-center">
                    <i class="fas fa-exclamation-triangle fa-2x mb-3 text-warning"></i>
                    <h4>加载失败</h4>
                    <p>${error.message || '加载分享内容失败，请检查分享ID是否正确'}</p>
                    <button class="btn btn-primary mt-3" onclick="window.location.href='/'">
                        <i class="fas fa-home me-1"></i>返回首页
                    </button>
                </div>
            `;
            contentContainer.classList.remove('d-none');
        })
        .finally(() => {
            contentLoader.classList.add('d-none');
        });
}

// 渲染分享内容
function renderShareContent(data) {
    const contentContainer = document.getElementById('contentContainer');
    const textContent = document.getElementById('textContent');
    const fileContent = document.getElementById('fileContent');
    const downloadBtn = document.getElementById('downloadBtn');
    const contentTitle = document.getElementById('contentTitle');
    const viewCount = document.getElementById('viewCount');
    
    // 文件信息元素
    const fileNameEl = document.getElementById('fileName');
    const fileSizeEl = document.getElementById('fileSize');
    const fileTypeEl = document.getElementById('fileType');

    // 更新标题
    contentTitle.innerHTML = `<i class="fas fa-${data.file ? 'file' : 'font'} me-2 text-primary"></i>${data.file ? data.fileName : '分享的文本内容'}`;

    // 更新查看次数
    viewCount.textContent = data.viewCount || 0;

    // 显示内容容器
    contentContainer.classList.remove('d-none');

    // 文件分享
    if (data.file) {
        // 设置文件信息
        fileNameEl.textContent = data.fileName;
        fileSizeEl.textContent = Utils.formatFileSize(data.size);
        fileTypeEl.textContent = data.contentType || Utils.getFileType(data.fileName);
        
        // 显示文件内容区域
        fileContent.classList.remove('d-none');
        textContent.classList.add('d-none');
        
        // 绑定下载事件
        downloadBtn.addEventListener('click', function() {
            if (downloadInProgress) return;
            downloadFile(data.shareId, data.fileName);
        });
    }
    // 文本分享
    else {
        // 渲染文本内容
        let contentHtml = '';

        if (data.isRichText) {
            const textType = Utils.detectTextType(data.textContent);

            if (textType === 'markdown') {
                // Markdown 渲染
                contentHtml = marked.parse(data.textContent);
            } else if (textType === 'json') {
                // JSON 格式化
                try {
                    const formatted = JSON.stringify(JSON.parse(data.textContent), null, 2);
                    contentHtml = `<pre><code class="language-json">${Utils.escapeHtml(formatted)}</code></pre>`;
                } catch (e) {
                    contentHtml = `<pre><code class="language-text">${Utils.escapeHtml(data.textContent)}</code></pre>`;
                }
            } else if (textType === 'html') {
                // HTML 预览
                contentHtml = `
                    <div class="border rounded p-3" style="max-height: 600px; overflow: auto;">
                        ${data.textContent}
                    </div>
                `;
            } else {
                // 代码高亮
                contentHtml = `<pre><code class="language-${textType}">${Utils.escapeHtml(data.textContent)}</code></pre>`;
            }
        } else {
            // 普通文本
            contentHtml = `<pre>${Utils.escapeHtml(data.textContent)}</pre>`;
        }

        textContent.innerHTML = contentHtml;
        
        // 显示文本内容区域
        textContent.classList.remove('d-none');
        fileContent.classList.add('d-none');
        
        // 应用代码高亮
        if (textContent.querySelector('pre code')) {
            hljs.highlightAll();
        }
    }

    // 绑定复制内容事件
    const copyContentBtn = document.getElementById('copyContentBtn');
    if (copyContentBtn) {
        copyContentBtn.addEventListener('click', function() {
            let contentToCopy = '';

            if (data.file) {
                contentToCopy = `${window.location.origin}/view.html?id=${data.shareId}`;
            } else {
                contentToCopy = data.textContent;
            }

            Utils.copyToClipboard(contentToCopy, this);
        });
    }
}

// 下载文件
function downloadFile(shareId, fileName) {
    return new Promise((resolve, reject) => {
        downloadInProgress = true;
        const downloadBtn = document.getElementById('downloadBtn');
        const originalBtnHTML = downloadBtn.innerHTML;
        downloadBtn.disabled = true;
        downloadBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status"></span>下载中...';

        const url = `/api/share/download?shareId=${shareId}`;
        const xhr = new XMLHttpRequest();

        xhr.open('GET', url, true);
        xhr.responseType = 'blob';

        xhr.onload = function() {
            if (xhr.status === 200) {
                // 直接使用前端获取到的文件名，避免从响应头解析导致的问题
                const filename = fileName || 'download';

                // 创建下载链接
                const blob = new Blob([xhr.response]);
                const downloadUrl = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = downloadUrl;
                a.download = filename;
                document.body.appendChild(a);
                a.click();

                // 清理
                setTimeout(() => {
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(downloadUrl);
                }, 100);

                downloadBtn.disabled = false;
                downloadBtn.innerHTML = originalBtnHTML;
                downloadInProgress = false;
                resolve();
            } else {
                downloadBtn.disabled = false;
                downloadBtn.innerHTML = originalBtnHTML;
                downloadInProgress = false;
                reject(new Error(`下载失败: HTTP ${xhr.status}`));
            }
        };

        xhr.onerror = function() {
            downloadBtn.disabled = false;
            downloadBtn.innerHTML = originalBtnHTML;
            downloadInProgress = false;
            reject(new Error('网络错误'));
        };

        xhr.send();
    });
}

// 复制分享ID
const copyShareIdBtn = document.getElementById('copyShareIdBtn');
if (copyShareIdBtn) {
    copyShareIdBtn.addEventListener('click', function() {
        const shareIdDisplay = document.getElementById('shareIdDisplay');
        if (shareIdDisplay) {
            Utils.copyToClipboard(shareIdDisplay.textContent, this);
        }
    });
}
