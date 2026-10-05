package org.potato.supervisor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.potato.supervisor.storage.JournalRepository

@Composable fun FinishReview(task: String, repository: JournalRepository, dismiss: ()->Unit, saved: ()->Unit) {
    var completion by remember { mutableStateOf("partial") }
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy) dismiss()},title={Text("这次任务做到哪了？")},text={Column {
        Text(task)
        listOf("done" to "已完成","partial" to "完成一部分","not_started" to "未开始").forEach { (key,label) ->
            Row(Modifier.fillMaxWidth().selectable(selected=completion==key,onClick={completion=key},role=Role.RadioButton)) {
                RadioButton(completion==key,null); Text(label,Modifier.padding(top=12.dp))
            }
        }
        OutlinedTextField(content,{content=it},label={Text("做了什么 / 下一步（可选）")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Text("监督已经结束。保存会追加到今天的记录；未开始不算学习打卡。")
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    } },confirmButton={TextButton(enabled=!busy,onClick={busy=true; scope.launch {
        try { repository.review(task,completion,content); saved() }
        catch (_: Exception) { error="保存失败或当天内容超过 1000 字，请缩短内容或到学习记录页整理。" }
        finally { busy=false }
    } }) { Text("保存到学习记录") }},dismissButton={TextButton(enabled=!busy,onClick=dismiss) { Text("暂不记录") }})
}
