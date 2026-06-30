package com.openclaw.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App localization. Every user-facing string lives here in RU + EN. The current instance
 * is provided via [LocalStrings] (driven by the language toggle in Settings), so switching
 * the language re-composes the whole UI instantly — no Activity restart, no Android resources.
 * Protocol strings (the `[Вложение:]` / `[[FILE:]]` markers the agent relies on) are NOT here.
 */
class Strings(val en: Boolean) {
    private fun p(ru: String, eng: String) = if (en) eng else ru

    // tabs / nav
    val tabChat = p("Чат", "Chat")
    val tabAgents = p("Агенты", "Agents")
    val tabJournal = p("Журнал", "Journal")
    val tabMore = p("Ещё", "More")
    val reconnecting = p("Переподключение…", "Reconnecting…")
    val signingIn = p("Входим…", "Connecting…")
    val editCreds = p("Изменить данные", "Edit connection")

    // connect screen
    val connectTitle = p("Подключение VPS", "Connect to VPS")
    val connectSubtitle = p("Агент живёт на твоём сервере. Телефон — тонкий клиент.",
        "The agent lives on your server. The phone is a thin client.")
    val fieldServerIp = p("IP сервера", "Server IP")
    val hintServerIp = p("напр. 203.0.113.10", "e.g. 203.0.113.10")
    val fieldSshUser = p("SSH-логин", "SSH user")
    val fieldSshPass = p("SSH-пароль", "SSH password")
    val hintSshPass = p("пароль root", "root password")
    val fieldClaudeKey = p("Ключ Claude (опц.)", "Claude key (opt.)")
    val connectBtn = p("Установить и подключиться", "Set up & connect")
    val connectingBtn = p("Подключаюсь…", "Connecting…")
    val needCreds = p("Заполни IP сервера и SSH-пароль", "Enter the server IP and SSH password")

    // chat header / project
    val agent = p("Агент", "Agent")
    val workdirMenu = p("📂 Рабочая папка…", "📂 Working folder…")
    val renameWord = p("Переименовать", "Rename")
    val sharedOn = p("👥 Общий контекст: вкл", "👥 Shared context: on")
    val sharedOff = p("👥 Общий контекст: выкл", "👥 Shared context: off")
    val newProjectMenu = p("➕ Новый проект…", "➕ New project…")
    val messageHint = p("Сообщение агенту…", "Message the agent…")
    val attachPhoto = p("🖼  Фото", "🖼  Photo")
    val attachFile = p("📄  Файл", "📄  File")
    val attach = p("Прикрепить", "Attach")
    val stop = p("Стоп", "Stop")

    // dialogs
    val newProjectTitle = p("Новый проект", "New project")
    val projectNameHint = p("имя проекта", "project name")
    val projectPathHint = p("/путь на сервере (опц.)", "/path on the server (opt.)")
    val create = p("Создать", "Create")
    val cancel = p("Отмена", "Cancel")
    val renameProjectTitle = p("Переименовать проект", "Rename project")
    val workdirHint = p("Пусто = агент сам выбирает папку под проект.",
        "Empty = the agent picks the project folder itself.")
    val save = p("Сохранить", "Save")
    fun workdirTitle(name: String) = p("Рабочая папка «$name»", "Working folder «$name»")

    // chat blocks
    val showAll = p("▾ показать всё", "▾ show all")
    val collapse = p("▴ свернуть", "▴ collapse")
    val downloading = p("загрузка…", "downloading…")
    val openFile = p("открыть", "open")
    val downloadFile = p("скачать", "download")
    val interrupted = p("прервано", "interrupted")
    val linkLost = p("связь прервалась", "connection lost")

    // questions
    val answerSent = p("✓ ответ отправлен", "✓ answer sent")
    val waitTurn = p("дождись завершения ответа…", "wait for the reply to finish…")
    val answer = p("Ответить", "Answer")
    val myAnswers = p("Мои ответы:", "My answers:")

    // agents screen
    val agentsTitle = p("Агенты", "Agents")
    val agentsSubtitle = p("Каждый агент — пресет промпта, инструментов и темы. Своя ветка истории.",
        "Each agent is a preset of prompt, tools and theme — with its own history thread.")
    val createAgent = p("+ Создать", "+ Create")
    val importAgent = p("↓ Импорт", "↓ Import")
    val openChat = p("Открыть чат", "Open chat")
    val edit = p("Изменить", "Edit")
    val agentFallback = p("агент", "agent")

    // journal
    val journalTitle = p("Журнал команд", "Command log")
    val journalSubtitle = p("Всё, что агент выполнял на твоём VPS.", "Everything the agent ran on your VPS.")
    val journalEmpty = p("Пока пусто — выполненные агентом команды появятся здесь.",
        "Empty for now — commands the agent runs will show up here.")

    // settings
    val settingsTitle = p("Настройки", "Settings")
    val settingsSubtitle = p("Ключи и история — только на устройстве. Сервера-посредника нет.",
        "Keys and history stay on your device. No middleman server.")
    val sectionVps = p("VPS пользователя", "Your VPS")
    val connectNewVps = p("Подключить новый VPS", "Connect a new VPS")
    val sectionLanguage = p("Язык", "Language")
    val sectionSecurity = p("Безопасность", "Security")
    val encryptHistory = p("Шифровать историю", "Encrypt history")
    val encryptHistorySub = p("SQLite + ключ из Keystore", "SQLite + key from Keystore")
    val auditLog = p("Вести audit-log", "Keep an audit log")
    val auditLogSub = p("Логировать команды на VPS", "Log commands run on the VPS")
    val about = p("ClawNest · v1.1 · MIT", "ClawNest · v1.1 · MIT")
    val aboutSub = p("опенсорс · без трекеров · без подписок", "open-source · no trackers · no subscriptions")

    // persona editor
    val editorTitle = p("Редактор агента", "Agent editor")
    val name = p("Имя", "Name")
    val nameHint = p("Имя агента", "Agent name")
    val themeColor = p("Цвет темы", "Theme color")
    val defaultModel = p("Модель по умолчанию", "Default model")
    val systemPrompt = p("Системный промпт", "System prompt")
    val promptHint = p("Характер, стиль, экспертиза…", "Character, style, expertise…")
    val allTools = p("Все инструменты", "All tools")
    val allToolsSub = p("Полный доступ: bash, файлы, веб, задачи", "Full access: bash, files, web, tasks")
    val allowedTools = p("Разрешённые инструменты", "Allowed tools")
    val exportJson = p("Экспорт JSON", "Export JSON")

    // viewmodel status
    val stConnecting = p("Соединение…", "Connecting…")
    val stAuth = p("Авторизация…", "Authorizing…")
    val stReconnecting = p("Переподключаюсь…", "Reconnecting…")
    val stTimeout = p("Таймаут, пробую снова…", "Timed out, retrying…")
    val stConnected = p("Подключено", "Connected")
    val stDisconnected = p("Отключено", "Disconnected")
    fun stConnectFail(msg: String?) = p("Не удалось подключиться: ${msg ?: "таймаут"}",
        "Couldn't connect: ${msg ?: "timeout"}")
    fun stError(msg: String) = p("Ошибка: $msg", "Error: $msg")
    fun stUploadFail(msg: String?) = p("загрузка не удалась: $msg", "upload failed: $msg")
    fun stAttachFail(msg: String?) = p("Не удалось прикрепить: $msg", "Couldn't attach: $msg")
    fun stOpenFail(msg: String?) = p("Открыть не удалось: $msg", "Couldn't open: $msg")

    // background service / notifications
    val svcTitle = p("ClawNest на связи", "ClawNest connected")
    val svcText = p("Агент работает в фоне", "Agent running in the background")
    val svcChannel = p("Соединение", "Connection")
    val msgChannel = p("Сообщения агента", "Agent messages")

    // desktop (Mac) layout
    val newDialog = p("Новый диалог", "New dialog")
    val history = p("История", "History")
    val agentBranch = p("ветка истории этого агента", "this agent's history branch")
    val model = p("Модель", "Model")
    val commandLog = p("Журнал команд", "Command log")
    val noCommands = p("Пока пусто", "Empty for now")
    val channelDesc = p("WebSocket внутри SSH · аутентификация по токену · audit-log на сервере.",
        "WebSocket inside SSH · token auth · server audit-log on.")
    val reconnectBtn = p("Переподключить", "Reconnect")
    val disconnect = p("Отключиться", "Disconnect")
    val aboutNoTrackers = p("без трекеров", "no trackers")

    // command palette (⌘K)
    val cmdSearch = p("Команда…", "Command…")
    val cmdOpen = p("Открыть", "Open")
    val cmdPanel = p("Боковая панель", "Side panel")
    val cmdLang = p("Язык RU / EN", "Language RU / EN")
    val cmdHintAgent = p("агент", "agent")
    val cmdHintDialog = p("диалог", "dialog")
    val cmdEmpty = p("Ничего не найдено", "No matches")
}

val LocalStrings = staticCompositionLocalOf { Strings(false) }
