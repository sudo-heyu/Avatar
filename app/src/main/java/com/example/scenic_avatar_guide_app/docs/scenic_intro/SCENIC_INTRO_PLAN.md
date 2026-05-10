# 辛亥革命景区介绍 — 排版渲染计划

## 一、数据源结构

### 1.1 静态 JSON 文件位置
```
app/src/main/assets/scenic_intro/
├── index.json                          # 景区索引（下拉列表数据源）
├── xinhai_museum.json                  # 辛亥革命博物馆（主 demo）
├── xiangjingyu_cemetery.json           # 向警予烈士陵园
├── shiyang_cemetery.json               # 施洋烈士陵园
├── wuhan_erqi_memorial.json            # 武汉二七纪念馆
├── zhonggong_hankou_memorial.json      # 武汉中共中央机关旧址纪念馆
├── wuhan_antiwar_memorial.json         # 武汉抗战纪念园
├── balujun_wuhan_office.json           # 八路军武汉办事处旧址
├── hankou_n4a_hq.json                  # 汉口新四军军部旧址
├── mao_zedong_house.json               # 毛泽东故居陈列馆
├── jinghan_railway_union.json          # 京汉铁路总工会旧址
├── hankou_allchina_union.json          # 汉口中华全国总工会旧址
├── ccp_leaders_hankou.json             # 中共中央领导人汉口住地旧址
├── qiyimen.json                        # 起义门
├── zhongshan_warship.json              # 中山舰博物馆
├── heshengqiao_cemetery.json           # 贺胜桥北伐阵亡将士陵园
└── images/                             # 图片资源
    └── xinhai/
        ├── hero_group.png              # 思政课活动合影（顶部大图）
        ├── lecture_scene.png           # 讲解员课堂授课场景
        ├── qishu.png                   # 林觉民《与妻书》讲解
        ├── lecture_red.png             # 红色思政课讲解场景
        └── students.png                # 学生聆听场景
```

### 1.2 图片命名规范
- 使用英文蛇形命名：`hero_group.png`, `lecture_scene.png`
- 统一引用格式：`file:///android_asset/scenic_intro/images/xinhai/<filename>`
- 图注格式：`图：<场景描述>｜摄影：<来源>`（如 `图：思政课活动合影｜来源：辛亥革命博物院`）

---

## 二、数据模型定义

### 2.1 核心数据结构（Kotlin）
```kotlin
@Serializable
data class ScenicIntroContent(
    val scenicId: String,                    // 景区唯一标识
    val scenicName: String,                  // 景区名称
    val subtitle: String? = null,            // 副标题/定位语
    val heroImage: HeroImage? = null,        // 顶部沉浸式大图
    val highlights: List<String>? = null,    // 亮点标签（如 "4A景区"、"88米大佛"）
    val sections: List<ContentSection>       // 内容区块列表
)

@Serializable
data class HeroImage(
    val url: String,
    val caption: String? = null,
    val photographer: String? = null
)

@Serializable
data class ContentSection(
    val id: String,
    val layout: SectionLayout,             // 布局枚举
    val title: String? = null,               // 区块标题
    val subtitle: String? = null,            // 区块副标题
    val items: List<ContentItem>
)

@Serializable
enum class SectionLayout {
    TEXT_ONLY,                               // 纯文字段落
    IMAGE_FULL,                              // 全宽大图 + 底部图注
    IMAGE_GALLERY,                           // 图片画廊（2-3 张并排）
    TEXT_IMAGE_RIGHT,                        // 文字在左，图片在右
    TEXT_IMAGE_LEFT,                         // 图片在左，文字在右
    QUOTE,                                   // 引用/金句样式（居中、斜体、背景装饰）
    HIGHLIGHTS,                              // 亮点卡片（横向滚动或 Grid）
    TIMELINE,                                // 时间线（纵向或横向）
    SPOTS_GRID,                              // 景点/子项网格卡片
    STATISTICS                               // 数据统计（大数字 + 描述）
}

@Serializable
data class ContentItem(
    val type: ContentType,
    val text: String? = null,
    val imageUrl: String? = null,
    val imageCaption: String? = null,
    val highlights: List<String>? = null,
    val timelineEvents: List<TimelineEvent>? = null,
    val spots: List<SpotCard>? = null,
    val statisticValue: String? = null,      // 统计数字
    val statisticLabel: String? = null     // 统计标签
)

@Serializable
enum class ContentType {
    PARAGRAPH,      // 段落文字
    HEADING,        // 小标题
    IMAGE,          // 单张图片
    QUOTE,          // 引用/金句
    LIST,           // 列表项
    HIGHLIGHTS,     // 亮点标签
    TIMELINE,       // 时间线
    SPOTS,          // 景点卡片
    STATISTIC       // 统计数字
}

@Serializable
data class TimelineEvent(
    val year: String,
    val title: String,
    val description: String
)

@Serializable
data class SpotCard(
    val name: String,
    val imageUrl: String? = null,
    val description: String,
    val tags: List<String>? = null
)
```

---

## 三、UI 渲染组件架构

### 3.1 组件层级
```
ui/components/scenic/
├── ScenicIntroScreen.kt           # 主页面（Scaffold + LazyColumn）
├── ScenicSelector.kt              # 顶部景区选择器（ExposedDropdownMenu）
├── ScenicIntroViewModel.kt        # ViewModel（加载 assets JSON + 管理选中状态）
└── sections/
    ├── HeroImageSection.kt        # 顶部沉浸式大图（视差滚动 + 渐变遮罩 + 标题叠加）
    ├── TextSection.kt             # 纯文字段落
    ├── ImageFullSection.kt        # 全宽大图 + 底部图注
    ├── ImageGallerySection.kt     # 图片画廊（横向滚动或 Grid）
    ├── TextImageSection.kt        # 图文混排（左/右布局）
    ├── QuoteSection.kt            # 引用样式（居中、斜体、装饰引号、背景色）
    ├── HighlightsSection.kt       # 亮点卡片（横向滚动的 Chip 或 Grid）
    ├── TimelineSection.kt         # 时间线（纵向：左侧年份 + 右侧内容）
    ├── SpotsGridSection.kt        # 景点网格（2列卡片，图片 + 名称 + 标签）
    └── StatisticsSection.kt       # 数据统计（大数字 + 小标签，横向排列）
```

### 3.2 各组件渲染规范

#### HeroImageSection（顶部大图）
- 图片填满宽度，高度固定 240dp
- 底部渐变遮罩：`Brush.verticalGradient(Transparent → Black 60%)`
- 标题叠加在遮罩上方，白色，居中偏下
- 副标题较小，半透明
- 支持视差效果（图片向上偏移随滚动）

#### TextSection（文字段落）
- 标题：18sp，加粗，`#1D7A6D`（主色）
- 副标题：14sp，`#5A6772`（次文字）
- 段落：16sp，`#1C2328`，行高 1.6
- 内边距：16dp

#### ImageFullSection（全宽大图）
- 圆角 12dp
- 图注：12sp，`#5A6772`，居左，与图片间距 8dp
- 阴影：elevation 2dp

#### ImageGallerySection（画廊）
- 横向滚动 LazyRow
- 每张图片宽度 160dp，高度 120dp，圆角 8dp
- 间距 8dp
- 底部可选图注

#### QuoteSection（引用）
- 背景：`#F2A541` 10% 透明度 或 `#1D7A6D` 5% 透明度
- 圆角 16dp
- 文字居中，斜体，18sp，`#1C2328`
- 顶部装饰大号引号 `"` 或引用图标
- 来源居右，14sp，`#5A6772`

#### HighlightsSection（亮点卡片）
- 横向滚动 LazyRow 或 Wrap Flow
- 每个亮点：圆角卡片，主色背景或浅色背景 + 主色文字
- 图标 + 文字（如 "🌟 4A景区"）
- 卡片高度 80dp，宽度自适应

#### TimelineSection（时间线）
- 纵向布局
- 左侧：年份（14sp，主色，加粗）
- 中间：竖线 + 圆点（主色）
- 右侧：标题（16sp，加粗）+ 描述（14sp，次文字）
- 间距 16dp

#### SpotsGridSection（景点网格）
- 2 列 Grid
- 卡片圆角 12dp，阴影 elevation 2dp
- 图片宽高比 4:3，顶部圆角裁切
- 名称：16sp，加粗，居中
- 标签：小 Chip，主色背景

#### StatisticsSection（数据统计）
- 横向等分排列
- 数字：32sp，加粗，主色
- 标签：12sp，次文字
- 分隔线或间距区分

---

## 四、配色与主题规范

```kotlin
object ScenicIntroColors {
    val Primary      = Color(0xFF1D7A6D)   // 山湖青绿（标题、强调、时间线）
    val Secondary    = Color(0xFFF2A541)   // 暖阳橙（亮点、引用背景、按钮）
    val Accent       = Color(0xFF2B59C3)   // 强调蓝（链接、交互）
    val Error        = Color(0xFFC44536)   // 错误红
    val Background   = Color(0xFFF8FAF9)   // 页面背景
    val Surface      = Color(0xFFFFFFFF)   // 卡片背景
    val TextPrimary  = Color(0xFF1C2328)   // 主文字
    val TextSecondary= Color(0xFF5A6772)   // 次文字/图注
}
```

---

## 五、交互规范

1. **下拉选择器**：点击顶部标题栏右侧下拉箭头，弹出 `ExposedDropdownMenu`，展示 15 个景区列表。
2. **切换动画**：切换景区时，LazyColumn 淡出（200ms）→ 加载新数据 → 淡入（300ms）。
3. **图片点击**：全宽大图和画廊图片支持点击放大（FullScreenImageDialog）。
4. **景点卡片点击**：SpotCard 点击后跳转对应景区详情（预留 NavHost 路由）。
5. **视差滚动**：HeroImage 随列表滚动产生视差位移（图片向上移动速度 < 列表滚动速度）。

---

## 六、ViewModel 与 Repository 设计

### 6.1 ScenicIntroViewModel
```kotlin
@HiltViewModel
class ScenicIntroViewModel @Inject constructor(
    private val repository: ScenicIntroRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ScenicIntroContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<ScenicIntroContent>> = _uiState.asStateFlow()

    private val _selectedScenicId = MutableStateFlow("xinhai_museum")
    val selectedScenicId: StateFlow<String> = _selectedScenicId.asStateFlow()

    fun loadScenicIntro(scenicId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val content = repository.loadScenicIntro(scenicId)
                _uiState.value = UiState.Success(content)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun selectScenic(scenicId: String) {
        _selectedScenicId.value = scenicId
        loadScenicIntro(scenicId)
    }
}
```

### 6.2 ScenicIntroRepository
```kotlin
class ScenicIntroRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun loadScenicIntro(scenicId: String): ScenicIntroContent {
        return withContext(Dispatchers.IO) {
            val json = context.assets
                .open("scenic_intro/${scenicId}.json")
                .bufferedReader()
                .use { it.readText() }
            Json.decodeFromString(ScenicIntroContent.serializer(), json)
        }
    }

    suspend fun loadScenicIndex(): ScenicIndex {
        return withContext(Dispatchers.IO) {
            val json = context.assets
                .open("scenic_intro/index.json")
                .bufferedReader()
                .use { it.readText() }
            Json.decodeFromString(ScenicIndex.serializer(), json)
        }
    }
}
```

---

## 七、加载性能优化

1. **JSON 预加载**：App 启动时在后台线程预读取 `index.json`。
2. **图片懒加载**：使用 Coil `AsyncImage` + `Crossfade`，仅可见区域加载。
3. **JSON 缓存**：首次解析后缓存在内存 Map（`scenicId → ScenicIntroContent`）。
4. **避免主线程 IO**：Repository 使用 `Dispatchers.IO`。

---

## 八、当前 demo 内容映射

| 原文案/图片 | JSON Section 映射 | Layout |
|---|---|---|
| 图1：活动合影 | HeroImage | heroImage 字段 |
| 图2：讲解员授课 | 第二部分「思政课堂」图片 | IMAGE_FULL |
| 图3：林觉民《与妻书》 | 第三部分「文物故事」图片 | IMAGE_FULL |
| 图4：红色讲解 | 第四部分「革命历史」图片 | IMAGE_FULL |
| 图5：学生聆听 | 画廊/结尾 | IMAGE_GALLERY |
| "信仰的力量——我们的思政课" 品牌介绍 | 文字段落 | TEXT_ONLY |
| 走进南京大学活动背景 | 文字段落 | TEXT_ONLY |
| 课程已走进多所高校数据 | 数据统计 | STATISTICS |
| "从首义之区再启新程" | 金句引用 | QUOTE |
| 林觉民《与妻书》 | 引用/金句 | QUOTE |

---

## 九、实现顺序建议

1. **Step 1**：定义数据模型（`domain/model/ScenicContent.kt`）
2. **Step 2**：创建 Repository + ViewModel
3. **Step 3**：实现 `ScenicIntroScreen` 骨架（Scaffold + 选择器 + LazyColumn）
4. **Step 4**：逐个实现 Section 组件（从 HeroImageSection 和 TextSection 开始）
5. **Step 5**：填充示例 JSON（`xinhai_museum.json`）并验证渲染
6. **Step 6**：添加动画（淡入淡出、视差、卡片浮现）
7. **Step 7**：扩展到全部 15 个景区 JSON
