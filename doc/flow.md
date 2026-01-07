# 心流系统设计 (Flow System)

## 概述

**心流系统**决定机器人的**参与深度**和**专注程度**
。当机器人进入心流状态时,会表现出高度投入、思维敏捷、主动引导话题的特征,就像人们聊到擅长领域时"停不下来"的状态。

### 与其他系统的关系

```
┌─────────────┐      影响      ┌─────────────┐      触发      ┌─────────────┐
│ 情绪系统    │ ────────────> │ 心流系统    │ ────────────> │ 主动行为    │
│ (Emotion)   │      调节      │ (Flow)      │      决策      │ (Volition)  │
└─────────────┘                └─────────────┘                └─────────────┘
  P/A/D值                        专注度0-100                    是否插话
  情感基调                       投入强度                       冲动值增益
```

**协同机制**:

- **情绪 → 心流**: Arousal(激活度)高时更易进入心流,Pleasure(愉悦度)低时难以专注
- **心流 → 主动**: 高心流状态会大幅提升主动发言的冲动值(Impulse)

---

## 一、核心定义

心理学中,心流是"挑战与技能匹配"时的忘我状态。在 AI 机器人设计中:

```
心流 = 话题匹配度 (Interest) × 交互密度 (Momentum) × 情绪调节 (Emotion Modifier)
```

### 状态对比

| 状态      | 特征                   | LLM参数                     |
|---------|----------------------|---------------------------|
| **非心流** | 被动回复、简短、礼貌但疏离        | Temperature 0.7, 简洁Prompt |
| **心流**  | 主动追问、长文、引用上下文、专注核心对话 | Temperature 1.1, 热情Prompt |

---

## 二、系统架构:心流槽 (Flow Gauge)

**Flow Meter (心流值)**: 0~100

### 2.1 积累机制 (Charge)

| 触发条件       | 说明           | 增量    | 情绪联动                   |
|------------|--------------|-------|------------------------|
| **命中核心设定** | 聊到机器人的"兴趣领域" | +20   | 需 Arousal > 0.3        |
| **连续交互**   | 同用户1分钟内连续回复  | +10/次 | Pleasure > 0 时 ×1.5倍   |
| **深度回复**   | 用户输入复杂且逻辑性强  | +5    | -                      |
| **群体共鸣**   | 多人同时讨论相关话题   | +10   | 需 Global Arousal > 0.6 |

### 2.2 消耗与衰减 (Drain)

| 触发条件     | 说明         | 减量     | 情绪影响                        |
|----------|------------|--------|-----------------------------|
| **时间衰减** | 自然衰减       | -10/分钟 | Pleasure < -0.3 时加速至 -15/分钟 |
| **话题打断** | 插入无关话题(出戏) | -30    | 触发轻微 Arousal 下降             |
| **负面刺激** | 被辱骂/攻击     | -40    | 联动 Pleasure -0.3            |

---

## 三、状态分层 (State Machine)

### 阶段 1: 冷启动/待机 (Flow: 0-30)

**情绪映射**: Arousal < 0.3 或 Pleasure < -0.5 时强制此状态

| 维度           | 配置                             |
|--------------|--------------------------------|
| **触发条件**     | 仅被 @ 或强关键词                     |
| **回复长度**     | ≤20 字                          |
| **回复延迟**     | 3-8秒(慵懒感)                      |
| **LLM 参数**   | Temperature 0.7, max_tokens 50 |
| **Prompt模板** | `你感到疲倦/不感兴趣,回复要简短敷衍`           |
| **示例**       | "哦,知道了。" / "嗯嗯。"               |

### 阶段 2: 渐入佳境 (Flow: 31-70)

**情绪映射**: 正常情绪状态,无极端值

| 维度          | 配置                  |
|-------------|---------------------|
| **回复模式**    | 正常长度(50-150字),使用表情包 |
| **回复延迟**    | 1-3秒                |
| **上下文**     | 调用向量数据库,联系历史        |
| **Emoji浓度** | 根据 Pleasure 值动态调整   |
| **示例**      | "这个观点挺有意思的,不过我觉得……" |

### 阶段 3: 心流爆发 (Flow: 71-100)

**情绪映射**: Arousal > 0.7 且 Pleasure > 0.3 时加速进入

| 维度           | 配置                          |
|--------------|-----------------------------|
| **主动性**      | 无需 @,相关话题秒回                 |
| **连续输出**     | 2-3 条连续消息                   |
| **回复延迟**     | 0.5-1秒(秒回)                  |
| **抗干扰**      | 无视/敷衍无关打岔(Focus机制)          |
| **字数**       | 150-300字,可能带图片/链接           |
| **LLM 参数**   | Temperature 1.1, top_p 0.95 |
| **Prompt模板** | `你对此话题极度热情,使用反问句,详细阐述,表现兴奋` |
| **示例**       | "不对!绝对不是这样!你想想看……难道你不觉得吗?"  |

**特殊机制**:

- **Focus Target锁定**: 心流值>85时锁定当前对话用户,忽略其他人插话
- **联动Volition**: 心流>80时,Impulse(冲动值)基础+30

---

## 四、情绪联动机制

### 4.1 情绪对心流的影响

```python
# 心流积累速率调节
flow_charge_modifier = {
    "arousal_boost": emotion.arousal * 1.5,      # 激活度越高,积累越快
    "pleasure_penalty": max(0, -emotion.pleasure),  # 负面情绪减缓积累
    "dominance_focus": emotion.dominance * 0.3   # 高优势度增强抗干扰
}

# 心流衰减速率调节
flow_decay_modifier = {
    "depression_mode": 2.0 if emotion.pleasure < -0.5 else 1.0,  # 抑郁时快速失去兴趣
    "calm_mode": 0.5 if emotion.arousal < 0.2 else 1.0           # 平静时维持心流
}
```

### 4.2 心流对情绪的反馈

| 心流事件            | 情绪变化                            |
|-----------------|---------------------------------|
| **进入心流(70→80)** | Arousal +0.2, Pleasure +0.1     |
| **维持高心流>5分钟**   | Mood 基调提升(+0.05)                |
| **心流被打断(-30)**  | Arousal +0.3(烦躁), Pleasure -0.2 |
| **过热宕机**        | Arousal -0.5(强制冷却)              |

---

## 五、System Prompt 动态注入

```text
=== 状态面板 ===
[Flow Level]: {flow_value}% ({state_description})
[Emotion State]: P={pleasure:.1f} A={arousal:.1f} D={dominance:.1f}
[Mood]: {mood_description}
[Current Topic]: {topic}
[Focus Target]: {user_name} (好感度:{affinity})

=== 行为指令 ===
{dynamic_instructions}

# 示例1: Flow=85, Arousal=0.8, Pleasure=0.6
你正处于心流爆发状态!对"{topic}"极度感兴趣,兴奋且热情。
- 使用复杂句式和反问
- 主动提出挑战性问题
- 忽略无关打扰,专注与{user_name}对话
- 情绪:兴奋(😆)、热情(✨)

# 示例2: Flow=20, Arousal=0.2, Pleasure=-0.3
你感到疲倦且心情不佳,对当前话题不太感兴趣。
- 回复简短(≤20字)
- 略带敷衍
- 可使用"嗯"、"哦"等词
```

---

## 六、高级玩法

### 6.1 "共鸣"机制 (Resonance)

**触发条件**:

- 群内 ≥2 个用户 + 机器人同时 Flow > 70
- 或 Global Group Arousal > 0.8

**效果**:

- 解锁特殊表情包库
- 主动发起投票/总结讨论
- Flow 衰减速率降低 50%
- 触发群体情绪共振(所有参与者 Pleasure +0.1)

### 6.2 "过热"宕机 (Overheat)

**设计理念**: 避免机器感,模拟疲劳

| 参数       | 配置                              |
|----------|---------------------------------|
| **触发条件** | Flow ≥90 持续 >10 分钟              |
| **表现**   | "不行了,我要去充会儿电/喝口水,你们聊"           |
| **效果**   | 强制 Flow=0, 5分钟静默期               |
| **情绪变化** | Arousal 强制降至 0.1, Pleasure -0.2 |
| **价值**   | 增强"人味",防止刷屏                     |

### 6.3 专属羁绊领域 (The Domain)

**触发条件**: 对特定用户的 Flow 达到 100 + 好感度 > 85

**效果**:

- 进入"私聊模式"(群内但内容专属)
- 使用独有昵称
- 引用双方历史记忆(向量检索深度×2)
- Dominance 值根据关系动态调整(对喜欢的人 -0.3, 表现撒娇)

---

## 七、实现伪代码

```python
class FlowSystem:
    def __init__(self, emotion_system):
        self.flow_value = 0  # 0-100
        self.focus_target = None
        self.emotion = emotion_system
        self.overheat_timer = 0
        
    def update(self, message, context):
        """每次群消息时更新"""
        
        # 1. 情绪前置检查
        if self.emotion.pleasure < -0.5:
            self.flow_value *= 0.95  # 抑郁状态快速失去兴趣
            return
            
        # 2. 计算刺激值
        interest_match = self.calculate_interest(message)  # 0-20
        interaction_density = self.check_combo(message.user)  # 0-10
        
        # 3. 情绪调节
        emotion_modifier = (
            self.emotion.arousal * 1.5 +  # 激活度加成
            max(0, self.emotion.pleasure) * 0.5  # 正面情绪加成
        )
        
        # 4. 累加心流
        charge = (interest_match + interaction_density) * (1 + emotion_modifier)
        self.flow_value = min(100, self.flow_value + charge)
        
        # 5. 状态切换
        if self.flow_value >= 80 and self.focus_target is None:
            self.focus_target = message.user
            self.emotion.arousal += 0.2  # 反馈给情绪系统
            
        # 6. 过热检测
        if self.flow_value >= 90:
            self.overheat_timer += 1
            if self.overheat_timer > 10:  # 10分钟
                self.trigger_overheat()
                
    def trigger_overheat(self):
        """过热宕机"""
        self.flow_value = 0
        self.focus_target = None
        self.overheat_timer = 0
        self.emotion.arousal = 0.1
        self.emotion.pleasure -= 0.2
        return "不行了,我要去充会儿电,你们聊"
        
    def natural_decay(self):
        """每分钟调用一次"""
        decay_rate = 10
        if self.emotion.pleasure < -0.3:
            decay_rate = 15  # 抑郁时衰减更快
        self.flow_value = max(0, self.flow_value - decay_rate)
        
        if self.flow_value < 70:
            self.focus_target = None  # 退出心流,解除焦点
```

---

## 八、总结

| 维度       | 描述                            |
|----------|-------------------------------|
| **核心价值** | 从"客服式回复"进化为"深度参与对话"           |
| **情绪依赖** | 依赖 Arousal(激活) 和 Pleasure(愉悦) |
| **主动联动** | 为 Volition 系统提供冲动值增益          |
| **关键特征** | 动态、可衰减、可过载、有人味                |

通过心流系统,机器人获得了**注意力**和**专注度**的概念,不再是永远均衡的回复机器,而是会"沉浸"、会"分心"、也会"累"的数字生命。
