import google.generativeai as genai
import os
from dotenv import load_dotenv

# Загружаем твой API ключ из .env файла
load_dotenv()
GOOGLE_API_KEY = os.getenv('GOOGLE_API_KEY')

if not GOOGLE_API_KEY:
    print("Ошибка: GOOGLE_API_KEY не найден в файле .env")
else:
    try:
        genai.configure(api_key=GOOGLE_API_KEY)

        print("\n--- ✅ МОДЕЛИ, ДОСТУПНЫЕ ТВОЕМУ КЛЮЧУ ---")
        for m in genai.list_models():
            # Нас интересуют только те, что умеют генерировать текст
            if 'generateContent' in m.supported_generation_methods:
                print(f"  - {m.name}")
        print("------------------------------------------\n")
        print("❗️ СКОПИРУЙ ТОЧНОЕ НАЗВАНИЕ НУЖНОЙ МОДЕЛИ (например, 'models/gemini-1.5-pro-latest')")
        print("   И ВСТАВЬ ЕГО В ФАЙЛЫ generation.py и group_messages.py")

    except Exception as e:
        print(f"Произошла ошибка при подключении к Google AI: {e}")