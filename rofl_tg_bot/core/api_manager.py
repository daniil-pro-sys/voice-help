import os
import logging
import google.generativeai as genai

class ApiKeyManager:
    def __init__(self):
        self.keys = self._load_keys()
        if not self.keys:
            logging.critical("Не найден ни один GOOGLE_API_KEY с нумерацией (например, GOOGLE_API_KEY_1) в .env файле!")
            raise ValueError("Не найдены API ключи для Google Gemini.")
        
        self.current_key_index = 0
        self.configure_genai()
        logging.info(f"Загружено {len(self.keys)} API ключей. Используется ключ #{self.current_key_index + 1}.")

    def _load_keys(self):
        """Загружает все ключи из .env файла, которые имеют вид GOOGLE_API_KEY_..."""
        keys = []
        i = 1
        while True:
            key = os.getenv(f"GOOGLE_API_KEY_{i}")
            if key:
                keys.append(key)
                i += 1
            else:
                break
        return keys

    def configure_genai(self):
        """Конфигурирует библиотеку genai с текущим ключом."""
        current_key = self.keys[self.current_key_index]
        genai.configure(api_key=current_key)

    def get_current_key(self):
        """Возвращает текущий активный ключ."""
        return self.keys[self.current_key_index]

    def switch_to_next_key(self):
        """Переключается на следующий ключ по кругу."""
        self.current_key_index = (self.current_key_index + 1) % len(self.keys)
        self.configure_genai()
        logging.warning(f"Переключаюсь на API ключ #{self.current_key_index + 1}. Причина: предыдущий ключ мог исчерпать лимиты.")
        # Возвращаем True, чтобы можно было сделать еще одну попытку генерации
        return True