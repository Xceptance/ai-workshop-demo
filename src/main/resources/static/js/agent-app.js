/**
 * AI Mini-Agent Workshop - Client Side Reactive Engine
 */

document.addEventListener('DOMContentLoaded', () => {
    initSseStream();
    scrollToBottom('chatScrollArea');
    scrollToBottom('timelineScrollArea');
});

let eventSource = null;

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
        const stepHtml = createStepCardHtml(event);
        timelineContainer.insertAdjacentHTML('beforeend', stepHtml);
        scrollToBottom('timelineScrollArea');
    }

    // Refresh token counters if stats provided
    if (event.tokenStats && event.tokenStats.totalTokens > 0) {
        refreshSessionStats();
    }
}

function createStepCardHtml(step) {
    const borderClass = getBorderClass(step.type);
    const iconClass = getIconClass(step.type);
    const rawSafe = escapeHtml(step.rawContent || '');
    const reqSafe = escapeHtml(step.requestPayload || '');
    const respSafe = escapeHtml(step.responsePayload || '');
    const summarySafe = escapeHtml(step.summary || '');
    const titleSafe = escapeHtml(step.title || step.type);

    let statsBadge = '';
    if (step.tokenStats && step.tokenStats.totalTokens > 0) {
        statsBadge = `<span class="badge badge-pill-glow ms-auto"><i class="fa-solid fa-bolt me-1"></i>${step.tokenStats.totalTokens} toks</span>`;
    }

    let payloadBoxesHtml = '';
    if (reqSafe || respSafe) {
        payloadBoxesHtml = `
        <div class="mt-2">
            ${reqSafe ? `
            <details class="mb-2">
                <summary class="small text-primary fw-semibold" style="cursor: pointer;">
                    <i class="fa-solid fa-arrow-up-right-from-square me-1"></i> View Ask / Prompt Payload
                </summary>
                <pre class="payload-box mt-1 mb-0 font-mono"><code>${reqSafe}</code></pre>
            </details>` : ''}
            ${respSafe ? `
            <details>
                <summary class="small text-success fw-semibold" style="cursor: pointer;">
                    <i class="fa-solid fa-reply me-1"></i> View Model Response / Decision
                </summary>
                <pre class="payload-box mt-1 mb-0 font-mono"><code>${respSafe}</code></pre>
            </details>` : ''}
        </div>`;
    } else if (rawSafe) {
        payloadBoxesHtml = `
        <details class="mt-1">
            <summary class="small text-primary fw-medium" style="cursor: pointer;">
                <i class="fa-solid fa-code me-1"></i> View Raw Payload
            </summary>
            <pre class="payload-box mt-2 mb-0 font-mono"><code>${rawSafe}</code></pre>
        </details>`;
    }

    return `
    <div class="step-card ${borderClass} p-3 mb-2 shadow-sm animate__animated animate__fadeIn">
        <div class="d-flex align-items-center mb-1">
            <span class="badge bg-light border border-secondary-subtle text-dark font-mono me-2">#${step.stepNumber}</span>
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

// HTMX Lifecycle hooks
document.body.addEventListener('htmx:afterSwap', function(evt) {
    scrollToBottom('chatScrollArea');
    scrollToBottom('timelineScrollArea');
    const input = document.getElementById('promptInput');
    if (input) {
        input.value = '';
        input.focus();
    }
});
