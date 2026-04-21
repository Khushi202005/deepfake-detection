import tensorflow as tf
import numpy as np
import cv2

# Load trained model
model = tf.keras.models.load_model("deepfake_model.h5")

# Image path (change this)
IMAGE_PATH = "test.jpg"

# Read and preprocess image
img = cv2.imread(IMAGE_PATH)
img = cv2.resize(img, (224, 224))
img = img / 255.0
img = np.reshape(img, (1, 224, 224, 3))

# Prediction
prediction = model.predict(img)[0][0]

if prediction > 0.5:
    print(f"🟢 REAL IMAGE ({prediction:.2f})")
else:
    print(f"🔴 FAKE IMAGE ({prediction:.2f})")
