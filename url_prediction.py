import joblib
import pickle
import numpy as np
from urllib.parse import urlparse
import re

# Load model
model = joblib.load('url_simple_model/url_model.pkl')
scaler = joblib.load('url_simple_model/scaler.pkl')

with open('url_simple_model/feature_names.pkl', 'rb') as f:
    feature_names = pickle.load(f)

print(f"✅ URL model loaded with {len(feature_names)} features")


def extract_simple_url_features(url):
    """Extract ONLY simple features from URL string"""
    features = {}
    
    try:
        parsed = urlparse(url)
        
        features['url_length'] = len(url)
        features['hostname_length'] = len(parsed.netloc)
        features['path_length'] = len(parsed.path)
        features['nb_dots'] = url.count('.')
        features['nb_hyphens'] = url.count('-')
        features['nb_at'] = url.count('@')
        features['nb_qm'] = url.count('?')
        features['nb_and'] = url.count('&')
        features['nb_eq'] = url.count('=')
        features['nb_underscore'] = url.count('_')
        features['nb_slash'] = url.count('/')
        features['nb_percent'] = url.count('%')
        
        digits = sum(c.isdigit() for c in url)
        features['digit_ratio'] = digits / len(url) if len(url) > 0 else 0
        features['has_https'] = 1 if parsed.scheme == 'https' else 0
        
        ip_pattern = re.compile(r'\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}')
        features['has_ip'] = 1 if ip_pattern.search(parsed.netloc) else 0
        
        suspicious = ['login', 'signin', 'bank', 'account', 'update', 
                      'verify', 'secure', 'confirm', 'password']
        features['suspicious_words'] = sum(1 for word in suspicious if word in url.lower())
        
        features['has_port'] = 1 if ':' in parsed.netloc and '@' not in parsed.netloc else 0
        features['subdomain_count'] = len(parsed.netloc.split('.')) - 2
        
        shorteners = ['bit.ly', 'goo.gl', 'tinyurl', 't.co', 'ow.ly']
        features['is_shortened'] = 1 if any(s in url.lower() for s in shorteners) else 0
        features['domain_has_numbers'] = 1 if any(c.isdigit() for c in parsed.netloc) else 0
        
    except:
        for fname in feature_names:
            features[fname] = 0
    
    return features


def predict_url(url):
    """Predict if URL is phishing"""
    try:
        features = extract_simple_url_features(url)
        features_array = np.array([features.get(f, 0) for f in feature_names]).reshape(1, -1)
        features_scaled = scaler.transform(features_array)
        
        prediction = model.predict(features_scaled)[0]
        probability = model.predict_proba(features_scaled)[0]
        
        result = "Phishing" if prediction == 1 else "Legitimate"
        confidence = float(probability[prediction])
        
        return {
            "result": result,
            "confidence": round(confidence, 2)
        }
        
    except Exception as e:
        print(f"Error: {e}")
        return {"result": "Error", "confidence": 0.0}