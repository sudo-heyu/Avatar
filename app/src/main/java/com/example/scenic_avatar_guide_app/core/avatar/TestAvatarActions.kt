package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.MotionQueueItem

/**
 * 测试动作数据
 * 基于 docs/architecture/AVATAR_TEST_DATA.md 定义
 */
object TestAvatarActions {

    /**
     * 所有测试场景
     */
    val allScenarios = listOf(
        // 1. 欢迎问候
        AvatarPlayAction(
            text = "您好，欢迎来到灵山胜境，我是您的数字导游。请问您想先了解景点讲解、路线推荐，还是语音导览？",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem(type = "wave", startOffsetMs = 0, durationMs = 1200)
            )
        ),

        // 2. 景点讲解
        AvatarPlayAction(
            text = "灵山大佛是灵山胜境的核心地标，佛像高八十八米，整体气势宏伟，是游客最常参观的讲解点之一。",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = emptyList()
        ),

        // 3. 左侧指路
        AvatarPlayAction(
            text = "您左前方是祥符禅寺，沿着当前步道继续前行大约两分钟即可到达。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_LEFT,
            motionQueue = emptyList()
        ),

        // 4. 右侧指路
        AvatarPlayAction(
            text = "您右前方是九龙灌浴表演区，每天整点会有精彩的水景表演。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = emptyList()
        ),

        // 5. 路线推荐
        AvatarPlayAction(
            text = "如果您有半天时间，建议先参观九龙灌浴，再前往灵山大佛，最后到梵宫完成整条核心游览路线。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem(type = "guide", startOffsetMs = 0, durationMs = 1600),
                MotionQueueItem(type = "point_forward", startOffsetMs = 1800, durationMs = 1800)
            )
        ),

        // 6. 安全提醒
        AvatarPlayAction(
            text = "前方台阶较多，请您注意脚下安全，雨天路面可能会有些湿滑。",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = emptyList()
        ),

        // 7. 无法回答/兜底
        AvatarPlayAction(
            text = "抱歉，我目前主要提供景区导览、景点讲解和路线推荐，暂时无法回答这个问题。",
            expression = AvatarExpression.APologetic,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.BOW,
            motionQueue = emptyList()
        ),

        // 8. 惊喜介绍
        AvatarPlayAction(
            text = "太好了！梵宫是灵山胜境最令人震撼的建筑之一，内部装饰金碧辉煌，非常值得一看！",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.NOD,
            motionQueue = emptyList()
        )
    )

    /**
     * 按钮配置
     */
    data class ActionButton(
        val id: String,
        val label: String,
        val icon: String,
        val description: String,
        val action: AvatarPlayAction
    )

    /**
     * 获取按钮列表
     */
    fun getButtons(): List<ActionButton> = listOf(
        ActionButton(
            id = "welcome",
            label = "欢迎",
            icon = "👋",
            description = "开场播报与挥手",
            action = allScenarios[0]
        ),
        ActionButton(
            id = "intro",
            label = "讲解",
            icon = "👉",
            description = "景点讲解",
            action = allScenarios[1]
        ),
        ActionButton(
            id = "left",
            label = "指左",
            icon = "👈",
            description = "左侧指路",
            action = allScenarios[2]
        ),
        ActionButton(
            id = "right",
            label = "指右",
            icon = "👉",
            description = "右侧指路",
            action = allScenarios[3]
        ),
        ActionButton(
            id = "route",
            label = "路线",
            icon = "🗺️",
            description = "路线推荐",
            action = allScenarios[4]
        ),
        ActionButton(
            id = "warning",
            label = "提醒",
            icon = "⚠️",
            description = "安全提醒",
            action = allScenarios[5]
        ),
        ActionButton(
            id = "sorry",
            label = "抱歉",
            icon = "🙇",
            description = "无法回答",
            action = allScenarios[6]
        ),
        ActionButton(
            id = "surprise",
            label = "惊喜",
            icon = "🎉",
            description = "惊喜介绍",
            action = allScenarios[7]
        )
    )

    /**
     * 单独的动作测试（无语音）
     */
    fun getGestureButtons(): List<Pair<String, AvatarGesture>> = listOf(
        "挥手" to AvatarGesture.WAVE,
        "点头" to AvatarGesture.NOD,
        "摇头" to AvatarGesture.SHAKE,
        "指左" to AvatarGesture.POINT_LEFT,
        "指右" to AvatarGesture.POINT_RIGHT,
        "指前" to AvatarGesture.POINT_FORWARD,
        "鞠躬" to AvatarGesture.BOW,
        "思考" to AvatarGesture.THINKING_POSE,
        "引导" to AvatarGesture.GUIDE
    )

    /**
     * 单独的表情测试
     */
    fun getExpressionButtons(): List<Pair<String, AvatarExpression>> = listOf(
        "中性" to AvatarExpression.NEUTRAL,
        "开心" to AvatarExpression.HAPPY,
        "思考" to AvatarExpression.THINKING,
        "惊讶" to AvatarExpression.SURPRISED,
        "兴奋" to AvatarExpression.EXCITED,
        "关切" to AvatarExpression.CONCERNED,
        "抱歉" to AvatarExpression.APologetic,
        "欢迎" to AvatarExpression.WELCOMING
    )
}
