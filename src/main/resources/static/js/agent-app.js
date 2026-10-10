/**
 * AI Mini-Agent Workshop - Client Side Reactive Engine
 * High-Contrast Light Theme, Resizable Inspector & Fullscreen Data Overlay
 */

document.addEventListener('DOMContentLoaded', () => {
    initSseStream();
    scrollToBottom('chatScrollArea');
    scrollToBottom('timelineScrollArea');
    renderAllMath();
    initPayloadInspector();
});

window.addEventListener('load', () => {
    renderAllMath();
    highlightAllPayloadBoxes();
});

let eventSource = null;
let activeModalPayload = '';
let activeModalIsJson = false;
let activeModalFilename = 'payload';

function initSseStream() {
    const sessionIdInput = document.getElementById('currentSessionId');
    if (!sessionIdInput) return;
    const sessionId = sessionIdInput.value;

    if (eventSource) {
        eventSource.close();
    }

    eventSource = new EventSource(`/api/stream/${sessionId}`);

    eventSource.addEventListener('connected', (e) => {
        console.log('[SSE] Connected to agent stream for session:', sessionId);
        updateSseStatus(true);
    });

    eventSource.addEventListener('agent-step', (e) => {
        try {
            const event = JSON.parse(e.data);
            handleLiveStepEvent(event);
        } catch (err) {
            console.error('[SSE] Error parsing event payload:', err);
        }
    });

    eventSource.onerror = (err) => {
        console.debug('[SSE] Stream closed or reconnecting...');
        updateSseStatus(false);
    };
}

function updateSseStatus(connected) {
    const badge = document.getElementById('sseStatusBadge');
    if (badge) {
        if (connected) {
            badge.className = 'badge bg-success-subtle text-success border border-success-subtle';
            badge.innerHTML = '<i class="fa-solid fa-circle-dot me-1"></i> Live Stream Active';
        } else {
            badge.className = 'badge bg-warning-subtle text-warning border border-warning-subtle';
            badge.innerHTML = '<i class="fa-solid fa-circle-pause me-1"></i> Polling / Reconnecting';
        }
    }
}

function handleLiveStepEvent(event) {
    const timelineContainer = document.getElementById('liveTimelineContainer');
    const emptyNotice = document.getElementById('emptyTimelineNotice');
    if (emptyNotice) {
        emptyNotice.remove();
    }

    if (timelineContainer) {
        // Prevent duplicate step cards if already rendered (e.g. SSE duplicate or reconnect)
        const stepCardId = `step-card-${event.stepNumber}`;
        if (document.getElementById(stepCardId)) {
            console.debug(`[SSE] Step #${event.stepNumber} already rendered, skipping duplicate.`);
            return;
        }

        const stepHtml = createStepCardHtml(event);
        timelineContainer.insertAdjacentHTML('beforeend', stepHtml);
        highlightAllPayloadBoxes(timelineContainer);
        scrollToBottom('timelineScrollArea');
    }

    // Refresh token counters if stats provided
    if (event.tokenStats && event.tokenStats.totalTokens > 0) {
        refreshSessionStats();
    }
}

function createPayloadBoxHtml(contentSafe, stepTitle, stepNum, payloadTitle, typeBadge, subTitle) {
    return `
    <div class="payload-wrapper">
        <div class="payload-toolbar">
            <div class="d-flex align-items-center gap-2">
                <span class="payload-type-badge font-mono">${typeBadge}</span>
                <span class="text-secondary small">${subTitle}</span>
            </div>
            <div class="d-flex align-items-center gap-1">
                <button type="button" class="payload-btn payload-copy-btn" title="Copy raw payload to clipboard">
                    <i class="fa-regular fa-copy"></i> <span>Copy</span>
                </button>
                <button type="button" class="payload-btn payload-expand-btn" title="Toggle box height (expand/collapse)">
                    <i class="fa-solid fa-up-down"></i> <span>Expand</span>
                </button>
                <button type="button" class="payload-btn btn-fullscreen payload-fullscreen-btn" title="Open Fullscreen Overlay">
                    <i class="fa-solid fa-expand"></i> <span>Fullscreen</span>
                </button>
            </div>
        </div>
        <div class="payload-box-container">
            <pre class="payload-box font-mono" tabindex="0"
                 data-step-title="${stepTitle}"
                 data-step-number="${stepNum}"
                 data-payload-title="${payloadTitle}"
                 title="Click to open Fullscreen Overlay"><code>${contentSafe}</code></pre>
            <div class="payload-hover-hint">
                <i class="fa-solid fa-maximize me-1"></i> Click for Fullscreen
            </div>
        </div>
    </div>`;
}

function createStepCardHtml(step) {
    const borderClass = getBorderClass(step.type);
    const iconClass = getIconClass(step.type);
    const rawSafe = escapeHtml(step.rawContent || '');
    const reqSafe = escapeHtml(step.requestPayload || '');
    const respSafe = escapeHtml(step.responsePayload || '');
    const summarySafe = escapeHtml(step.summary || '');
    const titleSafe = escapeHtml(step.title || step.type);
    const stepNum = step.stepNumber || '';

    let statsBadge = '';
    if (step.tokenStats && step.tokenStats.totalTokens > 0) {
        const cachedNote = step.tokenStats.cachedTokens > 0 ? ` <span class="text-info opacity-75 small">(${step.tokenStats.cachedTokens} cached)</span>` : '';
        statsBadge = `<span class="badge badge-pill-glow ms-auto"><i class="fa-solid fa-bolt me-1"></i>${step.tokenStats.totalTokens} toks${cachedNote}</span>`;
    }

    let payloadBoxesHtml = '';
    if (reqSafe || respSafe) {
        const isMcp = step.type === 'MCP_TOOL_CALL';
        const reqTitle = isMcp ? 'View MCP Request (JSON-RPC 2.0)' : 'View Ask / Prompt Payload';
        const respTitle = isMcp ? 'View MCP Response (JSON-RPC 2.0)' : 'View Model Response / Decision';
        const reqLabel = isMcp ? 'MCP JSON-RPC Request' : 'Model Ask / Prompt';
        const respLabel = isMcp ? 'MCP JSON-RPC Response' : 'Model Response';
        const reqColor = isMcp ? 'text-purple' : 'text-primary';
        const reqIcon = isMcp ? 'fa-network-wired' : 'fa-arrow-up-right-from-square';

        payloadBoxesHtml = `
        <div class="mt-2">
            ${reqSafe ? `
            <details class="mb-2">
                <summary class="small ${reqColor} fw-semibold" style="cursor: pointer;">
                    <i class="fa-solid ${reqIcon} me-1"></i> ${reqTitle}
                </summary>
                ${createPayloadBoxHtml(reqSafe, titleSafe, stepNum, reqTitle, 'JSON-RPC', reqLabel)}
            </details>` : ''}
            ${respSafe ? `
            <details>
                <summary class="small text-success fw-semibold" style="cursor: pointer;">
                    <i class="fa-solid fa-reply me-1"></i> ${respTitle}
                </summary>
                ${createPayloadBoxHtml(respSafe, titleSafe, stepNum, respTitle, 'JSON-RPC', respLabel)}
            </details>` : ''}
        </div>`;
    } else if (rawSafe) {
        payloadBoxesHtml = `
        <details class="mt-1">
            <summary class="small text-primary fw-medium" style="cursor: pointer;">
                <i class="fa-solid fa-code me-1"></i> View Raw Payload
            </summary>
            ${createPayloadBoxHtml(rawSafe, titleSafe, stepNum, 'Raw Payload', 'PAYLOAD', 'Raw Content')}
        </details>`;
    }

    return `
    <div id="step-card-${stepNum}" data-step-number="${stepNum}" class="step-card ${borderClass} p-3 mb-2 shadow-sm animate__animated animate__fadeIn">
        <div class="d-flex align-items-center mb-1">
            <span class="badge bg-light border border-secondary-subtle text-dark font-mono me-2">#${stepNum}</span>
            <i class="fa-solid ${iconClass} me-2 text-primary"></i>
            <strong class="small text-dark">${titleSafe}</strong>
            ${statsBadge}
        </div>
        ${summarySafe ? `<div class="small text-secondary mb-2">${summarySafe}</div>` : ''}
        ${payloadBoxesHtml}
    </div>`;
}

function getBorderClass(type) {
    switch (type) {
        case 'USER_INPUT': return 'border-primary';
        case 'CONTEXT_ASSEMBLY': return 'border-secondary';
        case 'MODEL_REQUEST': return 'border-info';
        case 'MODEL_THINKING':
        case 'TOOL_CALL_REQUEST': return 'border-warning';
        case 'TOOL_EXECUTION': return 'border-danger';
        case 'TOOL_OBSERVATION': return 'border-info';
        case 'MCP_HANDSHAKE':
        case 'MCP_DISCOVERY':
        case 'MCP_TOOL_CALL': return 'border-purple';
        case 'MCP_TOOL_RESULT': return 'border-info';
        case 'FINAL_SYNTHESIS': return 'border-success';
        case 'AGENT_ERROR': return 'border-danger';
        default: return 'border-secondary';
    }
}

function getIconClass(type) {
    switch (type) {
        case 'USER_INPUT': return 'fa-user';
        case 'CONTEXT_ASSEMBLY': return 'fa-layer-group';
        case 'MODEL_REQUEST': return 'fa-paper-plane';
        case 'MODEL_THINKING': return 'fa-brain';
        case 'TOOL_CALL_REQUEST': return 'fa-wrench';
        case 'TOOL_EXECUTION': return 'fa-gear';
        case 'TOOL_OBSERVATION': return 'fa-clipboard-check';
        case 'MCP_HANDSHAKE': return 'fa-handshake';
        case 'MCP_DISCOVERY': return 'fa-cubes';
        case 'MCP_TOOL_CALL': return 'fa-network-wired';
        case 'MCP_TOOL_RESULT': return 'fa-arrow-down-short-wide';
        case 'FINAL_SYNTHESIS': return 'fa-circle-check';
        case 'AGENT_ERROR': return 'fa-triangle-exclamation';
        default: return 'fa-circle-info';
    }
}

function refreshSessionStats() {
    const sessionIdInput = document.getElementById('currentSessionId');
    if (!sessionIdInput) return;
    const sessionId = sessionIdInput.value;

    fetch(`/session/${sessionId}/token-meter`)
        .then(res => res.text())
        .then(html => {
            const meter = document.getElementById('tokenMeterWrapper');
            if (meter) meter.innerHTML = html;
        })
        .catch(err => console.debug('Could not refresh token meter:', err));
}

function usePresetPrompt(text) {
    const input = document.getElementById('promptInput');
    if (input) {
        input.value = text;
        input.focus();
    }
}

function scrollToBottom(elementId) {
    const el = document.getElementById(elementId);
    if (el) {
        el.scrollTop = el.scrollHeight;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * High-Contrast Light Theme JSON Syntax Highlighter
 */
function highlightJson(rawText) {
    if (!rawText) return '';
    const trimmed = rawText.trim();
    let isJson = false;
    let formatted = rawText;

    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        try {
            const parsed = JSON.parse(trimmed);
            formatted = JSON.stringify(parsed, null, 2);
            isJson = true;
        } catch (e) {
            // Might already be formatted JSON
            isJson = true;
        }
    }

    if (!isJson) {
        return escapeHtml(rawText);
    }

    return formatted.replace(
        /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?|[{}[\],:])/g,
        function (match) {
            if (match.startsWith('"')) {
                if (match.endsWith(':')) {
                    const keyOnly = match.slice(0, -1);
                    return '<span class="json-key">' + escapeHtml(keyOnly) + '</span><span class="json-punct">:</span>';
                } else {
                    return '<span class="json-string">' + escapeHtml(match) + '</span>';
                }
            } else if (match === 'true' || match === 'false') {
                return '<span class="json-boolean">' + match + '</span>';
            } else if (match === 'null') {
                return '<span class="json-null">' + match + '</span>';
            } else if (/^[{}[\],:]$/.test(match)) {
                return '<span class="json-punct">' + match + '</span>';
            } else {
                return '<span class="json-number">' + match + '</span>';
            }
        }
    );
}

/**
 * Applies syntax highlighting and setup to all payload boxes
 */
function highlightAllPayloadBoxes(root) {
    const context = root || document;
    const boxes = context.querySelectorAll('.payload-box');

    boxes.forEach(box => {
        if (box.dataset.highlighted === 'true') return;

        const codeEl = box.querySelector('code');
        const raw = box.dataset.rawPayload || (codeEl ? codeEl.textContent : box.textContent);
        box.dataset.rawPayload = raw;

        if (codeEl) {
            codeEl.innerHTML = highlightJson(raw);
        }

        const trimmed = (raw || '').trim();
        const isJson = (trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'));
        const wrapper = box.closest('.payload-wrapper');
        if (wrapper) {
            const typeBadge = wrapper.querySelector('.payload-type-badge');
            if (typeBadge && !typeBadge.dataset.custom) {
                typeBadge.textContent = isJson ? 'JSON' : 'PAYLOAD';
            }
        }

        box.dataset.highlighted = 'true';
    });
}

/**
 * Initialize event listeners for payload toolbar actions & fullscreen overlay
 */
function initPayloadInspector() {
    highlightAllPayloadBoxes();

    // Re-highlight when any details drawer is opened
    document.addEventListener('toggle', (e) => {
        if (e.target && e.target.tagName === 'DETAILS' && e.target.open) {
            highlightAllPayloadBoxes(e.target);
        }
    }, true);

    // Track mouse coordinates to distinguish drag-resizing from clicking
    let mouseDownX = 0;
    let mouseDownY = 0;

    document.addEventListener('mousedown', (e) => {
        mouseDownX = e.clientX;
        mouseDownY = e.clientY;
    });

    // Delegated click handler
    document.addEventListener('click', (e) => {
        // 1. Copy button
        const copyBtn = e.target.closest('.payload-copy-btn');
        if (copyBtn) {
            e.preventDefault();
            e.stopPropagation();
            const wrapper = copyBtn.closest('.payload-wrapper');
            if (wrapper) {
                const box = wrapper.querySelector('.payload-box');
                const raw = box ? (box.dataset.rawPayload || box.textContent) : '';
                copyTextWithFeedback(copyBtn, raw);
            }
            return;
        }

        // 2. Expand/Collapse button
        const expandBtn = e.target.closest('.payload-expand-btn');
        if (expandBtn) {
            e.preventDefault();
            e.stopPropagation();
            const wrapper = expandBtn.closest('.payload-wrapper');
            if (wrapper) {
                const box = wrapper.querySelector('.payload-box');
                if (box) {
                    const isExpanded = box.classList.toggle('expanded');
                    const span = expandBtn.querySelector('span');
                    const icon = expandBtn.querySelector('i');
                    if (isExpanded) {
                        if (span) span.textContent = 'Collapse';
                        if (icon) icon.className = 'fa-solid fa-compress';
                        expandBtn.title = 'Collapse box height';
                    } else {
                        if (span) span.textContent = 'Expand';
                        if (icon) icon.className = 'fa-solid fa-up-down';
                        expandBtn.title = 'Expand box height';
                    }
                }
            }
            return;
        }

        // 3. Fullscreen button in toolbar
        const fsBtn = e.target.closest('.payload-fullscreen-btn');
        if (fsBtn) {
            e.preventDefault();
            e.stopPropagation();
            const wrapper = fsBtn.closest('.payload-wrapper');
            if (wrapper) {
                const box = wrapper.querySelector('.payload-box');
                if (box) openPayloadModal(box);
            }
            return;
        }

        // 4. Click directly on payload box to open fullscreen overlay
        const box = e.target.closest('.payload-box');
        if (box) {
            // If dragging (resize) or selecting text, do not trigger modal
            const dragDist = Math.hypot(e.clientX - mouseDownX, e.clientY - mouseDownY);
            const selection = window.getSelection ? window.getSelection().toString() : '';
            if (dragDist > 6 || (selection && selection.length > 0)) {
                return;
            }
            e.preventDefault();
            openPayloadModal(box);
            return;
        }
    });

    // Keyboard trigger on focused payload box
    document.addEventListener('keydown', (e) => {
        if (e.target && e.target.classList && e.target.classList.contains('payload-box')) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                openPayloadModal(e.target);
            }
        }
    });

    // Modal controls initialization
    initModalControls();
}

/**
 * Opens and populates the Fullscreen Overlay Modal
 */
function openPayloadModal(box) {
    const raw = box.dataset.rawPayload || box.textContent;
    const stepTitle = box.dataset.stepTitle || 'Step Details';
    const stepNum = box.dataset.stepNumber || '';
    const payloadTitle = box.dataset.payloadTitle || 'Payload';

    const trimmed = (raw || '').trim();
    const isJson = (trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'));

    activeModalPayload = raw;
    activeModalIsJson = isJson;
    activeModalFilename = (stepNum ? 'step_' + stepNum + '_' : '') + payloadTitle.toLowerCase().replace(/[^a-z0-9]+/g, '_');

    // Title & Badges
    const labelEl = document.getElementById('payloadViewerModalLabel');
    if (labelEl) {
        labelEl.textContent = (stepNum ? '#' + stepNum + ' ' : '') + stepTitle + ' — ' + payloadTitle;
    }

    const badgeEl = document.getElementById('payloadModalBadge');
    if (badgeEl) {
        badgeEl.textContent = isJson ? 'JSON' : 'RAW';
    }

    const lines = (raw || '').split('\n').length;
    const sizeKb = (new Blob([raw || '']).size / 1024).toFixed(1);
    const statsEl = document.getElementById('payloadModalStats');
    if (statsEl) {
        statsEl.textContent = `${lines} lines (${sizeKb} KB)`;
    }

    // Reset Search & Render Code
    const searchInput = document.getElementById('payloadModalSearch');
    if (searchInput) searchInput.value = '';

    const codeInner = document.getElementById('payloadModalCodeInner');
    if (codeInner) {
        codeInner.innerHTML = highlightJson(raw);
    }

    // Show Modal via Bootstrap 5
    const modalEl = document.getElementById('payloadViewerModal');
    if (modalEl && window.bootstrap && bootstrap.Modal) {
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }
}

/**
 * Initializes modal action listeners (search, wrap, copy, download)
 */
function initModalControls() {
    // 1. In-modal Search / Filter
    const searchInput = document.getElementById('payloadModalSearch');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const query = e.target.value;
            filterModalContent(query);
        });
    }

    // 2. Wrap Lines Toggle
    const wrapBtn = document.getElementById('payloadModalWrapBtn');
    if (wrapBtn) {
        wrapBtn.addEventListener('click', () => {
            const codePre = document.getElementById('payloadModalCode');
            const wrapText = document.getElementById('payloadModalWrapText');
            if (codePre) {
                const isWrapped = codePre.classList.toggle('wrap-lines');
                if (wrapText) wrapText.textContent = isWrapped ? 'Unwrap' : 'Wrap';
                wrapBtn.classList.toggle('active', isWrapped);
            }
        });
    }

    // 3. Modal Copy Button
    const copyBtn = document.getElementById('payloadModalCopyBtn');
    if (copyBtn) {
        copyBtn.addEventListener('click', () => {
            copyTextWithFeedback(copyBtn, activeModalPayload, 'payloadModalCopyText');
        });
    }

    // 4. Modal Download Button
    const downloadBtn = document.getElementById('payloadModalDownloadBtn');
    if (downloadBtn) {
        downloadBtn.addEventListener('click', () => {
            if (!activeModalPayload) return;
            const ext = activeModalIsJson ? '.json' : '.txt';
            const mime = activeModalIsJson ? 'application/json' : 'text/plain';
            const blob = new Blob([activeModalPayload], { type: mime });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = (activeModalFilename || 'payload') + ext;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        });
    }
}

/**
 * Highlights search matches inside the modal code viewer without breaking syntax spans
 */
function filterModalContent(query) {
    const codeInner = document.getElementById('payloadModalCodeInner');
    if (!codeInner) return;

    // Reset to base highlighted HTML
    codeInner.innerHTML = highlightJson(activeModalPayload);

    const q = (query || '').trim();
    if (!q) return;

    const walker = document.createTreeWalker(codeInner, NodeFilter.SHOW_TEXT, null, false);
    const textNodes = [];
    let node;
    while ((node = walker.nextNode())) {
        textNodes.push(node);
    }

    const regex = new RegExp(escapeRegex(q), 'gi');
    let firstMatch = null;

    for (const textNode of textNodes) {
        const val = textNode.nodeValue;
        if (regex.test(val)) {
            const frag = document.createDocumentFragment();
            let lastIndex = 0;
            regex.lastIndex = 0;
            let match;
            while ((match = regex.exec(val)) !== null) {
                if (match.index > lastIndex) {
                    frag.appendChild(document.createTextNode(val.substring(lastIndex, match.index)));
                }
                const mark = document.createElement('mark');
                mark.className = 'search-highlight';
                mark.textContent = match[0];
                if (!firstMatch) firstMatch = mark;
                frag.appendChild(mark);
                lastIndex = regex.lastIndex;
            }
            if (lastIndex < val.length) {
                frag.appendChild(document.createTextNode(val.substring(lastIndex)));
            }
            if (textNode.parentNode) {
                textNode.parentNode.replaceChild(frag, textNode);
            }
        }
    }

    if (firstMatch) {
        firstMatch.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

/**
 * Utility: Copy text with instant button visual feedback
 */
function copyTextWithFeedback(btn, textToCopy, spanId) {
    if (!textToCopy) return;

    const performFeedback = () => {
        const span = spanId ? document.getElementById(spanId) : btn.querySelector('span');
        const icon = btn.querySelector('i');
        const prevText = span ? span.textContent : '';
        const prevIcon = icon ? icon.className : '';

        if (span) span.textContent = 'Copied!';
        if (icon) icon.className = 'fa-solid fa-check';
        btn.classList.add('btn-copied');

        setTimeout(() => {
            if (span) span.textContent = prevText;
            if (icon) icon.className = prevIcon;
            btn.classList.remove('btn-copied');
        }, 2000);
    };

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(textToCopy)
            .then(performFeedback)
            .catch(() => fallbackCopy(textToCopy, performFeedback));
    } else {
        fallbackCopy(textToCopy, performFeedback);
    }
}

function fallbackCopy(text, callback) {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
        document.execCommand('copy');
        if (callback) callback();
    } catch (err) {
        console.error('Fallback copy failed', err);
    }
    document.body.removeChild(textArea);
}

// HTMX Lifecycle hooks
document.body.addEventListener('htmx:afterSwap', function (evt) {
    scrollToBottom('chatScrollArea');
    scrollToBottom('timelineScrollArea');
    const input = document.getElementById('promptInput');
    if (input) {
        input.value = '';
        input.focus();
    }
    renderAllMath();
    highlightAllPayloadBoxes();
});

/**
 * Render any LaTeX math notation in chat messages
 */
function renderAllMath() {
    const chatContainer = document.getElementById('chatScrollArea');
    if (chatContainer && typeof renderMathInElement === 'function') {
        renderMathInElement(chatContainer, {
            delimiters: [
                { left: '$$', right: '$$', display: false },
                { left: '$', right: '$', display: false },
                { left: '\\(', right: '\\)', display: false },
                { left: '\\[', right: '\\]', display: true }
            ],
            throwOnError: false,
            ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
        });
    }
}
