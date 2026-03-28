document.addEventListener('DOMContentLoaded', () => {
    // Elements
    const chatArea = document.getElementById('chat-area');
    const chatInput = document.getElementById('chat-input');
    const sendBtn = document.getElementById('send-btn');
    const proposalArea = document.getElementById('proposal-area');
    const btnApprove = document.getElementById('btn-approve');
    const btnDeny = document.getElementById('btn-deny');
    const sidebar = document.getElementById('sidebar');
    const toggleSidebarBtn = document.getElementById('toggle-sidebar');
    const sessionList = document.getElementById('session-list');
    const statusIndicator = document.querySelector('.status-indicator');
    const statusText = statusIndicator.querySelector('.text');
    const suggestionDropdown = document.getElementById('suggestion-dropdown');

    // Mock Data
    const sessions = [
        { id: 1, title: 'Crafting 64k Storage', time: '2m ago', active: true, visibility: 'public' },
        { id: 2, title: 'Auto-Processing Setup', time: '1h ago', active: false, visibility: 'team' },
        { id: 3, title: 'System Diagnostics', time: '1d ago', active: false, visibility: 'private' },
        { id: 4, title: 'Recipe Search: Gold', time: '2d ago', active: false, visibility: 'private' }
    ];

    const initialMessages = [
        { type: 'user', content: 'Can you craft 64 <item id="ae2:fluix_crystal" display_name="Fluix Crystal"> for me?', time: '14:20' },
        { type: 'ai', content: 'I can help with that. I checked the network and we have enough resources.', time: '14:20' },
        { type: 'ai', content: 'Generating a crafting plan...', time: '14:20' }
    ];

    const itemSuggestions = [
        { id: 'ae2:fluix_crystal', name: 'Fluix Crystal' },
        { id: 'ae2:charged_certus_quartz_crystal', name: 'Charged Certus Quartz' },
        { id: 'minecraft:redstone', name: 'Redstone Dust' },
        { id: 'minecraft:quartz', name: 'Nether Quartz' }
    ];

    // Initialize
    renderSessions();
    renderMessages();

    // Event Listeners
    sendBtn.addEventListener('click', handleSendMessage);
    chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleSendMessage();
    });

    // Suggestion Logic
    chatInput.addEventListener('input', (e) => {
        const val = e.target.value;
        const lastWord = val.split(' ').pop();
        if (lastWord.startsWith('@')) {
            showSuggestions(lastWord.substring(1));
        } else {
            hideSuggestions();
        }
    });

    btnApprove.addEventListener('click', () => handleProposalAction('approved'));
    btnDeny.addEventListener('click', () => handleProposalAction('denied'));

    toggleSidebarBtn.addEventListener('click', () => {
        sidebar.classList.toggle('collapsed');
    });

    // Functions
    function setStatus(status) {
        // statuses: online, indexing, thinking, executing, failed
        statusIndicator.className = `status-indicator ${status}`;
        statusText.textContent = status.toUpperCase();
    }

    function renderSessions() {
        sessionList.innerHTML = '';
        sessions.forEach(session => {
            const el = document.createElement('div');
            el.className = `session-item ${session.active ? 'active' : ''}`;
            el.innerHTML = `
                <div style="flex:1">
                    <div style="display:flex; align-items:center;">
                        <span class="session-visibility ${session.visibility}">${session.visibility}</span>
                        <span class="session-title">${session.title}</span>
                    </div>
                </div>
                <span class="session-time">${session.time}</span>
            `;
            el.addEventListener('click', () => {
                sessions.forEach(s => s.active = false);
                session.active = true;
                renderSessions();
            });
            sessionList.appendChild(el);
        });
    }

    function renderMessages() {
        // Clear existing messages except system message
        const systemMsg = chatArea.querySelector('.system-message');
        chatArea.innerHTML = '';
        if (systemMsg) chatArea.appendChild(systemMsg);

        initialMessages.forEach(msg => addMessageToDom(msg));

        // Show proposal after a delay to simulate "thinking"
        setStatus('thinking');
        setTimeout(() => {
            proposalArea.classList.remove('hidden');
            setStatus('online'); // Waiting for approval
            scrollToBottom();
        }, 1500);
    }

    function parseItemTokens(content) {
        // Regex to replace <item ...> with token HTML
        // Example: <item id="ae2:fluix_crystal" display_name="Fluix Crystal">
        // Basic replacement for demo purposes
        return content.replace(/<item id="([^"]+)" display_name="([^"]+)োজন/g, (match, id, name) => {
            return `<span class="item-token" title="${id}"><span class="icon"></span>${name}</span>`;
        }).replace(/<item id="([^"]+)" display_name="([^"]+)">/g, (match, id, name) => {
            return `<span class="item-token" title="${id}"><span class="icon"></span>${name}</span>`;
        });
    }

    function addMessageToDom(msg) {
        const el = document.createElement('div');
        el.className = `message ${msg.type}`;

        // Parse content for item tokens
        const parsedContent = parseItemTokens(msg.content);

        el.innerHTML = `
            <div class="message-content">${parsedContent}</div>
            <span class="message-meta">${msg.time}</span>
        `;
        chatArea.appendChild(el);
        scrollToBottom();
    }

    function handleSendMessage() {
        const text = chatInput.value.trim();
        if (!text) return;

        // Add user message
        const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        addMessageToDom({ type: 'user', content: text, time: now });
        chatInput.value = '';
        hideSuggestions();

        // Simulate AI response sequence
        setStatus('thinking');

        setTimeout(() => {
            setStatus('executing');
            // Simulate execution time
            setTimeout(() => {
                addMessageToDom({ type: 'ai', content: 'I received your request. Processing...', time: now });
                setStatus('online');
            }, 800);
        }, 1000);
    }

    function handleProposalAction(action) {
        proposalArea.classList.add('hidden');
        const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        if (action === 'approved') {
            setStatus('executing');
            setTimeout(() => {
                addMessageToDom({ type: 'ai', content: 'Proposal approved. Starting crafting job #8821.', time: now });
                setStatus('online');
            }, 1000);
        } else {
            addMessageToDom({ type: 'ai', content: 'Proposal denied. Action cancelled.', time: now });
        }
    }

    function showSuggestions(query) {
        suggestionDropdown.innerHTML = '';
        const matches = itemSuggestions.filter(item =>
            item.name.toLowerCase().includes(query.toLowerCase()) ||
            item.id.toLowerCase().includes(query.toLowerCase())
        );

        if (matches.length > 0) {
            matches.forEach(item => {
                const el = document.createElement('div');
                el.className = 'suggestion-item';
                el.innerHTML = `<span class="icon"></span><span>${item.name}</span>`;
                el.addEventListener('click', () => {
                    // Insert item token string
                    const currentVal = chatInput.value;
                    const lastAtIndex = currentVal.lastIndexOf('@');
                    const newVal = currentVal.substring(0, lastAtIndex) +
                        `<item id="${item.id}" display_name="${item.name}"> ` +
                        currentVal.substring(lastAtIndex + query.length + 1);

                    chatInput.value = newVal;
                    chatInput.focus();
                    hideSuggestions();
                });
                suggestionDropdown.appendChild(el);
            });
            suggestionDropdown.classList.add('visible');
        } else {
            hideSuggestions();
        }
    }

    function hideSuggestions() {
        suggestionDropdown.classList.remove('visible');
    }

    function scrollToBottom() {
        chatArea.scrollTop = chatArea.scrollHeight;
    }
});
