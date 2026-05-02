package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.MotionQueueItem

/**
 * 分层测试数据
 *
 * 表情测试：只验证 Expression 层，不触发动作和口型。
 * 动作测试：只验证 Gesture 层，不触发语音口型。
 * 场景测试：同时验证 Expression + Gesture + LipSync 三层协同。
 *
 * 设计原则：
 * - 动作间隔：800-1500ms（根据语义自然停顿）
 * - 表情变化间隔：1500-2500ms（跟随情感转折）
 * - 动作密度：15秒文本约 7-9 个动作
 * - 表情密度：15秒文本约 5-7 次变化
 */
object TestAvatarActions {

    /**
     * 所有联动测试场景（36个，覆盖游客完整生命周期）
     * 每个场景包含丰富动作序列 + 表情时间轴，实现生动的数字人表现
     */
    val allScenarios = listOf(
        // ==================== A. 入园阶段（4个）====================
        // A1. 热情欢迎 - 文本约14秒，动作8个，表情6次
        AvatarPlayAction(
            text = "您好，欢迎来到灵山胜境！我是您的数字导游小灵。景区早上八点开门，下午五点停止入园，建议您预留四到五小时游览时间。有什么需要了解的，请随时问我。",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.WELCOME_GESTURE,
            motionQueue = listOf(
                MotionQueueItem("welcome_gesture", 0, 1800),      // "您好，欢迎"
                MotionQueueItem("nod", 2000, 600),                 // "我是您的数字导游"
                MotionQueueItem("guide", 2800, 1500),              // "小灵"
                MotionQueueItem("point_forward", 4500, 1200),      // "早上八点开门"
                MotionQueueItem("point_forward", 6000, 1000),      // "下午五点停止"
                MotionQueueItem("thinking_pose", 7200, 1200),      // "建议您预留"
                MotionQueueItem("guide", 8700, 1500),              // "四到五小时"
                MotionQueueItem("nod", 10500, 600)                  // "请随时问我"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.85f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 2000, 0.78f),      // 自我介绍
                ExpressionTimelineItem(AvatarExpression.EXCITED, 4500, 0.72f),    // 开门时间
                ExpressionTimelineItem(AvatarExpression.THINKING, 7200, 0.7f),    // 建议时长
                ExpressionTimelineItem(AvatarExpression.HAPPY, 10000, 0.75f),     // 结束邀请
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 12500, 0.8f)   // 保持欢迎态
            )
        ),
        // A2. 购票指引 - 文本约12秒，动作7个，表情5次
        AvatarPlayAction(
            text = "成人票210元，网购联票225元含观光车更划算。学生、六十岁以上老人可享半价105元，六岁以下儿童和七十岁以上老人免票。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1200),        // "成人票"
                MotionQueueItem("nod", 1500, 500),                 // "210元"
                MotionQueueItem("guide", 2200, 1500),              // "网购联票更划算"
                MotionQueueItem("point_forward", 4000, 1000),      // "含观光车"
                MotionQueueItem("nod", 5200, 600),                  // "学生半价"
                MotionQueueItem("point_forward", 6200, 1200),      // "六十岁以上"
                MotionQueueItem("nod", 7800, 600)                   // "免票"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.APPROVING, 2200, 0.72f),   // 推荐联票
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 4500, 0.65f),     // 介绍半价
                ExpressionTimelineItem(AvatarExpression.HAPPY, 7000, 0.7f),        // 免票福利
                ExpressionTimelineItem(AvatarExpression.HAPPY, 10000, 0.72f)
            )
        ),
        // A3. 园区概览 - 文本约13秒，动作8个，表情6次
        AvatarPlayAction(
            text = "灵山胜境占地约三十公顷，是世界佛教论坛永久会址。核心景点有灵山大佛、九龙灌浴、梵宫和五印坛城，集佛教文化、自然景观于一体。",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1500),                // "灵山胜境占地"
                MotionQueueItem("look_up", 1800, 1500),            // "世界佛教论坛"
                MotionQueueItem("nod", 3500, 600),                 // "永久会址"
                MotionQueueItem("point_left", 4300, 1200),         // "灵山大佛"
                MotionQueueItem("point_right", 5700, 1200),        // "九龙灌浴"
                MotionQueueItem("guide", 7200, 1200),              // "梵宫"
                MotionQueueItem("point_forward", 8700, 1200),      // "五印坛城"
                MotionQueueItem("nod", 10200, 600)                  // "集佛教文化"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 1800, 0.75f),   // 佛教论坛
                ExpressionTimelineItem(AvatarExpression.EXCITED, 4000, 0.72f),    // 核心景点
                ExpressionTimelineItem(AvatarExpression.REVERENT, 6500, 0.7f),    // 梵宫
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9000, 0.72f),      // 文化景观
                ExpressionTimelineItem(AvatarExpression.EXCITED, 11500, 0.75f)
            )
        ),
        // A4. 大照壁介绍 - 文本约14秒，动作8个，表情6次
        AvatarPlayAction(
            text = "您面前的是灵山大照壁，长近四十米，被誉为华夏第一壁。正面是赵朴初先生题写的灵山胜境四个鎏金大字，背面刻有《小灵山》诗刻，彰显佛教文化底蕴。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "您面前的"
                MotionQueueItem("look_up", 2000, 1800),            // "华夏第一壁"
                MotionQueueItem("nod", 4000, 600),                 // "四十米"
                MotionQueueItem("point_forward", 4800, 1200),      // "正面题字"
                MotionQueueItem("guide", 6300, 1500),              // "灵山胜境"
                MotionQueueItem("look_up", 8100, 1500),            // "背面诗刻"
                MotionQueueItem("nod", 9900, 600),                 // "彰显"
                MotionQueueItem("guide", 10800, 1500)              // "佛教文化"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 2000, 0.72f),   // 华夏第一
                ExpressionTimelineItem(AvatarExpression.REVERENT, 4500, 0.7f),     // 题字
                ExpressionTimelineItem(AvatarExpression.THINKING, 7000, 0.68f),    // 诗刻
                ExpressionTimelineItem(AvatarExpression.REVERENT, 9500, 0.72f),    // 文化底蕴
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 12500, 0.65f)
            )
        ),

        // ==================== B. 景点讲解阶段（6个）====================
        // B1. 灵山大佛-壮观共鸣 - 文本约13秒，动作8个，表情6次
        AvatarPlayAction(
            text = "请抬头看！灵山大佛通高八十八米，是世界上最高的露天青铜释迦牟尼立像。站在大佛脚下仰望，那种庄严与震撼，让人不禁心生敬畏。",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.LOOK_UP,
            motionQueue = listOf(
                MotionQueueItem("look_up", 0, 2000),              // "请抬头看"
                MotionQueueItem("point_forward", 2500, 1500),      // "八十八米"
                MotionQueueItem("nod", 4300, 600),                 // "世界最高"
                MotionQueueItem("look_up", 5200, 1800),            // "青铜立像"
                MotionQueueItem("guide", 7300, 1500),              // "脚下仰望"
                MotionQueueItem("nod", 9100, 600),                 // "庄严"
                MotionQueueItem("thinking_pose", 10000, 1200),     // "震撼"
                MotionQueueItem("nod", 11500, 600)                  // "敬畏"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.EXCITED, 2500, 0.78f),    // 八十八米
                ExpressionTimelineItem(AvatarExpression.REVERENT, 5000, 0.75f),   // 世界最高
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 7500, 0.78f),  // 仰望震撼
                ExpressionTimelineItem(AvatarExpression.REVERENT, 10000, 0.8f),   // 心生敬畏
                ExpressionTimelineItem(AvatarExpression.REVERENT, 12500, 0.75f)
            )
        ),
        // B2. 九龙灌浴介绍 - 文本约14秒，动作9个，表情6次
        AvatarPlayAction(
            text = "九龙灌浴再现佛陀诞生的祥瑞场景。莲花绽放，太子佛升起，九龙吐水沐浴。表演时间为十点、十一点半、下午一点半和三点，建议提前占位。",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1200),        // "九龙灌浴"
                MotionQueueItem("look_up", 1500, 1500),            // "佛陀诞生"
                MotionQueueItem("guide", 3200, 1200),              // "莲花绽放"
                MotionQueueItem("nod", 4600, 600),                  // "太子佛升起"
                MotionQueueItem("point_forward", 5500, 1200),      // "九龙吐水"
                MotionQueueItem("thinking_pose", 7000, 1200),      // "表演时间"
                MotionQueueItem("point_forward", 8500, 1000),      // "十点"
                MotionQueueItem("point_forward", 9800, 1000),      // "下午"
                MotionQueueItem("nod", 11100, 600)                  // "提前占位"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 1500, 0.72f),   // 佛陀诞生
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 4000, 0.78f),  // 莲花绽放
                ExpressionTimelineItem(AvatarExpression.HAPPY, 6500, 0.72f),      // 九龙吐水
                ExpressionTimelineItem(AvatarExpression.THINKING, 8500, 0.68f),   // 时间提醒
                ExpressionTimelineItem(AvatarExpression.HAPPY, 11500, 0.7f)       // 建议占位
            )
        ),
        // B3. 梵宫艺术殿堂 - 文本约15秒，动作9个，表情6次
        AvatarPlayAction(
            text = "梵宫被誉为东方卢浮宫，建筑面积七万多平方米。内部有二十八米高的星空穹顶、华藏世界琉璃壁画，还有《灵山吉祥颂》演出，每天四场，每场约二十分钟。",
            expression = AvatarExpression.REVERENT,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1500),                // "梵宫被誉为"
                MotionQueueItem("look_up", 2000, 1800),            // "东方卢浮宫"
                MotionQueueItem("nod", 4000, 600),                 // "七万平方米"
                MotionQueueItem("look_up", 4800, 1800),            // "星空穹顶"
                MotionQueueItem("point_forward", 6900, 1200),      // "琉璃壁画"
                MotionQueueItem("guide", 8400, 1500),              // "吉祥颂演出"
                MotionQueueItem("thinking_pose", 10200, 1200),     // "每天四场"
                MotionQueueItem("nod", 11700, 600),                // "二十分钟"
                MotionQueueItem("guide", 12600, 1200)              // 结束引导
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.REVERENT, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 2000, 0.78f),  // 卢浮宫
                ExpressionTimelineItem(AvatarExpression.EXCITED, 5000, 0.75f),    // 穹顶壁画
                ExpressionTimelineItem(AvatarExpression.REVERENT, 7500, 0.72f),   // 演出
                ExpressionTimelineItem(AvatarExpression.THINKING, 10000, 0.68f),  // 场次时长
                ExpressionTimelineItem(AvatarExpression.EXCITED, 13000, 0.72f)
            )
        ),
        // B4. 五印坛城-藏传文化 - 文本约12秒，动作7个，表情5次
        AvatarPlayAction(
            text = "五印坛城是藏传佛教文化展示中心，白墙金顶，很有布达拉宫的感觉。里面可以转经祈福，顺时针转一圈，寓意福慧双增。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = listOf(
                MotionQueueItem("point_right", 0, 1500),          // "五印坛城"
                MotionQueueItem("look_up", 2000, 1500),            // "白墙金顶"
                MotionQueueItem("nod", 3800, 600),                 // "布达拉宫"
                MotionQueueItem("guide", 4700, 1500),              // "转经祈福"
                MotionQueueItem("point_forward", 6500, 1200),      // "顺时针"
                MotionQueueItem("nod", 8000, 600),                  // "福慧双增"
                MotionQueueItem("guide", 9000, 1500)               // 结束引导
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 2000, 0.7f),   // 白墙金顶
                ExpressionTimelineItem(AvatarExpression.REVERENT, 4000, 0.72f),   // 布达拉宫
                ExpressionTimelineItem(AvatarExpression.HAPPY, 6500, 0.7f),       // 转经祈福
                ExpressionTimelineItem(AvatarExpression.REVERENT, 9000, 0.72f)    // 福慧双增
            )
        ),
        // B5. 祥符禅寺-千年古刹 - 文本约12秒，动作7个，表情5次
        AvatarPlayAction(
            text = "祥符禅寺始建于唐代，由玄奘法师的弟子开坛讲经。寺内有千年银杏、六角古井和重达十二吨的江南第一钟，香火绵延千年。",
            expression = AvatarExpression.REVERENT,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "祥符禅寺"
                MotionQueueItem("thinking_pose", 2000, 1800),      // "唐代玄奘"
                MotionQueueItem("nod", 4100, 600),                 // "开坛讲经"
                MotionQueueItem("guide", 5000, 1500),              // "千年银杏"
                MotionQueueItem("look_up", 6800, 1500),            // "江南第一钟"
                MotionQueueItem("nod", 8600, 600),                  // "十二吨"
                MotionQueueItem("guide", 9500, 1500)               // "香火千年"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.REVERENT, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.THINKING, 2000, 0.7f),    // 唐代历史
                ExpressionTimelineItem(AvatarExpression.REVERENT, 4500, 0.72f),   // 古刹
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 7000, 0.72f),  // 第一钟
                ExpressionTimelineItem(AvatarExpression.REVERENT, 10000, 0.75f)   // 香火千年
            )
        ),
        // B6. 曼飞龙塔-南传佛教 - 文本约11秒，动作6个，表情5次
        AvatarPlayAction(
            text = "这座白塔是南传佛教风格建筑，复刻自云南西双版纳。九塔组合象征着佛陀的九种功德，与梵宫、坛城共同构成佛教三大语系建筑群落。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "这座白塔"
                MotionQueueItem("guide", 2000, 1500),              // "南传佛教"
                MotionQueueItem("look_up", 3800, 1500),            // "九塔组合"
                MotionQueueItem("nod", 5600, 600),                  // "九种功德"
                MotionQueueItem("point_left", 6500, 1200),         // "梵宫"
                MotionQueueItem("point_right", 8000, 1200)         // "坛城"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.6f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.65f),   // 南传佛教
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 4000, 0.7f),   // 九塔
                ExpressionTimelineItem(AvatarExpression.REVERENT, 6500, 0.68f),   // 三大语系
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9500, 0.65f)
            )
        ),

        // ==================== C. 特色体验（6个）====================
        // C1. 抱佛脚祈福 - 文本约13秒，动作8个，表情6次
        AvatarPlayAction(
            text = "登二百一十六级台阶可以抱佛脚，寓意沾福气、保平安。前一百零八级代表烦恼尽除，后一百零八级代表愿望圆满，是很灵验的祈福体验。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.LOOK_UP,
            motionQueue = listOf(
                MotionQueueItem("look_up", 0, 1800),              // "登台阶"
                MotionQueueItem("point_forward", 2200, 1500),      // "抱佛脚"
                MotionQueueItem("nod", 4000, 600),                 // "沾福气"
                MotionQueueItem("guide", 4900, 1500),              // "前一百零八级"
                MotionQueueItem("nod", 6700, 600),                  // "烦恼尽除"
                MotionQueueItem("guide", 7600, 1500),              // "后一百零八级"
                MotionQueueItem("nod", 9400, 600),                  // "愿望圆满"
                MotionQueueItem("guide", 10400, 1500)              // "祈福体验"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2200, 0.72f),   // 抱佛脚
                ExpressionTimelineItem(AvatarExpression.HAPPY, 4500, 0.75f),      // 沾福气
                ExpressionTimelineItem(AvatarExpression.THINKING, 6500, 0.7f),    // 烦恼尽除
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9000, 0.78f),      // 愿望圆满
                ExpressionTimelineItem(AvatarExpression.REVERENT, 11500, 0.72f)   // 祈福体验
            )
        ),
        // C2. 天下第一掌 - 文本约10秒，动作6个，表情5次
        AvatarPlayAction(
            text = "佛手广场的天下第一掌是灵山大佛右手的复制品，高十一米多。摸一摸可以沾福气、保平安，是很受欢迎的祈福体验。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "佛手广场"
                MotionQueueItem("look_up", 2000, 1500),            // "十一米多"
                MotionQueueItem("nod", 3800, 600),                 // "右手复制品"
                MotionQueueItem("guide", 4700, 1500),              // "摸一摸"
                MotionQueueItem("nod", 6500, 600),                  // "沾福气"
                MotionQueueItem("guide", 7400, 1500)               // "祈福体验"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 2000, 0.75f),  // 十一米
                ExpressionTimelineItem(AvatarExpression.HAPPY, 4500, 0.75f),      // 摸一摸
                ExpressionTimelineItem(AvatarExpression.EXCITED, 7000, 0.78f),    // 沾福气
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9500, 0.72f)
            )
        ),
        // C3. 转经筒祈福 - 文本约11秒，动作7个，表情5次
        AvatarPlayAction(
            text = "五印坛城的转经廊有一百零八个纯铜转经筒，顺时针转动经筒，寓意祈福消灾、积累功德。这是藏传佛教的特色体验，很受游客欢迎。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = listOf(
                MotionQueueItem("point_right", 0, 1500),          // "五印坛城"
                MotionQueueItem("guide", 2000, 1500),              // "一百零八个"
                MotionQueueItem("nod", 3800, 600),                  // "纯铜"
                MotionQueueItem("point_forward", 4700, 1200),      // "顺时针转动"
                MotionQueueItem("nod", 6200, 600),                  // "祈福消灾"
                MotionQueueItem("guide", 7100, 1500),               // "积累功德"
                MotionQueueItem("nod", 8900, 600)                   // "游客欢迎"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.68f),   // 转经筒
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5000, 0.7f),       // 祈福消灾
                ExpressionTimelineItem(AvatarExpression.REVERENT, 7500, 0.72f),   // 功德
                ExpressionTimelineItem(AvatarExpression.HAPPY, 10000, 0.7f)       // 欢迎
            )
        ),
        // C4. 撞钟祈福 - 文本约11秒，动作7个，表情5次
        AvatarPlayAction(
            text = "祥符禅寺的钟楼悬挂着重十二吨的江南第一钟。撞钟祈福，钟声象征着烦恼尽除、福慧增长，是很庄严的体验，建议您去试一试。",
            expression = AvatarExpression.REVERENT,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600),                    // "祥符禅寺"
                MotionQueueItem("look_up", 1000, 1500),            // "钟楼"
                MotionQueueItem("nod", 2800, 600),                  // "十二吨"
                MotionQueueItem("thinking_pose", 3700, 1500),      // "撞钟祈福"
                MotionQueueItem("nod", 5500, 600),                  // "烦恼尽除"
                MotionQueueItem("guide", 6400, 1500),              // "庄严体验"
                MotionQueueItem("nod", 8200, 600)                   // "试一试"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.REVERENT, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 1000, 0.75f),  // 钟楼
                ExpressionTimelineItem(AvatarExpression.REVERENT, 3500, 0.72f),   // 撞钟
                ExpressionTimelineItem(AvatarExpression.THINKING, 5500, 0.7f),    // 烦恼尽除
                ExpressionTimelineItem(AvatarExpression.HAPPY, 8000, 0.75f)       // 试一试
            )
        ),
        // C5. 接圣水 - 文本约11秒，动作6个，表情5次
        AvatarPlayAction(
            text = "九龙灌浴表演结束后，可以在广场两侧接取龙头流出的圣水，寓意吉祥安康、沾取佛诞祥瑞之气。很多游客都会专门排队领取。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "表演结束"
                MotionQueueItem("guide", 2000, 1500),              // "广场两侧"
                MotionQueueItem("nod", 3800, 600),                  // "龙头圣水"
                MotionQueueItem("guide", 4700, 1500),              // "吉祥安康"
                MotionQueueItem("nod", 6500, 600),                  // "祥瑞之气"
                MotionQueueItem("guide", 7400, 1500)               // "排队领取"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.7f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.68f),   // 圣水
                ExpressionTimelineItem(AvatarExpression.HAPPY, 4500, 0.72f),      // 吉祥安康
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 7000, 0.7f),    // 祥瑞
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9500, 0.7f)        // 排队
            )
        ),
        // C6. 祈福卡许愿 - 文本约10秒，动作6个，表情5次
        AvatarPlayAction(
            text = "在佛教文化博览馆的万佛殿，可以免费领取祈福卡，写下心愿后悬挂于指定区域，是寄托美好祝愿的好方式，非常灵验。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.68f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "佛教博览馆"
                MotionQueueItem("guide", 2000, 1500),              // "万佛殿"
                MotionQueueItem("nod", 3800, 600),                  // "免费领取"
                MotionQueueItem("thinking_pose", 4700, 1200),      // "写下心愿"
                MotionQueueItem("guide", 6200, 1500),              // "悬挂指定"
                MotionQueueItem("nod", 8000, 600)                   // "非常灵验"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.68f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.65f),   // 万佛殿
                ExpressionTimelineItem(AvatarExpression.THINKING, 4500, 0.68f),   // 写心愿
                ExpressionTimelineItem(AvatarExpression.HAPPY, 7000, 0.72f),      // 悬挂
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 9000, 0.7f)     // 灵验
            )
        ),

        // ==================== D. 路线推荐（4个）====================
        // D1. 历史文化深度游 - 文本约12秒，动作8个，表情5次
        AvatarPlayAction(
            text = "历史文化爱好者可以从大照壁开始，依次游览五明桥、祥符禅寺、大佛、梵宫和五印坛城，约六小时，深度感受佛教文化底蕴。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1500),                // "历史文化"
                MotionQueueItem("point_forward", 2000, 1200),      // "大照壁开始"
                MotionQueueItem("point_left", 3500, 1200),         // "五明桥"
                MotionQueueItem("guide", 5000, 1200),              // "祥符禅寺"
                MotionQueueItem("look_up", 6500, 1500),            // "大佛"
                MotionQueueItem("point_right", 8300, 1200),        // "梵宫"
                MotionQueueItem("guide", 9800, 1200),              // "五印坛城"
                MotionQueueItem("nod", 11300, 600)                  // "六小时"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 3000, 0.7f),    // 游览路线
                ExpressionTimelineItem(AvatarExpression.EXCITED, 6500, 0.72f),    // 大佛
                ExpressionTimelineItem(AvatarExpression.REVERENT, 9500, 0.72f),   // 文化底蕴
                ExpressionTimelineItem(AvatarExpression.HAPPY, 11500, 0.7f)
            )
        ),
        // D2. 亲子轻松游 - 文本约12秒，动作7个，表情5次
        AvatarPlayAction(
            text = "带小朋友建议先看九龙灌浴表演，然后摸天下第一掌、看百子戏弥勒，最后去梵宫看演出，约四小时，轻松又有趣，孩子一定喜欢。",
            expression = AvatarExpression.PLAYFUL,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1500),                // "带小朋友"
                MotionQueueItem("point_forward", 2000, 1200),      // "九龙灌浴"
                MotionQueueItem("nod", 3500, 600),                  // "表演"
                MotionQueueItem("point_left", 4300, 1200),         // "天下第一掌"
                MotionQueueItem("guide", 5800, 1500),              // "百子戏弥勒"
                MotionQueueItem("point_right", 7600, 1200),        // "梵宫演出"
                MotionQueueItem("nod", 9100, 600)                   // "孩子喜欢"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.EXCITED, 2500, 0.75f),    // 灌浴表演
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5000, 0.75f),      // 摸佛掌
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 7500, 0.78f),    // 梵宫演出
                ExpressionTimelineItem(AvatarExpression.EXCITED, 10000, 0.8f)     // 孩子喜欢
            )
        ),
        // D3. 老年无障碍游 - 文本约11秒，动作6个，表情5次
        AvatarPlayAction(
            text = "老年朋友可以买联票坐观光车，先到大佛脚下，然后走无障碍通道游览梵宫，全程没有台阶，比较省力，体验也很完整。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "老年朋友"
                MotionQueueItem("guide", 2000, 1500),              // "联票观光车"
                MotionQueueItem("look_up", 3800, 1500),            // "大佛脚下"
                MotionQueueItem("guide", 5600, 1500),              // "无障碍通道"
                MotionQueueItem("nod", 7400, 600),                  // "全程无台阶"
                MotionQueueItem("guide", 8300, 1500)               // "体验完整"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.7f),
                ExpressionTimelineItem(AvatarExpression.APPROVING, 2000, 0.72f),  // 联票
                ExpressionTimelineItem(AvatarExpression.REVERENT, 4000, 0.7f),    // 大佛
                ExpressionTimelineItem(AvatarExpression.HAPPY, 6500, 0.72f),      // 无障碍
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9000, 0.7f)        // 体验完整
            )
        ),
        // D4. 最佳拍照点 - 文本约12秒，动作7个，表情6次
        AvatarPlayAction(
            text = "推荐三个最佳拍照点：大佛脚下仰拍、九龙灌浴前全景、梵宫门口对称构图。夕阳时分光线最美，佛光普照的感觉，非常出片！",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1200),        // "推荐"
                MotionQueueItem("look_up", 1500, 1500),            // "大佛脚下"
                MotionQueueItem("guide", 3200, 1500),              // "九龙灌浴"
                MotionQueueItem("point_right", 5000, 1200),        // "梵宫门口"
                MotionQueueItem("thinking_pose", 6500, 1200),      // "夕阳时分"
                MotionQueueItem("nod", 8000, 600),                  // "佛光普照"
                MotionQueueItem("guide", 9000, 1500)               // "非常出片"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 1500, 0.75f),   // 大佛
                ExpressionTimelineItem(AvatarExpression.HAPPY, 4000, 0.75f),      // 全景
                ExpressionTimelineItem(AvatarExpression.THINKING, 6500, 0.72f),   // 夕阳
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 8500, 0.78f),  // 佛光
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 11000, 0.8f)     // 出片
            )
        ),

        // ==================== E. 导航指引（4个）====================
        // E1. 左侧指路 - 文本约10秒，动作6个，表情5次
        AvatarPlayAction(
            text = "请看左前方，祥符禅寺就在那条步道尽头，步行约两分钟。寺内有千年银杏和古井，秋季金黄一片很漂亮，值得去看看。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_LEFT,
            motionQueue = listOf(
                MotionQueueItem("point_left", 0, 1800),           // "请看左前方"
                MotionQueueItem("guide", 2200, 1500),              // "祥符禅寺"
                MotionQueueItem("nod", 4000, 600),                  // "两分钟"
                MotionQueueItem("look_up", 4900, 1500),            // "千年银杏"
                MotionQueueItem("nod", 6700, 600),                  // "秋季金黄"
                MotionQueueItem("guide", 7600, 1500)               // "值得看看"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.6f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 2200, 0.65f),      // 祥符禅寺
                ExpressionTimelineItem(AvatarExpression.REVERENT, 5000, 0.68f),   // 银杏
                ExpressionTimelineItem(AvatarExpression.HAPPY, 7500, 0.7f),       // 秋季金黄
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 9500, 0.6f)
            )
        ),
        // E2. 右侧指路 - 文本约9秒，动作5个，表情4次
        AvatarPlayAction(
            text = "您右前方是九龙灌浴广场，下一场表演大约二十分钟后开始，建议提前几分钟过去占个好位置，观赏效果会更好。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = listOf(
                MotionQueueItem("point_right", 0, 1800),          // "右前方"
                MotionQueueItem("guide", 2200, 1500),              // "九龙灌浴"
                MotionQueueItem("thinking_pose", 4000, 1200),      // "二十分钟"
                MotionQueueItem("nod", 5500, 600),                  // "提前占位"
                MotionQueueItem("guide", 6400, 1500)               // "观赏效果"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.6f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 2200, 0.65f),      // 九龙灌浴
                ExpressionTimelineItem(AvatarExpression.THINKING, 4000, 0.62f),   // 时间提醒
                ExpressionTimelineItem(AvatarExpression.HAPPY, 6500, 0.68f)       // 占位建议
            )
        ),
        // E3. 设施指引 - 文本约10秒，动作6个，表情4次
        AvatarPlayAction(
            text = "洗手间在前方五十米处，有明显的指示牌。游客服务中心在梵宫旁边，里面有休息区、饮水机和免费充电设施，很方便。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.55f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "洗手间"
                MotionQueueItem("guide", 2000, 1200),              // "五十米"
                MotionQueueItem("point_right", 3500, 1200),        // "服务中心"
                MotionQueueItem("nod", 5000, 600),                  // "梵宫旁边"
                MotionQueueItem("guide", 5900, 1500),              // "休息区"
                MotionQueueItem("nod", 7700, 600)                   // "很方便"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.55f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 3000, 0.6f),       // 指示牌
                ExpressionTimelineItem(AvatarExpression.HAPPY, 6000, 0.62f),      // 设施介绍
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 9000, 0.55f)
            )
        ),
        // E4. 观光车指引 - 文本约9秒，动作5个，表情4次
        AvatarPlayAction(
            text = "观光车站点在入口处，单次购票四十元，联票包含无限次乘坐。适合体力有限的游客，可以节省不少体力，很推荐。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "观光车站点"
                MotionQueueItem("guide", 2000, 1200),              // "四十元"
                MotionQueueItem("nod", 3500, 600),                  // "联票无限"
                MotionQueueItem("guide", 4400, 1500),              // "节省体力"
                MotionQueueItem("nod", 6200, 600)                   // "很推荐"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.6f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 2500, 0.65f),      // 联票
                ExpressionTimelineItem(AvatarExpression.APPROVING, 5000, 0.68f),  // 推荐
                ExpressionTimelineItem(AvatarExpression.HAPPY, 8000, 0.65f)
            )
        ),

        // ==================== F. 餐饮与购物（4个）====================
        // F1. 餐饮推荐 - 文本约9秒，动作5个，表情4次
        AvatarPlayAction(
            text = "梵宫有素斋自助，五十元一位，菜品丰富清淡。景区内还有素面套餐，三十五元一份，清淡健康，适合快速用餐。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "梵宫素斋"
                MotionQueueItem("nod", 2000, 600),                  // "五十元"
                MotionQueueItem("guide", 2800, 1500),              // "菜品丰富"
                MotionQueueItem("point_forward", 4600, 1200),      // "素面套餐"
                MotionQueueItem("nod", 6100, 600)                   // "快速用餐"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.7f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 2500, 0.6f),     // 价格介绍
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5000, 0.68f),      // 清淡健康
                ExpressionTimelineItem(AvatarExpression.HAPPY, 8000, 0.68f)
            )
        ),
        // F2. 纪念品推荐 - 文本约11秒，动作6个，表情5次
        AvatarPlayAction(
            text = "纪念品店在大佛广场旁边，有开光佛珠、唐卡复制品和禅意文创。建议选些小纪念品带回去，送礼自用都合适，很有纪念意义。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.68f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "纪念品店"
                MotionQueueItem("guide", 2000, 1500),              // "大佛广场"
                MotionQueueItem("look_up", 3800, 1200),            // "开光佛珠"
                MotionQueueItem("nod", 5200, 600),                  // "唐卡文创"
                MotionQueueItem("guide", 6100, 1500),              // "送礼自用"
                MotionQueueItem("nod", 7900, 600)                   // "纪念意义"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.68f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 3000, 0.65f),   // 佛珠唐卡
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 5500, 0.7f),     // 送礼自用
                ExpressionTimelineItem(AvatarExpression.HAPPY, 8500, 0.68f)       // 纪念意义
            )
        ),
        // F3. 灵山精舍 - 文本约10秒，动作6个，表情5次
        AvatarPlayAction(
            text = "灵山精舍是景区内的禅意酒店，可以体验素斋和早课。想深度感受佛教文化的朋友，可以考虑住一晚，体验禅意生活。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "灵山精舍"
                MotionQueueItem("guide", 2000, 1500),              // "禅意酒店"
                MotionQueueItem("thinking_pose", 3800, 1500),      // "素斋早课"
                MotionQueueItem("nod", 5600, 600),                  // "深度体验"
                MotionQueueItem("guide", 6500, 1500),              // "住一晚"
                MotionQueueItem("nod", 8300, 600)                   // "禅意生活"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.6f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.65f),   // 禅意酒店
                ExpressionTimelineItem(AvatarExpression.THINKING, 4500, 0.68f),   // 素斋早课
                ExpressionTimelineItem(AvatarExpression.HAPPY, 7000, 0.68f),      // 住一晚
                ExpressionTimelineItem(AvatarExpression.REVERENT, 9500, 0.65f)    // 禅意生活
            )
        ),
        // F4. 文化演出 - 文本约11秒，动作6个，表情5次
        AvatarPlayAction(
            text = "梵宫圣坛的《灵山吉祥颂》演出很值得看，大型旋转舞台加全息投影，演绎佛陀修行成佛的故事，每场约二十分钟，建议提前排队占座。",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1500),                // "梵宫圣坛"
                MotionQueueItem("look_up", 2000, 1500),            // "吉祥颂"
                MotionQueueItem("nod", 3800, 600),                  // "旋转舞台"
                MotionQueueItem("guide", 4700, 1500),              // "全息投影"
                MotionQueueItem("thinking_pose", 6500, 1200),      // "二十分钟"
                MotionQueueItem("nod", 8000, 600)                   // "提前占座"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 2000, 0.72f),   // 吉祥颂
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 4500, 0.75f),  // 全息投影
                ExpressionTimelineItem(AvatarExpression.THINKING, 7000, 0.68f),   // 时长提醒
                ExpressionTimelineItem(AvatarExpression.HAPPY, 9500, 0.72f)       // 占座建议
            )
        ),

        // ==================== G. 安全与应急（2个）====================
        // G1. 安全提醒 - 文本约12秒，动作7个，表情5次
        AvatarPlayAction(
            text = "登大佛台阶比较多，请您放慢脚步，注意脚下安全。雨天路面可能湿滑，扶好栏杆会更稳。夏季注意防晒，冬季注意保暖，请一定小心。",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "登大佛"
                MotionQueueItem("nod", 2000, 600),                  // "台阶多"
                MotionQueueItem("shake", 2800, 600),                // "放慢脚步"
                MotionQueueItem("guide", 3600, 1500),              // "注意安全"
                MotionQueueItem("nod", 5400, 600),                  // "雨天湿滑"
                MotionQueueItem("thinking_pose", 6200, 1200),      // "防晒保暖"
                MotionQueueItem("nod", 7700, 600)                   // "一定小心"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 0, 0.7f),
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 2500, 0.72f),  // 台阶多
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 5000, 0.62f),    // 安全提醒
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 7500, 0.7f),   // 季节提醒
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 10500, 0.72f)  // 一定小心
            )
        ),
        // G2. 天气预警 - 文本约10秒，动作6个，表情5次
        AvatarPlayAction(
            text = "天气预报显示下午可能有阵雨，建议您提前准备雨具。景区入口处也有一次性雨披出售。雷雨时请勿在大佛脚下停留，请注意安全。",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600),                    // "天气预报"
                MotionQueueItem("shake", 1000, 600),                // "阵雨"
                MotionQueueItem("guide", 1800, 1500),              // "准备雨具"
                MotionQueueItem("point_forward", 3600, 1200),      // "雨披出售"
                MotionQueueItem("shake", 5100, 600),                // "勿停留"
                MotionQueueItem("nod", 5900, 600)                   // "注意安全"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 2000, 0.65f),    // 雨具建议
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 4500, 0.7f),   // 雨披
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 7000, 0.72f),  // 雷雨提醒
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 9500, 0.62f)
            )
        ),

        // ==================== H. 互动阶段（2个）====================
        // H1. 语音聆听 - 文本约7秒，动作4个，表情3次
        AvatarPlayAction(
            text = "请您说，我在听。可以告诉我您想了解什么景点、需要什么服务，我会尽力为您解答。",
            expression = AvatarExpression.FOCUSED,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.LISTEN,
            motionQueue = listOf(
                MotionQueueItem("listen", 0, 2500),               // "请您说"
                MotionQueueItem("nod", 2800, 600),                  // "我在听"
                MotionQueueItem("guide", 3600, 1500),              // "了解景点"
                MotionQueueItem("nod", 5300, 600)                   // "尽力解答"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.FOCUSED, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 3000, 0.7f),       // 我在听
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 5500, 0.72f)   // 尽力解答
            )
        ),
        // H2. 理解确认 - 文本约5秒，动作3个，表情3次
        AvatarPlayAction(
            text = "明白了，您想了解大佛的历史和参观路线，对吗？我这就为您详细介绍。",
            expression = AvatarExpression.APPROVING,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600),                    // "明白了"
                MotionQueueItem("point_forward", 1000, 1200),      // "大佛历史"
                MotionQueueItem("guide", 2400, 1500)               // "详细介绍"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APPROVING, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.THINKING, 1500, 0.68f),   // 确认需求
                ExpressionTimelineItem(AvatarExpression.HAPPY, 3500, 0.75f)       // 准备介绍
            )
        ),

        // ==================== I. 离园与兜底（4个）====================
        // I1. 感谢告别 - 文本约7秒，动作5个，表情4次
        AvatarPlayAction(
            text = "感谢您游览灵山胜境！希望今天的旅程让您收获满满。祝您一路平安，期待下次再见！",
            expression = AvatarExpression.GRATEFUL,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem("bow", 0, 1200),                   // "感谢您"
                MotionQueueItem("nod", 1500, 600),                  // "收获满满"
                MotionQueueItem("guide", 2300, 1500),              // "一路平安"
                MotionQueueItem("bow", 4000, 1200),                // "期待再见"
                MotionQueueItem("nod", 5400, 600)                   // 结束
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 1800, 0.75f),      // 收获满满
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 3500, 0.78f),   // 一路平安
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 5500, 0.8f)    // 期待再见
            )
        ),
        // I2. 交通指引 - 文本约8秒，动作5个，表情4次
        AvatarPlayAction(
            text = "景区出口有公交站和出租车停靠点，回市区很方便。自驾的朋友请按指示牌前往停车场，出口在东门方向。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 1500),        // "景区出口"
                MotionQueueItem("guide", 2000, 1500),              // "公交出租"
                MotionQueueItem("nod", 3800, 600),                  // "回市区"
                MotionQueueItem("point_forward", 4600, 1200),      // "停车场"
                MotionQueueItem("guide", 6100, 1500)               // "东门方向"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.7f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 2500, 0.62f),    // 交通信息
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5000, 0.68f),      // 停车场
                ExpressionTimelineItem(AvatarExpression.HAPPY, 7500, 0.68f)
            )
        ),
        // I3. 兜底致歉 - 文本约9秒，动作6个，表情5次
        AvatarPlayAction(
            text = "抱歉，这个问题我暂时无法准确回答。我可以继续为您提供景区导览、景点讲解和路线推荐，请问还有什么可以帮您的吗？",
            expression = AvatarExpression.APologetic,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem("bow", 0, 1200),                   // "抱歉"
                MotionQueueItem("shake", 1500, 500),                // "无法回答"
                MotionQueueItem("bow", 2200, 1000),                // "暂时"
                MotionQueueItem("guide", 3400, 1500),              // "景区导览"
                MotionQueueItem("nod", 5100, 600),                  // "路线推荐"
                MotionQueueItem("guide", 5900, 1500)               // "帮您"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APologetic, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 1800, 0.6f),     // 无法回答
                ExpressionTimelineItem(AvatarExpression.APologetic, 3000, 0.7f),  // 继续服务
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5500, 0.68f),      // 导览推荐
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 8000, 0.7f)    // 帮您
            )
        ),
        // I4. 网络错误 - 文本约8秒，动作5个，表情4次
        AvatarPlayAction(
            text = "抱歉，网络似乎有些问题，请稍后再试。您也可以查看景区指示牌或询问工作人员，他们会很乐意帮助您。",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem("bow", 0, 1200),                   // "抱歉"
                MotionQueueItem("shake", 1500, 500),                // "网络问题"
                MotionQueueItem("point_forward", 2200, 1200),      // "指示牌"
                MotionQueueItem("guide", 3600, 1500),              // "工作人员"
                MotionQueueItem("nod", 5300, 600)                   // "乐意帮助"
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.APologetic, 1500, 0.7f),  // 网络问题
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 3000, 0.62f),    // 指示牌
                ExpressionTimelineItem(AvatarExpression.HAPPY, 5500, 0.68f)       // 乐意帮助
            )
        )
    )

    /**
     * 表情评测按钮配置
     */
    data class ExpressionButton(
        val id: String,
        val label: String,
        val expression: AvatarExpression,
        val description: String
    ) {
        fun toPlayAction(): AvatarPlayAction = AvatarPlayAction(
            text = null,
            expression = expression,
            expressionIntensity = 1.0f,
            gesture = AvatarGesture.IDLE,
            motionQueue = emptyList()
        )
    }

    /**
     * 动作评测按钮配置
     */
    data class GestureButton(
        val id: String,
        val label: String,
        val gesture: AvatarGesture,
        val description: String
    ) {
        fun toPlayAction(): AvatarPlayAction = AvatarPlayAction(
            text = null,
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.7f,
            gesture = gesture,
            motionQueue = if (gesture == AvatarGesture.IDLE) {
                emptyList()
            } else {
                listOf(MotionQueueItem(gesture.value, 0, 1600))
            }
        )
    }

    /**
     * 场景评测按钮配置
     */
    data class ScenarioButton(
        val id: String,
        val label: String,
        val category: String,
        val expression: AvatarExpression,
        val gesture: AvatarGesture,
        val action: AvatarPlayAction
    )

    /**
     * 获取表情评测按钮列表（13个）
     */
    fun getExpressionButtons(): List<ExpressionButton> = listOf(
        ExpressionButton("neutral", "中性", AvatarExpression.NEUTRAL, "基础态复位"),
        ExpressionButton("happy", "开心", AvatarExpression.HAPPY, "温暖微笑"),
        ExpressionButton("excited", "兴奋", AvatarExpression.EXCITED, "高能量兴奋"),
        ExpressionButton("welcoming", "欢迎", AvatarExpression.WELCOMING, "亲切迎客"),
        ExpressionButton("approving", "赞许", AvatarExpression.APPROVING, "肯定认可"),
        ExpressionButton("surprised", "惊叹", AvatarExpression.SURPRISED, "惊喜共鸣"),
        ExpressionButton("grateful", "感恩", AvatarExpression.GRATEFUL, "欣慰感谢"),
        ExpressionButton("playful", "俏皮", AvatarExpression.PLAYFUL, "俏皮眨眼"),
        ExpressionButton("thinking", "思考", AvatarExpression.THINKING, "沉思侧目"),
        ExpressionButton("focused", "专注", AvatarExpression.FOCUSED, "聆听关注"),
        ExpressionButton("reverent", "敬畏", AvatarExpression.REVERENT, "庄严敬畏"),
        ExpressionButton("concerned", "关切", AvatarExpression.CONCERNED, "担忧提醒"),
        ExpressionButton("apologetic", "歉意", AvatarExpression.APologetic, "抱歉鞠躬")
    )

    /**
     * 获取动作评测按钮列表（13个）
     */
    fun getGestureButtons(): List<GestureButton> = listOf(
        GestureButton("idle", "待机", AvatarGesture.IDLE, "复位归零"),
        GestureButton("nod", "点头", AvatarGesture.NOD, "头部Y轴联动"),
        GestureButton("shake", "摇头", AvatarGesture.SHAKE, "头部X轴联动"),
        GestureButton("wave", "致意", AvatarGesture.WAVE, "侧首欢迎"),
        GestureButton("welcome_gesture", "欢迎", AvatarGesture.WELCOME_GESTURE, "展臂热情"),
        GestureButton("point_left", "看左", AvatarGesture.POINT_LEFT, "视线引导左"),
        GestureButton("point_right", "看右", AvatarGesture.POINT_RIGHT, "视线引导右"),
        GestureButton("point_forward", "示意", AvatarGesture.POINT_FORWARD, "颔首前方"),
        GestureButton("bow", "欠身", AvatarGesture.BOW, "恭敬致意"),
        GestureButton("thinking_pose", "沉思", AvatarGesture.THINKING_POSE, "仰首思考"),
        GestureButton("guide", "引导", AvatarGesture.GUIDE, "侧身导览"),
        GestureButton("look_up", "仰望", AvatarGesture.LOOK_UP, "抬头看上方"),
        GestureButton("listen", "聆听", AvatarGesture.LISTEN, "侧耳倾听")
    )

    /**
     * 口型测试场景（12个，覆盖容易出错和较难的音素组合）
     *
     * 测试重点：
     * - 爆破音闭气释放：b/p/d/t/g/k
     * - 舌尖前后音区分：z/c/s vs zh/ch/sh
     * - 元音快速过渡：a/o/e/i/u/ü
     * - 鼻音对比：n/ng
     * - 复杂连续音素
     *
     * 注意：口型测试保持常态，无动作和表情变化，专注口型同步精度
     */
    val lipSyncTestScenarios = listOf(
        // L1. 爆破音测试 - b/p/m + 元音快速过渡
        AvatarPlayAction(
            text = "爸爸妈妈抱抱宝贝，跑步爬山不费劲，平平常常的日子，奔向北方的大平原。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L2. 舌尖音测试 - d/t/n/l
        AvatarPlayAction(
            text = "大肚子的弟弟，偷懒的骡子，来来往往的旅客，踏踏实实地走过了大马路。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L3. 舌根音测试 - g/k/h
        AvatarPlayAction(
            text = "高高的高山，宽阔的江河，喝水的黑马，看过的花开过又落。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L4. 舌面音测试 - j/q/x
        AvatarPlayAction(
            text = "姐姐骑车去学校，秋千轻轻摇摆，小青蛙跳下水，星星闪闪亮晶晶。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L5. 舌尖前音测试 - z/c/s
        AvatarPlayAction(
            text = "自从早晨起床，层层叠叠的山峰，丝丝缕缕的云彩，走过了多少次。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L6. 舌尖后音测试 - zh/ch/sh/r
        AvatarPlayAction(
            text = "知了在树上唱，春风吹绿了草地，山上有人山人海，日出东方红似火。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L7. 元音过渡测试 - a/o/e/i/u/ü
        AvatarPlayAction(
            text = "阿姨爱安全，偶尔去欧洲，一五一十地数，鱼儿游来游去，月儿弯弯照九州。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L8. 复杂连续测试 - 多种音素混合
        AvatarPlayAction(
            text = "蓝蓝天上白云飘，白云下面马儿跑，挥动鞭儿响四方，百鸟齐飞翔。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L9. 快速语速测试 - 经典绕口令
        AvatarPlayAction(
            text = "吃葡萄不吐葡萄皮，不吃葡萄倒吐葡萄皮，葡萄皮不吐葡萄皮。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L10. 爆破音极限测试 - 标兵炮兵
        AvatarPlayAction(
            text = "八百标兵奔北坡，炮兵并排北边跑，炮兵怕把标兵碰，标兵怕碰炮兵炮。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L11. 鼻音对比测试 - n/ng
        AvatarPlayAction(
            text = "南方南方有南方人，北方北方有北方人，南方人北方人都是中国人。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        ),
        // L12. 声调口型测试 - 妈马骂麻
        AvatarPlayAction(
            text = "妈妈骑马马慢妈妈骂马，妞妞轰牛牛拧妞妞拧牛，哥哥放羊羊跑哥哥赶羊。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.5f,
            gesture = AvatarGesture.IDLE
        )
    )

    /**
     * 口型测试按钮配置
     */
    data class LipSyncTestButton(
        val id: String,
        val label: String,
        val category: String,
        val description: String,
        val action: AvatarPlayAction
    )

    /**
     * 获取口型测试按钮列表（12个）
     */
    fun getLipSyncTestButtons(): List<LipSyncTestButton> {
        val categories = listOf(
            "爆破音", "舌尖音", "舌根音", "舌面音",
            "舌尖前音", "舌尖后音", "元音过渡", "复杂连续",
            "快速语速", "极限测试", "鼻音对比", "声调测试"
        )
        val labels = listOf(
            "bpm爆破音", "dtnl舌尖音", "gkh舌根音", "jqx舌面音",
            "zcs舌尖前音", "zhchshr舌尖后音", "元音过渡", "混合音素",
            "葡萄皮绕口令", "标兵炮兵极限", "鼻音n/ng对比", "妈马骂麻声调"
        )
        val descriptions = listOf(
            "测试双唇爆破音b/p/m与元音的快速过渡",
            "测试舌尖中音d/t/n/l的清晰度",
            "测试舌根音g/k/h的口型开合",
            "测试舌面音j/q/x的扁嘴口型",
            "测试舌尖前音z/c/s的舌尖位置",
            "测试舌尖后音zh/ch/sh/r的卷舌程度",
            "测试元音a/o/e/i/u/ü之间的平滑过渡",
            "测试多种音素混合连续发音",
            "测试快速语速下的口型同步精度",
            "爆破音极限压力测试",
            "测试鼻音n与ng的口型区分",
            "测试相同音素不同声调的口型差异"
        )

        return lipSyncTestScenarios.mapIndexed { index, action ->
            LipSyncTestButton(
                id = "lipsync_$index",
                label = labels.getOrElse(index) { "口型测试${index + 1}" },
                category = categories.getOrElse(index) { "其他" },
                description = descriptions.getOrElse(index) { "" },
                action = action
            )
        }
    }

    /**
     * 获取场景评测按钮列表（36个，按类别分组）
     */
    fun getScenarioButtons(): List<ScenarioButton> {
        val categories = listOf(
            "入园指引", "入园指引", "入园指引", "入园指引",
            "景点讲解", "景点讲解", "景点讲解", "景点讲解", "景点讲解", "景点讲解",
            "特色体验", "特色体验", "特色体验", "特色体验", "特色体验", "特色体验",
            "路线推荐", "路线推荐", "路线推荐", "路线推荐",
            "导航指引", "导航指引", "导航指引", "导航指引",
            "餐饮购物", "餐饮购物", "餐饮购物", "餐饮购物",
            "安全应急", "安全应急",
            "互动响应", "互动响应",
            "离园兜底", "离园兜底", "离园兜底", "离园兜底"
        )

        val labels = listOf(
            "欢迎入园", "购票指引", "园区概览", "大照壁",
            "大佛壮观", "九龙灌浴", "梵宫艺术", "五印坛城", "祥符禅寺", "曼飞龙塔",
            "抱佛脚", "天下第一掌", "转经祈福", "撞钟祈福", "接圣水", "祈福许愿",
            "历史文化游", "亲子轻松游", "老年无障碍", "最佳拍照点",
            "左侧指路", "右侧指路", "设施指引", "观光车",
            "餐饮推荐", "纪念品", "灵山精舍", "文化演出",
            "安全提醒", "天气预警",
            "语音聆听", "理解确认",
            "感谢告别", "交通指引", "兜底致歉", "网络错误"
        )

        return allScenarios.mapIndexed { index, action ->
            ScenarioButton(
                id = "scenario_$index",
                label = labels.getOrElse(index) { "场景${index + 1}" },
                category = categories.getOrElse(index) { "其他" },
                expression = action.expression,
                gesture = action.gesture,
                action = action
            )
        }
    }

    // ==================== Combo（情感表现单元）====================

    /**
     * Combo 评测按钮配置
     *
     * Combo 是数字人的最短情感表现单元，将表情+动作+短TTS打包为一个独立的情感片段。
     * 时长 1～3 秒，TTS 仅限 ≤4 字感叹词（用于驱动唇形和增强感染力，不描述实物）。
     *
     * 设计流程（强制）：
     * 1. 明确情感目标和使用场景
     * 2. 查阅 .exp3.json 表情参数，逐参数确认视觉效果
     * 3. 编排 expressionTimeline + motionQueue + TTS 音节对齐
     * 4. 设计 emotionTags（5～15 个，口语+书面并重）
     * 5. 自检清单全部通过
     *
     * 大模型集成：LLM 根据用户输入词语/短语的情感色彩 →
     * 按 emotionTags 匹配最合适的 Combo → 返回 combo_id → 客户端播放。
     */
    data class ComboButton(
        val id: String,
        val label: String,
        val emotionCategory: String,
        val emotionTags: List<String>,
        val description: String,
        val action: AvatarPlayAction
    )

    /**
     * 所有 Combo 列表（32个，覆盖导游解说全场景）
     *
     * 分层设计：
     * - 基础 Combo：单动作 + 表情，时长 0.5-1s，高频快速响应
     * - 增强 Combo：2-3动作序列 + 表情时间轴，时长 1.5-2.5s，强调情感表达
     *
     * 设计原则：
     * - TTS ≤4 字感叹词，驱动唇形 + 增强感染力
     * - 动作与音节对齐
     * - emotionTags 5-15 个，口语+书面并重
     * - LLM 语义匹配：用户词语 → emotionTags → combo_id → 播放
     */
    val allCombos = listOf(
        // ==================== 一、喜悦类（3个：2基础+1增强）====================
        // C1: 喜悦-嘻嘻（基础）
        AvatarPlayAction(
            text = "嘻嘻",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.82f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 400),
                MotionQueueItem("nod", 450, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.82f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 700, 0.86f)
            )
        ),
        // C2: 喜悦-真好呀（基础）
        AvatarPlayAction(
            text = "真好呀",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.82f)
            )
        ),
        // C3: 喜悦-太棒了（增强）
        AvatarPlayAction(
            text = "太棒了",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.88f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 450),
                MotionQueueItem("nod", 500, 450),
                MotionQueueItem("guide", 1000, 700)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.EXCITED, 0, 0.88f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.85f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 1200, 0.8f)
            )
        ),

        // ==================== 二、欢迎类（3个：2基础+1增强）====================
        // C4: 欢迎-您好呀（基础）
        AvatarPlayAction(
            text = "您好呀",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem("wave", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.85f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.8f)
            )
        ),
        // C5: 欢迎-这边请（基础）
        AvatarPlayAction(
            text = "这边请",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem("guide", 0, 1000)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 800, 0.75f)
            )
        ),
        // C6: 欢迎-欢迎来玩（增强）
        AvatarPlayAction(
            text = "欢迎来玩",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.9f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem("wave", 0, 600),
                MotionQueueItem("guide", 700, 900),
                MotionQueueItem("nod", 1700, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.9f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 800, 0.85f),
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 1600, 0.82f)
            )
        ),

        // ==================== 三、引导类（4个基础）====================
        // C7: 引导-看这里（基础）
        AvatarPlayAction(
            text = "看这里",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 700, 0.65f)
            )
        ),
        // C8: 引导-往左走（基础）
        AvatarPlayAction(
            text = "往左走",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_LEFT,
            motionQueue = listOf(
                MotionQueueItem("point_left", 0, 900)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 800, 0.68f)
            )
        ),
        // C9: 引导-往右走（基础）
        AvatarPlayAction(
            text = "往右走",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = listOf(
                MotionQueueItem("point_right", 0, 900)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 800, 0.68f)
            )
        ),
        // C10: 引导-往前走（基础）
        AvatarPlayAction(
            text = "往前走",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.65f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem("point_forward", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 0, 0.65f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 700, 0.68f)
            )
        ),

        // ==================== 四、惊叹类（4个：2基础+2增强）====================
        // C11: 惊叹-哇（基础）
        AvatarPlayAction(
            text = "哇",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.88f,
            gesture = AvatarGesture.LOOK_UP,
            motionQueue = listOf(
                MotionQueueItem("look_up", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 0, 0.88f),
                ExpressionTimelineItem(AvatarExpression.EXCITED, 600, 0.82f)
            )
        ),
        // C12: 惊叹-好美啊（基础）
        AvatarPlayAction(
            text = "好美啊",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.82f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 0, 0.82f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.8f)
            )
        ),
        // C13: 惊叹-好壮观（增强）
        AvatarPlayAction(
            text = "好壮观",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.9f,
            gesture = AvatarGesture.LOOK_UP,
            motionQueue = listOf(
                MotionQueueItem("look_up", 0, 900),
                MotionQueueItem("nod", 1000, 500),
                MotionQueueItem("guide", 1600, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.SURPRISED, 0, 0.9f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 800, 0.85f),
                ExpressionTimelineItem(AvatarExpression.EXCITED, 1500, 0.8f)
            )
        ),
        // C14: 惊叹-好神圣（增强）
        AvatarPlayAction(
            text = "好神圣",
            expression = AvatarExpression.REVERENT,
            expressionIntensity = 0.88f,
            gesture = AvatarGesture.LOOK_UP,
            motionQueue = listOf(
                MotionQueueItem("look_up", 0, 600),
                MotionQueueItem("bow", 700, 800),
                MotionQueueItem("nod", 1600, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.REVERENT, 0, 0.88f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 900, 0.85f),
                ExpressionTimelineItem(AvatarExpression.REVERENT, 1500, 0.82f)
            )
        ),

        // ==================== 五、认同类（3个：2基础+1增强）====================
        // C15: 认同-好的（基础）
        AvatarPlayAction(
            text = "好的",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.APPROVING, 400, 0.7f)
            )
        ),
        // C16: 认同-对的呀（基础）
        AvatarPlayAction(
            text = "对的呀",
            expression = AvatarExpression.APPROVING,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APPROVING, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 500, 0.75f)
            )
        ),
        // C17: 认同-当然啦（增强）
        AvatarPlayAction(
            text = "当然啦",
            expression = AvatarExpression.APPROVING,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 400),
                MotionQueueItem("nod", 450, 400),
                MotionQueueItem("guide", 900, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APPROVING, 0, 0.85f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 500, 0.82f),
                ExpressionTimelineItem(AvatarExpression.APPROVING, 1100, 0.78f)
            )
        ),

        // ==================== 六、思考类（2个：1基础+1增强）====================
        // C18: 思考-让我想想（基础）
        AvatarPlayAction(
            text = "让我想想",
            expression = AvatarExpression.THINKING,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.THINKING_POSE,
            motionQueue = listOf(
                MotionQueueItem("thinking_pose", 0, 1200)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.THINKING, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.THINKING, 1000, 0.72f)
            )
        ),
        // C19: 思考-不太确定（增强）
        AvatarPlayAction(
            text = "不太确定",
            expression = AvatarExpression.THINKING,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.THINKING_POSE,
            motionQueue = listOf(
                MotionQueueItem("thinking_pose", 0, 600),
                MotionQueueItem("shake", 700, 500),
                MotionQueueItem("nod", 1300, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.THINKING, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 700, 0.68f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 1300, 0.62f)
            )
        ),

        // ==================== 七、歉意类（2个：1基础+1增强）====================
        // C20: 歉意-抱歉呀（基础）
        AvatarPlayAction(
            text = "抱歉呀",
            expression = AvatarExpression.APologetic,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem("bow", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APologetic, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 700, 0.65f)
            )
        ),
        // C21: 歉意-好遗憾（增强）
        AvatarPlayAction(
            text = "好遗憾",
            expression = AvatarExpression.APologetic,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.SHAKE,
            motionQueue = listOf(
                MotionQueueItem("shake", 0, 500),
                MotionQueueItem("bow", 600, 600),
                MotionQueueItem("nod", 1300, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APologetic, 0, 0.72f),
                ExpressionTimelineItem(AvatarExpression.APologetic, 600, 0.75f),
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 1200, 0.68f)
            )
        ),

        // ==================== 八、关切类（2个：1基础+1增强）====================
        // C22: 关切-小心哦（基础）
        AvatarPlayAction(
            text = "小心哦",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.CONCERNED, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.NEUTRAL, 500, 0.65f)
            )
        ),
        // C23: 关切-别担心（增强）
        AvatarPlayAction(
            text = "别担心",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 300),
                MotionQueueItem("wave", 400, 500),
                MotionQueueItem("nod", 1000, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.HAPPY, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.APPROVING, 400, 0.78f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 1000, 0.72f)
            )
        ),

        // ==================== 九、感恩类（2个：1基础+1增强）====================
        // C24: 感恩-谢谢您（基础）
        AvatarPlayAction(
            text = "谢谢您",
            expression = AvatarExpression.GRATEFUL,
            expressionIntensity = 0.82f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 0, 0.82f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 600, 0.78f)
            )
        ),
        // C25: 感恩-感谢您（增强）
        AvatarPlayAction(
            text = "感谢您",
            expression = AvatarExpression.GRATEFUL,
            expressionIntensity = 0.88f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem("bow", 0, 600),
                MotionQueueItem("bow", 700, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 0, 0.85f),
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 700, 0.88f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 1300, 0.78f)
            )
        ),

        // ==================== 十、告别类（2个：1基础+1增强）====================
        // C26: 告别-再见啦（基础）
        AvatarPlayAction(
            text = "再见啦",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.78f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem("wave", 0, 800)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.78f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 700, 0.75f)
            )
        ),
        // C27: 告别-一路平安（增强）
        AvatarPlayAction(
            text = "一路平安",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem("wave", 0, 600),
                MotionQueueItem("bow", 800, 600)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.WELCOMING, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 700, 0.82f),
                ExpressionTimelineItem(AvatarExpression.GRATEFUL, 1400, 0.78f)
            )
        ),

        // ==================== 十一、俏皮类（3个：2基础+1增强）====================
        // C28: 俏皮-嘿嘿（基础）
        AvatarPlayAction(
            text = "嘿嘿",
            expression = AvatarExpression.PLAYFUL,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 400),
                MotionQueueItem("nod", 450, 400)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 700, 0.72f)
            )
        ),
        // C29: 俏皮-好玩吧（基础）
        AvatarPlayAction(
            text = "好玩吧",
            expression = AvatarExpression.PLAYFUL,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.EXCITED, 500, 0.78f)
            )
        ),
        // C30: 俏皮-猜猜看（增强）
        AvatarPlayAction(
            text = "猜猜看",
            expression = AvatarExpression.PLAYFUL,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 300),
                MotionQueueItem("nod", 350, 300),
                MotionQueueItem("guide", 700, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 0, 0.85f),
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 400, 0.82f),
                ExpressionTimelineItem(AvatarExpression.PLAYFUL, 900, 0.78f)
            )
        ),

        // ==================== 十二、聆听类（2个基础）====================
        // C31: 聆听-我在听（基础）
        AvatarPlayAction(
            text = "我在听",
            expression = AvatarExpression.FOCUSED,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.LISTEN,
            motionQueue = listOf(
                MotionQueueItem("listen", 0, 1000)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.FOCUSED, 0, 0.8f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 900, 0.72f)
            )
        ),
        // C32: 聆听-明白了（基础）
        AvatarPlayAction(
            text = "明白了",
            expression = AvatarExpression.APPROVING,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.NOD,
            motionQueue = listOf(
                MotionQueueItem("nod", 0, 500)
            ),
            expressionTimeline = listOf(
                ExpressionTimelineItem(AvatarExpression.APPROVING, 0, 0.75f),
                ExpressionTimelineItem(AvatarExpression.HAPPY, 500, 0.72f)
            )
        )
    )

    /**
     * 获取 Combo 评测按钮列表（32个）
     */
    fun getComboButtons(): List<ComboButton> {
        val labels = listOf(
            // 喜悦类（3个：2基础+1增强）
            "嘻嘻", "真好呀", "太棒了",
            // 欢迎类（3个：2基础+1增强）
            "您好呀", "这边请", "欢迎来玩",
            // 引导类（4个基础）
            "看这里", "往左走", "往右走", "往前走",
            // 惊叹类（4个：2基础+2增强）
            "哇", "好美啊", "好壮观", "好神圣",
            // 认同类（3个：2基础+1增强）
            "好的", "对的呀", "当然啦",
            // 思考类（2个：1基础+1增强）
            "让我想想", "不太确定",
            // 歉意类（2个：1基础+1增强）
            "抱歉呀", "好遗憾",
            // 关切类（2个：1基础+1增强）
            "小心哦", "别担心",
            // 感恩类（2个：1基础+1增强）
            "谢谢您", "感谢您",
            // 告别类（2个：1基础+1增强）
            "再见啦", "一路平安",
            // 俏皮类（3个：2基础+1增强）
            "嘿嘿", "好玩吧", "猜猜看",
            // 聆听类（2个基础）
            "我在听", "明白了"
        )

        val categories = listOf(
            // 喜悦类
            "喜悦", "喜悦", "喜悦",
            // 欢迎类
            "欢迎", "欢迎", "欢迎",
            // 引导类
            "引导", "引导", "引导", "引导",
            // 惊叹类
            "惊叹", "惊叹", "惊叹", "惊叹",
            // 认同类
            "认同", "认同", "认同",
            // 思考类
            "思考", "疑惑",
            // 歉意类
            "歉意", "歉意",
            // 关切类
            "关切", "关切",
            // 感恩类
            "感恩", "感恩",
            // 告别类
            "告别", "告别",
            // 俏皮类
            "俏皮", "俏皮", "俏皮",
            // 聆听类
            "聆听", "聆听"
        )

        val tags = listOf(
            // 喜悦类
            listOf("哈哈", "嘻嘻", "开心", "笑", "好笑", "有趣", "欢乐", "太美了", "真好", "真棒", "好漂亮", "乐死了"),
            listOf("真好", "好耶", "太好了", "好极了", "棒极了", "舒服", "惬意", "美滋滋"),
            listOf("棒", "太棒", "厉害", "好厉害", "真棒", "绝了", "牛", "赞", "点赞", "超赞", "完美", "精彩"),
            // 欢迎类
            listOf("你好", "您好", "欢迎", "欢迎光临", "您来啦", "见到您", "好久不见", "幸会"),
            listOf("请跟我来", "跟我走", "来这边", "这边走", "往这边", "请往这边", "跟我来"),
            listOf("欢迎来", "来玩呀", "期待您来", "等您来", "欢迎光临", "请来玩"),
            // 引导类
            listOf("看", "您看", "看这里", "往这里看", "注意看", "仔细看", "快看", "看一下"),
            listOf("左", "左边", "往左", "向左", "左手边", "左前方", "左侧"),
            listOf("右", "右边", "往右", "向右", "右手边", "右前方", "右侧"),
            listOf("前", "前面", "往前", "向前", "直走", "一直走", "前方", "直行"),
            // 惊叹类
            listOf("哇", "哇塞", "天哪", "我的天", "太壮观了", "震撼", "惊呆了", "好厉害", "绝了"),
            listOf("美", "好美", "太美", "美极了", "漂亮", "好漂亮", "美丽", "绝美", "超美"),
            listOf("壮观", "宏伟", "雄伟", "大气", "气势磅礴", "震撼人心", "气势恢弘", "恢弘"),
            listOf("神圣", "庄严", "肃穆", "庄重", "令人敬畏", "心生敬意", "圣洁", "崇敬"),
            // 认同类
            listOf("好", "好的", "好呀", "OK", "没问题", "可以", "行", "没问题呀"),
            listOf("对", "没错", "正确", "是的", "对的", "确实", "真的", "就是说嘛"),
            listOf("当然", "必须的", "肯定", "那当然", "毫无疑问", "绝对的", "一定的", "必须"),
            // 思考类
            listOf("想想", "让我想想", "我想一想", "嗯...", "我考虑一下", "思考中", "想一想"),
            listOf("不确定", "不太清楚", "我也不太知道", "有点疑问", "不太明白", "不太懂"),
            // 歉意类
            listOf("抱歉", "对不起", "不好意思", "歉意", "失误了", "我的错", "不好意思啊"),
            listOf("遗憾", "可惜", "好可惜", "太遗憾了", "真遗憾", "有点可惜", "很遗憾"),
            // 关切类
            listOf("小心", "注意", "当心", "小心点", "注意安全", "别摔倒", "慢慢来", "小心哦"),
            listOf("别担心", "放心", "没事的", "不要紧", "别怕", "有我在", "不用担心"),
            // 感恩类
            listOf("谢谢", "感谢", "多谢", "太感谢了", "非常感谢", "谢谢啦", "谢了"),
            listOf("感激", "感恩", "心存感激", "太感谢", "十分感谢", "万分感谢"),
            // 告别类
            listOf("再见", "拜拜", "回见", "下次见", "期待再见", "慢走", "走好"),
            listOf("一路平安", "旅途愉快", "注意安全", "保重", "一路顺风", "平安"),
            // 俏皮类
            listOf("嘿嘿", "嘻嘻", "呵呵", "悄悄的", "小声说", "神秘兮兮"),
            listOf("好玩", "有趣", "挺好玩", "蛮有趣的", "趣味十足", "很有意思", "好玩儿"),
            listOf("猜", "猜猜", "你猜", "猜猜看", "想知道吗", "来猜猜", "猜一猜"),
            // 聆听类
            listOf("您说", "请说", "我听着呢", "在听", "请继续说", "您讲", "请讲"),
            listOf("明白", "了解", "懂了", "知道了", "清楚了", "我懂了", "理解了")
        )

        val descriptions = listOf(
            // 喜悦类
            "半眯眼嘻嘻笑 + 双点头节奏，用于与游客开心互动、回应赞美",
            "温和微笑 + 点头，表达满意和愉悦之情",
            "兴奋表情 + 双点头 + 展开引导，强烈表达赞赏和兴奋（增强）",
            // 欢迎类
            "热情欢迎表情 + 侧首致意，用于初次见面或打招呼",
            "引导动作 + 欢迎表情，用于引导游客前往某处",
            "招手 + 引导 + 点头，热情迎接游客到来（增强）",
            // 引导类
            "示意动作 + 开心表情，引导游客注意某处",
            "向左示意 + 中性表情，引导游客向左行进",
            "向右示意 + 中性表情，引导游客向右行进",
            "向前示意 + 中性表情，引导游客向前行进",
            // 惊叹类
            "仰望动作 + 惊叹表情，表达震撼和惊叹",
            "点头 + 惊叹表情，表达对美景的赞叹",
            "仰望 + 点头 + 引导，表达对壮观景色的震撼（增强）",
            "仰望 + 鞠躬 + 点头，表达对神圣氛围的敬畏（增强）",
            // 认同类
            "点头 + 开心表情，表达同意和接受",
            "点头 + 赞许表情，表达认同和肯定",
            "双点头 + 引导展开，表达强烈的认同（增强）",
            // 思考类
            "思考姿态 + 思考表情，表达正在思考",
            "思考 + 摇头 + 点头，表达不确定或疑惑（增强）",
            // 歉意类
            "欠身 + 歉意表情，表达歉意",
            "摇头 + 鞠躬 + 点头，表达遗憾之情（增强）",
            // 关切类
            "点头 + 关切表情，提醒游客注意安全",
            "点头 + 挥手安抚，安抚游客情绪（增强）",
            // 感恩类
            "点头 + 感恩表情，表达感谢",
            "双鞠躬 + 感恩表情，表达深深的感激（增强）",
            // 告别类
            "侧首致意 + 欢迎表情，道别并期待再见",
            "挥手 + 鞠躬 + 祝福表情，祝福游客一路平安（增强）",
            // 俏皮类
            "双点头 + 俏皮表情，调皮可爱的回应",
            "点头 + 俏皮表情，表达有趣好玩",
            "双点头 + 引导 + 俏皮表情，神秘地引导猜测（增强）",
            // 聆听类
            "聆听姿态 + 专注表情，表达正在认真聆听",
            "点头 + 赞许表情，表达理解和确认"
        )

        return allCombos.mapIndexed { index, action ->
            ComboButton(
                id = "combo_$index",
                label = labels.getOrElse(index) { "Combo${index + 1}" },
                emotionCategory = categories.getOrElse(index) { "其他" },
                emotionTags = tags.getOrElse(index) { emptyList() },
                description = descriptions.getOrElse(index) { "" },
                action = action
            )
        }
    }
}
