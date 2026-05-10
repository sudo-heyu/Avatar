package com.example.scenic_avatar_guide_app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownBubbleText(
    content: String,
    textColor: Color,
    linkColor: Color,
    codeBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        modifier = modifier,
        colors = markdownColor(
            text = textColor,
            codeText = textColor,
            inlineCodeText = textColor,
            linkText = linkColor,
            codeBackground = codeBackgroundColor,
            inlineCodeBackground = codeBackgroundColor,
            dividerColor = textColor.copy(alpha = 0.2f)
        ),
        typography = markdownTypography(
            h1 = TextStyle(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold
            ),
            h2 = TextStyle(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            ),
            h3 = TextStyle(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            ),
            text = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            paragraph = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            code = TextStyle(fontSize = 13.sp),
            quote = TextStyle(fontSize = 14.sp),
            ordered = TextStyle(fontSize = 14.sp),
            bullet = TextStyle(fontSize = 14.sp)
        )
    )
}
