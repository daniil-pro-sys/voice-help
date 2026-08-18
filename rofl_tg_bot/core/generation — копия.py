import os
import io
import struct
import logging
import google.generativeai as genai
from google.generativeai import types as genai_types
from google.api_core import exceptions as google_exceptions # <-- Важный импорт для отлова ошибок
from aiogram import Bot
from aiogram.types import BufferedInputFile
from pydub import AudioSegment

import database
from config import BASE_DIR
from core.api_manager import ApiKeyManager # <-- Импортируем наш менеджер

def convert_raw_to_wav_bytes(raw_data: bytes, sample_rate=24000, channels=1, bits_per_sample=16):
    # ... (эта функция без изменений)
    datasize = len(raw_data)
    o = io.BytesIO()
    o.write(b'RIFF'); o.write(struct.pack('<I', datasize + 36)); o.write(b'WAVEfmt ')
    o.write(struct.pack('<IHHIIHH', 16, 1, channels, sample_rate, sample_rate * channels * (bits_per_sample // 8), channels * (bits_per_sample // 8), bits_per_sample))
    o.write(b'data'); o.write(struct.pack('<I', datasize)); o.write(raw_data); o.seek(0)
    return o.getvalue()

async def generate_statistics_for_chat(chat_id: int, bot: Bot, api_manager: ApiKeyManager, retry_attempt=False):
    try:
        logging.info(f"Начинаю генерацию отчета для чата {chat_id}...")
        messages = await database.get_messages_for_chat(chat_id)
        if not messages or len(messages) < 3:
            logging.warning(f"Недостаточно сообщений в чате {chat_id}. Генерация отменена.")
            return

        user_names = {row['user_id']: row['user_fullname'] for row in messages}
        chat_log = "".join(f"{msg['user_fullname']}{f' (отвечает {user_names[msg['reply_to_user_id']]})' if msg['reply_to_user_id'] and msg['reply_to_user_id'] in user_names else ''}: {msg['message_text']}\n" for msg in messages)

        prompt_filename = await database.get_settings_for_chat(chat_id)
        prompt_path = os.path.join(BASE_DIR, 'prompts', prompt_filename)
        with open(prompt_path, "r", encoding="utf-8") as f:
            prompt_text = f.read()

        text_model = genai.GenerativeModel('models/gemini-2.5-pro')
        full_request_text = prompt_text + "\n\n[ЛОГ ЧАТА ДЛЯ АНАЛИЗА]\n" + chat_log
        text_response = await text_model.generate_content_async(full_request_text)
        generated_text = text_response.text
        
        response_format = await database.get_response_format_for_chat(chat_id)

        if response_format == 'text':
            await bot.send_message(chat_id, generated_text)
            logging.info(f"Текстовый отчет для чата {chat_id} успешно отправлен.")
        elif response_format == 'voice':
            voice_name = await database.get_voice_for_chat(chat_id)
            logging.info(f"Генерирую речь для чата {chat_id} (голос: {voice_name})...")

            # --- ИСПРАВЛЕНИЕ ОШИБКИ TTS ---
            tts_model = genai.GenerativeModel('models/text-to-speech')

            # ПРАВИЛЬНЫЙ ВЫЗОВ:
            # Параметры, включая голос, передаются через generation_config
            response = await tts_model.generate_content_async(
                generated_text,
                generation_config=genai.types.GenerationConfig(
                    tts_voice=voice_name
                )
            )
            # --------------------------------

            # Доступ к аудио данным осуществляется через response.audio.content
            wav_bytes = response.audio.content
            wav_stream = io.BytesIO(wav_bytes)
            audio = AudioSegment.from_wav(wav_stream)
            ogg_stream = io.BytesIO()
            audio.export(ogg_stream, format="ogg", codec="libopus")
            ogg_stream.seek(0)

            input_file = BufferedInputFile(ogg_stream.read(), filename="report.ogg")
            await bot.send_voice(chat_id, voice=input_file)
            logging.info(f"Голосовой отчет для чата {chat_id} успешно сгенерирован и отправлен.")
        await database.clear_messages_for_chat(chat_id)
        logging.info(f"Сообщения для чата {chat_id} очищены.")

    except (google_exceptions.PermissionDenied, google_exceptions.ResourceExhausted) as e:
        logging.error(f"Ошибка API ключа для чата {chat_id}: {e}")
        if not retry_attempt:
            api_manager.switch_to_next_key()
            # Пробуем еще раз с новым ключом
            await generate_statistics_for_chat(chat_id, bot, api_manager, retry_attempt=True)
        else:
            logging.critical(f"Все API ключи не работают! Пропущена генерация для чата {chat_id}.")
            await bot.send_message(chat_id, "Произошла критическая ошибка с API ключами. Все ключи исчерпали лимиты. Администратор уведомлен.")

    except Exception as e:
        logging.error(f"Критическая ошибка при генерации отчета для чата {chat_id}:", exc_info=True)
        try:
            await bot.send_message(chat_id, "Произошла внутренняя ошибка. Администратор уже уведомлен и разбирается с проблемой.")
        except Exception as send_error:
            logging.error(f"Не удалось отправить сообщение об ошибке в чат {chat_id}: {send_error}")