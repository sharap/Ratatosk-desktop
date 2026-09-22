package chat.ratatosk.desktop.ui

import java.util.Locale

/**
 * Строки приложения — русские и английские рядом.
 *
 * Язык выбирается один раз при запуске по системной локали: смена языка
 * в работающем окне не нужна никому, а ради неё пришлось бы тащить состояние
 * в каждую строку. Хранятся они обычным объектом, а не ресурсами Compose,
 * потому что строки нужны не только в отрисовке: уведомления, трей и модели
 * получают их вне композиции.
 */
object Strings {
    /** Русский — и для родственных локалей: там английский был бы хуже. */
    private val russian = Locale.getDefault().language in setOf("ru", "uk", "be", "kk")

    private fun tr(ru: String, en: String) = if (russian) ru else en

    val APP_NAME = "Ratatosk"
    val CHATS = tr("Чаты", "Chats")
    val CONTACTS = tr("Контакты", "Contacts")
    val SETTINGS = tr("Настройки", "Settings")
    val PROFILE = tr("Профиль", "Profile")
    val WELCOME_BACK = tr("С возвращением", "Welcome back")
    val HIDDEN_ACCOUNT = tr("Скрытый аккаунт", "Hidden account")
    val CORE_CALL_FAILED = tr("Ядро не выполнило команду", "The core did not run the command")
    val REGISTRY_FAILED = tr("Не удалось открыть список аккаунтов: %s", "Could not open the account list: %s")
    val CORE_UNAVAILABLE = tr("Ядро недоступно: %s", "Core unavailable: %s")
    val ACCOUNT_CREATE_FAILED = tr("Не удалось создать аккаунт: %s", "Could not create the account: %s")
    val ACCOUNT_OPEN_FAILED = tr("Не удалось открыть аккаунт", "Could not open the account")
    val PAIRING_LINK_MISSING = tr("Ссылки сопряжения нет в системном хранилище паролей — вставьте её заново", "The pairing link is not in the system password store — paste it again")
    val COMPANION_LINK_FAILED = tr("Не удалось подключиться к телефону: %s", "Could not connect to the phone: %s")
    val FILE_SAVE_FAILED = tr("Не удалось сохранить файл", "Could not save the file")
    val NOSTR_RELAYS_REJECTED = tr("Ядро приняло не все адреса реле", "The core did not accept all relay addresses")
    val MAIL_NEEDS_TOR = tr("Чтобы регистрировать ящик через Tor, включите Tor", "To register a mailbox over Tor, turn Tor on")
    val HIDDEN_NOT_FOUND = tr("С таким PIN скрытого аккаунта нет", "No hidden account with this PIN")
    val OPENING_ACCOUNT = tr("Открываем аккаунт…", "Opening the account…")
    val OPENING_SLOW_NOTE = tr("Ключ выводится из PIN — это занимает несколько секунд.", "The key is derived from the PIN — this takes a few seconds.")
    val ACCOUNT_LABEL = tr("Ярлык аккаунта (виден только на этом компьютере)", "Account label (visible only on this computer)")
    val CREATE_ACCOUNT_TITLE = "Ratatosk"
    val CREATE_ACCOUNT_SUBTITLE = tr("Переписка без серверов и без учётных записей", "Messaging without servers and without accounts")
    val UNLOCK = tr("Разблокировать", "Unlock")
    val ENTER_PIN = tr("Введите PIN (необязательно)", "Enter a PIN (optional)")
    val GENERATE_IDENTITY = tr("Создать профиль", "Create a profile")
    val DISPLAY_NAME = tr("Отображаемое имя", "Display name")
    val SECURITY_NOTICES = tr("Важные уведомления безопасности", "Important security notices")
    val NO_CHAT_SELECTED = tr("Выберите чат слева", "Pick a chat on the left")
    val NO_CHATS = tr("Нет активных чатов. Нажмите +, чтобы добавить контакт.", "No active chats. Press + to add a contact.")
    val NO_CONTACTS = tr("Список контактов пуст.", "The contact list is empty.")
    val ADD_CONTACT = tr("Добавить контакт", "Add contact")
    val RATATOSK_URI = "Ratatosk URI"
    val MET_IN_PERSON = tr("Встретились лично", "Met in person")
    val MET_IN_PERSON_DESC = tr("Если вы не встретились лично, необходимо проверить отпечаток позже.", "If you did not meet in person, you need to check the fingerprint later.")
    val ADD = tr("Добавить", "Add")
    val CANCEL = tr("Отмена", "Cancel")
    val CLEAR_CHAT = tr("Очистить чат", "Clear chat")

    // Экран чата
    val CHAT_REACT = tr("Реакция", "React")
    val CHAT_MORE = tr("Ещё", "More")
    val CHAT_COPY_TEXT = tr("Копировать текст", "Copy text")
    val CHAT_DELETE_FOR_ME = tr("Удалить у себя", "Delete for me")
    val CHAT_RETRACT_FOR_ALL = tr("Удалить у всех", "Delete for everyone")
    val CHAT_RETRY = tr("Отправить ещё раз", "Send again")
    val CHAT_EDITED = tr("изменено", "edited")
    val CHAT_SHOW_ALL = tr("Показать полностью", "Show more")
    val CHAT_COLLAPSE = tr("Свернуть", "Show less")
    val CHAT_REPLYING_TO = tr("Ответ: %s", "Reply: %s")
    val CHAT_EDITING = tr("Правка сообщения", "Editing message")
    val CHAT_YOU = tr("Вы", "You")
    val CHAT_UNKNOWN_AUTHOR = tr("Участник", "Member")
    val CHAT_NOT_FOUND = tr("Сообщение не нашлось в загруженной истории", "The message is not in the loaded history")
    val CHAT_SCROLL_DOWN = tr("К последним сообщениям", "Jump to latest messages")
    val CHAT_DROP_FILES = tr("Отпустите, чтобы прикрепить", "Drop to attach")
    val CHAT_NO_RESULTS = tr("Ничего не найдено", "Nothing found")
    val CHAT_SEARCH_COMPANION = tr("Поиск недоступен на втором экране: истории на нём нет", "Search is unavailable on the second screen: it has no history")
    val CHAT_SAVED_TO = tr("Сохранено: %s", "Saved: %s")
    val CHAT_SHOW_IN_FOLDER = tr("Показать в папке", "Show in folder")
    val CHAT_DELETE_TITLE = tr("Удалить у себя?", "Delete for me?")
    val CHAT_RETRACT_TITLE = tr("Удалить у всех?", "Delete for everyone?")
    val CHAT_HISTORY_START = tr("Начало переписки", "Start of the conversation")
    val CHAT_LOADING_OLDER = tr("Загружаем историю…", "Loading history…")
    val CHAT_COMPANION_LINKING = tr("Подключаемся к телефону…", "Connecting to the phone…")
    val CHAT_SEND_HINT_ENTER = tr("Сообщение (Enter — отправить, Shift+Enter — новая строка)", "Message (Enter — send, Shift+Enter — new line)")
    val CHAT_SEND_HINT_CTRL = tr("Сообщение (Ctrl+Enter — отправить)", "Message (Ctrl+Enter — send)")
    val STATUS_PENDING = tr("Отправляется", "Sending")
    val STATUS_WAITING = tr("Ждёт собеседника", "Waiting for the recipient")
    val STATUS_SENT = tr("Отправлено", "Sent")
    val STATUS_DELIVERED = tr("Доставлено", "Delivered")
    val STATUS_READ = tr("Прочитано", "Read")
    val STATUS_UNDELIVERABLE = tr("Не удалось отправить", "Could not send")
    val FILE_ACCEPT = tr("Принять", "Accept")
    val FILE_DECLINE = tr("Отклонить", "Decline")
    val FILE_OPEN = tr("Открыть", "Open")
    val FILE_SAVE = tr("Сохранить как файл", "Save as file")
    val FILE_CANCEL = tr("Отменить", "Cancel")
    val FILE_RECEIVING = tr("Получение: %d%%", "Receiving: %d%%")
    val FILE_SENDING = tr("Отдаётся: %d%%", "Sending: %d%%")
    val FILE_PAUSE = tr("Остановить приём", "Stop receiving")
    val FILE_RESUME = tr("Продолжить приём", "Resume receiving")
    val FILE_PAUSED = tr("Остановлено на %d%%", "Stopped at %d%%")
    val FILE_WAITING_PHONE = tr("Ждём связи с телефоном — приём продолжится с того же места", "Waiting for the phone — receiving will continue from the same place")
    val FILE_FETCHING = tr("Забираем с телефона: %d%%", "Fetching from the phone: %d%%")
    val FILE_FETCHING_UNKNOWN = tr("Забираем с телефона…", "Fetching from the phone…")
    val SHARED_CARD_YOU = tr("Это вы", "This is you")
    val SHARED_CARD_KNOWN = tr("Открыть переписку", "Open conversation")
    val SHARED_CARD_ADD = tr("Добавить в контакты", "Add to contacts")
    val SHARED_CARD_UNVERIFIED = tr("Добавленный так человек остаётся непроверенным: сверить личность можно только при встрече.", "Someone added this way stays unverified: identity can only be checked in person.")
    val FORWARD_GROUPS = tr("Группы", "Groups")
    val FORWARD_CONTACTS = tr("Контакты", "Contacts")
    val SETTINGS_MESSAGES = tr("Сообщения", "Messages")
    val SETTINGS_CTRL_ENTER = tr("Отправлять по Ctrl+Enter", "Send with Ctrl+Enter")
    val SETTINGS_CTRL_ENTER_DESC = tr("Enter будет переносить строку. Выключено — Enter отправляет, Shift+Enter переносит.", "Enter will add a line break. Off — Enter sends, Shift+Enter breaks the line.")

    // Группы
    val CREATE_GROUP = tr("Создать группу", "Create group")
    val GROUP_TITLE = tr("Название группы", "Group name")
    val GROUP_INFO = tr("О группе", "About the group")
    val GROUP_MEMBERS = tr("Участники", "Members")
    val GROUP_MEMBERS_COUNT = tr("Участников: %d", "Members: %d")
    val GROUP_INVITE = tr("Пригласить", "Invite")
    val GROUP_INVITE_TITLE = tr("Пригласить в группу", "Invite to the group")
    val GROUP_INVITE_NOBODY = tr("Все ваши контакты уже в группе.", "All your contacts are already in the group.")
    val GROUP_RENAME = tr("Переименовать", "Rename")
    val GROUP_LEAVE = tr("Покинуть группу", "Leave group")
    val GROUP_EVICT = tr("Исключить", "Remove")
    val GROUP_EVICT_TITLE = tr("Исключить %s?", "Remove %s?")
    val GROUP_YOU = tr("Вы", "You")
    val GROUP_OWNER = tr("создатель", "creator")
    val GROUP_YOU_LEFT = tr("Вы вышли из группы. Переписка осталась только для чтения.", "You left the group. The conversation is read-only now.")
    val GROUP_LEFT_BADGE = tr("вы вышли", "you left")
    val GROUP_OPEN_CHAT = tr("Открыть чат", "Open chat")
    val GROUP_NOT_FOUND = tr("Группа не найдена", "Group not found")
    val GROUP_CREATED_AT = tr("Создана: %s", "Created: %s")
    val GROUP_MEMBERS_LOADING = tr("Загружаем состав…", "Loading members…")
    val GROUP_NO_MESSAGES = tr("Сообщений пока нет", "No messages yet")
    val CONTINUE = tr("Продолжить", "Continue")
    val CLEAR_CHAT_DESC = tr("Это удалит все сообщения в этом чате. Действие нельзя отменить.", "This will delete all messages in this chat. The action cannot be undone.")
    val DELETE = tr("Удалить", "Delete")
    val MESSAGE = tr("Сообщение", "Message")
    val EDIT = tr("Изменить", "Edit")
    val EDIT_CONTACT_NAME = tr("Изменить имя контакта", "Edit contact name")
    val SEND = tr("Отправить", "Send")
    val SAVE = tr("Сохранить", "Save")
    val ONLINE_LAN = tr("В сети (LAN)", "Online (LAN)")
    val ENCRYPTED_CONNECTION = tr("Шифрованное соединение установлено.", "Encrypted connection established.")
    val FORWARDED = tr("Переслано", "Forwarded")
    val FORWARD = tr("Переслать", "Forward")
    val FORWARD_TO = tr("Переслать в...", "Forward to...")
    val COPY = tr("Копировать", "Copy")
    val RETRACT = tr("Отозвать", "Retract")
    val DELETE_FOR_ME = tr("Удалить у меня", "Delete for me")
    val RETRY = tr("Повторить", "Retry")
    val CONTACT_DETAILS = tr("Данные контакта", "Contact details")
    val OFFLINE = tr("Не в сети", "Offline")
    val IDENTITY_VERIFIED = tr("Профиль подтвержден", "Profile verified")
    val UNVERIFIED = tr("Не подтвержден", "Not verified")
    val VERIFY_IDENTITY = tr("Подтвердить профиль", "Verify profile")
    val REVOKE_TRUST = tr("Отозвать доверие", "Revoke trust")
    val FINGERPRINT = tr("Отпечаток (IK)", "Fingerprint (IK)")
    val SHARE_CONTACT = tr("Поделиться контактом", "Share contact")
    val SHOW_QR = tr("Показать QR", "Show QR")
    val COPY_LINK = tr("Копировать ссылку", "Copy link")
    val LINK_COPIED = tr("Ссылка скопирована", "Link copied")
    val LINK_COPY_FAILED = tr("Не удалось получить ссылку", "Could not get the link")
    val SHOW = tr("Показать", "Show")
    val HIDE = tr("Скрыть", "Hide")
    val NO_PIN_TITLE = tr("Без PIN", "Without a PIN")
    val CREATE_WITHOUT_PIN = tr("Создать без PIN", "Create without a PIN")
    val BIND_TO_DEVICE = tr("Привязать к этому компьютеру", "Bind to this computer")
    val BIND_TO_DEVICE_DESC = tr("Файл аккаунта не откроется на другой машине — ни с PIN, ни без него.", "The account file will not open on another machine — with or without a PIN.")
    val BIND_TO_DEVICE_WARNING = tr("Секрет хранится в системной связке ключей. Переустановка системы или сброс связки ключей — потеря переписки: её не вернёт ни PIN, ни резервная фраза. Выбирается один раз, при создании.", "The secret is kept in the system keychain. Reinstalling the system or resetting the keychain means losing the conversation: neither the PIN nor the recovery phrase will bring it back. Chosen once, at creation.")
    val COMPANION_NOT_REMEMBERED = tr("На этом компьютере нет системного хранилища паролей: сопряжение не будет запомнено, ссылку придётся вставлять при каждом запуске.", "This computer has no system password store: the pairing will not be remembered, and the link will have to be pasted at every launch.")
    val NO_PIN_FALLBACK = tr("Без PIN ключ базы хранится в ней открыто: любой, кто получит файл аккаунта, сможет его прочитать.", "Without a PIN the database key is stored in it in the clear: anyone who gets the account file will be able to read it.")
    val ACCOUNT_DELETE = tr("Удалить аккаунт", "Delete account")
    val ACCOUNT_DELETE_TITLE = tr("Удалить аккаунт «%s»?", "Delete the account “%s”?")
    val ACCOUNT_DELETE_DESC = tr("Будут стёрты переписка, вложения и ключи этого аккаунта на этом компьютере. Вернуть их не сможет ни PIN, ни резервная фраза — только резервная копия, если вы её делали.", "The conversation, attachments and keys of this account on this computer will be erased. Neither the PIN nor the recovery phrase will bring them back — only a backup, if you made one.")
    val ACCOUNT_DELETE_FLASH_NOTE = tr("Обещать, что байты исчезли с диска, нельзя: на флеш-памяти запись поверх не гарантирована. Делается всё, что возможно из приложения.", "There is no promising that the bytes are gone from the disk: on flash memory overwriting is not guaranteed. Everything possible from the app is done.")
    val ACCOUNT_DELETE_FAILED = tr("Не удалось удалить аккаунт", "Could not delete the account")
    val DELETE_CONTACT = tr("Удалить контакт", "Delete contact")
    val PURGE_HISTORY = tr("Очистить историю чата", "Clear chat history")
    val MY_QR_CODE = tr("Мой QR-код", "My QR code")
    val CLOSE = tr("Закрыть", "Close")
    val LAN_TRANSPORT = tr("LAN транспорт", "LAN transport")
    val LAN_DESC = tr("Подключаться к контактам в локальной сети", "Connect to contacts on the local network")
    val NOTIFICATION_PRIVACY = tr("Конфиденциальность уведомлений", "Notification privacy")
    val PRIVACY_DESC = tr("Управляйте информацией, отображаемой в системных уведомлениях.", "Control what is shown in system notifications.")
    val SHOW_SENDER_NAME = tr("Показывать имя отправителя", "Show sender name")
    val SHOW_MESSAGE_TEXT = tr("Показывать текст сообщения", "Show message text")
    val CHAT_THEME = tr("Тема чата", "Chat theme")
    val THEME_COLOR = tr("Цвет темы", "Theme color")
    val BACKGROUND_OPACITY = tr("Прозрачность фона", "Background opacity")
    val REMOVE_BACKGROUND = tr("Удалить фон", "Remove background")
    val MAIL_CONNECTING = tr("Подключаемся", "Connecting")
    val MAIL_FAILED = tr("Не удалось подключиться", "Could not connect")
    val MAIL_EDIT = tr("Изменить ящик", "Edit mailbox")
    val MAIL_PORT_INVALID = tr("Порт — число от 1 до 65535", "The port is a number from 1 to 65535")
    val MAIL_MAILBOX_USED = tr("Ящик занят: %s из %s", "Mailbox used: %s of %s")
    val MAIL_MAILBOX_CROWDED = tr("Ящик почти полон — письма могут перестать приходить", "The mailbox is almost full — mail may stop arriving")
    val FILE_SETTINGS = tr("Настройки файлов", "File settings")
    val LIMIT_UP_TO = tr("до %s", "up to %s")
    val SWEEP_ORPHANS = tr("Очистить потерянные файлы", "Clean up orphaned files")
    val SWEEP_ORPHANS_DESC = tr("Удаляет фрагменты вложений, которые больше ни к чему не относятся.", "Deletes attachment chunks that no longer belong to anything.")
    val SWEEP_RESULT = tr("Очищено: файлов %s, фрагментов %s, освободилось %s", "Cleaned up: files %s, chunks %s, freed %s")
    val SET_BACKGROUND = tr("Установить фон чата", "Set chat background")
    val CHANGE_BACKGROUND = tr("Сменить фон чата", "Change chat background")
    val LAN_VISIBILITY = tr("Видимость в LAN", "Visibility on LAN")
    val ENABLE = tr("Включить", "Turn on")
    val UNVERIFIED_WARNING = tr("Этот контакт не подтвержден. Проверьте отпечаток для безопасного общения.", "This contact is not verified. Check the fingerprint for secure communication.")
    val YOU_PREFIX = tr("Вы: %s", "You: %s")
    val TRAY_OPEN = tr("Открыть", "Open")
    val TRAY_HIDE = tr("Скрыть", "Hide")
    val EXIT = tr("Выход", "Quit")
    val IDENTITY_NOT_AVAILABLE = tr("Профиль недоступен", "Profile unavailable")
    val AUTO = tr("Авто", "Auto")
    val NICKNAME = tr("Никнейм", "Nickname")
    val NAME_RESTART_NOTE = tr("Примечание: смена имени станет видна другим после перезапуска приложения.", "Note: the new name will be visible to others after the app restarts.")
    
    // New Features
    val REPLY = tr("Ответить", "Reply")
    val SEARCH = tr("Поиск", "Search")
    val SEARCH_HINT = tr("Найти сообщения...", "Find messages...")
    val NO_RESULTS = tr("Ничего не найдено", "Nothing found")
    val MESSAGE_UNAVAILABLE = tr("Сообщение недоступно", "Message unavailable")
    val SHARE_TO = tr("Поделиться в...", "Share to...")
    val ATTACH_FILES = tr("Прикрепить файлы", "Attach files")
    val DOWNLOAD_DIR = tr("Папка для загрузок", "Downloads folder")
    val SELECT_FOLDER = tr("Выбрать папку", "Choose folder")
    val AUTO_ACCEPT_LIMIT = tr("Автоприем файлов до", "Auto-accept files up to")
    val LIMIT_NEVER = tr("Никогда", "Never")
    val LIMIT_ALWAYS = tr("Всегда", "Always")
    val LIMIT_DISABLED = tr("Выключено", "Off")
    val SIZE_MB = tr("%s МБ", "%s MB")
    val OPEN_FILE = tr("Открыть файл", "Open file")
    val OPEN_FOLDER = tr("Открыть папку", "Open folder")
    val FILE_SAVED = tr("Файл сохранен: %s", "File saved: %s")
    val DOWNLOAD = tr("Скачать", "Download")
    val ACCEPT = tr("Принять", "Accept")
    val DECLINE = tr("Отклонить", "Decline")
    val SELECT_ACCOUNT = tr("Выберите аккаунт", "Choose an account")
    val NO_ACCOUNTS = tr("Нет доступных аккаунтов", "No accounts available")
    val FIND_HIDDEN = tr("Найти скрытый", "Find hidden")
    val FIND = tr("Найти", "Find")
    val HIDDEN_PIN_DESC = tr("Введите PIN для поиска скрытого аккаунта", "Enter the PIN to find the hidden account")
    val FINDING_HIDDEN_DESC = tr("Поиск скрытого аккаунта...", "Looking for the hidden account...")

    // Transports
    val TRANSPORTS = tr("Транспорты", "Transports")

    // Сопряжённые устройства (сторона полного клиента)
    val PAIRED_DEVICES = tr("Сопряжённые устройства", "Paired devices")
    val PAIRED_DEVICES_DESC = tr("Второй экран для этого аккаунта: у него нет своих ключей и истории, для собеседников вы остаётесь одним пользователем.", "A second screen for this account: it has no keys or history of its own, and to the people you talk to you stay one user.")
    val PAIRED_NONE = tr("Сопряжённых устройств нет", "No paired devices")
    val PAIR_DEVICE = tr("Сопрячь устройство", "Pair a device")
    val PAIR_LABEL = tr("Название устройства", "Device name")
    val PAIR_LABEL_DEFAULT = tr("Ноутбук", "Laptop")
    val PAIR_WAITING = tr("Готовим ссылку…", "Preparing the link…")
    val PAIR_SCAN = tr("Отсканируйте код на втором устройстве или вставьте ссылку в «Подключить как компаньон».", "Scan the code on the second device or paste the link into “Sign in as companion”.")
    val PAIR_ONCE = tr("Ссылка показывается один раз: в ней секрет сопряжения, и больше она нигде не хранится. Кто её получит — станет вторым экраном этого аккаунта до отзыва.", "The link is shown once: it carries the pairing secret and is not stored anywhere else. Whoever gets it becomes a second screen of this account until it is revoked.")
    val PAIR_COPY = tr("Копировать ссылку", "Copy link")
    val PAIR_COPIED = tr("Скопировано — буфер очистится через минуту", "Copied — the clipboard will be cleared in a minute")
    val PAIR_CONNECTED = tr("Устройство «%s» подключено", "Device “%s” connected")
    val PAIR_CLOSE_TITLE = tr("Устройство ещё не подключилось", "The device has not connected yet")
    val PAIR_CLOSE_TEXT = tr("После закрытия ссылка больше не покажется. Если вы её никуда не передали, сопряжение лучше отменить — иначе в списке останется запись, по которой никто не подключится.", "After closing, the link will not be shown again. If you did not pass it on anywhere, it is better to cancel the pairing — otherwise the list keeps an entry nobody will connect through.")
    val PAIR_CANCEL = tr("Отменить сопряжение", "Cancel pairing")
    val PAIR_KEEP = tr("Закрыть, ссылка передана", "Close, the link is passed on")
    val PAIR_REVOKE = tr("Отозвать", "Revoke")
    val PAIR_REVOKE_TITLE = tr("Отозвать «%s»?", "Revoke “%s”?")
    val PAIR_REVOKE_TEXT = tr("Сессия оборвётся сразу: устройство перестанет видеть переписку и писать от вашего имени. Подключить его снова можно только новым сопряжением.", "The session will break immediately: the device will stop seeing the conversation and writing on your behalf. It can only be connected again through a new pairing.")
    val DEVICE_PAIRED_AT = tr("Сопряжено %s", "Paired %s")
    val DEVICE_NEVER_SEEN = tr("Ни разу не подключалось", "Never connected")
    val DEVICE_LAST_SEEN = tr("Последний раз на связи %s", "Last seen %s")
    val DEVICE_CONNECTED = tr("На связи", "Connected")
    val DEVICE_CACHE_EXPIRED = tr("Больше 30 суток без связи — кэш на устройстве стёрт", "More than 30 days without a connection — the cache on the device is erased")
    val DEVICE_LOCAL_ONLY = tr("Работает, когда устройства в одной сети", "Works when the devices are on the same network")
    val DEVICE_ANYWHERE = tr("Доступно и вне общей сети", "Also works outside the shared network")

    // Превью сообщений и уведомления
    val PREVIEW_SPOILER = tr("спойлер", "spoiler")
    val PREVIEW_PHOTO = tr("[фото]", "[photo]")
    val PREVIEW_VIDEO = tr("[видео]", "[video]")
    val PREVIEW_AUDIO = tr("[аудио]", "[audio]")
    val PREVIEW_FILE = tr("[файл]", "[file]")
    val PREVIEW_CONTACT = tr("[контакт]", "[contact]")
    val PREVIEW_EMPTY = tr("[сообщение]", "[message]")
    val NOTIFY_NEW_MESSAGE = tr("Новое сообщение", "New message")
    val NOTIFY_MORE = tr("%s\n+%d ещё", "%s\n+%d more")
    val NOTIFY_REACTION_TO = tr("%s на: %s", "%s to: %s")
    val NOTIFY_REACTION = tr("Реакция %s", "Reaction %s")
    val NOTIFY_OPEN = tr("Открыть", "Open")

    // Резервная копия
    val BACKUP = tr("Резервная копия", "Backup")
    val BACKUP_DESC = tr("Единственный способ перенести переписку на другое устройство: синхронизации и облака нет.", "The only way to move the conversation to another device: there is no sync and no cloud.")
    val BACKUP_EXPORT = tr("Сохранить архив", "Save archive")
    val BACKUP_MERGE = tr("Добавить контакты из архива", "Add contacts from an archive")
    val BACKUP_SCOPE = tr("Что сохранить", "What to save")
    val BACKUP_SCOPE_EVERYTHING = tr("Всё", "Everything")
    val BACKUP_SCOPE_EVERYTHING_DESC = tr("Переписка, вложения, знакомства — перенос целиком", "Conversations, attachments, contacts — the whole move")
    val BACKUP_SCOPE_NO_FILES = tr("Без вложений", "Without attachments")
    val BACKUP_SCOPE_NO_FILES_DESC = tr("Легче на порядок; файлы остаются на этом устройстве", "An order of magnitude lighter; files stay on this device")
    val BACKUP_SCOPE_GRAPH = tr("Только связи", "Connections only")
    val BACKUP_SCOPE_GRAPH_DESC = tr("Контакты, аватарки и своя личность — без истории", "Contacts, avatars and your own identity — without history")
    val BACKUP_PHRASE = tr("Парольная фраза", "Passphrase")
    val BACKUP_PHRASE_REPEAT = tr("Повторите фразу", "Repeat the phrase")
    val BACKUP_PHRASE_MISMATCH = tr("Фразы не совпадают", "The phrases do not match")
    val BACKUP_PHRASE_DESC = tr("Ей вы будете открывать архив. Мы её не храним и не покажем.", "You will open the archive with it. We do not keep it and will not show it.")
    val BACKUP_NO_PHRASE = tr("Без фразы — только ключ", "No phrase — key only")
    val BACKUP_NO_PHRASE_DESC = tr("Архив откроется только 52-значным ключом, который покажем после сохранения.", "The archive will only open with a 52-character key, which we will show after saving.")
    val BACKUP_CHOOSE_FILE = tr("Выбрать место и сохранить", "Choose a location and save")
    val BACKUP_SAVE_TITLE = tr("Сохранить архив", "Save archive")
    val BACKUP_FILE_EXISTS = tr("Такой файл уже есть — выберите другое имя.", "That file already exists — choose another name.")
    val BACKUP_WORKING = tr("Идёт работа с архивом — это может занять время…", "Working on the archive — this may take a while…")
    val BACKUP_FAILED = tr("Не получилось: %s", "Failed: %s")
    val BACKUP_DONE = tr("Архив сохранён", "Archive saved")
    val BACKUP_SAVED_TO = tr("Файл: %s", "File: %s")
    val BACKUP_SIZE = tr("Вложений: %d, %s", "Attachments: %d, %s")
    val BACKUP_KEY = tr("Ключ архива", "Archive key")
    val BACKUP_KEY_ONLY = tr("Без этой строки архив не откроется никогда. Сохраните её в менеджер паролей.", "Without this string the archive will never open. Save it to a password manager.")
    val BACKUP_KEY_SPARE = tr("Архив открывается вашей фразой. Если забудете её — откроете этой строкой.", "The archive opens with your phrase. If you forget it, this string will open it.")
    val BACKUP_KEY_SAVED = tr("Я сохранил ключ", "I saved the key")
    val BACKUP_SHOW_IN_FOLDER = tr("Показать в папке", "Show in folder")
    val BACKUP_DONE_BUTTON = tr("Готово", "Done")

    // Ввоз архива
    val IMPORT_ARCHIVE = tr("Восстановить из архива", "Restore from archive")
    val IMPORT_PICK_TITLE = tr("Выберите архив", "Choose an archive")
    val IMPORT_NOT_ARCHIVE = tr("Не удалось прочитать архив: %s", "Could not read the archive: %s")
    val IMPORT_CONTENTS = tr("В архиве: %s", "In the archive: %s")
    val IMPORT_BY_PHRASE = tr("Фраза", "Phrase")
    val IMPORT_BY_KEY = tr("У меня есть ключ", "I have a key")
    val IMPORT_KEY_FIELD = tr("Ключ архива", "Archive key")
    val IMPORT_LABEL = tr("Название аккаунта на этом компьютере", "Account name on this computer")
    val IMPORT_LABEL_DEFAULT = tr("Восстановленный", "Restored")
    val IMPORT_BUTTON = tr("Восстановить", "Restore")
    val IMPORT_DONE = tr("Аккаунт восстановлен", "Account restored")
    val IMPORT_STATS = tr("Контактов: %d, сообщений: %d, вложений: %d", "Contacts: %d, messages: %d, attachments: %d")
    val IMPORT_OLD_DEVICE = tr("Прежним устройством с этим аккаунтом пользоваться больше нельзя: два устройства с одной личностью запутают собеседников. Для второго экрана есть режим компаньона.", "The old device can no longer be used with this account: two devices with one identity will confuse the people you talk to. For a second screen there is companion mode.")
    val IMPORT_OLD_PIN = tr("Аккаунт открывается прежним PIN — тем, что был на старом устройстве.", "The account opens with the old PIN — the one from the old device.")
    val IMPORT_DEVICE_BOUND = tr("Если на прежнем устройстве аккаунт был привязан к нему (секрет устройства), здесь он не откроется: этот секрет в архив не попадает.", "If the account was bound to the old device (a device secret), it will not open here: that secret does not go into the archive.")
    val BACKUP_DEVICE_BOUND = tr("Аккаунт привязан к этому компьютеру: восстановить его из архива получится только здесь — секрет устройства в архив не попадает.", "The account is bound to this computer: it can only be restored from an archive here — the device secret does not go into the archive.")
    val IMPORT_PARTIAL_FILES = tr("Вложений доехало целиком %d из %d — остальные остались на прежнем устройстве и показываются неполученными.", "%d of %d attachments arrived in full — the rest stayed on the old device and are shown as not received.")
    val MERGE_UNLOCK_TITLE = tr("Добавить контакты из архива", "Add contacts from an archive")
    val MERGE_BUTTON = tr("Добавить", "Add")
    val MERGE_DONE = tr("Контакты добавлены", "Contacts added")
    val MERGE_ADDED = tr("Добавлено: %d. Уже были: %d (их не трогали).", "Added: %d. Already there: %d (left untouched).")
    val MERGE_REFUSED = tr("Негодных записей: %d — стоит проверить, откуда этот архив.", "Bad entries: %d — worth checking where this archive came from.")
    val MERGE_FOREIGN = tr("Список чужой: никто из добавленных не сверен, локальные имена не перенесены.", "The list belongs to someone else: none of the added contacts are verified, and local names were not carried over.")

    // Bluetooth
    val BT_TRANSPORT = "Bluetooth"
    val BT_DESC = tr("Находит людей рядом по эфиру, вообще без сети", "Finds people nearby over the air, with no network at all")
    val BT_WARNING = tr("Пока Bluetooth включён, компьютер постоянно объявляет себя в эфире и слушает его: любой рядом с Bluetooth-сканером видит, что здесь работает Ratatosk, — как в локальной сети, но без сети вовсе. Содержимое переписки эфиром не раскрывается.", "While Bluetooth is on, the computer keeps announcing itself over the air and listening to it: anyone nearby with a Bluetooth scanner sees that Ratatosk is running here — as on a local network, but with no network at all. The contents of the conversation are not revealed over the air.")
    val BT_NOT_UP = tr("Включено, но эфир не поднялся — проверьте, что Bluetooth-адаптер включён", "Turned on, but the radio did not come up — check that the Bluetooth adapter is on")

    // Диагностика
    val DIAGNOSTICS = tr("Диагностика", "Diagnostics")
    val CORE_LOG = tr("Вести журнал ядра", "Keep a core log")
    val CORE_LOG_DESC = tr("Файл для разбора неполадок. В нём нет текстов сообщений, но есть служебные сведения о соединениях: адреса пиров и реле, время событий. Перезаписывается при каждом запуске; включение и выключение — со следующего запуска.", "A file for troubleshooting. It holds no message texts, but it does hold service details about connections: peer and relay addresses, event times. Overwritten at every launch; turning it on and off takes effect from the next launch.")
    val CORE_LOG_SHOW = tr("Показать журнал в папке", "Show the log in the folder")

    // Yggdrasil
    val YGG_TRANSPORT = "Yggdrasil"
    val YGG_DESC = tr("Меш-сеть: доставка за пределами локальной сети без серверов", "Mesh network: delivery beyond the local network without servers")
    val YGG_MODE_OFF = tr("Выключен", "Off")
    val YGG_MODE_EMBEDDED = tr("Свой узел", "Built-in node")
    val YGG_MODE_EXTERNAL = tr("Внешний демон", "External daemon")
    val YGG_ADDRESS = tr("Адрес в меше: %s", "Address in the mesh: %s")
    val YGG_KEY = tr("Ключ узла: %s", "Node key: %s")
    val YGG_KEY_NOT_SET = tr("Ключ узла не задан — меш не работает", "No node key set — the mesh does not work")
    val YGG_SET_KEY = tr("Задать ключ", "Set key")
    val YGG_KEY_TITLE = tr("Ключ узла Yggdrasil", "Yggdrasil node key")
    val YGG_KEY_HINT = tr("Открытый ключ вашего демона: поле key в выводе `yggdrasilctl getSelf`. Из адреса 200::/7 ключ вывести нельзя.", "The public key of your daemon: the key field in the output of `yggdrasilctl getSelf`. The key cannot be derived from a 200::/7 address.")
    val YGG_KEY_FIELD = tr("Ключ (64 hex-символа)", "Key (64 hex characters)")
    val YGG_KEY_INVALID = tr("Ключ — это 64 шестнадцатеричных символа", "The key is 64 hexadecimal characters")
    val YGG_KEY_FROM_DAEMON = tr("Взять у yggdrasilctl", "Take from yggdrasilctl")
    val YGG_KEY_DAEMON_FAILED = tr("yggdrasilctl не ответил: %s", "yggdrasilctl did not answer: %s")
    val YGG_KEY_CLEAR = tr("Снять ключ", "Clear key")
    val YGG_PEERS = tr("Пиры: %d", "Peers: %d")
    val YGG_PEERS_ALIVE = tr("Пиры: %d из %d на связи", "Peers: %d of %d connected")
    val YGG_NO_PEERS = tr("Пиры не заданы — узлу не с кем соединиться", "No peers set — the node has nobody to connect to")
    val YGG_EDIT_PEERS = tr("Изменить пиров", "Edit peers")
    val YGG_PEERS_TITLE = tr("Пиры встроенного узла", "Peers of the built-in node")
    val YGG_PEERS_HINT = tr("По одному на строку: tcp://узел:порт, tls://…, quic://… Пир видит, кто кому пишет в меше, — выбирайте тех, кому доверяете.", "One per line: tcp://host:port, tls://…, quic://… A peer sees who writes to whom in the mesh — pick the ones you trust.")
    val YGG_PEER_INBOUND = tr("входящий", "inbound")
    val YGG_LATENCY = tr("%.0f мс", "%.0f ms")

    // Nostr
    val NOSTR_TRANSPORT = "Nostr"
    val NOSTR_DESC = tr("Доставка через реле, когда собеседник не в сети", "Delivery through relays when the other person is offline")
    val NOSTR_NPUB = tr("Ключ: %s", "Key: %s")
    val NOSTR_RELAYS_ALIVE = tr("Реле: %d из %d на связи", "Relays: %d of %d connected")
    val NOSTR_RELAYS = tr("Реле: %d", "Relays: %d")
    val NOSTR_NO_RELAYS = tr("Реле не заданы — ступень не работает", "No relays set — this step does not work")
    val NOSTR_EDIT_RELAYS = tr("Изменить реле", "Edit relays")
    val NOSTR_RELAYS_TITLE = tr("Реле nostr", "Nostr relays")
    val NOSTR_RELAYS_HINT = tr("По одному на строку: wss://relay.example (схему можно не писать). Несколько реле делят след переписки между собой. Открытый ws:// — только до этого компьютера.", "One per line: wss://relay.example (the scheme can be left out). Several relays split the trace of the conversation between them. Plain ws:// — only as far as this computer.")
    val NOSTR_ADVERTISED = tr("В карточку уходят первые %d: %s", "The first %d go into the card: %s")
    val NOSTR_DIRECT = tr("Мимо Tor", "Bypass Tor")
    val NOSTR_DIRECT_DESC = tr("Только там, где Tor недоступен", "Only where Tor is unavailable")
    val INVALID_LINES = tr("Не подходят: %s", "Not accepted: %s")
    val COPIED = tr("Скопировано", "Copied")
    val TOR_TRANSPORT = tr("Tor транспорт", "Tor transport")
    val TOR_DESC = tr("Использовать Tor для анонимного общения", "Use Tor for anonymous communication")
    val TOR_STATUS = tr("Статус Tor", "Tor status")
    val TOR_BOOTSTRAP = tr("Подключение: %d%%", "Connecting: %d%%")
    val TOR_CONNECTED = tr("Подключено", "Connected")
    val TOR_BLOCKED = tr("Заблокировано: %s", "Blocked: %s")
    val MAIL_TRANSPORT = tr("Почтовый транспорт", "Mail transport")
    val MAIL_DESC = tr("Использовать электронную почту (chatmail) как резервный канал", "Use email (chatmail) as a fallback channel")
    val MAIL_STATUS = tr("Статус почты", "Mail status")
    val MAIL_ONLINE = tr("В сети", "Online")
    val MAIL_OFFLINE = tr("Не в сети", "Offline")
    val MAIL_CONFIGURED = tr("Настроено: %s", "Configured: %s")
    val MAIL_NOT_CONFIGURED = tr("Не настроено", "Not configured")
    val MAIL_SETUP = tr("Настроить почту", "Set up mail")
    val MAIL_ADDRESS = tr("Email адрес", "Email address")
    val MAIL_PASSWORD = tr("Пароль", "Password")
    val MAIL_IMAP_HOST = tr("IMAP сервер", "IMAP server")
    val MAIL_IMAP_PORT = tr("IMAP порт", "IMAP port")
    val MAIL_SMTP_HOST = tr("SMTP сервер", "SMTP server")
    val MAIL_SMTP_PORT = tr("SMTP порт", "SMTP port")
    val MAIL_VIA_TOR = tr("Работать через Tor", "Work through Tor")
    val MAIL_VIA_TOR_DESC = tr("Скрывает ваш IP от почтового сервера, но работает медленнее", "Hides your IP from the mail server, but works slower")
    val MAIL_REGISTER = tr("Завести новый ящик", "Create a new mailbox")
    val MAIL_REGISTER_DESC = tr("Автоматическая регистрация на chatmail сервере", "Automatic registration on a chatmail server")
    val MAIL_SERVER_URL = tr("URL сервера", "Server URL")
    val MAIL_DELETE = tr("Удалить почтовый аккаунт", "Delete mail account")
    val MAIL_DELETE_CONFIRM = tr("Вы уверены, что хотите удалить настройки почты?", "Are you sure you want to delete the mail settings?")

    // Contact Details & Reachability
    val REACHABILITY = tr("Достижимость", "Reachability")
    val DIRECT_CHANNEL = tr("Прямой канал", "Direct channel")
    val ROUTE = tr("Маршрут: %s", "Route: %s")
    val RISING = tr("Поднимается: %s", "Coming up: %s")
    val ANOMALIES = tr("Аномалии", "Anomalies")
    val UNKNOWN_SESSIONS = tr("Неизвестные сессии: %d", "Unknown sessions: %d")
    val BAD_TAGS = tr("Битые теги: %d", "Broken tags: %d")
    val MALFORMED_FRAMES = tr("Искаженные кадры: %d", "Malformed frames: %d")
    val STATUS_ENABLED = tr("Включен", "Enabled")
    val STATUS_READY = tr("Готов", "Ready")
    val STATUS_ADDRESSABLE = tr("Адресуем", "Addressable")
    val CARD_VERSION = tr("Версия карточки: %d", "Card version: %d")
    val ONION_ADDRESS = tr("Onion адрес", "Onion address")
    val CONTACT_ADDED = tr("Добавлен: %s", "Added: %s")
    val TECHNICAL_DETAILS = tr("Технические детали", "Technical details")

    // Списки чатов и контактов
    val SEARCH_CHATS_HINT = tr("Найти чат...", "Find a chat...")
    val SEARCH_CONTACTS_HINT = tr("Найти контакт...", "Find a contact...")
    val NOTHING_FOUND = tr("Ничего не найдено", "Nothing found")
    val OPEN_CARD = tr("Открыть карточку", "Open card")
    val GROUP_CARD = tr("О группе", "About the group")
    val DATE_YESTERDAY = tr("вчера", "yesterday")
    val CONTACT_NOT_IN_LIST = tr("Этого человека нет в ваших контактах", "This person is not in your contacts")
    val CONTACT_NOT_IN_LIST_DESC = tr("Он написал в группе. Чтобы завести личный чат, попросите у него ссылку-приглашение и добавьте контакт.", "They wrote in a group. To start a private chat, ask them for an invite link and add the contact.")
    val GROUP_MEMBER = tr("Участник группы", "Group member")
    val YGG_ADDRESS_LABEL = tr("Адрес в меше", "Address in the mesh")

    // Просмотр картинок
    val MEDIA_ZOOM_IN = tr("Увеличить", "Zoom in")
    val MEDIA_ZOOM_OUT = tr("Уменьшить", "Zoom out")
    val MEDIA_COPY = tr("Копировать картинку", "Copy image")
    val MEDIA_CANNOT_SHOW = tr("Не удалось прочитать картинку", "Could not read the image")

    // Кадрирование аватарки
    val AVATAR_CROP_TITLE = tr("Фото профиля", "Profile photo")
    val AVATAR_CROP_GROUP_TITLE = tr("Картинка группы", "Group picture")
    val AVATAR_CROP_HINT = tr("Колесо мыши — масштаб, перетаскивание — сдвиг", "Mouse wheel — zoom, drag — move")
    val AVATAR_CROP_FAILED = tr("Не удалось подготовить картинку — прежняя осталась на месте", "Could not prepare the image — the previous one stayed in place")
    val AVATAR_CHANGE = tr("Сменить картинку", "Change picture")
    val AVATAR_REMOVE = tr("Удалить фото", "Remove photo")

    // --- Каналы (фаза 2, §6, §10) ----------------------------------------
    val CHANNEL = tr("Канал", "Channel")
    val CHANNEL_NO_TITLE_YET = tr(
        "Канал — его представление ещё не приехало",
        "Channel — its description has not arrived yet",
    )
    val CHANNEL_NO_WRITE_RIGHT = tr("Права писать в этом канале нет", "You have no right to write in this channel")
    val CHANNEL_AWAITING = tr("Ждём, пока владелец впустит", "Waiting for the owner to let you in")
    val CHANNEL_NOT_READABLE = tr("Читать пока нечем: ключ ещё не приехал", "Nothing to read it with yet: the key has not arrived")
    val CHANNEL_LEFT = tr("Вы больше не читаете этот канал", "You are not reading this channel any more")
    val CHANNEL_DETAILS = tr("О канале", "Channel")
    val CHANNEL_READERS = tr("Читатели", "Readers")
    val CHANNEL_READERS_COUNT = tr("Читателей: %d", "%d readers")
    val CHANNEL_OPEN_SHORT = tr("Открытый канал", "Open channel")
    val CHANNEL_PRIVATE_SHORT = tr("Канал по приглашению", "Channel by invitation")
    val CHANNEL_KIND = tr("Порода", "Kind")
    val CHANNEL_KIND_OPEN = tr("Открытый: читает любой, кому дали ссылку", "Open: anyone with the link reads it")
    val CHANNEL_KIND_PRIVATE = tr("По приглашению: читателей впускаете вы", "By invitation: you let readers in")
    val CHANNEL_KIND_UNKNOWN = tr(
        "Пока неизвестна: представление канала ещё не приехало",
        "Not known yet: the channel description has not arrived",
    )

    val CHANNEL_CREATE = tr("Создать канал", "Create a channel")
    val CHANNEL_TITLE_HINT = tr("Название канала", "Channel name")
    val CHANNEL_SUBSCRIBE = tr("Подписаться на канал", "Subscribe to a channel")
    val CHANNEL_SUBSCRIBE_HINT = tr("Ссылка на канал", "Channel link")
    val CHANNEL_SUBSCRIBE_ACTION = tr("Подписаться", "Subscribe")
    val CHANNEL_SUBSCRIBE_OPEN_IF = tr("Если ссылка на открытый канал:", "If the link is to an open channel:")
    val CHANNEL_SUBSCRIBE_PRIVATE_IF = tr("Если ссылка по приглашению:", "If the link is by invitation:")
    val CHANNEL_UNSUBSCRIBE = tr("Отписаться", "Unsubscribe")
    val CHANNEL_UNSUBSCRIBE_WARNING = tr(
        "Вместе с чатом уйдёт и архив: ключи чтения хранятся здесь и больше нигде. " +
            "Вернувшись по той же ссылке, вы прочтёте только то, что приедет заново.",
        "The chat and its archive go with it: the reading keys are kept here and nowhere else. " +
            "Coming back by the same link, you will only read what arrives anew.",
    )
    val CHANNEL_OWNER_STAYS = tr(
        "Из своего канала не уходят: отписываться не от кого.",
        "A channel of your own cannot be left: there is nobody to unsubscribe from.",
    )

    val CHANNEL_LINK = tr("Ссылка на канал", "Channel link")
    val CHANNEL_LINK_SHOW = tr("Показать ссылку", "Show the link")
    val CHANNEL_LINK_FAILED = tr("Не удалось собрать ссылку", "Could not build the link")
    val CHANNEL_LINK_OPEN_WARNING = tr(
        "В этой ссылке ключ чтения: любой, к кому она попадёт, будет читать канал.",
        "This link carries the reading key: anyone it reaches will read the channel.",
    )

    val CHANNEL_REQUESTS = tr("Просятся внутрь", "Asking to be let in")
    val CHANNEL_REQUESTS_NONE = tr("Никто не просится", "No one is asking")
    val CHANNEL_NO_REFUSAL = tr("Отказа не отправить: отказ — это молчание.", "There is no refusal to send: staying silent is the refusal.")
    val CHANNEL_ADMIT = tr("Впустить", "Let in")
    val CHANNEL_ADMITTED = tr("Впущенные читатели", "Readers you let in")
    val CHANNEL_ADMITTED_NONE = tr("Пока никого", "No one yet")
    val CHANNEL_ADMITTED_BY = tr("впустил %s", "let in by %s")
    val CHANNEL_ADMIT_CONTACT = tr("Впустить контакт", "Let a contact in")
    val CHANNEL_ADMIT_CONTACT_DESC = tr(
        "Ключ чтения уедет ему без всякой ссылки: он запечатывается на его карточку, " +
            "поэтому впустить так можно только контакт.",
        "They will get the reading key without any link: it is sealed to their card, " +
            "so only a contact can be let in this way.",
    )
    val CHANNEL_ADMIT_NONE_LEFT = tr("Все ваши контакты уже впущены", "Everyone in your contacts has been let in already")

    val CHANNEL_GRANTS = tr("Выданные права", "Rights given out")
    val CHANNEL_GRANTS_NONE = tr("Никому ничего не выдано", "Nobody has been given anything")
    val CHANNEL_GRANT_EDIT = tr("Права", "Rights")
    val CHANNEL_RIGHT_WRITE = tr("Писать", "Write")
    val CHANNEL_RIGHT_ADMIT = tr("Впускать читателей", "Let readers in")
    val CHANNEL_RIGHT_EVICT = tr("Исключать", "Evict")
    val CHANNEL_RIGHT_EDIT = tr("Править представление", "Edit the description")
    val CHANNEL_RIGHT_NONE = tr("Ничего: право снимается", "Nothing: the right is taken away")
    val CHANNEL_RIGHT_TERM = tr("На какой срок", "For how long")
    val CHANNEL_RIGHT_TERM_MONTH = tr("Месяц", "A month")
    val CHANNEL_RIGHT_TERM_QUARTER = tr("Три месяца", "Three months")
    val CHANNEL_RIGHT_TERM_YEAR = tr("Год", "A year")
    val CHANNEL_RIGHT_TERM_REQUIRED = tr(
        "Срок обязателен: непродлённое право истекает само, а право без срока означало бы отзыв — в рое он не работает.",
        "A term is required: an unrenewed right expires by itself, and a right without a term would mean revocation — which does not work in a swarm.",
    )
    val CHANNEL_RIGHT_UNTIL = tr("до %s", "until %s")
    val CHANNEL_RIGHT_EXPIRED = tr("истекло", "expired")
    val CHANNEL_GRANTS_EXPIRING = tr(
        "Истекают в течение месяца: %d. Продлите, пока не поздно.",
        "Rights running out within a month: %d. Renew them before they lapse.",
    )
    val CHANNEL_MY_RIGHT_UNTIL = tr("Ваше право писать — до %s", "Your right to write lasts until %s")
    val CHANNEL_OWNER_QUIET = tr("От владельца давно ничего не приходило", "Nothing has come from the owner for a long time")

    val CHANNEL_ROTATE = tr("Повернуть ключ чтения", "Rotate the reading key")
    val CHANNEL_POW_TITLE = tr("Цена слова", "Price of a word")
    val CHANNEL_POW_CURRENT = tr("Сейчас: %d бит", "Now: %d bits")
    val CHANNEL_POW_FREE = tr("Сейчас: работа не требуется", "Now: no work required")
    val CHANNEL_POW_HINT = tr("Бит", "Bits")
    val CHANNEL_POW_EXPLAIN = tr(
        "Работа поднимает пол против тривиального флуда; против видеокарты не работает, " +
            "телефон наказывает всерьёз (§11). Это фильтр первого уровня, а не защита.",
        "Proof of work raises the floor against trivial flooding; it does not work against a graphics card " +
            "and punishes a phone in earnest (§11). It is a first-level filter, not a defence.",
    )

    val CHANNEL_SEEDING = tr("Раздача канала", "Sharing the channel")
    val CHANNEL_SEEDING_OFF = tr("Не раздаём: никому", "Not sharing: to nobody")
    val CHANNEL_SEEDING_OFF_DESC = tr(
        "Канал читается по-прежнему — выключается раздача, а не подписка.",
        "The channel still reads as before — what stops is the giving, not the subscription.",
    )
    val CHANNEL_SEEDING_QUIET = tr("Тихо (умолчание)", "Quietly (default)")
    val CHANNEL_SEEDING_QUIET_DESC = tr(
        "Адрес не объявлен, набрать вас нельзя, но тем, к кому подключились сами, отдаёте наравне со всеми.",
        "The address is not announced and nobody can dial you, but to those you connected to yourself you give like everyone else.",
    )
    val CHANNEL_SEEDING_ANNOUNCED = tr("Объявленный сид", "Announced seed")
    val CHANNEL_SEEDING_ANNOUNCED_DESC = tr(
        "Адрес уходит в каталог, набирают незнакомые, отдаёте всякому, кто спросил.",
        "The address goes into the catalogue, strangers dial it, and you give to whoever asks.",
    )
    val CHANNEL_SEEDS = tr("Кто ещё раздаёт", "Who else is sharing")
    val CHANNEL_SEEDS_NONE = tr("Никто не объявлялся", "Nobody has announced themselves")
    val CHANNEL_SEED_UNTIL = tr("объявлен до %s", "announced until %s")
    val CHANNEL_SEED_UNVERIFIED = tr("карточку проверить было нечем", "the card could not be checked")
    val CHANNEL_SHARING_LEVEL = tr("Кому отдавать", "Whom to give to")
    val CHANNEL_SHARING_EVERYONE = tr("Всем, кто спросил (умолчание)", "Everyone who asks (default)")
    val CHANNEL_SHARING_CONTACTS = tr("Только контактам", "Only contacts")
    val CHANNEL_SHARING_VERIFIED = tr(
        "Только сверенным — их обычно единицы, это ближе к «не раздавать»",
        "Only verified — usually a handful, closer to not sharing at all",
    )
    val CHANNEL_RENAME = tr("Переименовать канал", "Rename the channel")


    // Переход по ссылке: её прислал собеседник, и подпись у неё любая.
    val LINK_OPEN_TITLE = tr("Перейти по ссылке?", "Open this link?")
    val LINK_LEADS_TO = tr("Ведёт на", "Leads to")
    val LINK_UNKNOWN_HOST = tr("Адрес не удалось разобрать", "The address could not be read")
    val LINK_OPEN = tr("Перейти", "Open")
    val LINK_WARN_SHOWN_HOST = tr(
        "В тексте написано %s, а ведёт в другое место",
        "The text says %s, the address leads elsewhere",
    )
    val LINK_WARN_USERINFO = tr(
        "Всё, что до @, — не адрес: ведёт на %s",
        "Everything before @ is not the address — it leads to %s",
    )
    val LINK_WARN_PUNYCODE = tr(
        "Хост записан кодом (xn--): он может выглядеть как другой",
        "The host is written in punycode (xn--): it may look like another one",
    )
    val LINK_WARN_SCHEME = tr("Это не веб-адрес: %s", "This is not a web address: %s")
    val PROFILE_COMPANION_DESC = tr("Это второй экран телефона: ключи, отпечаток и ссылка-приглашение живут на телефоне. Фото профиля можно посмотреть и сменить здесь — оно уйдёт на телефон. Имя задаётся на телефоне.", "This is the second screen of your phone: the keys, the fingerprint and the invite link live on the phone. The profile photo can be viewed and changed here — it will go to the phone. The name is set on the phone.")
    val CONTACT_COMPANION_DESC = tr("Второй экран показывает только имя и фото: ключей и сверки личности на нём нет. Подтвердить личность можно на телефоне.", "The second screen shows only the name and photo: it has no keys and no identity check. Identity can be verified on the phone.")


    // Companion Mode
    val LINK_COMPANION = tr("Войти как компаньон", "Sign in as companion")
    val SCAN_QR = tr("Сканировать QR-код", "Scan QR code")
    val PASTE_LINK = tr("Вставить ссылку", "Paste link")
    val COMPANION_DESC = tr("Использовать этот компьютер как второй экран для вашего телефона", "Use this computer as a second screen for your phone")
    val LINKING_COMPANION = tr("Подключение к телефону...", "Connecting to the phone...")
    val COMPANION_CACHE_DESC = tr("Сохранять копию переписки на этом компьютере для работы без телефона", "Keep a copy of the conversation on this computer to work without the phone")
    val INVALID_PAIRING_URI = tr("Некорректная ссылка сопряжения", "Invalid pairing link")
    val COMPANION_STALE = tr("Данные с прошлого подключения — телефон ещё не ответил", "Data from the last connection — the phone has not answered yet")
    val COMPANION_MANUAL_TITLE = tr("Телефон не находит этот компьютер?", "The phone does not find this computer?")
    val COMPANION_MANUAL_DESC = tr("Обычно телефон находит второй экран сам. Если он в другой сети, за VPN или в гостевом Wi-Fi, введите на телефоне эти значения руками.", "Usually the phone finds the second screen by itself. If it is on another network, behind a VPN or on a guest Wi-Fi, enter these values on the phone by hand.")
    val COMPANION_MANUAL_PORT = tr("Порт", "Port")
    val COMPANION_MANUAL_KEY = tr("Ключ этого экрана", "Key of this screen")
    val COMPANION_MANUAL_HINT = tr("Нажмите на полосу «Подключаемся», чтобы увидеть это снова.", "Click the “Connecting” bar to see this again.")
    val COMPANION_CACHE = tr("Хранить копию переписки на этом компьютере", "Keep a copy of the conversation on this computer")
    val COMPANION_CACHE_ON_TITLE = tr("Что даёт и чего не даёт этот файл", "What this file gives and what it does not")
    val COMPANION_CACHE_ON_DESC = tr("Копия переписки шифруется, но ключ выводится из ссылки сопряжения, которая лежит на этой же машине. Значит защита работает против скопированного файла и резервной копии — и не работает против забранного компьютера.", "The copy of the conversation is encrypted, but the key is derived from the pairing link, which lies on this same machine. So the protection works against a copied file and a backup — and does not work against a computer that has been taken away.")
    val COMPANION_CACHE_OFF_DESC = tr("Выключение стирает уже сохранённое. Без копии окно показывает только то, что приехало с телефона в этот раз.", "Turning it off erases what is already saved. Without a copy the window shows only what came from the phone this time.")
    val COMPANION_CACHE_NEEDS_LINK = tr("Ссылки сопряжения нет в хранилище паролей — копию переписки нечем будет открыть в следующий раз", "The pairing link is not in the password store — there will be nothing to open the copy of the conversation with next time")
    val COMPANION_CACHE_FAILED = tr("Не удалось переключить копию переписки", "Could not switch the copy of the conversation")
    val COMPANION_SECTION = tr("Второй экран", "Second screen")
    val COMPANION_TOR = tr("Подключаться через Tor", "Connect through Tor")
    val COMPANION_TOR_DESC = tr("Нужен, если телефон не в одной сети с этим компьютером. Первый подъём идёт десятки секунд, и до него телефон дотянется только по локальной сети.", "Needed if the phone is not on the same network as this computer. The first start-up takes tens of seconds, and until then the phone can only reach it over the local network.")
    val COMPANION_ADVANCED = tr("Дополнительно", "Advanced")
    val COMPANION_PORT = tr("Порт для входящих (пусто — выберет система)", "Port for incoming connections (empty — the system picks one)")
    val COMPANION_PORT_INVALID = tr("Порт — число от 1 до 65535", "The port is a number from 1 to 65535")
    val COMPANION_PEER = tr("Адрес телефона, например 192.168.1.5:41234", "Phone address, for example 192.168.1.5:41234")
    val COMPANION_PEER_DESC = tr("Обычно не нужен: в общей сети телефон находится сам. Пригодится в гостевом Wi-Fi с изоляцией клиентов и за корпоративными точками.", "Usually not needed: on a shared network the phone is found by itself. Useful on a guest Wi-Fi with client isolation and behind corporate access points.")

    // Системные диалоги и служебные подписи
    val PICK_IMAGE = tr("Выберите изображение", "Choose an image")
    val PICK_FILES = tr("Выберите файлы", "Choose files")
    val PICK_DOWNLOAD_DIR = tr("Выберите папку для загрузок", "Choose a downloads folder")
    val SIZE_BYTES = tr("%.0f Б", "%.0f B")
    val SIZE_KILOBYTES = tr("%.1f КБ", "%.1f KB")
    val SIZE_MEGABYTES = tr("%.1f МБ", "%.1f MB")
    val SIZE_GIGABYTES = tr("%.1f ГБ", "%.1f GB")
    val ACCOUNT_SECTION = tr("Аккаунт", "Account")
    val COMPANION_UNLINK = tr("Отвязать и сменить аккаунт", "Unlink and switch account")
    val COMPANION_BADGE = tr("Компаньон", "Companion")
    val PAIRING_DELETE = tr("Удалить сопряжение", "Remove pairing")
    val SWITCH_ACCOUNT = tr("Сменить аккаунт", "Switch account")
    val LOADING = tr("Загрузка…", "Loading…")
    val WAITING_TOR = tr("Ожидание Tor…", "Waiting for Tor…")
    val PHONE_OFFLINE = tr("Телефон не на связи", "The phone is offline")
    val FETCH_BUSY = tr("Уже забираем другое вложение — дождитесь конца", "Another attachment is being fetched — wait for it to finish")

    /**
     * «2 вложения», «5 вложений», «21 вложение» — по-русски три формы,
     * строкой с подстановкой их не выразить. По-английски достаточно двух.
     */
    fun attachments(n: Int): String {
        if (!russian) return if (n == 1) "[1 attachment]" else "[$n attachments]"
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "вложений"
            mod10 == 1 -> "вложение"
            mod10 in 2..4 -> "вложения"
            else -> "вложений"
        }
        return "[$n $word]"
    }
}
