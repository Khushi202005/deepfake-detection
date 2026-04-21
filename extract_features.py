import librosa
import numpy as np

def extract_mfcc(file_path, n_mfcc=40):
    audio, sr = librosa.load(file_path, sr=16000, mono=True)
    mfcc = librosa.feature.mfcc(y=audio, sr=sr, n_mfcc=n_mfcc)
    mfcc_mean = np.mean(mfcc.T, axis=0)
    return mfcc_mean
