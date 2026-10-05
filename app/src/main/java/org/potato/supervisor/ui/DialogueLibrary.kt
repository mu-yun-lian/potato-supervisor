package org.potato.supervisor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.potato.supervisor.content.Dialogue

@Composable fun DialogueLibrary(dialogue: Dialogue, defaultTone: String) {
    var tone by remember { mutableStateOf(defaultTone) }
    var scene by remember { mutableStateOf("entry") }
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember(tone,scene) { mutableStateOf(false) }
    val scenes=listOf("entry" to "刚进入应用","reentry" to "再次进入","accept_study" to "同意查资料",
        "accept_rest" to "同意休息","followup_study" to "查资料到时","followup_rest" to "休息到时",
        "repeat_extension" to "再次延长","last_grant" to "最后一次放行","over_limit" to "只提醒到时",
        "cooldown" to "额度用完","return_to_task" to "回到任务","past_self" to "自己写的话")
    val lines=remember(revision,tone,scene) { dialogue.lines.filter { it.tone==tone && it.scene==scene } }
    Text("提醒语录库",style=MaterialTheme.typography.headlineMedium)
    GardenLabel("${dialogue.lines.size} 条 · 本机离线 · 同一场景与语气先轮完再重复，重启仍记得。")
    Row { listOf("gentle" to "温和","snark" to "讽刺","roast" to "狠话").forEach { (key,label) ->
        TextButton(onClick={tone=key},modifier=Modifier.weight(1f)) { Text(if(tone==key) "✓ $label" else label) }
    } }
    Box {
        OutlinedButton(onClick={expanded=true}) { Text("场景：${scenes.first { it.first==scene }.second}") }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) { scenes.forEach { (key,label) ->
            DropdownMenuItem(text={Text(label)},onClick={scene=key; expanded=false})
        } }
    }
    if(scene=="past_self") GardenLabel("预留场景：当前监督流程尚不自动调用。")
    GardenCard {
        Text("写一句对你有用的话")
        OutlinedTextField(text,{text=it},label={Text("自写语录（最多 300 字）")},minLines=2,modifier=Modifier.fillMaxWidth())
        Button(onClick={try { dialogue.add(scene,tone,text); text=""; revision++; message="已加入当前场景" }
            catch(e: Exception) { message=e.message.orEmpty() }}) { Text("加入语录库") }
        GardenLabel("提醒时跟随已保存的语气。本页查看狠话不会自动启用狠话。自写内容也只在本机保存。")
        if(message.isNotBlank()) Text(message)
    }
    lines.take(if(showAll) lines.size else 8).forEach { line -> GardenCard {
        Text(line.text)
        if(dialogue.isCustom(line.id)) TextButton(onClick={dialogue.delete(line.id); revision++}) { Text("删除自写语录") }
    } }
    if(lines.size>8) TextButton(onClick={showAll=!showAll}) { Text(if(showAll) "收起" else "查看全部 ${lines.size} 条") }
    GardenLabel("{task}、{grantCount} 等占位会在实际提醒时替换为任务和使用记录。借鉴公开内容的表达方式，内置文案为重新创作。")
}
