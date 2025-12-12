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
        
        // 生成下载命令
        generateDownloadCommands(data.shareId, data.fileName);

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

        // UI 元素
        const downloadBtn = document.getElementById('downloadBtn');
        const downloadProgress = document.getElementById('downloadProgress');
        const progressBar = document.getElementById('progressBar');
        const downloadPercent = document.getElementById('downloadPercent');
        const downloadSpeed = document.getElementById('downloadSpeed');
        const downloadedSize = document.getElementById('downloadedSize');
        const estimatedTime = document.getElementById('estimatedTime');

        const originalBtnHTML = downloadBtn.innerHTML;

        // 初始化UI
        downloadBtn.disabled = true;
        downloadBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status"></span>准备下载...';
        downloadProgress.classList.remove('d-none');

        // 进度跟踪变量
        let startTime = Date.now();
        let totalSize = 0;
        let loadedSize = 0;
        let lastTime = startTime;
        let lastLoaded = 0;

        const url = `/api/share/download?shareId=${shareId}`;
        const xhr = new XMLHttpRequest();

        xhr.open('GET', url, true);
        xhr.responseType = 'blob';

        // 进度事件
        xhr.onprogress = function(event) {
            if (event.lengthComputable && progressBar && downloadPercent && downloadSpeed && downloadedSize && estimatedTime) {
                totalSize = event.total;
                loadedSize = event.loaded;

                // 计算百分比
                const percent = Math.round((loadedSize / totalSize) * 100);

                // 更新进度条
                progressBar.style.width = percent + '%';
                downloadPercent.textContent = percent + '%';

                // 计算下载速度
                const currentTime = Date.now();
                const timeDiff = (currentTime - lastTime) / 1000; // 秒
                const sizeDiff = loadedSize - lastLoaded;

                if (timeDiff > 0.5 && sizeDiff > 0) { // 每0.5秒更新一次速度
                    const speed = sizeDiff / timeDiff; // bytes/s
                    downloadSpeed.textContent = formatSpeed(speed);

                    // 计算预计剩余时间
                    const remainingSize = totalSize - loadedSize;
                    if (speed > 0) {
                        const estimatedSeconds = Math.round(remainingSize / speed);
                        estimatedTime.textContent = '预计剩余时间: ' + formatTime(estimatedSeconds);
                    }

                    lastTime = currentTime;
                    lastLoaded = loadedSize;
                }

                // 更新已下载大小
                downloadedSize.textContent = `${Utils.formatFileSize(loadedSize)} / ${Utils.formatFileSize(totalSize)}`;

                // 更新按钮文本
                downloadBtn.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status"></span>下载中 ${percent}%`;
            }
        };

        xhr.onload = function() {
            if (xhr.status === 200) {
                // 下载完成，更新UI
                progressBar.style.width = '100%';
                downloadPercent.textContent = '100%';
                downloadBtn.innerHTML = '<i class="fas fa-check me-2"></i>下载完成';
                downloadSpeed.textContent = '下载完成';
                estimatedTime.textContent = '已完成';

                // 直接使用前端获取到的文件名
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

                // 3秒后重置UI
                setTimeout(() => {
                    resetDownloadUI();
                    resolve();
                }, 3000);
            } else {
                resetDownloadUI();
                reject(new Error(`下载失败: HTTP ${xhr.status}`));
            }
        };

        xhr.onerror = function() {
            resetDownloadUI();
            reject(new Error('网络错误'));
        };

        // 重置下载UI的函数
        function resetDownloadUI() {
            downloadBtn.disabled = false;
            downloadBtn.innerHTML = originalBtnHTML;
            downloadProgress.classList.add('d-none');
            progressBar.style.width = '0%';
            downloadPercent.textContent = '0%';
            downloadSpeed.textContent = '0 KB/s';
            downloadedSize.textContent = '0 MB / 0 MB';
            estimatedTime.textContent = '预计剩余时间: --';
            downloadInProgress = false;
        }

        xhr.send();
    });
}

// 格式化速度显示
function formatSpeed(bytesPerSecond) {
    if (bytesPerSecond === 0) return '0 KB/s';

    const units = ['B/s', 'KB/s', 'MB/s', 'GB/s'];
    let unitIndex = 0;
    let speed = bytesPerSecond;

    while (speed >= 1024 && unitIndex < units.length - 1) {
        speed /= 1024;
        unitIndex++;
    }

    return speed.toFixed(1) + ' ' + units[unitIndex];
}

// 格式化时间显示
function formatTime(seconds) {
    if (!seconds || seconds === Infinity || isNaN(seconds)) {
        return '--';
    }

    if (seconds < 60) {
        return `${seconds}秒`;
    } else if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        return `${minutes}分${remainingSeconds}秒`;
    } else {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        return `${hours}小时${minutes}分钟`;
    }
}

// 生成下载命令
function generateDownloadCommands(shareId, fileName) {
    const baseUrl = window.location.origin;
    const downloadUrl = `${baseUrl}/api/share/download?shareId=${shareId}`;

    // 生成命令
    const wgetCommand = `wget -O "${fileName}" "${downloadUrl}"`;
    const curlCommand = `curl -o "${fileName}" "${downloadUrl}"`;

    // 设置命令到输入框
    const wgetCommandEl = document.getElementById('wgetCommand');
    const curlCommandEl = document.getElementById('curlCommand');

    if (wgetCommandEl) wgetCommandEl.value = wgetCommand;
    if (curlCommandEl) curlCommandEl.value = curlCommand;

    // 绑定复制事件
    const copyWgetBtn = document.getElementById('copyWgetBtn');
    const copyCurlBtn = document.getElementById('copyCurlBtn');

    if (copyWgetBtn) {
        copyWgetBtn.addEventListener('click', function() {
            Utils.copyToClipboard(wgetCommand, this);
        });
    }

    if (copyCurlBtn) {
        copyCurlBtn.addEventListener('click', function() {
            Utils.copyToClipboard(curlCommand, this);
        });
    }
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
