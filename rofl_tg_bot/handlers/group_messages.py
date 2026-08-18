import asyncio
import os
import io # <-- Добавлен импорт io
#import speech_recognition as sr
from uuid import uuid4
import logging
from core.api_manager import ApiKeyManager
from aiogram import Router, F, types, Bot
from pydub import AudioSegment
from PIL import Image
import google.generativeai as genai
#import google.generativeai.files.aio as aou # <-- Импортируем модуль для асинхронной работы с файлами
from google.api_core import exceptions as google_exceptions # <-- Импортируем исключения Google
from database import add_message

router = Router()
router.message.filter(F.chat.type.in_({"group", "supergroup"}))

    
MAX_FILE_SIZE = 20 * 1024 * 1024

@router.message(F.text)
async def handle_text_messages(message: types.Message):
    # ... (без изменений)
    reply_to_user_id = message.reply_to_message.from_user.id if message.reply_to_message else None
    await add_message(chat_id=message.chat.id, user_id=message.from_user.id, user_fullname=message.from_user.full_name, content_type='text', text=message.text, reply_to_user_id=reply_to_user_id)
    logging.info(f"Сохранено [текстовое] сообщение от {message.from_user.full_name}")

@router.message(F.voice)
async def handle_voice_messages(message: types.Message, bot: Bot, api_manager: ApiKeyManager):
    if not api_manager.keys:
        return

    text_to_save = "[голосовое]: (ошибка обработки)" # Значение по умолчанию

    if message.voice.file_size > MAX_FILE_SIZE:
        logging.warning(f"Голосовое от {message.from_user.full_name} слишком большое, пропускаю.")
        text_to_save = "[голосовое]: (файл слишком большой)"
    else:
        try:
            # Скачиваем голосовое сообщение в память
            voice_buffer = io.BytesIO()
            await bot.download(file=message.voice.file_id, destination=voice_buffer)
            voice_buffer.seek(0) # Переводим "курсор" в начало файла

            # Создаем "часть" запроса с аудиоданными
            audio_part = {
                'mime_type': message.voice.mime_type or 'audio/ogg', # Используем mime_type от Telegram
                'data': voice_buffer.read()
            }
            
            # Используем вашу логику из первого бота
            # Запускаем синхронную функцию SDK в отдельном потоке, чтобы не блокировать бота
            def generate():
                # Переключаемся на ключ из менеджера при каждом вызове
                genai.configure(api_key=api_manager.get_current_key()) 
                model = genai.GenerativeModel(model_name="models/gemini-2.5-pro") # Используем более мощную модель для аудио
                prompt = "Транскрибируй это аудиосообщение. В ответе должен быть только текст из сообщения, без вводных фраз. также опиши его максимально подробно звуки фоновые и так далее "
                return model.generate_content([prompt, audio_part])

            # Выполняем синхронный код асинхронно
            response = await asyncio.to_thread(generate)

            if response and response.text:
                recognized_text = response.text.strip()
                text_to_save = f'[голосовое]: "{recognized_text}"'
                logging.info(f"Распознано голосовое от {message.from_user.full_name}: {text_to_save}")
            else:
                text_to_save = "[голосовое]: (не удалось распознать речь)"
                logging.warning(f"Не удалось распознать речь от {message.from_user.full_name} (пустой ответ от модели)")

        except Exception as e:
            logging.error(f"Ошибка Gemini (голосовое) от {message.from_user.full_name}: {e}", exc_info=True)
            
            api_manager.switch_to_next_key() 
            # text_to_save = "[голосовое]: (ошибка API, ключ переключен)"

    # Сохраняем результат в базу данных
    reply_to_user_id = message.reply_to_message.from_user.id if message.reply_to_message else None
    await add_message(chat_id=message.chat.id, user_id=message.from_user.id, user_fullname=message.from_user.full_name, content_type='voice', text=text_to_save, reply_to_user_id=reply_to_user_id)

@router.message(F.photo)
async def handle_photo_messages(message: types.Message, bot: Bot, api_manager: ApiKeyManager):
    # --- ИЗМЕНЕНИЕ: Проверяем наличие ключей через менеджер ---
    if not api_manager.keys: return

    photo = message.photo[-1]
    if photo.file_size > MAX_FILE_SIZE:
        logging.warning(f"Фото от {message.from_user.full_name} слишком большое, пропускаю.")
        text_to_save = "[фото]: (файл слишком большой)"
    else:
        file_id = photo.file_id
        file_info = await bot.get_file(file_id)
        photo_filename = f"media/photos/{uuid4()}.jpg"
        await bot.download_file(file_info.file_path, destination=photo_filename)
        text_to_save = "[фото]: (не удалось получить описание)"
        try:
            # --- ИСПОЛЬЗУЕМ ЛУЧШУЮ FLASH-МОДЕЛЬ ИЗ ТВОЕГО СПИСКА ---
            model = genai.GenerativeModel('models/gemini-2.5-flash')
            # ----------------------------------------------------
            image = Image.open(photo_filename)
            response = await model.generate_content_async(["опиши, что на этой картинке максимально подробно ", image])
            if response and response.text:
                text_to_save = f"[фото]: {response.text.strip().replace('\n', ' ')}"
                logging.info(f"Получено описание фото от {message.from_user.full_name}")
        except Exception as e:
            logging.error(f"Ошибка Gemini (фото) от {message.from_user.full_name}: {e}", exc_info=True)
        finally:
            if os.path.exists(photo_filename): os.remove(photo_filename)
    reply_to_user_id = message.reply_to_message.from_user.id if message.reply_to_message else None
    await add_message(chat_id=message.chat.id, user_id=message.from_user.id, user_fullname=message.from_user.full_name, content_type='photo', text=text_to_save, reply_to_user_id=reply_to_user_id)

@router.message(F.video)
async def handle_video_messages(message: types.Message, bot: Bot, api_manager: ApiKeyManager):
    if not api_manager.keys: return
    if message.video.file_size > MAX_FILE_SIZE:
        logging.warning(f"Видео от {message.from_user.full_name} слишком большое, пропускаю.")
        text_to_save = "[видео]: (файл слишком большой)"
    else:
        file_id = message.video.file_id
        file_info = await bot.get_file(file_id)
        video_filename = f"media/videos/{uuid4()}.mp4"
        await bot.download_file(file_info.file_path, destination=video_filename)
        text_to_save = "[видео]: (не удалось получить описание)"
        video_file = None
        try:
            logging.info(f"Загрузка видео от {message.from_user.full_name} на сервер Google...")
            video_file = genai.upload_file(path=video_filename)
            logging.info(f"Видео загружено: {video_file.name}. Ожидаю обработку...")
            while video_file.state.name == "PROCESSING":
                await asyncio.sleep(5)
                video_file = genai.get_file(video_file.name)
            if video_file.state.name == "FAILED":
                raise Exception("Ошибка обработки видео на стороне Google.")
            logging.info(f"Видео обработано. Генерация описания...")
            # --- ИСПОЛЬЗУЕМ ЛУЧШУЮ FLASH-МОДЕЛЬ ИЗ ТВОЕГО СПИСКА ---
            model = genai.GenerativeModel('models/gemini-2.5-flash')
            # ----------------------------------------------------
            response = await model.generate_content_async([video_file, "подробно опиши, что происходит на этом видео."])
            if response and response.text:
                text_to_save = f"[видео]: {response.text.strip().replace('\n', ' ')}"
                logging.info(f"Получено описание видео от {message.from_user.full_name}")
        except Exception as e:
            logging.error(f"Ошибка Gemini (видео) от {message.from_user.full_name}: {e}", exc_info=True)
        finally:
            if os.path.exists(video_filename): os.remove(video_filename)
            if video_file:
                try:
                    genai.delete_file(video_file.name)
                    logging.info(f"Удален временный видеофайл {video_file.name} с сервера Google.")
                except Exception as del_e:
                    logging.error(f"Не удалось удалить файл {video_file.name}: {del_e}")
    reply_to_user_id = message.reply_to_message.from_user.id if message.reply_to_message else None
    await add_message(chat_id=message.chat.id, user_id=message.from_user.id, user_fullname=message.from_user.full_name, content_type='video', text=text_to_save, reply_to_user_id=reply_to_user_id)