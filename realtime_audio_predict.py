import os
import numpy as np
import sounddevice as sd
from scipy.io.wavfile import write
import tensorflow as tf
from extract_features import extract_mfcc

# -------------------------------
# CONFIG
# -------------------------------
MODEL_PATH = "audio_deepfake_model.h5"
TEMP_FILE = "temp_audio.wav"
DURATION = 3          # seconds
SAMPLE_RATE = 16000

# Hide TensorFlow info/warnings
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

# -------------------------------
# LOAD MODEL
# -------------------------------
model = tf.keras.models.load_model(MODEL_PATH)
print("Model loaded ✅")

# -------------------------------
# RECORD AUDIO
# -------------------------------
def record_audio(duration=DURATION, sample_rate=SAMPLE_RATE):
    print("Recording...")
    audio = sd.rec(
        int(duration * sample_rate),
        samplerate=sample_rate,
        channels=1,
        dtype="float32"
    )
    sd.wait()
    print("Recording finished.")
    return audio.flatten()

# -------------------------------
# PREPROCESS AUDIO
# -------------------------------
def preprocess_audio(audio):
    # Save temporary WAV file
    write(TEMP_FILE, SAMPLE_RATE, audio)

    # Extract MFCC (same as training)
    mfcc = extract_mfcc(TEMP_FILE)

    # Safety check
    if mfcc is None or mfcc.size == 0:
        raise ValueError("MFCC extraction failed")

    # Add channel & batch dimensions
    mfcc = np.expand_dims(mfcc, axis=-1)  # (time, 1)
    mfcc = np.expand_dims(mfcc, axis=0)   # (1, time, 1)

    return mfcc

# -------------------------------
# PREDICT AUDIO
# -------------------------------
def predict_audio(audio):
    X = preprocess_audio(audio)

    # Sigmoid output (single value)
    score = model.predict(X, verbose=0)[0][0]

    if score >= 0.5:
        print(f"Predicted class: FAKE (confidence: {score:.2f})")
    else:
        print(f"Predicted class: REAL (confidence: {1 - score:.2f})")

# -------------------------------
# MAIN LOOP
# -------------------------------
if __name__ == "__main__":
    try:
        while True:
            audio = record_audio()
            predict_audio(audio)

            cont = input("Press Enter to record again or type 'q' to quit: ")
            if cont.lower() == "q":
                break
    finally:
        # Cleanup temp file
        if os.path.exists(TEMP_FILE):
            os.remove(TEMP_FILE)
