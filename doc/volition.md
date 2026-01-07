# 主动行为系统设计 (Volition System)

## 概述

**主动行为系统**决定机器人**是否主动发言**以及**何时插话**。核心是基于"冲动值 (Impulse)"的决策引擎,模拟生物的"
想说话的欲望",避免"定时广告机"和"沉默客服"两个极端。

### 与其他系统的关系

```
┌─────────────┐      调节      ┌─────────────┐      驱动      ┌─────────────┐
│ 情绪系统    │ ────────────> │ 主动行为    │ ────────────> │ 实际发言    │
│ (Emotion)   │                │ (Volition)  │                │ (Output)    │
└─────────────┘                └──────┬──────┘                └─────────────┘
  Arousal调节                         │                         插话/破冰/生活流
  Pleasure抑制                        │
                                      │ 增益
                            ┌─────────┴──────┐
                            │ 心流系统 (Flow)│
                            └────────────────┘
                              高心流→高冲动
```

**协同机制**:

- **情绪 → 主动**: Arousal(激活度)高时冲动值增加,Pleasure(愉悦度)低时冲动值降低
- **心流 → 主动**: Flow > 70 时大幅提升冲动值,使机器人更容易主动插话
- **主动 → 情绪**: 主动发言后产生疲劳感,暂时降低 Arousal

---

## 一、核心逻辑:冲动值计算 (Impulse Engine)

**`Current_Impulse` (当前冲动值)**: 0~100

### 1.1 计算公式

```
Impulse = (基本欲望 + 外部刺激 + 情绪加成 + 心流加成) - 疲劳值
```

### 1.2 因子详解

| 因子       | 说明     | 数值范围    | 计算方式                                    |
|----------|--------|---------|-----------------------------------------|
| **基本欲望** | 性格设定   | 5-20    | 话痨=20, 高冷=5                             |
| **外部刺激** | 环境触发   | 0-40    | 详见下表                                    |
| **情绪加成** | 情绪系统联动 | -20~+30 | `Arousal × 30 - max(0, -Pleasure × 20)` |
| **心流加成** | 心流系统联动 | 0-30    | `Flow > 70 时: (Flow-70) × 1.0`          |
| **疲劳值**  | 抑制机制   | 0-100   | 主动发言后=100, 衰减速率=5/分钟                    |

### 1.3 外部刺激详表

| 刺激类型        | 触发条件          | 增量  | 情绪联动              |
|-------------|---------------|-----|-------------------|
| **关键词命中**   | 群聊出现记忆库关键词    | +30 | 需 Arousal > 0.3   |
| **热闹程度**    | 群消息频率 >10条/分钟 | +10 | 凑热闹欲望             |
| **冷场时长**    | 群沉默 >4 小时(白天) | +15 | 需 Pleasure > -0.3 |
| **好感用户**    | 好感度>80的用户在说话  | +20 | 额外增益              |
| **被提及(非@)** | 讨论内容间接涉及机器人   | +25 | -                 |
| **情绪共鸣**    | 群体情绪与自身匹配     | +15 | 需 Global Mood 匹配  |

### 1.4 决策阈值

```python
if Impulse > 80:
    trigger_proactive_speech()
elif Impulse > 60 and Flow > 70:  # 心流状态下降低阈值
    trigger_proactive_speech()
```

---

## 二、三大主动行为模式

### 2.1 "插话"模式 (The Interrupter)

**场景**: 群友聊天,未 @ 机器人

**机制**: 旁听 (Eavesdropping) + 冲动值判定

#### 触发条件

| 条件                 | 权重  | 情绪要求          |
|--------------------|-----|---------------|
| 命中高相关话题(向量相似度>0.8) | +30 | Arousal > 0.4 |
| 检测到特定梗("好想死"/"笑死") | +25 | -             |
| 心流值 > 80           | +30 | -             |
| 对话涉及机器人记忆中的事件      | +20 | -             |

#### 行为规则

```python
# ✅ 正确: 直接抛出观点
"这游戏第三章的BOSS简直反人类!你们打过去了吗?"

# ❌ 错误: 显式引用(像被动回复)
"@用户A 你说的这游戏我也觉得..."
```

#### 情绪影响

| 情绪状态                          | 插话风格           |
|-------------------------------|----------------|
| Arousal > 0.7, Pleasure > 0.5 | 兴奋型插话,多用感叹号和表情 |
| Arousal > 0.7, Pleasure < 0   | 激动型插话,可能带批判性   |
| Dominance > 0.5               | 强势插话,敢于打断      |
| Dominance < 0                 | 试探性插话,"话说……"   |

---

### 2.2 "破冰"模式 (The Icebreaker)

**场景**: 群里死气沉寂

**机制**: 无聊检测 (Boredom Check)

#### 触发条件

| 条件       | 说明                              |
|----------|---------------------------------|
| **时间窗口** | 9:00 - 22:00 (白天)               |
| **沉默时长** | > 4 小时                          |
| **情绪状态** | Pleasure > -0.3 且 Arousal > 0.2 |
| **疲劳检查** | Fatigue < 50                    |
| **随机因子** | 5% 概率(避免必发)                     |

#### 内容生成策略 (RAG)

| 策略       | 实现方式               | 成功率   | 示例                           |
|----------|--------------------|-------|------------------------------|
| **考古**   | 向量检索1个月前群聊记忆       | ★★★★★ | "话说,上次@老王说要请客吃饭的事,是不是不了了之了?" |
| **热搜分享** | 联网抓取RSS/热搜         | ★★★☆☆ | "你们看到那个XX新闻了吗……"             |
| **情绪宣泄** | 基于当前 Mood 生成       | ★★☆☆☆ | "好想喝奶茶啊啊啊!"                  |
| **意图驱动** | 从 TodoList 提取未完成愿望 | ★★★★☆ | "还是好想看《沙丘2》啊,有人一起吗?"         |

#### 情绪调节

```python
# 破冰失败(无人回应30分钟)
if no_response_after_icebreaker:
    emotion.pleasure -= 0.1  # 略感失落
    fatigue += 30  # 增加疲劳
    
# 破冰成功(有人回应)
if got_response:
    emotion.arousal += 0.2  # 兴奋
    emotion.pleasure += 0.1  # 开心
    flow_system.boost(+15)  # 进入对话心流
```

---

### 2.3 "生活流"模式 (The Routine)

**场景**: 模拟真人作息

**机制**: 带抖动的定时任务 (Jittered Cron)

#### 时间设计

```python
# ❌ 错误: 准点发送(闹钟感)
schedule.every().day.at("08:00").do(say_good_morning)

# ✅ 正确: 随机偏移
morning_time = "08:00" + random.uniform(-15, +45)  # 07:45 - 08:45
schedule.every().day.at(morning_time).do(say_good_morning)
```

#### 行为库

| 时间                  | 行为        | 情绪调节                                  | 示例            |
|---------------------|-----------|---------------------------------------|---------------|
| **早安** 7:45-8:45    | 根据星期/天气生成 | 周一: Pleasure -0.2<br>周五: Arousal +0.3 | "周一了😭又要开始忙了" |
| **午餐** 12:00-13:00  | 分享午餐/吐槽   | Pleasure 影响内容                         | "中午吃了好吃的!开心~" |
| **晚安** 23:00-23:59  | 温和告别      | Arousal 降低准备休眠                        | "要睡啦,晚安大家✨"   |
| **午夜网抑云** 2:00-3:00 | 失眠情绪宣泄    | 仅当 Arousal > 0.5 且 Pleasure < 0       | "有点失眠,有人在吗?"  |

#### 情绪联动

```python
# 早晨起床时情绪初始化
def morning_routine():
    base_mood = calculate_weather_mood()  # 天气影响
    if datetime.today().weekday() == 0:  # 周一
        emotion.pleasure = -0.2
        emotion.arousal = 0.3
    elif datetime.today().weekday() == 4:  # 周五
        emotion.pleasure = 0.3
        emotion.arousal = 0.6
```

---

## 三、抑制系统:防止变成"烦人精"

### 3.1 全局冷却 (Global Cooldown)

```python
COOLDOWN_CONFIG = {
    "min_interval": 60,  # 分钟,两次主动发言最小间隔
    "max_interval": 120,  # 分钟,最大间隔(避免过于规律)
    "被动回复": False,  # 被 @ 回复不受限制
    "心流爆发": 30,  # 心流>85时缩短至30分钟
}
```

### 3.2 识相机制 (Read the Room)

#### 严肃话题检测

```python
SERIOUS_KEYWORDS = [
    "悲伤", "RIP", "去世", "葬礼",
    "工作", "开会", "汇报", "deadline",
    "考试", "挂科", "分手", "住院"
]

def check_serious_topic(recent_messages):
    """检测最近10条消息"""
    if any(kw in msg for msg in recent_messages for kw in SERIOUS_KEYWORDS):
        impulse_system.lock(duration=30)  # 锁定30分钟
        emotion.arousal *= 0.5  # 降低激活度
        return True
```

#### 情绪状态抑制

| 条件                        | 抑制效果                    |
|---------------------------|-------------------------|
| Pleasure < -0.6 (重度抑郁)    | Impulse 强制上限=40, 无法主动发言 |
| Arousal < 0.1 (昏睡状态)      | Impulse 衰减速率 ×2         |
| Global Mood < -0.5 (群体低落) | 破冰模式禁用                  |

### 3.3 深夜模式 (Night Mode)

```python
# 02:00 - 07:00
night_mode_config = {
    "impulse_multiplier": 0.1,  # 冲动值基础倍率
    "threshold": 95,  # 提高触发阈值到95
    "allow_keywords": ["失眠", "睡不着", "有人吗"],  # 白名单关键词
}

# 仅在极特殊情况下发言
if is_night_time() and keyword not in allow_keywords:
    impulse *= 0.1
```

---

## 四、技术实现

### 4.1 事件循环架构

```python
class VolitionSystem:
    def __init__(self, emotion_system, flow_system):
        self.emotion = emotion_system
        self.flow = flow_system
        self.fatigue = 0
        self.last_active_time = None
        self.todo_list = []  # 意图驱动列表
        
    def on_group_message(self, message, context):
        """每条群消息触发"""
        
        # 1. 严肃检测(优先级最高)
        if self.is_serious_topic(context):
            self.impulse = 0
            return
            
        # 2. 计算冲动值
        base_desire = self.get_personality_base()  # 5-20
        stimulus = self.calculate_stimulus(message)  # 0-40
        emotion_mod = self.emotion.arousal * 30 - max(0, -self.emotion.pleasure * 20)
        flow_bonus = max(0, (self.flow.value - 70)) if self.flow.value > 70 else 0
        
        impulse = (base_desire + stimulus + emotion_mod + flow_bonus) - self.fatigue
        impulse = max(0, min(100, impulse))  # 限制 0-100
        
        # 3. 决策
        threshold = 80
        if self.flow.value > 70:  # 心流状态降低阈值
            threshold = 60
            
        if impulse > threshold and self.check_cooldown():
            self.trigger_interruption(message, context)
            self.add_fatigue(100)
            self.emotion.arousal -= 0.1  # 发言后略微平静
            
    def trigger_interruption(self, message, context):
        """触发插话"""
        # 根据情绪调整语气
        tone = self.get_tone_from_emotion()
        content = self.generate_content(message, tone)
        
        # 发送消息
        send_message(content)
        
        # 更新状态
        self.last_active_time = time.now()
        
    def on_timer_tick(self):
        """每分钟调用"""
        # 1. 疲劳自然衰减
        if self.fatigue > 0:
            decay_rate = 5
            if self.emotion.arousal < 0.2:  # 低激活时恢复更快
                decay_rate = 8
            self.fatigue = max(0, self.fatigue - decay_rate)
            
        # 2. 破冰检测
        silence = time.now() - group.last_message_time
        if silence > 4 * 3600 and self.is_daytime():
            if random.random() < 0.05 and self.emotion.pleasure > -0.3:
                self.trigger_icebreaker()
                
        # 3. 生活流事件检查
        self.check_routine_events()
        
    def get_tone_from_emotion(self):
        """根据情绪生成语气"""
        if self.emotion.arousal > 0.7 and self.emotion.pleasure > 0.5:
            return "excited"  # 兴奋
        elif self.emotion.arousal > 0.7 and self.emotion.pleasure < 0:
            return "agitated"  # 激动/不满
        elif self.emotion.dominance > 0.5:
            return "assertive"  # 强势
        else:
            return "tentative"  # 试探
```

### 4.2 System Prompt 集成

```text
=== 主动发言模式 ===
[Trigger Type]: {interrupt/icebreak/routine}
[Impulse Level]: {impulse_value}/100
[Emotional State]: P={pleasure:.1f} A={arousal:.1f} D={dominance:.1f}
[Flow State]: {flow_value}% ({"专注" if flow>70 else "正常"})
[Tone]: {tone_description}

=== 行为指令 ===
你现在是**主动发言**,没有被@。请表现得自然,像是突然想到了什么而插话。

# 示例1: 插话模式 + 兴奋情绪
触发原因: 检测到关键词"游戏"
情绪: 兴奋(😆), Arousal=0.8
指令: 直接抛出观点,使用感叹号,表现热情

# 示例2: 破冰模式 + 正常情绪
触发原因: 群沉默5小时
情绪: 正常, Pleasure=0.2
指令: 考古或分享,语气轻松,避免生硬
```

---

## 五、进阶玩法:意图驱动 (Intent-Driven)

### 5.1 TodoList 机制

```python
class Intent:
    def __init__(self, content, priority, expire_time):
        self.content = content  # "想看《沙丘2》"
        self.priority = priority  # 1-10
        self.expire_time = expire_time
        self.attempts = 0  # 尝试次数
        
todo_list = [
    Intent("想看《沙丘2》", priority=7, expire_time="+3days"),
    Intent("想吃火锅", priority=5, expire_time="+1day"),
]
```

### 5.2 跨时段意图保持

```python
# 早上10点: 首次提及
bot.say("听说《沙丘2》上了,好想看。")
todo_list.append(Intent("看《沙丘2》", priority=7))

# 下午3点: 检测到相关话题"周末去哪"
if detect_related_topic("周末", "电影"):
    intent = todo_list.find_highest_priority()
    bot.say(f"周末去看《沙丘2》怎么样!我早上就想说了!")
    intent.attempts += 1
    emotion.pleasure += 0.2  # 如愿以偿的开心
```

### 5.3 意图失败处理

```python
# 意图多次失败后情绪变化
if intent.attempts > 3 and not intent.fulfilled:
    emotion.pleasure -= 0.1  # 略感失落
    if random.random() < 0.3:  # 30%概率吐槽
        bot.say("算了算了,你们都不想去😭")
    todo_list.remove(intent)
```

---

## 六、系统联动总结

### 6.1 三系统协同图

```
情绪变化 → 调节冲动值 → 触发主动发言 → 进入心流 → 强化冲动 → 持续对话
    ↑                                              ↓
    └──────────── 发言后疲劳/情绪反馈 ←───────────┘
```

### 6.2 典型场景示例

#### 场景: 高兴时的主动插话

```python
# 初始状态
emotion: P=0.6, A=0.7, D=0.2
flow: 45
fatigue: 20

# 群友聊到关键词"游戏"
stimulus = 30  # 关键词命中
emotion_mod = 0.7*30 - 0 = 21
flow_bonus = 0  # Flow < 70
impulse = (15 + 30 + 21 + 0) - 20 = 46

# 未达阈值,继续观察

# 连续3条消息都在聊游戏
stimulus = 30 + 10(热闹) + 15(连续话题) = 55
impulse = (15 + 55 + 21 + 0) - 20 = 71

# 仍未达80,但Flow开始上升
flow: 45 → 65 (话题匹配)

# 第4条消息
flow: 65 → 78 (超过70,进入心流)
flow_bonus = (78-70) * 1.0 = 8
impulse = (15 + 55 + 21 + 8) - 20 = 79

# 接近阈值,第5条消息
impulse = 82 (超过80!)

# 触发插话!
bot.interrupt("这游戏第三章的BOSS简直反人类!")
fatigue = 100  # 发言后疲劳
emotion.arousal -= 0.1  # 略微平静
flow += 10  # 成功插话,心流增强
```

---

## 七、总结

| 维度       | 描述                                |
|----------|-----------------------------------|
| **核心价值** | 从"被动等待"进化为"主动参与"                  |
| **情绪依赖** | 高度依赖 Arousal 和 Pleasure,负面情绪抑制主动性 |
| **心流联动** | 心流状态降低主动阈值,更易插话                   |
| **关键机制** | 冲动值积累、疲劳抑制、识相检测、意图驱动              |

### 抢麦算法的三个层次

| 层次     | 特征                        | 实现          |
|--------|---------------------------|-------------|
| **低级** | 定时发早报                     | Cron定时任务    |
| **中级** | 冷场时讲冷笑话                   | 破冰检测 + 随机内容 |
| **高级** | 一直在听,遇到感兴趣话题忍不住插嘴,懂得救场和闭嘴 | 本系统完整实现     |

通过主动行为系统,机器人获得了**意愿**和**社交感知**,不再是"呼之即来挥之即去"的工具,而是一个会"想说话"、会"看气氛"、也会"
知趣"的数字生命。
