// 情绪枚举映射表（与 EmotionalTendencies 对应）
const EMOTION_MAP = {
    'JOY': {name: '喜悦', pad: {pleasure: 2.77, arousal: 1.21, dominance: 1.42}, desc: '充满欢乐与活力的状态'},
    'OPTIMISM': {name: '乐观', pad: {pleasure: 2.48, arousal: 1.05, dominance: 1.75}, desc: '对未来持积极态度'},
    'RELAXATION': {name: '轻松', pad: {pleasure: 2.19, arousal: -0.66, dominance: 1.05}, desc: '平和舒适的放松状态'},
    'SURPRISE': {name: '惊奇', pad: {pleasure: 1.72, arousal: 1.71, dominance: 0.22}, desc: '对意外事件的反应'},
    'MILDNESS': {name: '温和', pad: {pleasure: 1.57, arousal: -0.79, dominance: 0.38}, desc: '温柔平静的情绪'},
    'DEPENDENCE': {name: '依赖', pad: {pleasure: 0.39, arousal: -0.81, dominance: 1.48}, desc: '需要他人支持的状态'},
    'BOREDOM': {name: '无聊', pad: {pleasure: -0.53, arousal: -1.25, dominance: -0.84}, desc: '缺乏兴趣与刺激'},
    'SADNESS': {name: '悲伤', pad: {pleasure: -0.89, arousal: 0.17, dominance: -0.70}, desc: '消极低落的情绪'},
    'FEAR': {name: '恐惧', pad: {pleasure: -0.93, arousal: 1.30, dominance: -0.64}, desc: '对威胁的警觉反应'},
    'ANXIETY': {name: '焦虑', pad: {pleasure: -0.95, arousal: 0.32, dominance: -0.63}, desc: '不安与担忧的状态'},
    'CONTEMPT': {name: '藐视', pad: {pleasure: -1.58, arousal: 0.32, dominance: 1.02}, desc: '轻视与不屑的态度'},
    'DISGUST': {name: '厌恶', pad: {pleasure: -1.80, arousal: 0.40, dominance: 0.67}, desc: '强烈的排斥感'},
    'RESENTMENT': {name: '愤懑', pad: {pleasure: -1.98, arousal: 1.10, dominance: 0.60}, desc: '压抑的愤怒与不满'},
    'HOSTILITY': {name: '敌意', pad: {pleasure: -2.08, arousal: 1.00, dominance: 1.12}, desc: '对抗性的负面情绪'}
};

// Tone 枚举映射
const TONE_MAP = {
    'FRIENDLY': '友好',
    'GENTLE': '温柔',
    'NEUTRAL': '中性',
    'IRONIC': '讽刺',
    'LOW_ENERGY': '低能量'
};

// Aggressiveness 枚举映射
const AGGRESSION_MAP = {
    'NONE': '无攻击性',
    'ABSTRACT_SARCASM': '抽象讽刺',
    'TEASING': '戏弄'
};

// EmojiLevel 枚举映射
const EMOJI_MAP = {
    'NONE': '无表情',
    'LOW': '少量表情',
    'MEDIUM': '适度表情',
    'HIGH': '丰富表情'
};

// 存储折叠状态，避免刷新时重置
const collapsedStates = {};

function toggleModule(groupIndex, moduleType) {
    const key = `group-${groupIndex}-${moduleType}`;
    collapsedStates[key] = !collapsedStates[key];

    const title = document.querySelector(`#title-${key}`);
    const content = document.querySelector(`#content-${key}`);

    if (collapsedStates[key]) {
        title.classList.add('collapsed');
        content.classList.add('collapsed');
    } else {
        title.classList.remove('collapsed');
        content.classList.remove('collapsed');
    }
}

// 处理机器人列表响应
document.body.addEventListener('htmx:afterSwap', function (evt) {
    if (evt.detail.target.id === 'bot-list') {
        const bots = JSON.parse(evt.detail.xhr.responseText);
        renderBotList(bots);
    }
});

function renderBotList(bots) {
    const botList = document.getElementById('bot-list');
    if (bots.length === 0) {
        botList.innerHTML = '<div class="no-bots">暂无在线机器人</div>';
        return;
    }

    botList.innerHTML = bots.map(botId => `
            <button class="bot-card"
                    hx-get="/status/${botId}"
                    hx-target="#status-detail"
                    hx-swap="innerHTML"
                    onclick="setActiveBot(this)">
                <div class="bot-avatar"><i data-lucide="bot"></i></div>
                <div class="bot-name">${botId}</div>
                <div class="bot-status-indicator"></div>
            </button>
        `).join('');

    htmx.process(botList);
    lucide.createIcons();
}

function setActiveBot(element) {
    document.querySelectorAll('.bot-card').forEach(card => card.classList.remove('active'));
    element.classList.add('active');
}

// 处理状态详情响应
document.body.addEventListener('htmx:afterSwap', function (evt) {
    if (evt.detail.target.id === 'status-detail') {
        try {
            const status = JSON.parse(evt.detail.xhr.responseText);
            renderStatusDetail(status);
        } catch (e) {
            // 可能是错误响应
        }
    }
});

function renderStatusDetail(status) {
    const container = document.getElementById('status-detail');

    container.innerHTML = `
            <article class="blog-article">
                <header class="article-header">
                    <div class="article-meta-bar">
                        <div class="meta-left">
                            <span class="meta-badge bot-id">
                                <i data-lucide="bot"></i>
                                <span>${status.name} (${status.id})</span>
                            </span>
                            <span class="meta-badge timestamp">
                                <i data-lucide="clock"></i>
                                <span>${new Date().toLocaleString('zh-CN', {
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    })}</span>
                            </span>
                        </div>
                        <div class="meta-right">
                            <span class="meta-badge live-indicator">
                                <span class="pulse-dot"></span>
                                <span>实时监控</span>
                            </span>
                        </div>
                    </div>
                </header>

                <nav class="article-nav">
                    <div class="nav-header">
                        <i data-lucide="menu"></i>
                        <span>目录导航</span>
                    </div>
                    <ul class="nav-list">
                        ${status.status.map((g, i) => `
                            <li class="nav-item">
                                <a href="#group-${i}" class="nav-link">
                                    <span class="nav-number">${i + 1}</span>
                                    <span class="nav-text">群组 ${g.groupId}</span>
                                    <span class="nav-arrow"><i data-lucide="chevron-right"></i></span>
                                </a>
                            </li>
                        `).join('')}
                    </ul>
                </nav>

                <div class="article-content">
                    <section class="markdown-section" style="margin-top: 1.5rem;">
                        <h2 class="section-title">
                            <span class="title-prefix">#</span>
                            插件统计
                        </h2>
                        <div class="stats-grid">
                            <div class="stat-item">
                                <div class="stat-label">
                                    <i data-lucide="puzzle"></i>
                                    <span>Extensions</span>
                                </div>
                                <div class="stat-value">${status.pluginStats.totalExtensions}</div>
                                <div class="stat-desc">个扩展总数</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-label">
                                    <i data-lucide="terminal"></i>
                                    <span>Cmd</span>
                                </div>
                                <div class="stat-value">${status.pluginStats.cmdExtensions}</div>
                                <div class="stat-desc">个命令扩展</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-label">
                                    <i data-lucide="route"></i>
                                    <span>Route</span>
                                </div>
                                <div class="stat-value">${status.pluginStats.routeExtensions}</div>
                                <div class="stat-desc">个路由扩展</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-label">
                                    <i data-lucide="eye"></i>
                                    <span>Passive</span>
                                </div>
                                <div class="stat-value">${status.pluginStats.passiveExtensions}</div>
                                <div class="stat-desc">个被动扩展</div>
                            </div>
                        </div>

                        ${status.pluginStats.plugins && status.pluginStats.plugins.length > 0 ? `
                        <h3 class="subsection-title">
                            <i data-lucide="boxes"></i> 已加载插件
                        </h3>
                        <div class="vocab-container">
                            <div class="vocab-grid">
                                ${status.pluginStats.plugins.map((plugin, idx) => `
                                    <span class="vocab-item">
                                        <span class="vocab-index">${idx + 1}</span>
                                        <span class="vocab-word">${plugin.id} (${plugin.extensionCount})</span>
                                    </span>
                                `).join('')}
                            </div>
                        </div>
                        ` : ''}

                    </section>

                    <hr class="section-divider" style="margin-bottom: 0;">

                    ${status.status.map((groupStatus, index) => renderGroupStatus(groupStatus, index, status.id)).join('')}
                </div>

                <footer class="article-footer">
                    <p><i data-lucide="info"></i> <em>本报告每 10 秒自动刷新，展示机器人在各群组的实时状态数据</em></p>
                </footer>
            </article>
        `;

    lucide.createIcons();

    // 设置自动刷新
    setTimeout(() => {
        const activeBot = document.querySelector('.bot-card.active');
        if (activeBot) {
            htmx.trigger(activeBot, 'click');
        }
    }, 10000);
}

function renderGroupStatus(groupStatus, index, botId) {
    const bp = groupStatus.behaviorProfile;
    const pad = groupStatus.pad;
    const flow = groupStatus.flowState;
    const volition = groupStatus.volitionState;

    const isFactsCollapsed = collapsedStates[`group-${index}-facts`];
    const isProfilesCollapsed = collapsedStates[`group-${index}-profiles`];
    const isVocabCollapsed = collapsedStates[`group-${index}-vocab`];
    const isMemesCollapsed = collapsedStates[`group-${index}-memes`];

    return `
            <section class="markdown-section" id="group-${index}">
                <h2 class="section-title">
                    <span class="title-prefix">#</span>
                    群组 ${groupStatus.groupId}
                    <a href="/view/${botId}/${groupStatus.groupId}" class="view-detail-link" target="_blank" title="在新标签页查看详情">
                        <i data-lucide="external-link"></i>
                    </a>
                </h2>

                ${bp ? `
                <h3 class="subsection-title">
                    <i data-lucide="smile"></i> 情绪状态
                </h3>
                ${renderBehaviorProfileMarkdown(bp, pad)}
                ` : '<div class="info-box"><i data-lucide="alert-triangle"></i> 暂无情绪数据</div>'}

                <h3 class="subsection-title">
                    <i data-lucide="waves"></i> 心流投入度
                </h3>
                ${renderFlowStateMarkdown(flow)}

                <h3 class="subsection-title">
                    <i data-lucide="brain"></i> 冲动值
                </h3>
                ${renderVolitionStateMarkdown(volition)}

                <h3 class="subsection-title">
                    <i data-lucide="database"></i> 记忆
                </h3>
                <div class="stats-grid">
                    <div class="stat-item">
                        <div class="stat-label">
                            <i data-lucide="file-text"></i>
                            <span>Facts</span>
                        </div>
                        <div class="stat-value">${groupStatus.factSize}</div>
                        <div class="stat-desc">条事实记忆</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">
                            <i data-lucide="user"></i>
                            <span>UserProfile</span>
                        </div>
                        <div class="stat-value">${groupStatus.userProfileSize}</div>
                        <div class="stat-desc">个用户档案</div>
                    </div>
                </div>

                ${groupStatus.facts && (groupStatus.facts.group.length > 0 || groupStatus.facts.user.length > 0) ? `
                <h3 class="subsection-title collapsible ${isFactsCollapsed ? 'collapsed' : ''}"
                    id="title-group-${index}-facts"
                    onclick="toggleModule(${index}, 'facts')">
                    <i data-lucide="lightbulb"></i> 事实记忆
                    <span class="toggle-icon"><i data-lucide="chevron-down"></i></span>
                </h3>
                <div class="collapsible-content ${isFactsCollapsed ? 'collapsed' : ''}" id="content-group-${index}-facts">
                    <div class="facts-container">
                        ${groupStatus.facts.group.length > 0 ? `
                        <div class="fact-category">
                            <div class="fact-category-header">
                                <i data-lucide="users"></i>
                                <span>群组事实</span>
                                <span class="fact-count">${groupStatus.facts.group.length}</span>
                            </div>
                            <div class="fact-list">
                                ${groupStatus.facts.group.map(fact => `
                                <div class="fact-item">
                                    <div class="fact-header">
                                        <span class="fact-keyword">${fact.keyword}</span>
                                        ${fact.subjects.length > 0 ? `<span class="fact-subjects">${fact.subjects.join(', ')}</span>` : ''}
                                    </div>
                                    <div class="fact-description">${fact.description}</div>
                                    ${fact.values ? `<div class="fact-values"><i data-lucide="tag"></i> ${fact.values}</div>` : ''}
                                </div>
                                `).join('')}
                            </div>
                        </div>
                        ` : ''}
                        ${groupStatus.facts.user.length > 0 ? `
                        <div class="fact-category">
                            <div class="fact-category-header">
                                <i data-lucide="user-circle"></i>
                                <span>用户事实</span>
                                <span class="fact-count">${groupStatus.facts.user.length}</span>
                            </div>
                            <div class="fact-list">
                                ${groupStatus.facts.user.map(fact => `
                                <div class="fact-item">
                                    <div class="fact-header">
                                        <span class="fact-keyword">${fact.keyword}</span>
                                        ${fact.subjects.length > 0 ? `<span class="fact-subjects">${fact.subjects.join(', ')}</span>` : ''}
                                    </div>
                                    <div class="fact-description">${fact.description}</div>
                                    ${fact.values ? `<div class="fact-values"><i data-lucide="tag"></i> ${fact.values}</div>` : ''}
                                </div>
                                `).join('')}
                            </div>
                        </div>
                        ` : ''}
                    </div>
                </div>
                ` : ''}

                ${groupStatus.userProfiles && groupStatus.userProfiles.filter(p => p.profile || p.preferences).length > 0 ? `
                <h3 class="subsection-title collapsible ${isProfilesCollapsed ? 'collapsed' : ''}"
                    id="title-group-${index}-profiles"
                    onclick="toggleModule(${index}, 'profiles')">
                    <i data-lucide="id-card"></i> 用户画像
                    <span class="vocab-count">${groupStatus.userProfiles.filter(p => p.profile || p.preferences).length}</span>
                    <span class="toggle-icon"><i data-lucide="chevron-down"></i></span>
                </h3>
                <div class="collapsible-content ${isProfilesCollapsed ? 'collapsed' : ''}" id="content-group-${index}-profiles">
                    <div class="user-profiles-container">
                        ${groupStatus.userProfiles.filter(p => p.profile || p.preferences).map((profile, idx) => `
                        <div class="user-profile-item">
                            <div class="user-profile-header">
                                <i data-lucide="user"></i>
                                <span>用户 ${profile.id}</span>
                            </div>
                            <div class="user-profile-content">
                                ${profile.profile ? `
                                <div class="profile-section">
                                    <div class="profile-section-label">
                                        <i data-lucide="contact"></i>
                                        <span>个人信息</span>
                                    </div>
                                    <div class="profile-section-text">${profile.profile}</div>
                                </div>
                                ` : ''}
                                ${profile.preferences ? `
                                <div class="profile-section">
                                    <div class="profile-section-label">
                                        <i data-lucide="heart"></i>
                                        <span>偏好设置</span>
                                    </div>
                                    <div class="profile-section-text">${profile.preferences}</div>
                                </div>
                                ` : ''}
                            </div>
                        </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <h3 class="subsection-title">
                    <i data-lucide="images"></i> 表情包 & 词汇
                </h3>
                <div class="stats-grid">
                    <div class="stat-item">
                        <div class="stat-label">
                            <i data-lucide="images"></i>
                            <span>Memes</span>
                        </div>
                        <div class="stat-value">${groupStatus.memeSize}</div>
                        <div class="stat-desc">个表情包（${groupStatus.analyzedMemeSize} 已分析）</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">
                            <i data-lucide="languages"></i>
                            <span>Vocabulary</span>
                        </div>
                        <div class="stat-value">${groupStatus.vocabularies ? groupStatus.vocabularies.length : 0}</div>
                        <div class="stat-desc">个学习词汇</div>
                    </div>
                </div>

                ${groupStatus.memes && groupStatus.memes.length > 0 ? `
                <h3 class="subsection-title collapsible ${isMemesCollapsed ? 'collapsed' : ''}"
                    id="title-group-${index}-memes"
                    onclick="toggleModule(${index}, 'memes')">
                    <i data-lucide="images"></i> 表情包库
                    <span class="vocab-count">${groupStatus.memes.length}</span>
                    <span class="toggle-icon"><i data-lucide="chevron-down"></i></span>
                </h3>
                <div class="collapsible-content ${isMemesCollapsed ? 'collapsed' : ''}" id="content-group-${index}-memes">
                    <div class="facts-container">
                        <div class="fact-list">
                            ${groupStatus.memes.map(meme => `
                            <div class="fact-item">
                                <div class="fact-header">
                                    <span class="fact-keyword">${meme.description || '未分析'}</span>
                                    <span class="fact-subjects">出现 ${meme.seenCount} 次 | 使用 ${meme.usageCount} 次</span>
                                </div>
                                ${meme.purpose ? `<div class="fact-description"><i data-lucide="target"></i> ${meme.purpose}</div>` : ''}
                                ${meme.tags && meme.tags.length > 0 ? `<div class="fact-values"><i data-lucide="tags"></i> ${meme.tags.join(', ')}</div>` : ''}
                            </div>
                            `).join('')}
                        </div>
                    </div>
                </div>
                ` : ''}

                ${groupStatus.vocabularies && groupStatus.vocabularies.length > 0 ? `
                <h3 class="subsection-title collapsible ${isVocabCollapsed ? 'collapsed' : ''}"
                    id="title-group-${index}-vocab"
                    onclick="toggleModule(${index}, 'vocab')">
                    <i data-lucide="languages"></i> 学习词汇库
                    <span class="vocab-count">${groupStatus.vocabularies.length}</span>
                    <span class="toggle-icon"><i data-lucide="chevron-down"></i></span>
                </h3>
                <div class="collapsible-content ${isVocabCollapsed ? 'collapsed' : ''}" id="content-group-${index}-vocab">
                    <div class="vocab-container">
                        <div class="vocab-grid">
                            ${groupStatus.vocabularies.map((word, idx) => `
                                <span class="vocab-item">
                                    <span class="vocab-index">${idx + 1}</span>
                                    <span class="vocab-word">${word}</span>
                                </span>
                            `).join('')}
                        </div>
                    </div>
                </div>
                ` : ''}

                ${groupStatus.summary ? `
                <h3 class="subsection-title">
                    <i data-lucide="book-open"></i> 会话摘要
                </h3>
                <div class="summary-container">
                    <div class="summary-content">
                        <i data-lucide="quote"></i>
                        <div class="summary-text">${groupStatus.summary}</div>
                    </div>
                </div>
                ` : ''}

                <hr class="section-divider">
            </section>
        `;
}

function renderBehaviorProfileMarkdown(bp, pad) {
    const emotionData = EMOTION_MAP[bp.emotion] || {
        name: bp.emotion,
        pad: {pleasure: 0, arousal: 0, dominance: 0},
        desc: '未知情绪状态'
    };

    const normalizeForDisplay = (val) => {
        return Math.max(0, Math.min(100, (val + 4) / 8 * 100));
    };

    const getEmotionTendency = (p, a, d) => {
        if (p > 1) return '积极愉悦';
        if (p < -1) return '消极低落';
        return '情绪平和';
    };

    return `
            <div class="emotion-container">
                <div class="emotion-header">
                    <div class="emotion-current">
                        <span class="emotion-name">${emotionData.name}</span>
                        <span class="emotion-code">${bp.emotion}</span>
                    </div>
                    <div class="emotion-tendency">${getEmotionTendency(pad.p, pad.a, pad.d)}</div>
                </div>

                <div class="emotion-description">
                    <i data-lucide="quote"></i>
                    ${emotionData.desc}
                </div>

                <div class="pad-grid">
                    <div class="pad-item">
                        <div class="pad-header">
                            <div class="pad-label">
                                <i data-lucide="smile"></i>
                                <span>Pleasure</span>
                            </div>
                            <div class="pad-value">${pad.p.toFixed(2)}</div>
                        </div>
                        <div class="pad-bar-wrapper">
                            <div class="pad-bar pleasure" style="width: ${normalizeForDisplay(pad.p)}%"></div>
                        </div>
                        <div class="pad-scale">
                            <span>-4</span>
                            <span class="scale-center">0</span>
                            <span>+4</span>
                        </div>
                    </div>

                    <div class="pad-item">
                        <div class="pad-header">
                            <div class="pad-label">
                                <i data-lucide="zap"></i>
                                <span>Arousal</span>
                            </div>
                            <div class="pad-value">${pad.a.toFixed(2)}</div>
                        </div>
                        <div class="pad-bar-wrapper">
                            <div class="pad-bar arousal" style="width: ${normalizeForDisplay(pad.a)}%"></div>
                        </div>
                        <div class="pad-scale">
                            <span>-4</span>
                            <span class="scale-center">0</span>
                            <span>+4</span>
                        </div>
                    </div>

                    <div class="pad-item">
                        <div class="pad-header">
                            <div class="pad-label">
                                <i data-lucide="crown"></i>
                                <span>Dominance</span>
                            </div>
                            <div class="pad-value">${pad.d.toFixed(2)}</div>
                        </div>
                        <div class="pad-bar-wrapper">
                            <div class="pad-bar dominance" style="width: ${normalizeForDisplay(pad.d)}%"></div>
                        </div>
                        <div class="pad-scale">
                            <span>-4</span>
                            <span class="scale-center">0</span>
                            <span>+4</span>
                        </div>
                    </div>
                </div>

                <div class="behavior-profile">
                    <div class="profile-label">行为特征</div>
                    <div class="profile-tags">
                        <span class="profile-tag">
                            <i data-lucide="message-circle"></i>
                            ${TONE_MAP[bp.tone] || bp.tone}
                        </span>
                        <span class="profile-tag">
                            <i data-lucide="hand"></i>
                            ${AGGRESSION_MAP[bp.aggressiveness] || bp.aggressiveness}
                        </span>
                        <span class="profile-tag">
                            <i data-lucide="sparkles"></i>
                            ${EMOJI_MAP[bp.emojiLevel] || bp.emojiLevel}
                        </span>
                    </div>
                </div>
            </div>
        `;
}

function renderFlowStateMarkdown(flow) {
    const stateColors = {
        'STANDBY': '#71717a',
        'GETTING_BETTER': '#3b82f6',
        'FLOW_BURST': '#ef4444'
    };
    const stateNames = {
        'STANDBY': '待机模式',
        'GETTING_BETTER': '渐入佳境',
        'FLOW_BURST': '心流爆发'
    };
    const stateIcons = {
        'STANDBY': 'circle',
        'GETTING_BETTER': 'toggle-left',
        'FLOW_BURST': 'disc'
    };

    return `
            <div class="flow-state-container">
                <div class="flow-header">
                    <div class="flow-current-state" style="color: ${stateColors[flow.state]}">
                        <i data-lucide="${stateIcons[flow.state]}"></i>
                        <span class="state-name">${stateNames[flow.state]}</span>
                    </div>
                    <div class="flow-meter-value">${flow.meter.toFixed(1)}</div>
                </div>

                <div class="flow-bar-wrapper">
                    <div class="flow-bar" style="width: ${flow.meter}%; background: ${stateColors[flow.state]}"></div>
                    <div class="flow-marker" style="left: 30%" data-label="30"></div>
                    <div class="flow-marker" style="left: 70%" data-label="70"></div>
                </div>

                <div class="flow-scale">
                    <span class="scale-point">
                        <span class="scale-value">0</span>
                        <span class="scale-label">待机</span>
                    </span>
                    <span class="scale-point">
                        <span class="scale-value">30</span>
                        <span class="scale-label">阈值</span>
                    </span>
                    <span class="scale-point">
                        <span class="scale-value">70</span>
                        <span class="scale-label">阈值</span>
                    </span>
                    <span class="scale-point">
                        <span class="scale-value">100</span>
                        <span class="scale-label">爆发</span>
                    </span>
                </div>

            </div>
        `;
}

function renderVolitionStateMarkdown(volition) {
    const shouldSpeakClass = volition.shouldSpeak ? 'active' : 'inactive';
    const speakStatus = volition.shouldSpeak ? '已触发主动发言阈值' : '保持观察状态';
    const speakIcon = volition.shouldSpeak ? 'check-circle' : 'pause-circle';

    return `
            <div class="volition-container">
                <div class="volition-header">
                    <div class="volition-title">
                        <i data-lucide="brain"></i>
                        <span>冲动值累积系统</span>
                    </div>
                    <div class="volition-status ${shouldSpeakClass}">
                        <i data-lucide="${speakIcon}"></i>
                        <span>${speakStatus}</span>
                    </div>
                </div>

                <div class="volition-metrics">
                    <div class="volition-metric">
                        <div class="metric-header">
                            <div class="metric-label">
                                <i data-lucide="zap"></i>
                                <span>Stimulus</span>
                            </div>
                            <div class="metric-value">${volition.stimulus.toFixed(1)}</div>
                        </div>
                        <div class="metric-bar-wrapper">
                            <div class="metric-bar stimulus" style="width: ${volition.stimulus}%"></div>
                        </div>
                        <div class="metric-scale">
                            <span>0</span>
                            <span class="scale-center">50</span>
                            <span>100</span>
                        </div>
                    </div>

                    <div class="volition-metric">
                        <div class="metric-header">
                            <div class="metric-label">
                                <i data-lucide="bed"></i>
                                <span>Fatigue</span>
                            </div>
                            <div class="metric-value">${volition.fatigue.toFixed(1)}</div>
                        </div>
                        <div class="metric-bar-wrapper">
                            <div class="metric-bar fatigue" style="width: ${volition.fatigue}%"></div>
                        </div>
                        <div class="metric-scale">
                            <span>0</span>
                            <span class="scale-center">50</span>
                            <span>100</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
}

// 樱花飘落动画
function createSakura() {
    const sakuraContainer = document.getElementById('sakura-container');
    const sakura = document.createElement('div');
    sakura.className = 'sakura';
    sakura.innerHTML = '<i data-lucide="flower"></i>';
    sakura.style.left = Math.random() * 100 + '%';
    sakura.style.animationDuration = (Math.random() * 3 + 5) + 's';
    sakura.style.animationDelay = Math.random() * 2 + 's';
    sakuraContainer.appendChild(sakura);

    setTimeout(() => {
        sakura.remove();
    }, 8000);
}
