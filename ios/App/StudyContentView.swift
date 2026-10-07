import SwiftUI
import UIKit
import FamilyControls

struct StudyContentView: View {
    @ObservedObject var model: StudyModel
    @ObservedObject var diary: StudyDiary
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var picker = false
    @State private var endConfirmation = false
    @State private var breathing = false
    @State private var date = Date()
    @State private var content = ""
    @State private var minutes = ""
    @State private var exportURL: URL?
    private let green = Color(red: 0.26, green: 0.42, blue: 0.22)
    private var active: Bool { model.session?.active == true }

    var body: some View {
        TabView(selection: $tab) {
            home.tabItem { Label("学习", systemImage: "leaf.fill") }.tag(0)
            journal.tabItem { Label("记录", systemImage: "book.closed") }.tag(1)
            settings.tabItem { Label("设置", systemImage: "slider.horizontal.3") }.tag(2)
        }
        .tint(green)
        .sheet(isPresented: $picker) {
            NavigationStack {
                FamilyActivityPicker(selection: $model.selection)
                    .navigationTitle("选择监督应用")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { model.saveSelection(); picker = false } } }
            }
        }
        .alert("结束本次监督？", isPresented: $endConfirmation) {
            Button("结束并解除限制", role: .destructive) { model.end() }
            Button("继续学习", role: .cancel) { }
        } message: { Text("选定应用会恢复使用，学习记录保留。") }
        .onReceive(Timer.publish(every: 15, on: .main, in: .common).autoconnect()) { _ in
            if tab == 0 && scenePhase == .active { model.refresh() }
        }
    }
    private var home: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Image(uiImage: CharacterImage.load(model.bursting ? "potato-mine-explosion" : model.session?.phase == .cooldown ? "potato-mine-angry" : "potato-mine") ?? UIImage())
                        .resizable().scaledToFit().frame(height: 170)
                        .scaleEffect(breathing && !reduceMotion ? 1.035 : 1)
                        .animation(reduceMotion || scenePhase != .active ? nil : .easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: breathing)
                        .onAppear { breathing = true }
                        .accessibilityLabel("土豆监督员")
                    Text(model.session?.config.task ?? model.config.task).font(.title2.bold())
                    if let s = model.session {
                        Text(StudyDialogue.text(for: s)).font(.headline).multilineTextAlignment(.center)
                        Text(status(s)).font(.title3.bold()).foregroundStyle(green)
                        Text("本轮已发放 \(s.grants)/\(s.config.maxGrants) 段 · 已发放 \(s.grantedSeconds / 60)/\(s.config.budgetMinutes) 分钟")
                        if s.phase == .allowance {
                            Text("本段最多 \(s.segmentSeconds / 60) 分钟使用量。切走和锁屏不获得新额度；重新打开不会再次弹提醒。十五分钟窗口结束后收回放行。").font(.footnote)
                        }
                        if !s.lastMessage.isEmpty { Text(s.lastMessage).font(.footnote) }
                        if s.canGrant {
                            Button("搜资料 · \(s.nextGrantSeconds / 60) 分钟") { model.grant(.study) }.buttonStyle(.borderedProminent)
                            Button("休息一下 · \(s.nextGrantSeconds / 60) 分钟") { model.grant(.rest) }.buttonStyle(.bordered)
                        }
                    }
                    if active {
                        Button("结束本次监督", role: .destructive) { endConfirmation = true }
                    } else {
                        Button("开始学习") { model.start() }.buttonStyle(.borderedProminent).disabled(!model.authorized || model.selection.applicationTokens.isEmpty)
                    }
                    if !model.authorized { Button("授权屏幕使用时间") { Task { await model.authorize() } } }
                    if !model.message.isEmpty { Text(model.message).font(.footnote).foregroundStyle(.secondary) }
                }.padding(24).frame(maxWidth: .infinity)
            }
            .background(Color(red: 0.96, green: 0.97, blue: 0.89))
            .navigationTitle("土豆监督员")
        }
    }
    private func status(_ s: StudySession) -> String {
        switch s.phase {
        case .gate: return "先完成学习任务"
        case .arming: return "正在建立本段监测"
        case .allowance: return "本段已放行"
        case .cooldown: return "冷却中，回去学习"
        case .interrupted: return "监督已中断"
        case .ended: return "本次已结束"
        }
    }
    private var settings: some View {
        NavigationStack {
            Form {
                Section("权限与应用") {
                    Text(model.authorized ? "屏幕使用时间已授权" : "尚未授权")
                    Button("授权屏幕使用时间") { Task { await model.authorize() } }
                    Button("选择监督应用（已选 \(model.selection.applicationTokens.count) 个）") { picker = true }.disabled(active || !model.authorized)
                    Text("首版只选具体应用，不选择分类或网站。授权撤销后须重新选择。").font(.footnote)
                    Button("允许申请使用提示通知") { Task { await model.allowNotifications() } }
                }
                Section("学习约定") {
                    TextField("目标", text: $model.config.goal)
                    TextField("本次任务", text: $model.config.task, axis: .vertical)
                    TextField("给自己的寄语", text: $model.config.note, axis: .vertical)
                    Stepper("学习 \(model.config.studyMinutes) 分钟", value: $model.config.studyMinutes, in: 15...720, step: 15)
                    Stepper("每段 \(model.config.grantMinutes) 分钟", value: $model.config.grantMinutes, in: 1...5)
                    Stepper("每轮最多 \(model.config.maxGrants) 段", value: $model.config.maxGrants, in: 1...20)
                    Stepper("每轮总量 \(model.config.budgetMinutes) 分钟", value: $model.config.budgetMinutes, in: 1...100)
                    Stepper("冷却 \(model.config.cooldownMinutes) 分钟", value: $model.config.cooldownMinutes, in: 15...720, step: 5)
                }
                Section("土豆的脾气") {
                    Picker("语气", selection: $model.config.tone) {
                        Text("温和").tag("gentle"); Text("讽刺").tag("snark"); Text("狠话").tag("roast")
                    }
                    Text("狠话包含粗口和羞辱式反问，选择前请确认适合自己；尚无学习效果证明。").font(.footnote)
                    Toggle("本应用内音效", isOn: $model.config.sound)
                    Text("系统拦截页使用静态角色与文字；动画、音效只在土豆应用内展示。").font(.footnote)
                }
                Section {
                    Button("保存设置") { model.saveSettings() }
                    Text("规则与语气从下次学习生效。").font(.footnote)
                }
            }.navigationTitle("设置")
        }
    }
    private var journal: some View {
        NavigationStack {
            List {
                Section("记录真实完成的学习") {
                    DatePicker("日期", selection: $date, in: ...Date(), displayedComponents: .date)
                    TextField("今天学了什么", text: $content, axis: .vertical)
                    TextField("分钟数（可留空）", text: $minutes).keyboardType(.numberPad)
                    Button("保存当天记录") { diary.save(date: date, content: content, minutes: minutes) }
                    if !diary.message.isEmpty { Text(diary.message).font(.footnote) }
                    Button("准备导出记录") {
                        do { exportURL = try diary.export() } catch { diary.message = error.localizedDescription }
                    }
                    if let url = exportURL { ShareLink("分享学习记录", item: url) }
                }
                Section("已保存") {
                    ForEach(diary.entries) { item in
                        VStack(alignment: .leading) {
                            Text(item.id).font(.headline); Text(item.content)
                            if let value = item.minutes { Text("\(value) 分钟").font(.footnote) }
                        }
                    }.onDelete(perform: diary.delete)
                }
            }.navigationTitle("学习记录")
        }
    }
}
