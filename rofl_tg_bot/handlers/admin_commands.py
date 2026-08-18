import os
from aiogram import Router, F, types, Bot
from aiogram.filters import Command
from aiogram.utils.keyboard import InlineKeyboardBuilder
from aiogram.exceptions import TelegramBadRequest

import database
# --- ИЗМЕНЕНИЕ: Импортируем функцию и менеджер ---
from core.generation import generate_statistics_for_chat
from core.api_manager import ApiKeyManager

router = Router()
router.message.filter(F.chat.type.in_({"group", "supergroup"}))

async def is_admin(user_id: int, chat_id: int, bot: Bot):
    try:
        member = await bot.get_chat_member(chat_id, user_id)
        return member.status in ['creator', 'administrator']
    except Exception:
        return False

# --- ГЛАВНАЯ ФУНКЦИЯ ДЛЯ ПОСТРОЕНИЯ МЕНЮ НАСТРОЕК (без изменений) ---
async def build_settings_menu(chat_id: int):
    current_prompt = await database.get_settings_for_chat(chat_id)
    current_voice = await database.get_voice_for_chat(chat_id)
    current_format = await database.get_response_format_for_chat(chat_id)
    format_map = {'voice': 'Голос 🎤', 'text': 'Текст 📄'}
    text = (f"⚙️ *Настройки бота*\n\n"
            f"Текущий стиль отчета: `{current_prompt}`\n"
            f"Текущий голос озвучки: `{current_voice}`\n"
            f"Текущий формат отчета: `{format_map.get(current_format, 'Не задан')}`")
    builder = InlineKeyboardBuilder()
    builder.button(text="📝 Изменить стиль", callback_data="nav_style")
    builder.button(text="🗣️ Изменить голос", callback_data="nav_voice")
    builder.button(text="📦 Изменить формат", callback_data="nav_format")
    builder.adjust(2, 1)
    return text, builder.as_markup()

# --- ОБРАБОТЧИКИ КОМАНД ---

@router.message(Command("generate"))
async def manual_generate(message: types.Message, bot: Bot, api_manager: ApiKeyManager):
    # --- ИСПРАВЛЕНИЕ СИНТАКСИСА ---
    if not await is_admin(message.from_user.id, message.chat.id, bot):
        await message.reply("Эту команду могут использовать только администраторы.")
        return
    
    await message.reply("Принудительная генерация запущена. Это может занять до минуты...")
    # Передаем api_manager в функцию генерации
    await generate_statistics_for_chat(message.chat.id, bot, api_manager)
    # --------------------------------

@router.message(Command("settings"))
async def cmd_settings(message: types.Message):
    if not await is_admin(message.from_user.id, message.chat.id, message.bot):
        await message.reply("Эту команду могут использовать только администраторы.")
        return
    
    text, markup = await build_settings_menu(message.chat.id)
    await message.reply(text, reply_markup=markup, parse_mode="Markdown")

# --- ОБРАБОТЧИКИ НАЖАТИЙ НА КНОПКИ (CALLBACKS) - без изменений ---

@router.callback_query(F.data == "back_to_main_settings")
async def cb_back_to_main(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    text, markup = await build_settings_menu(callback.message.chat.id)
    await callback.message.edit_text(text, reply_markup=markup, parse_mode="Markdown")
    await callback.answer()

@router.callback_query(F.data == "nav_style")
async def cb_nav_style(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    prompt_files = [f for f in os.listdir('prompts') if f.endswith('.txt')]
    builder = InlineKeyboardBuilder()
    for prompt_file in prompt_files:
        prompt_name = os.path.splitext(prompt_file)[0]
        builder.button(text=prompt_name.capitalize(), callback_data=f"set_prompt_{prompt_file}")
    builder.button(text="⬅️ Назад", callback_data="back_to_main_settings")
    builder.adjust(2)
    await callback.message.edit_text("Выберите стиль для генерации статистики:", reply_markup=builder.as_markup())
    await callback.answer()

@router.callback_query(F.data == "nav_voice")
async def cb_nav_voice(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    voices = ["Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede"]
    builder = InlineKeyboardBuilder()
    for voice_name in voices:
        builder.button(text=voice_name, callback_data=f"set_voice_{voice_name}")
    builder.button(text="⬅️ Назад", callback_data="back_to_main_settings")
    builder.adjust(3)
    await callback.message.edit_text("Выберите голос для озвучки отчетов:", reply_markup=builder.as_markup())
    await callback.answer()

@router.callback_query(F.data == "nav_format")
async def cb_nav_format(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    builder = InlineKeyboardBuilder()
    builder.button(text="Голос 🎤", callback_data="set_format_voice")
    builder.button(text="Текст 📄", callback_data="set_format_text")
    builder.button(text="⬅️ Назад", callback_data="back_to_main_settings")
    builder.adjust(2, 1)
    await callback.message.edit_text("Выберите формат для публикации отчета:", reply_markup=builder.as_markup())
    await callback.answer()

# --- ОБРАБОТЧИКИ УСТАНОВКИ КОНКРЕТНЫХ ЗНАЧЕНИЙ (без изменений) ---

@router.callback_query(F.data.startswith("set_prompt_"))
async def cb_set_prompt(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    prompt_file = callback.data.split("_", 2)[2]
    await database.set_prompt_for_chat(callback.message.chat.id, prompt_file)
    await callback.answer(f"Стиль изменен на: {prompt_file}")
    text, markup = await build_settings_menu(callback.message.chat.id)
    try:
        await callback.message.edit_text(text, reply_markup=markup, parse_mode="Markdown")
    except TelegramBadRequest: pass

@router.callback_query(F.data.startswith("set_voice_"))
async def cb_set_voice(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    voice_name = callback.data.split("_", 2)[2]
    await database.set_voice_for_chat(callback.message.chat.id, voice_name)
    await callback.answer(f"Голос изменен на: {voice_name}")
    text, markup = await build_settings_menu(callback.message.chat.id)
    try:
        await callback.message.edit_text(text, reply_markup=markup, parse_mode="Markdown")
    except TelegramBadRequest: pass

@router.callback_query(F.data.startswith("set_format_"))
async def cb_set_format(callback: types.CallbackQuery):
    if not await is_admin(callback.from_user.id, callback.message.chat.id, callback.bot):
        await callback.answer("Менять настройки могут только администраторы.", show_alert=True)
        return
    response_format = callback.data.split("_", 2)[2]
    await database.set_response_format_for_chat(callback.message.chat.id, response_format)
    await callback.answer(f"Формат изменен на: {response_format}")
    text, markup = await build_settings_menu(callback.message.chat.id)
    try:
        await callback.message.edit_text(text, reply_markup=markup, parse_mode="Markdown")
    except TelegramBadRequest: pass