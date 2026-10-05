package org.potato.supervisor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.potato.supervisor.overlay.PotatoView

val GardenInk=Color(0xFF263C2A)
val GardenGreen=Color(0xFF426A38)
val GardenCream=Color(0xFFF7F5EA)

@Composable fun GardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=lightColorScheme(
        primary=GardenGreen,onPrimary=Color.White,primaryContainer=Color(0xFFE4ECCC),onPrimaryContainer=GardenInk,
        secondary=Color(0xFF8B6F3E),secondaryContainer=Color(0xFFF0E8D4),onSecondaryContainer=GardenInk,
        background=GardenCream,onBackground=GardenInk,surface=Color(0xFFFFFDF6),onSurface=GardenInk,
        surfaceVariant=Color(0xFFEDF0DE),onSurfaceVariant=Color(0xFF65725E),outline=Color(0xFFBFCAB0),
        surfaceContainer=Color(0xFFF0F1E3),surfaceContainerLow=Color(0xFFFFFDF6),surfaceContainerHigh=Color(0xFFE8ECD9),
        error=Color(0xFFAB453C)),shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(26.dp)),
        typography=Typography().let { it.copy(headlineMedium=it.headlineMedium.copy(fontSize=25.sp,fontWeight=FontWeight.Bold),
            titleLarge=it.titleLarge.copy(fontSize=21.sp,fontWeight=FontWeight.SemiBold)) },content=content)
}

@Composable fun GardenHero(mood: String, reducedMotion: Boolean,onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(208.dp).background(
        Brush.linearGradient(listOf(Color(0xFF345C32),Color(0xFF6F9149))),RoundedCornerShape(26.dp))) {
        Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("你的学习搭子",color=Color(0xFFDEEABF),fontSize=12.sp)
            Text("先做一点，\n就算开始。",color=Color.White,fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold)
            Text("点一下土豆，打个招呼",color=Color(0xFFDEEABF),fontSize=11.sp)
        }
        AndroidView(factory={PotatoView(it)},update={ it.expression=mood; it.motionEnabled=!reducedMotion; it.onInteraction=onTap },
            modifier=Modifier.weight(1f).height(187.dp))
        }
    }
}

@Composable fun GardenCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFFDF6)),
        border=BorderStroke(1.dp,Color(0xFFE4E7D8))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable fun GardenLabel(text: String) { Text(text,color=Color(0xFF73806A),fontSize=12.sp,fontWeight=FontWeight.Medium) }

@Composable fun GardenMetric(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier.background(Color(0xFFF0F3E5),RoundedCornerShape(14.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value,fontWeight=FontWeight.Bold,fontSize=19.sp,color=GardenInk)
        Text(label,fontSize=12.sp,color=Color(0xFF73806A))
    }
}
