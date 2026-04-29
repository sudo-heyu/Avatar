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
 */
object TestAvatarActions {

    /**
     * 所有联动测试场景
     */
    val allScenarios = listOf(
        // 1. 欢迎问候：欢迎表情 + 挥手 + 开场语音
        AvatarPlayAction(
            text = "您好，欢迎来到灵山胜境，我是您的数字导游。您可以问我景点讲解、路线推荐，或者附近服务。",
            expression = AvatarExpression.WELCOMING,
            expressionIntensity = 0.85f,
            gesture = AvatarGesture.WAVE,
            motionQueue = listOf(
                MotionQueueItem(type = "wave", startOffsetMs = 0, durationMs = 1200)
            )
        ),

        // 2. 景点讲解：兴奋表情 + 前方指引 + 大开口/圆唇口型覆盖
        AvatarPlayAction(
            text = "灵山大佛高八十八米，整体气势宏伟。站在广场正前方仰望，会感受到非常开阔而庄严的视觉震撼。",
            expression = AvatarExpression.EXCITED,
            expressionIntensity = 0.72f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem(type = "point_forward", startOffsetMs = 0, durationMs = 1600)
            )
        ),

        // 3. 左侧指路：中性表情 + 左侧动作 + 短句口型
        AvatarPlayAction(
            text = "请看左前方，祥符禅寺就在那条步道尽头，步行大约两分钟就能到达。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_LEFT,
            motionQueue = listOf(
                MotionQueueItem(type = "point_left", startOffsetMs = 0, durationMs = 1500)
            )
        ),

        // 4. 右侧指路：中性表情 + 右侧动作 + 停顿口型
        AvatarPlayAction(
            text = "您右前方是九龙灌浴表演区。整点时段会有水景表演，建议提前几分钟过去。",
            expression = AvatarExpression.NEUTRAL,
            expressionIntensity = 0.6f,
            gesture = AvatarGesture.POINT_RIGHT,
            motionQueue = listOf(
                MotionQueueItem(type = "point_right", startOffsetMs = 0, durationMs = 1500)
            )
        ),

        // 5. 路线推荐：开心表情 + 导览/前方指引组合 + 长句口型
        AvatarPlayAction(
            text = "如果您有半天时间，建议先看九龙灌浴，再前往灵山大佛，最后到梵宫参观，这样路线更顺，也不会来回折返。",
            expression = AvatarExpression.HAPPY,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.GUIDE,
            motionQueue = listOf(
                MotionQueueItem(type = "guide", startOffsetMs = 0, durationMs = 1600),
                MotionQueueItem(type = "point_forward", startOffsetMs = 1800, durationMs = 1800)
            )
        ),

        // 6. 安全提醒：关切表情 + 前方提示 + 慢节奏口型
        AvatarPlayAction(
            text = "前方台阶比较多，请您放慢脚步，注意脚下安全。雨天路面可能湿滑，扶好栏杆会更稳。",
            expression = AvatarExpression.CONCERNED,
            expressionIntensity = 0.7f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem(type = "point_forward", startOffsetMs = 0, durationMs = 1400)
            )
        ),

        // 7. 无法回答/兜底：歉意表情 + 鞠躬 + 闭口/停顿检查
        AvatarPlayAction(
            text = "抱歉，这个问题我暂时无法准确回答。我可以继续为您提供景区导览、景点讲解和路线推荐。",
            expression = AvatarExpression.APologetic,
            expressionIntensity = 0.75f,
            gesture = AvatarGesture.BOW,
            motionQueue = listOf(
                MotionQueueItem(type = "bow", startOffsetMs = 0, durationMs = 1300)
            )
        ),

        // 8. 惊喜介绍：惊讶表情 + 指向前方 + 高能语音口型
        // 注意：SURPRISED 表情本身含 AngleY=-9（后仰），若配 NOD（AngleY=-10 低头）会冲突，故改用 POINT_FORWARD
        AvatarPlayAction(
            text = "太好了！梵宫是灵山胜境最令人震撼的建筑之一，内部装饰非常精美，抬头看穹顶会特别惊艳。",
            expression = AvatarExpression.SURPRISED,
            expressionIntensity = 0.8f,
            gesture = AvatarGesture.POINT_FORWARD,
            motionQueue = listOf(
                MotionQueueItem(type = "point_forward", startOffsetMs = 0, durationMs = 1600)
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
                listOf(MotionQueueItem(type = gesture.value, startOffsetMs = 0, durationMs = 1600))
            }
        )
    }

    /**
     * 场景评测按钮配置：表情 + 动作 + 语音口型同时播放
     */
    data class ScenarioButton(
        val id: String,
        val label: String,
        val expressionLabel: String,
        val gestureLabel: String,
        val lipSyncLabel: String,
        val description: String,
        val action: AvatarPlayAction
    )

    /**
     * 获取表情评测按钮列表（8个纯表情，用于评测 exp3.json 效果）
     */
    fun getExpressionButtons(): List<ExpressionButton> = listOf(
        ExpressionButton(
            id = "neutral",
            label = "中性",
            expression = AvatarExpression.NEUTRAL,
            description = "Expression 层复位，不触发动作和口型"
        ),
        ExpressionButton(
            id = "happy",
            label = "开心",
            expression = AvatarExpression.HAPPY,
            description = "验证眼睛、眉毛和嘴形的开心叠加"
        ),
        ExpressionButton(
            id = "thinking",
            label = "思考",
            expression = AvatarExpression.THINKING,
            description = "验证思考表情与头部微姿态"
        ),
        ExpressionButton(
            id = "surprised",
            label = "惊讶",
            expression = AvatarExpression.SURPRISED,
            description = "验证惊讶眼型和嘴形，不启用 LipSync"
        ),
        ExpressionButton(
            id = "excited",
            label = "兴奋",
            expression = AvatarExpression.EXCITED,
            description = "验证兴奋表情强度和脸部变化"
        ),
        ExpressionButton(
            id = "concerned",
            label = "关切",
            expression = AvatarExpression.CONCERNED,
            description = "验证关切、担心类面部状态"
        ),
        ExpressionButton(
            id = "apologetic",
            label = "抱歉",
            expression = AvatarExpression.APologetic,
            description = "验证歉意表情，不触发欠身动作"
        ),
        ExpressionButton(
            id = "welcoming",
            label = "欢迎",
            expression = AvatarExpression.WELCOMING,
            description = "验证欢迎表情，不触发挥手动作"
        )
    )

    /**
     * 获取动作评测按钮列表
     */
    fun getGestureButtons(): List<GestureButton> = listOf(
        GestureButton(
            id = "idle",
            label = "待机",
            gesture = AvatarGesture.IDLE,
            description = "Gesture 层复位，身体、肩膀、手臂归零"
        ),
        GestureButton(
            id = "nod",
            label = "点头",
            gesture = AvatarGesture.NOD,
            description = "验证头部 Y 轴和肩膀联动"
        ),
        GestureButton(
            id = "shake",
            label = "摇头",
            gesture = AvatarGesture.SHAKE,
            description = "验证头部 X 轴和肩膀联动"
        ),
        GestureButton(
            id = "wave",
            label = "挥手",
            gesture = AvatarGesture.WAVE,
            description = "验证身体转向、手臂和手部参数"
        ),
        GestureButton(
            id = "point_left",
            label = "指左",
            gesture = AvatarGesture.POINT_LEFT,
            description = "验证左侧指引动作"
        ),
        GestureButton(
            id = "point_right",
            label = "指右",
            gesture = AvatarGesture.POINT_RIGHT,
            description = "验证右侧指引动作"
        ),
        GestureButton(
            id = "point_forward",
            label = "指前",
            gesture = AvatarGesture.POINT_FORWARD,
            description = "验证双臂前方提示动作"
        ),
        GestureButton(
            id = "bow",
            label = "欠身",
            gesture = AvatarGesture.BOW,
            description = "上半身模式下的欠身致意：肩膀微怂 + 手臂内收，不额外低头"
        ),
        GestureButton(
            id = "thinking_pose",
            label = "思考",
            gesture = AvatarGesture.THINKING_POSE,
            description = "验证手臂和手部的思考姿态"
        ),
        GestureButton(
            id = "guide",
            label = "导览",
            gesture = AvatarGesture.GUIDE,
            description = "验证导览手势和身体朝向"
        )
    )

    /**
     * 获取场景评测按钮列表
     */
    fun getScenarioButtons(): List<ScenarioButton> {
        val labels = listOf("欢迎问候", "景点讲解", "左侧指路", "右侧指路", "路线推荐", "安全提醒", "兜底致歉", "惊喜介绍")
        val expressionLabels = listOf("欢迎", "兴奋", "中性", "中性", "开心", "关切", "歉意", "惊讶")
        val gestureLabels = listOf("挥手", "指前", "指左", "指右", "导览+指前", "指前", "欠身", "指前")
        val lipSyncLabels = listOf(
            "开场短句",
            "开口/圆唇",
            "方向短句",
            "停顿短句",
            "长句连续口型",
            "慢节奏提醒",
            "停顿闭口",
            "高能语调"
        )

        return allScenarios.mapIndexed { index, action ->
            val expression = expressionLabels.getOrElse(index) { action.expression.value }
            val gesture = gestureLabels.getOrElse(index) { action.gesture.value }
            val lipSync = lipSyncLabels.getOrElse(index) { "语音口型" }
            ScenarioButton(
                id = "scenario_$index",
                label = labels.getOrElse(index) { "场景 ${index + 1}" },
                expressionLabel = expression,
                gestureLabel = gesture,
                lipSyncLabel = lipSync,
                description = "表情：$expression / 动作：$gesture / 口型：$lipSync",
                action = action
            )
        }
    }
}
