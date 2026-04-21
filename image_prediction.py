import cv2
import numpy as np
from tensorflow import keras
import os

class ImageDeepfakeDetector:
    """
    Image Deepfake Detector using trained CNN model
    Works with image_deepfake_model.h5 (from Google Colab)
    """
    
    def __init__(self, model_path='image_model/image_deepfake_model.h5'):
        """
        Initialize detector with model path
        Args:
            model_path: Path to the trained .h5 model file
        """
        self.model_path = model_path
        self.model = None
        self.img_height = 224
        self.img_width = 224
        self.load_model()
    
    def load_model(self):
        """Load the trained model"""
        try:
            if not os.path.exists(self.model_path):
                raise FileNotFoundError(f"Model not found at {self.model_path}")
            
            print(f"🔄 Loading image model from: {self.model_path}")
            self.model = keras.models.load_model(self.model_path)
            print("✅ Image deepfake detection model loaded successfully")
            
        except Exception as e:
            print(f"❌ Error loading image model: {str(e)}")
            raise
    
    def preprocess_image(self, image_path):
        """
        Preprocess image for model prediction
        Args:
            image_path: Path to the image file
        Returns:
            Preprocessed numpy array
        """
        try:
            # Read image
            img = cv2.imread(image_path)
            
            if img is None:
                raise ValueError(f"Could not read image from {image_path}")
            
            # Convert BGR to RGB (OpenCV loads as BGR)
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            
            # Resize to model input size
            img = cv2.resize(img, (self.img_width, self.img_height))
            
            # Normalize to [0, 1]
            img = img.astype('float32') / 255.0
            
            # Add batch dimension
            img = np.expand_dims(img, axis=0)
            
            return img
            
        except Exception as e:
            print(f"❌ Error preprocessing image: {str(e)}")
            raise
    
    def predict(self, image_path, threshold=0.5):
        """
        Predict if image is REAL or FAKE
        Args:
            image_path: Path to the image file
            threshold: Decision threshold (default 0.5)
        Returns:
            dict with 'result', 'confidence', 'raw_prediction'
        """
        try:
            print(f"🔍 Analyzing image: {image_path}")
            
            # Preprocess image
            processed_img = self.preprocess_image(image_path)
            
            # Get prediction
            prediction = self.model.predict(processed_img, verbose=0)[0][0]
            
            print(f"📊 Raw prediction: {prediction:.4f}")
            
            # Determine result based on threshold
            if prediction > threshold:
                result = "FAKE"
                confidence = float(prediction)
            else:
                result = "REAL"
                confidence = float(1 - prediction)
            
            print(f"✅ Result: {result} (confidence: {confidence*100:.2f}%)")
            
            return {
                'result': result,
                'confidence': round(confidence, 4),
                'raw_prediction': float(prediction),
                'is_deepfake': prediction > threshold
            }
            
        except Exception as e:
            print(f"❌ Error in prediction: {str(e)}")
            return {
                'result': 'ERROR',
                'confidence': 0.0,
                'error': str(e)
            }


# Test function (optional)
if __name__ == "__main__":
    # Test the detector
    detector = ImageDeepfakeDetector()
    
    # Example usage:
    # result = detector.predict('test_image.jpg')
    # print(result)
    
    print("\n" + "=" * 50)
    print("✅ ImageDeepfakeDetector ready to use!")
    print("=" * 50)
    print("\nUsage:")
    print("  detector = ImageDeepfakeDetector()")
    print("  result = detector.predict('path/to/image.jpg')")
    print("  print(result)")
    print("=" * 50)