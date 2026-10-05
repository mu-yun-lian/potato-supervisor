package org.potato.supervisor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.potato.supervisor.rules.*
import org.potato.supervisor.storage.*
import java.time.LocalDate

@Composable fun JournalBackup(repository: JournalRepository, state: JournalState) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<List<StudyEntry>?>(null) }
    var overwrite by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text=exportText; exportText=null
        if(uri!=null) { busy=true; scope.launch {
            try { withContext(Dispatchers.IO) {
                val snapshot=text ?: repository.export()
                checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
            }; message="备份已导出，请保留这份文件。" }
            catch (_: Exception) { message="导出失败，备份文件可能不完整，请重新导出。" }
            finally { busy=false }
        } }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { busy=true; scope.launch {
            try {
                incoming=withContext(Dispatchers.IO) {
                    val bytes=checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val buffer=ByteArray(4096); val output=java.io.ByteArrayOutputStream()
                        while(true) {
                            val count=input.read(buffer); if(count<0) break
                            require(output.size()+count<=2_000_000) { "备份文件最多 2 MB" }
                            output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size<=2_000_000) { "备份文件最多 2 MB" }
                    val entries=JournalRepository.decode(bytes.toString(Charsets.UTF_8))
                    entries.forEach { require(it.error()==null) { it.error()!! } }
                    entries
                }; overwrite=false; message=""
            } catch (e: Exception) { incoming=null; message=e.message?.takeIf { it.length<100 } ?: "无法读取备份，现有记录未改动。" }
            finally { busy=false }
        } }
    }
    GardenCard {
        Text("记录备份",style=MaterialTheme.typography.titleLarge)
        GardenLabel("卸载会删除本机数据。先导出文件，换机后可导入；不需要账号。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ scope.launch {
                try { exportText=repository.export(); export.launch("土豆学习记录-${LocalDate.now()}.json") }
                catch (_: Exception) { message="暂时无法导出记录。" }
            } },enabled=state.ready && state.error.isBlank() && !busy) { Text("导出备份") }
            OutlinedButton(onClick={ import.launch(arrayOf("application/json","text/plain","application/octet-stream")) },
                enabled=state.ready && state.error.isBlank() && !busy) { Text("导入备份") }
        }
        if(message.isNotBlank()) Text(message)
    }
    incoming?.let { entries ->
        val conflicts=entries.count { e -> state.entries.any { it.date==e.date } }
        AlertDialog(onDismissRequest={ if(!busy) incoming=null },title={Text("确认导入记录")},text={ Column {
            Text("共 ${entries.size} 天，其中 $conflicts 天与本机记录同日期。其余日期会合并，未包含的本机记录会保留。")
            Row(Modifier.fillMaxWidth().toggleable(overwrite,enabled=!busy,role=Role.Checkbox,onValueChange={overwrite=it})) {
                Checkbox(overwrite,null); Text("覆盖同日期的本机记录",Modifier.padding(top=12.dp))
            }
            Text(if(overwrite) "同日期将使用备份内容，无法撤回。" else "默认保留同日期的本机内容。")
        } },confirmButton={ TextButton(enabled=!busy,onClick={ busy=true; scope.launch {
            try { repository.import(entries,overwrite); incoming=null; message="备份已合并。" }
            catch (_: Exception) { message="导入失败，现有记录已保留。" }
            finally { busy=false }
        } }) { Text("确认导入") } },dismissButton={TextButton(enabled=!busy,onClick={incoming=null}) { Text("取消") }})
    }
}
