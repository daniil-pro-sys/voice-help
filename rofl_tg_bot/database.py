import aiosqlite
import datetime

DB_PATH = 'data/bot_database.db'

async def init_db():
    """Инициализирует базу данных и создает таблицы, если их нет."""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute('''
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id INTEGER NOT NULL,
            user_id INTEGER NOT NULL,
            user_fullname TEXT NOT NULL,
            reply_to_user_id INTEGER,
            content_type TEXT NOT NULL,
            message_text TEXT,
            timestamp DATETIME NOT NULL
        )
        ''')
        # В этой таблице должны быть все три колонки
        await db.execute('''
        CREATE TABLE IF NOT EXISTS group_settings (
            chat_id INTEGER PRIMARY KEY,
            selected_prompt TEXT DEFAULT 'mama.txt',
            selected_voice TEXT DEFAULT 'Zephyr',
            response_format TEXT DEFAULT 'voice'
        )
        ''')
        # Этот блок нужен, чтобы добавить колонку, если таблица уже существует
        try:
            await db.execute('ALTER TABLE group_settings ADD COLUMN response_format TEXT DEFAULT "voice"')
        except aiosqlite.OperationalError:
            # Колонка уже существует, всё в порядке
            pass
            
        await db.commit()
async def add_message(chat_id, user_id, user_fullname, content_type, text=None, reply_to_user_id=None):
    """Добавляет новое сообщение в базу данных."""
    timestamp = datetime.datetime.utcnow()
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "INSERT INTO messages (chat_id, user_id, user_fullname, reply_to_user_id, content_type, message_text, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (chat_id, user_id, user_fullname, reply_to_user_id, content_type, text, timestamp)
        )
        await db.commit()

# --- ДОБАВЛЯЕМ НОВЫЕ ФУНКЦИИ В КОНЕЦ ФАЙЛА ---

async def get_messages_for_chat(chat_id):
    """Возвращает все сообщения для указанного чата за последние 24 часа."""
    twenty_four_hours_ago = datetime.datetime.utcnow() - datetime.timedelta(hours=24)
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute(
            "SELECT * FROM messages WHERE chat_id = ? AND timestamp >= ?",
            (chat_id, twenty_four_hours_ago)
        )
        rows = await cursor.fetchall()
        return rows

async def get_all_active_chats():
    """Возвращает список ID всех чатов, где были сообщения."""
    async with aiosqlite.connect(DB_PATH) as db:
        cursor = await db.execute("SELECT DISTINCT chat_id FROM messages")
        rows = await cursor.fetchall()
        return [row[0] for row in rows]

async def clear_messages_for_chat(chat_id):
    """Удаляет все сообщения для указанного чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("DELETE FROM messages WHERE chat_id = ?", (chat_id,))
        await db.commit()

async def get_settings_for_chat(chat_id):
    """Возвращает настройки промпта для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        cursor = await db.execute("SELECT selected_prompt FROM group_settings WHERE chat_id = ?", (chat_id,))
        row = await cursor.fetchone()
        if row:
            return row[0]
        else:
            default_prompt = "mama.txt"
            await db.execute("INSERT OR REPLACE INTO group_settings (chat_id, selected_prompt) VALUES (?, ?)", (chat_id, default_prompt))
            await db.commit()
            return default_prompt

async def set_prompt_for_chat(chat_id, prompt_name):
    """Устанавливает выбранный промпт для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("INSERT OR REPLACE INTO group_settings (chat_id, selected_prompt) VALUES (?, ?)", (chat_id, prompt_name))
        await db.commit()

async def get_voice_for_chat(chat_id):
    """Возвращает выбранный голос для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        cursor = await db.execute("SELECT selected_voice FROM group_settings WHERE chat_id = ?", (chat_id,))
        row = await cursor.fetchone()
        # Если настроек нет, создаем их с голосом по умолчанию
        if not row:
            await db.execute("INSERT OR REPLACE INTO group_settings (chat_id, selected_voice) VALUES (?, ?)", (chat_id, 'Zephyr'))
            await db.commit()
            return 'Zephyr'
        return row[0]

async def set_voice_for_chat(chat_id, voice_name):
    """Устанавливает выбранный голос для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("UPDATE group_settings SET selected_voice = ? WHERE chat_id = ?", (voice_name, chat_id))
        await db.commit()
async def get_response_format_for_chat(chat_id):
    """Возвращает выбранный формат ответа для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        # Убеждаемся, что запись для чата существует
        await db.execute("INSERT OR IGNORE INTO group_settings (chat_id) VALUES (?)", (chat_id,))
        cursor = await db.execute("SELECT response_format FROM group_settings WHERE chat_id = ?", (chat_id,))
        row = await cursor.fetchone()
        # Если значение NULL (после INSERT OR IGNORE), возвращаем значение по умолчанию
        return row[0] if row and row[0] else 'voice'

async def set_response_format_for_chat(chat_id, response_format):
    """Устанавливает выбранный формат ответа для чата."""
    async with aiosqlite.connect(DB_PATH) as db:
        # INSERT OR REPLACE гарантирует, что запись будет создана, если её нет
        await db.execute("INSERT OR IGNORE INTO group_settings (chat_id) VALUES (?)", (chat_id,))
        await db.execute("UPDATE group_settings SET response_format = ? WHERE chat_id = ?", (response_format, chat_id))
        await db.commit()