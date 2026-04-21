import os
import numpy as np
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, Dropout, Flatten
from tensorflow.keras.utils import to_categorical
from sklearn.model_selection import train_test_split
from extract_features import extract_mfcc

# Path to dataset
DATASET_PATH = "dataset_audio"

# Prepare data
X = []
y = []

for label, folder in enumerate(["real", "fake"]):
    folder_path = os.path.join(DATASET_PATH, folder)
    for file in os.listdir(folder_path):
        if file.endswith(".wav"):
            file_path = os.path.join(folder_path, file)
            mfcc = extract_mfcc(file_path)  # shape: (n_mfcc, time)
            X.append(mfcc)
            y.append(label)

X = np.array(X)
y = np.array(y)

# Make sure X has shape (samples, n_mfcc, time, 1)
X = X[..., np.newaxis]  # add channel dimension
y = to_categorical(y, num_classes=2)

# Train-test split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# Build a simple neural network
model = Sequential([
    Flatten(input_shape=X_train.shape[1:]),
    Dense(256, activation="relu"),
    Dropout(0.3),
    Dense(128, activation="relu"),
    Dropout(0.3),
    Dense(2, activation="softmax")  # 2 classes: real/fake
])

model.compile(
    optimizer="adam",
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

# Train the model
history = model.fit(
    X_train, y_train,
    validation_data=(X_test, y_test),
    epochs=30,
    batch_size=32
)

# Evaluate
loss, acc = model.evaluate(X_test, y_test)
print(f"Test Accuracy: {acc:.4f}")

# Save the model
model.save("audio_deepfake_model.h5")
print("Model saved ✅")
