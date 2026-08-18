import google.generativeai as genai
from google.generativeai import types
import os

# Configure your API key
# It's recommended to store your API key securely, e.g., in environment variables
# Replace "YOUR_API_KEY" with your actual Gemini API key
genai.configure(api_key=os.environ.get("GOOGLE_API_KEY")) 

# Select a TTS model
# You can choose between models like "gemini-2.5-flash-preview-tts" or "gemini-2.5-pro-preview-tts"
model = genai.GenerativeModel("gemini-2.5-flash-preview-tts")

# Text to be converted to speech
text_to_speak = "Hello, this is a test of Gemini's text-to-speech capabilities."

# Generate content with audio modality
response = model.generate_content(
    contents=[text_to_speak],
    generation_config=types.GenerationConfig(
        response_modality=types.ResponseModality.AUDIO
    )
)

# Access the audio content
audio_data = response.candidates[0].content.parts[0].audio_data

# Save the audio to a file
with open("output.mp3", "wb") as f:
    f.write(audio_data)

print("Audio saved to output.mp3")