package org.potato.supervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.potato.supervisor.rules.*
import org.potato.supervisor.storage.*
import java.time.LocalDate
import java.time.YearMonth

@Composable fun JournalScreen(repository: JournalRepository) {
    val journal by repository.state.collectAsState(JournalState())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var showCalendar by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(today) }
    var deleting by remember { mutableStateOf<LocalDate?>(null) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val entry = journal.entries.find { it.date == selected }
    var content by remember(selected, entry) { mutableStateOf(entry?.content.orEmpty()) }
    var minutes by remember(selected, entry) { mutableStateOf(entry?.minutes?.toString().orEmpty()) }
    var completion by remember(selected, entry) { mutableStateOf(entry?.completion) }
    Text("今天学了什么？", style = MaterialTheme.typography.headlineMedium)
    Text("记录做过的事，明天继续。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GardenMetric("已学习天数", "${journal.entries.count { it.completion!="not_started" }}", Modifier.weight(1f))
        GardenMetric("连续打卡", "${StudyJournal.streak(journal.entries,today)} 天", Modifier.weight(1f))
    }
    GardenCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selected.toString(),Modifier.padding(top=12.dp))
            TextButton(onClick={ showCalendar=!showCalendar }) { Text(if(showCalendar) "收起日历" else "选择日期 / 补记") }
        }
        if(showCalendar) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("上月") }
            Text("${month.year} 年 ${month.monthValue} 月",Modifier.padding(top = 12.dp))
            TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) { Text("下月") }
        }
        Row { listOf("一","二","三","四","五","六","日").forEach { Text(it,Modifier.weight(1f),fontSize = 12.sp) } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - offset + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val recorded = journal.entries.any { it.date == date }
                        TextButton(onClick = { selected = date; showCalendar=false; message = "" },enabled = date <= today,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).background(
                                if (date == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(10.dp)),
                            contentPadding = PaddingValues(0.dp)) {
                            Text("$day${if(recorded) "•" else ""}",fontSize = 12.sp)
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
        GardenLabel("带 • 的日期有记录。可点选过去的日期补记或修改。")
        }
    }
    GardenCard {
        if(completion=="not_started") {
            GardenLabel("这天记录的是未开始，尚未计入学习打卡。学过之后可补充内容并改为打卡。")
            TextButton(onClick={completion=null}) { Text("改为学习打卡") }
        }
        Text(if(selected == today) "今天的学习" else "$selected 的学习",style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(content,{ content = it },label = { Text("实际完成的学习内容") },minLines = 3,modifier = Modifier.fillMaxWidth())
        OutlinedTextField(minutes,{ minutes = it },label = { Text("学习时长（分钟，可留空）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val duration = minutes.takeIf { it.isNotBlank() }?.toIntOrNull()
            val record = StudyEntry(selected, content.trim(),duration,completion)
            message = if (minutes.isNotBlank() && duration == null) "时长需填写整数分钟" else record.error(today).orEmpty()
            if (message.isBlank()) { saving = true; scope.launch {
                try { repository.save(record); message = "学习记录已保存" }
                catch (_: Exception) { message = "保存失败，填写内容还在，请重试。" }
                finally { saving = false }
            } }
        },enabled = journal.ready && journal.error.isBlank() && !saving,modifier = Modifier.fillMaxWidth()) {
            Text(if(saving) "正在保存…" else if(entry == null) "保存学习打卡" else "更新学习记录")
        }
        if (entry != null) TextButton(onClick = { deleting = selected }) { Text("删除这天的记录") }
        if (message.isNotBlank()) Text(message)
        GardenLabel("这是手动记录的学习，不由监督时长推算。每天一条，可反复补充。")
    }
    JournalBackup(repository,journal)
    if (journal.error.isNotBlank()) Text(journal.error,color = MaterialTheme.colorScheme.error)
    if (journal.entries.isNotEmpty()) {
        Text("最近的学习",style = MaterialTheme.typography.titleLarge)
        journal.entries.take(7).forEach { e -> GardenCard {
            GardenLabel("${e.date}${e.minutes?.let { " · $it 分钟（自填）" }.orEmpty()}")
            Text(e.content)
            TextButton(onClick = { selected = e.date; month = YearMonth.from(e.date); message = "" }) { Text("查看 / 修改") }
        } }
    }
    deleting?.let { date -> AlertDialog(onDismissRequest = { deleting = null },title = { Text("删除 $date 的学习记录？") },
        text = { Text("删除后无法撤回。") },confirmButton = { TextButton(onClick = { deleting = null; scope.launch {
            try { repository.delete(date); message = "已删除" } catch (_: Exception) { message = "删除失败，请重试。" }
        } }) { Text("删除") } },dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}
