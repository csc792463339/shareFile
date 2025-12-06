// 通用工具函数
class Utils {
    // 生成唯一ID
    static uuid() {
        return ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, c =>
            (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
        );
    }

    // 格式化文件大小
    static formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // 格式化日期
    static formatDate(date) {
        return new Date(date).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // 复制到剪贴板 (修复版：增加兼容性支持)
    static copyToClipboard(text, element) {
        // 成功后的UI回调
        const handleSuccess = () => {
            const originalHTML = element.innerHTML;
            element.innerHTML = '<i class="fas fa-check me-1"></i>已复制!';
            element.classList.add('btn-success');
            element.classList.remove('btn-primary', 'btn-outline-primary');
            element.disabled = true;

            setTimeout(() => {
                element.innerHTML = originalHTML;
                element.classList.add('btn-primary');
                element.classList.remove('btn-success');
                if (originalHTML.includes('outline')) {
                    element.classList.add('btn-outline-primary');
                }
                element.disabled = false;
            }, 2000);
        };

        // 失败后的回调
        const handleError = (err) => {
            console.error('复制失败:', err);
            // 最后的兜底：提示用户手动复制
            window.prompt("复制失败，请手动复制以下内容:", text);
        };

        // 优先使用现代 API (需要 HTTPS 或 localhost)
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(text).then(handleSuccess).catch(() => {
                // 如果权限被拒绝，尝试回退方案
                this.fallbackCopyTextToClipboard(text, handleSuccess, handleError);
            });
        } else {
            // HTTP 环境使用回退方案 (execCommand)
            this.fallbackCopyTextToClipboard(text, handleSuccess, handleError);
        }
    }

    // 兼容性复制方案 (用于 HTTP 环境)
    static fallbackCopyTextToClipboard(text, onSuccess, onError) {
        const textArea = document.createElement("textarea");
        textArea.value = text;

        // 避免回流和弹窗，将元素隐藏
        textArea.style.top = "0";
        textArea.style.left = "0";
        textArea.style.position = "fixed";
        textArea.style.opacity = "0";
        textArea.style.pointerEvents = "none";

        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();

        try {
            const successful = document.execCommand('copy');
            document.body.removeChild(textArea);
            if (successful) {
                onSuccess();
            } else {
                onError(new Error('execCommand returned false'));
            }
        } catch (err) {
            document.body.removeChild(textArea);
            onError(err);
        }
    }

    // 生成随机分享ID (4位数字)
    static generateShareId() {
        return Math.floor(1000 + Math.random() * 9000).toString();
    }

    // 检测文本类型
    static detectTextType(content) {
        if (this.isJSON(content)) return 'json';
        if (/<\/?[a-z][\s\S]*>/i.test(content)) return 'html';
        if (/^<\?xml/.test(content)) return 'xml';
        if (/# |\*|\_|\[.*\]\(.*\)/.test(content)) return 'markdown';
        return 'text';
    }

    // 检测是否JSON
    static isJSON(str) {
        try {
            JSON.parse(str);
            return true;
        } catch (e) {
            return false;
        }
    }

    // 节流函数
    static throttle(func, limit) {
        let inThrottle;
        return function () {
            const context = this, args = arguments;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    }

    // 防抖函数
    static debounce(func, delay) {
        let debounceTimer;
        return function () {
            const context = this, args = arguments;
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => func.apply(context, args), delay);
        };
    }

    // 创建动画数字
    static animateNumber(element, start, end, duration) {
        let startTimestamp = null;
        const step = (timestamp) => {
            if (!startTimestamp) startTimestamp = timestamp;
            const progress = Math.min((timestamp - startTimestamp) / duration, 1);
            const value = Math.floor(progress * (end - start) + start);
            element.textContent = value;
            if (progress < 1) {
                window.requestAnimationFrame(step);
            }
        };
        window.requestAnimationFrame(step);
    }

    // 显示通知
    static showNotification(message, type = 'info') {
        const toastContainer = document.getElementById('toastContainer') || this.createToastContainer();

        const toast = document.createElement('div');
        toast.className = `toast align-items-center text-white bg-${type} border-0`;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');

        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">
                    ${message}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto"
                        data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        `;

        toastContainer.appendChild(toast);

        const toastInstance = new bootstrap.Toast(toast, {
            delay: 3000
        });

        toastInstance.show();

        toast.addEventListener('hidden.bs.toast', () => {
            toast.remove();
            if (toastContainer.querySelectorAll('.toast').length === 0) {
                toastContainer.remove();
            }
        });
    }

    // 创建通知容器
    static createToastContainer() {
        const container = document.createElement('div');
        container.id = 'toastContainer';
        container.style.position = 'fixed';
        container.style.bottom = '20px';
        container.style.right = '20px';
        container.style.zIndex = '9999';
        document.body.appendChild(container);
        return container;
    }

    // 格式化倒计时
    static formatCountdown(seconds) {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = seconds % 60;

        return {
            hours: hours.toString().padStart(2, '0'),
            minutes: minutes.toString().padStart(2, '0'),
            seconds: secs.toString().padStart(2, '0')
        };
    }

    // 检查文件类型
    static getFileType(fileName) {
        const extension = fileName.split('.').pop().toLowerCase();
        const types = {
            'image': ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'],
            'video': ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'],
            'audio': ['mp3', 'wav', 'ogg', 'm4a'],
            'document': ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt'],
            'code': ['js', 'java', 'py', 'html', 'css', 'json', 'xml', 'yaml', 'md']
        };

        for (const [type, extensions] of Object.entries(types)) {
            if (extensions.includes(extension)) {
                return type;
            }
        }

        return 'other';
    }

    // 显示加载状态
    static showLoading(element, message = '加载中...') {
        element.innerHTML = `
            <div class="d-flex justify-content-center align-items-center py-3">
                <div class="spinner-border text-primary me-2" role="status">
                    <span class="visually-hidden">加载中...</span>
                </div>
                <span>${message}</span>
            </div>
        `;
    }

    // 显示错误
    static showError(element, message) {
        element.innerHTML = `
            <div class="alert alert-danger d-flex align-items-center" role="alert">
                <i class="fas fa-exclamation-triangle me-2"></i>
                <div>${message}</div>
            </div>
        `;
    }

    // HTML 转义
    static escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// 全局工具函数
function copyToClipboard(text, buttonElement) {
    Utils.copyToClipboard(text, buttonElement);
}

// 暴露全局变量
window.Utils = Utils;

// 初始化工具函数
document.addEventListener('DOMContentLoaded', function () {
    window.onerror = function (message, source, lineno, colno, error) {
        console.error('全局错误:', {message, source, lineno, colno, error});
        // 避免报错弹窗过于频繁，可选择性开启
        // Utils.showNotification('系统发生错误，请刷新页面重试', 'danger');
        return true;
    };

    window.onunhandledrejection = function (event) {
        console.error('未捕获的Promise错误:', event.reason);
        // Utils.showNotification('操作失败，请重试', 'warning');
        event.preventDefault();
    };
});

// API 类
class API {
    static async request(url, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            credentials: 'same-origin'
        };

        const config = {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...(options.headers || {})
            }
        };

        try {
            const response = await fetch(url, config);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `请求失败: ${response.status}`);
            }
            if (config.headers['Content-Type'] === 'application/octet-stream') {
                return response;
            }
            return await response.json();
        } catch (error) {
            console.error('API请求失败:', error);
            throw error;
        }
    }

    static get(url, params = {}) {
        const queryString = new URLSearchParams(params).toString();
        const fullUrl = queryString ? `${url}?${queryString}` : url;
        return this.request(fullUrl, {method: 'GET'});
    }

    static post(url, data) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    static upload(url, formData, onProgress = null) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.upload.onprogress = function (event) {
                if (event.lengthComputable && onProgress) {
                    const percent = (event.loaded / event.total) * 100;
                    const speed = event.loaded / ((Date.now() - startTime) / 1000) / 1024;
                    onProgress(percent, speed);
                }
            };
            xhr.onload = function () {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        resolve(JSON.parse(xhr.responseText));
                    } catch (e) {
                        resolve(xhr.responseText);
                    }
                } else {
                    reject(new Error(`上传失败: ${xhr.status}`));
                }
            };
            xhr.onerror = function () {
                reject(new Error('网络错误'));
            };
            xhr.onabort = function () {
                reject(new Error('上传已取消'));
            };
            const startTime = Date.now();
            xhr.open('POST', url, true);
            xhr.send(formData);
        });
    }

    static delete(url, data = {}, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': this.getCsrfToken(),
            ...(options.headers || {})
        };
        return this.request(url, {
            method: 'DELETE',
            body: Object.keys(data).length > 0 ? JSON.stringify(data) : undefined,
            headers: headers
        });
    }

    static getCsrfToken() {
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        return tokenMeta ? tokenMeta.getAttribute('content') : '';
    }
}

window.API = API;