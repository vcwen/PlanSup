# PlanSup

健身训练计时器 App，基于 Android Jetpack Compose 构建。

## 功能

### 任务管理
- 创建/删除/完成任务，支持子任务（计次类型 / 计时类型）
- 子任务间自动休息倒计时，支持顺序执行全部子任务
- 任务重复：每日 / 每周 / 每月
- 滑动删除任务

### 标签分类
- 任务可添加自定义标签（tag）
- 左侧抽屉菜单按标签过滤任务列表
- 标签输入支持前缀匹配已有标签 + 新建标签

### 计时器
- 前台 Service 后台持续计时，锁屏不中断
- MediaSession 锁屏媒体控件（暂停/继续/跳过/重启）
- TTS 语音播报阶段切换（智谱 GLM-TTS）
- 计时器前台时屏幕常亮
- 倒计时结束提示音

### 日历
- 周 / 月 / 年 三种视图切换
- 日历标记有任务的日期（圆点指示器）
- 支持重复任务在对应日期显示标记
- 点击日期查看当日任务列表，可跳转任务详情

### 个人中心
- 个人头像与昵称展示

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **导航**: Navigation 3
- **数据库**: Room
- **日历**: [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar)
- **TTS**: 智谱 GLM-TTS API
- **音频**: SoundPool + MediaPlayer
- **最低 SDK**: 33 (Android 13)
- **目标 SDK**: 37

## 构建

```bash
./gradlew assembleDebug
```

APK 输出路径: `app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
app/src/main/java/com/jtcamp/plansup/
├── MainActivity.kt              # 入口，导航，底部 Tab
├── TaskListScreen.kt            # 任务列表页 + 抽屉菜单
├── TaskDetailScreen.kt          # 任务详情页
├── SubTaskTimerScreen.kt        # 计时器页面
├── CalendarScreen.kt            # 日历页面
├── data/
│   ├── PlanTask.kt              # 任务实体
│   ├── SubTask.kt               # 子任务实体
│   ├── PlanTaskDao.kt           # Room DAO
│   └── AppDatabase.kt           # 数据库
├── viewmodel/
│   ├── TaskListViewModel.kt
│   ├── TaskDetailViewModel.kt
│   └── SubTaskTimerViewModel.kt
├── timer/
│   ├── TimerForegroundService.kt # 前台计时服务
│   └── TimerWidgetProvider.kt   # 桌面小组件
└── reminder/
    ├── NotificationHelper.kt
    ├── AlarmScheduler.kt
    └── ReminderReceiver.kt
```
