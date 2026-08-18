import os
import asyncio
import logging
from datetime import datetime, timedelta
from dotenv import load_dotenv

load_dotenv()

from aiogram import Bot, Dispatcher, types
from aiogram.filters import CommandStart, Command
from apscheduler.schedulers.asyncio import AsyncIOScheduler

# --- НАШИ НОВЫЕ ИМПОРТЫ ---
from core.api_manager import ApiKeyManager
from handlers import group_messages, admin_commands
from database import init_db, get_all_active_chats
from core.generation import generate_statistics_for_chat

# Настраиваем логирование
os.makedirs('logs', exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(name)s - %(message)s',
    handlers=[logging.FileHandler('logs/bot.log', encoding='utf-8'), logging.StreamHandler()]
)
logging.getLogger('apscheduler').setLevel(logging.WARNING)

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")

# --- ГЛАВНОЕ ИЗМЕНЕНИЕ: СОЗДАЕМ МЕНЕДЖЕР КЛЮЧЕЙ ПРИ СТАРТЕ ---
try:
    api_manager = ApiKeyManager()
except ValueError as e:
    logging.critical(e)
    exit()

if not BOT_TOKEN:
    logging.critical("Токен бота не найден! Работа невозможна.")
    exit()

bot = Bot(token=BOT_TOKEN)
dp = Dispatcher()

# --- НОВЫЙ УМНЫЙ ПЛАНИРОВЩИК ---
async def scheduled_job(scheduler: AsyncIOScheduler):
    logging.info("Планировщик запущен: начинаю проверку и распределение задач.")
    active_chats = await get_all_active_chats()
    logging.info(f"Найдены активные чаты для генерации: {active_chats}")
    
    start_time = datetime.now()
    delay_minutes = 0
    
    for chat_id in active_chats:
        run_time = start_time + timedelta(minutes=delay_minutes)
        # Для каждого чата создаем отдельную задачу со своим временем запуска
        scheduler.add_job(
            generate_statistics_for_chat,
            'date',
            run_date=run_time,
            args=[chat_id, bot, api_manager],
            id=f"job_for_chat_{chat_id}_{run_time.hour}_{run_time.minute}", # Уникальный ID задачи
            replace_existing=True
        )
        logging.info(f"Задача для чата {chat_id} запланирована на {run_time.strftime('%H:%M:%S')}")
        # Увеличиваем задержку для следующего чата
        delay_minutes += 4
    
    logging.info("Работа по распределению задач завершена.")

@dp.message(CommandStart())
async def send_welcome(message: types.Message):
    await message.reply("Привет! Я бот для создания шуточной статистики в группах.\nДобавьте меня в чат и дайте права администратора.")

@dp.message(Command("help"))
async def send_help(message: types.Message):
    help_text = (
        "🤖 *Я — бот для создания шуточных статистических отчетов.*\n\n"
        "👑 *Команды для администраторов:*\n"
        "`/settings` — Открыть меню настроек.\n"
        "`/generate` — Запустить генерацию отчета вручную."
    )
    await message.reply(help_text, parse_mode="Markdown")

async def main():
    await init_db()

    # Передаем менеджер ключей в диспетчер, чтобы он был доступен в хендлерах
    dp['api_manager'] = api_manager
    dp.include_router(admin_commands.router)
    dp.include_router(group_messages.router)

    scheduler = AsyncIOScheduler(timezone="Europe/Moscow")
    # Главная задача теперь - это сам 'распределитель' задач
    scheduler.add_job(scheduled_job, trigger='cron', hour=12, minute=0, args=[scheduler])
    scheduler.start()

    await bot.delete_webhook(drop_pending_updates=True)
    # Передаем api_manager в start_polling, чтобы он был доступен через bot['api_manager']
    await dp.start_polling(bot, api_manager=api_manager)

if __name__ == '__main__':
    logging.info("Бот запускается...")
    try:
        asyncio.run(main())
    except (KeyboardInterrupt, SystemExit):
        logging.info("Бот остановлен.")