package org.potato.supervisor.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*

open class TargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(35,100,35,35) }
        column.addView(TextView(this).apply { text="可控目标应用：用于验证监督机制，不代表抖音或游戏实测"; textSize=22f })
        column.addView(EditText(this).apply { hint="测试键盘输入" })
        column.addView(Button(this).apply { text="打开内部页面"; setOnClickListener { startActivity(Intent(this@TargetActivity,InternalActivity::class.java)) } })
        setContentView(column)
    }
}
class InternalActivity : TargetActivity()
