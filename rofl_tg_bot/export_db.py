import sqlite3
import json
import os

# settings
# file path 
DB_PATH = 'data/bot_database.db'
#file name 
OUTPUT_JSON_PATH = 'db_export.json'


def export_to_json():
    if not os.path.exists(DB_PATH):
        print(f"Ошибка: Файл базы данных не найден по пути: {DB_PATH}")
        return

    print(f"Подключаюсь к базе данных: {DB_PATH}")
    conn = None
    all_data = {}

    try:
# conect to data base 
        conn = sqlite3.connect(DB_PATH)
        # Эта настройка позволяет получать строки в виде словарей, а не кортежей
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()


        cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = cursor.fetchall()
        table_names = [table['name'] for table in tables]
        
        if not table_names:
            print("В базе данных не найдено ни одной таблицы.")
            return

        print(f"Найдены таблицы: {', '.join(table_names)}")

        
        for table_name in table_names:
            print(f"Экспортирую таблицу: '{table_name}'...")
            cursor.execute(f"SELECT * FROM {table_name}")
            rows = cursor.fetchall()
# Преобразуем каждую строку в обычный словарь
            all_data[table_name] = [dict(row) for row in rows]

        
        print(f"\nЗаписываю данные в файл: {OUTPUT_JSON_PATH}")
        with open(OUTPUT_JSON_PATH, 'w', encoding='utf-8') as f:
            # indent=4 делает файл красивым и читаемым
            # ensure_ascii=False правильно сохраняет кириллицу (русские буквы)
            json.dump(all_data, f, indent=4, ensure_ascii=False)
            
        print(f"\nГотово! База данных успешно экспортирована в файл: {OUTPUT_JSON_PATH}")

    except Exception as e:
        print(f"\nПроизошла ошибка: {e}")
    finally:
        if conn:
            conn.close()
            print("Соединение с базой данных закрыто.")

if __name__ == '__main__':
    export_to_json()
    input('готова! нажмите,enter , для продолжение')