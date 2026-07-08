package com.example.scenic_avatar_guide_app.ui.components.scenicintro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.domain.model.*
import com.example.scenic_avatar_guide_app.ui.screens.ScenicIntroViewModel
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimary
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryBg
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryDark
import com.example.scenic_avatar_guide_app.ui.theme.ScenicPrimaryDeep

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicIntroScreen(
    modifier: Modifier = Modifier,
    viewModel: ScenicIntroViewModel = hiltViewModel(),
    showScenicSelector: Boolean = true,
    showTopBar: Boolean = true
) {
    val introState by viewModel.introState.collectAsState()
    val indexState by viewModel.indexState.collectAsState()
    val selectedScenicId by viewModel.selectedScenicId.collectAsState()

    val indexItems = when (val state = indexState) {
        is UiState.Success -> state.data.scenics
        else -> emptyList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScenicPrimaryBg,
        contentWindowInsets = if (showTopBar) ScaffoldDefaults.contentWindowInsets else WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "景区导览",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C2328)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ScenicPrimaryBg
                    ),
                    actions = {
                        if (showScenicSelector && indexItems.isNotEmpty()) {
                            ScenicSelectorChip(
                                indexItems = indexItems,
                                selectedScenicId = selectedScenicId,
                                onScenicSelected = viewModel::selectScenic,
                                modifier = Modifier.padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when (val state = introState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ScenicPrimary)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = Color(0xFFC44536),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadScenicIntro(selectedScenicId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScenicPrimaryDark
                            )
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val content = state.data

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 24.dp
                    )
                ) {
                    content.heroImage?.let { hero ->
                        item {
                            HeroImageSection(heroImage = hero, scenicName = content.scenicName, subtitle = content.subtitle)
                        }
                    }

                    content.highlights?.let { highlights ->
                        if (highlights.isNotEmpty()) {
                            item {
                                HighlightsSection(highlights = highlights)
                            }
                        }
                    }

                    items(content.sections) { section ->
                        SectionRenderer(section = section)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicSelectorChip(
    indexItems: List<ScenicIndexItem>,
    selectedScenicId: String,
    onScenicSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = indexItems.find { it.scenicId == selectedScenicId }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 116.dp, max = 184.dp)
                    .height(34.dp)
                    .menuAnchor()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.78f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedItem?.name ?: "选择景区",
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 12.sp,
                        color = Color(0xFF5A6772),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ScenicPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                indexItems.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item.name,
                                fontSize = 14.sp,
                                color = Color(0xFF1C2328),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onScenicSelected(item.scenicId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ==================== Section Renderer ====================

@Composable
fun SectionRenderer(section: ContentSection) {
    when (section.id) {
        "route" -> {
            RouteRecommendationSection(section = section)
            return
        }
        "tips" -> {
            TravelTipsSection(section = section)
            return
        }
    }

    when (section.layout) {
        SectionLayout.TEXT_ONLY -> TextSection(section = section)
        SectionLayout.IMAGE_FULL -> ImageFullSection(section = section)
        SectionLayout.IMAGE_GALLERY -> ImageGallerySection(section = section)
        SectionLayout.TEXT_IMAGE_RIGHT -> TextImageSection(section = section, imageOnRight = true)
        SectionLayout.TEXT_IMAGE_LEFT -> TextImageSection(section = section, imageOnRight = false)
        SectionLayout.QUOTE -> QuoteSection(section = section)
        SectionLayout.HIGHLIGHTS -> HighlightsStatisticSection(section = section)
        SectionLayout.TIMELINE -> TimelineSection(section = section)
        SectionLayout.SPOTS_GRID -> SpotsGridSection(section = section)
        SectionLayout.STATISTICS -> StatisticsSection(section = section)
    }
}

// ==================== Hero Image ====================

@Composable
fun HeroImageSection(
    heroImage: HeroImage,
    scenicName: String,
    subtitle: String?
) {
    val showFullImage = scenicName == "灵山胜境" && heroImage.caption == "灵山大佛"
    val imageContainerModifier = if (showFullImage) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .aspectRatio(800f / 1067f)
    } else {
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Box(
        modifier = imageContainerModifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEAF0EC))
    ) {
        AsyncImage(
            model = heroImage.url,
            contentDescription = heroImage.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = scenicName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ==================== Text Section ====================

@Composable
fun TextSection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        section.subtitle?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = Color(0xFF5A6772)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        section.items.forEach { item ->
            when (item.type) {
                ContentType.PARAGRAPH -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2328),
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                ContentType.HEADING -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C2328),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ==================== Image Full Section ====================

@Composable
fun ImageFullSection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        section.items.forEach { item ->
            when (item.type) {
                ContentType.IMAGE -> {
                    item.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = item.imageCaption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        item.imageCaption?.let { caption ->
                            Text(
                                text = caption,
                                fontSize = 12.sp,
                                color = Color(0xFF5A6772),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                ContentType.PARAGRAPH -> {
                    item.text?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2328),
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ==================== Image Gallery Section ====================

@Composable
fun ImageGallerySection(section: ContentSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val images = section.items.filter { it.type == ContentType.IMAGE && it.imageUrl != null }
        if (images.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { item ->
                    Column(modifier = Modifier.width(180.dp)) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.imageCaption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        item.imageCaption?.let { caption ->
                            Text(
                                text = caption,
                                fontSize = 11.sp,
                                color = Color(0xFF5A6772),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Text + Image Section ====================

@Composable
fun TextImageSection(section: ContentSection, imageOnRight: Boolean) {
    val textItem = section.items.find { it.type == ContentType.PARAGRAPH }
    val imageItem = section.items.find { it.type == ContentType.IMAGE }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        section.subtitle?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = Color(0xFF5A6772),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (!imageOnRight && imageItem != null) {
                AsyncImage(
                    model = imageItem.imageUrl,
                    contentDescription = imageItem.imageCaption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            textItem?.text?.let { text ->
                Text(
                    text = text,
                    fontSize = 15.sp,
                    color = Color(0xFF1C2328),
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (imageOnRight && imageItem != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = imageItem.imageUrl,
                    contentDescription = imageItem.imageCaption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

// ==================== Route Recommendation Section ====================

@Composable
fun RouteRecommendationSection(section: ContentSection) {
    val imageItem = section.items.find { it.type == ContentType.IMAGE }
    val routes = remember(section.items) {
        section.items
            .filter { it.type == ContentType.PARAGRAPH }
            .mapNotNull { it.text?.takeIf(String::isNotBlank)?.let(::parseRouteCardData) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SectionHeader(
            title = section.title,
            subtitle = section.subtitle,
            subtitleColor = ScenicPrimaryDark
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Column {
                imageItem?.imageUrl?.let { url ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = imageItem.imageCaption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.42f)
                                        )
                                    )
                                )
                        )
                        imageItem.imageCaption?.let { caption ->
                            Text(
                                text = caption,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    routes.forEachIndexed { index, route ->
                        RoutePlanCard(route = route, index = index)
                        if (index != routes.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePlanCard(
    route: RouteCardData,
    index: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (index % 2 == 0) Color(0xFFF6FAF8) else Color(0xFFFFFBF5)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = ScenicPrimaryDark
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (index + 1).toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.title,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScenicPrimaryDeep
                    )
                    if (route.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = route.summary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFF5A6772)
                        )
                    }
                }
            }

            if (route.stops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                RouteStopTimeline(stops = route.stops)
            } else if (route.rawPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = route.rawPath,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color(0xFF1C2328)
                )
            }
        }
    }
}

@Composable
private fun RouteStopTimeline(stops: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        stops.forEachIndexed { index, stop ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                ) {
                    if (index != stops.lastIndex) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(ScenicPrimaryDark.copy(alpha = 0.18f))
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(24.dp),
                        shape = CircleShape,
                        color = if (index == 0 || index == stops.lastIndex) {
                            ScenicPrimaryDark
                        } else {
                            ScenicPrimaryDark.copy(alpha = 0.12f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when {
                                    index == 0 -> "起"
                                    index == stops.lastIndex -> "终"
                                    else -> index.toString()
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0 || index == stops.lastIndex) {
                                    Color.White
                                } else {
                                    ScenicPrimaryDark
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Text(
                    text = stop,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, bottom = if (index == stops.lastIndex) 0.dp else 12.dp),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (index == 0 || index == stops.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF1C2328)
                )
            }
        }
    }
}

// ==================== Travel Tips Section ====================

@Composable
fun TravelTipsSection(section: ContentSection) {
    val tips = section.items
        .filter { it.type == ContentType.PARAGRAPH }
        .mapNotNull { it.text?.takeIf(String::isNotBlank) }

    if (tips.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SectionHeader(
            title = section.title,
            subtitle = section.subtitle,
            subtitleColor = Color(0xFF5A6772)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tips.forEachIndexed { index, tip ->
                    val (title, body) = remember(tip) { splitTipTitleAndBody(tip) }
                    TravelTipCard(
                        index = index,
                        title = title,
                        body = body
                    )
                }
            }
        }
    }
}

@Composable
private fun TravelTipCard(
    index: Int,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (index % 2 == 0) Color(0xFFF6FAF8) else Color(0xFFFFFBF5),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (index % 2 == 0) ScenicPrimaryDark.copy(alpha = 0.13f) else Color(0xFFF2A541).copy(alpha = 0.18f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (index + 1).toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (index % 2 == 0) ScenicPrimaryDark else Color(0xFF986A1D)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C2328),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF4A5963)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String?,
    subtitle: String?,
    subtitleColor: Color
) {
    title?.let {
        Text(
            text = it,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ScenicPrimaryDeep
        )
    }
    subtitle?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = it,
            fontSize = 13.sp,
            color = subtitleColor
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

private fun splitRouteAndNote(text: String): Pair<String, String> {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return "" to ""
    val sentenceEnd = trimmed.indexOf('。')
    return if (sentenceEnd >= 0 && sentenceEnd < trimmed.lastIndex) {
        trimmed.substring(0, sentenceEnd).trim() to trimmed.substring(sentenceEnd + 1).trim()
    } else {
        trimmed to ""
    }
}

private data class RouteCardData(
    val title: String,
    val summary: String,
    val rawPath: String,
    val stops: List<String>
)

private fun parseRouteCardData(text: String): RouteCardData {
    val trimmed = text.trim()
    val titleSeparator = trimmed.indexOf('｜')
    val title = if (titleSeparator > 0) {
        trimmed.substring(0, titleSeparator).trim()
    } else {
        "推荐路线"
    }
    val body = if (titleSeparator > 0) trimmed.substring(titleSeparator + 1).trim() else trimmed
    val (summary, pathText) = splitRouteAndNote(body)
    return RouteCardData(
        title = title,
        summary = summary,
        rawPath = pathText,
        stops = parseRouteStops(pathText)
    )
}

private fun parseRouteStops(pathText: String): List<String> {
    return pathText
        .split("→")
        .map { it.trim().trim('。') }
        .filter { it.isNotBlank() }
}

private fun splitTipTitleAndBody(text: String): Pair<String, String> {
    val separators = listOf('：', ':')
    val index = separators
        .map { text.indexOf(it) }
        .filter { it > 0 }
        .minOrNull()
    return if (index != null) {
        text.substring(0, index).trim() to text.substring(index + 1).trim()
    } else {
        "提示" to text.trim()
    }
}

// ==================== Quote Section ====================

@Composable
fun QuoteSection(section: ContentSection) {
    val quoteItem = section.items.find { it.type == ContentType.QUOTE }
    quoteItem?.text?.let { text ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    color = Color(0xFFF2A541).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\"",
                    fontSize = 40.sp,
                    color = Color(0xFFF2A541),
                    lineHeight = 20.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    fontSize = 17.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF1C2328),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
                quoteItem.imageCaption?.let { caption ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = caption,
                        fontSize = 13.sp,
                        color = Color(0xFF5A6772),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

// ==================== Highlights Section ====================

@Composable
fun HighlightsSection(highlights: List<String>) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(highlights) { highlight ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ScenicPrimaryDeep.copy(alpha = 0.1f)
            ) {
                Text(
                    text = highlight,
                    fontSize = 12.sp,
                    color = ScenicPrimaryDeep,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ==================== Statistics Section ====================

@Composable
fun StatisticsSection(section: ContentSection) {
    val stats = section.items.filter { it.type == ContentType.STATISTIC }
    if (stats.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stats.forEach { stat ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stat.statisticValue ?: "",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScenicPrimaryDark
                )
                Text(
                    text = stat.statisticLabel ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF5A6772)
                )
            }
        }
    }
}

// ==================== Highlights + Statistics Combined ====================

@Composable
fun HighlightsStatisticSection(section: ContentSection) {
    val stats = section.items.filter { it.type == ContentType.STATISTIC }
    if (stats.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (stats.size == 5) {
            FivePointHighlightsLayout(stats = stats)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.forEach { stat ->
                    HighlightStatisticCard(
                        stat = stat,
                        titleFontSize = 20,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun FivePointHighlightsLayout(stats: List<ContentItem>) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(228.dp)
    ) {
        val cardWidth = minOf(112.dp, maxWidth * 0.5f - 8.dp)
        val cardHeight = 72.dp
        val bottomInset = ((maxWidth - cardWidth * 2f - 8.dp) * 0.5f).coerceAtLeast(0.dp)

        HighlightStatisticCard(
            stat = stats[0],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[1],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[2],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[3],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = bottomInset)
                .width(cardWidth)
                .height(cardHeight)
        )
        HighlightStatisticCard(
            stat = stats[4],
            titleFontSize = 18,
            labelMaxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -bottomInset)
                .width(cardWidth)
                .height(cardHeight)
        )
    }
}

@Composable
private fun HighlightStatisticCard(
    stat: ContentItem,
    titleFontSize: Int,
    modifier: Modifier = Modifier,
    labelMaxLines: Int = Int.MAX_VALUE
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ScenicPrimaryDark.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = stat.statisticValue ?: "",
                modifier = Modifier.fillMaxWidth(),
                fontSize = titleFontSize.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDark,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stat.statisticLabel ?: "",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp,
                color = Color(0xFF5A6772),
                textAlign = TextAlign.Center,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== Timeline Section ====================

@Composable
fun TimelineSection(section: ContentSection) {
    val timelineItem = section.items.find { it.type == ContentType.TIMELINE }
    val events = timelineItem?.timelineEvents ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val lineColor = ScenicPrimaryDark.copy(alpha = 0.28f)
        val nodeColor = ScenicPrimaryDark

        events.forEachIndexed { index, event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(86.dp)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (index == 0) 10.dp else 0.dp,
                                end = 6.dp
                            )
                            .width(2.dp)
                            .then(
                                if (index == events.lastIndex) Modifier.height(10.dp)
                                else Modifier.fillMaxHeight()
                            )
                            .background(lineColor)
                    )
                    Text(
                        text = event.year,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(68.dp)
                            .padding(top = 1.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = nodeColor,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 1.dp)
                            .size(12.dp)
                            .background(nodeColor, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index == events.lastIndex) 0.dp else 16.dp)
                ) {
                    Text(
                        text = event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C2328)
                    )
                    Text(
                        text = event.description,
                        fontSize = 13.sp,
                        color = Color(0xFF5A6772),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ==================== Spots Grid Section ====================

@Composable
fun SpotsGridSection(section: ContentSection) {
    val spotsItem = section.items.find { it.type == ContentType.SPOTS }
    val spots = spotsItem?.spots ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        section.title?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ScenicPrimaryDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val rows = spots.chunked(2)
        rows.forEach { rowSpots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowSpots.forEach { spot ->
                    SpotCardItem(
                        spot = spot,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSpots.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun SpotCardItem(spot: SpotCard, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column {
            spot.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = spot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = spot.name.take(2),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScenicPrimaryDark.copy(alpha = 0.5f)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = spot.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C2328),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spot.description,
                    fontSize = 12.sp,
                    color = Color(0xFF5A6772),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                spot.tags?.let { tags ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ScenicPrimaryDark.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = ScenicPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
