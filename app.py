from flask import Flask, request, jsonify
from flask_cors import CORS
from database import get_db_connection
from itsdangerous import URLSafeTimedSerializer
import secrets
import os
import cv2
import numpy as np
import tensorflow as tf
import bcrypt
from functools import wraps
from mtcnn import MTCNN
from url_prediction import predict_url
from audio_prediction import AudioDeepfakeDetector
from image_prediction import ImageDeepfakeDetector
import requests
from bs4 import BeautifulSoup
from urllib.parse import urlparse
import firebase_admin
from firebase_admin import credentials, messaging

# ── Groq key ─────────────────────────────

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

# ==================== APP INIT (ONLY ONCE) ====================
app = Flask(__name__)
CORS(app)
app.config['SECRET_KEY'] = 'deepguard-secret-key-2025'
serializer = URLSafeTimedSerializer(app.config['SECRET_KEY'])

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# ==================== INITIALIZE FIREBASE ====================
try:
    cred = credentials.Certificate('firebase-adminsdk.json')
    firebase_admin.initialize_app(cred)
    print("✅ Firebase Admin SDK initialized")
except Exception as e:
    print(f"⚠️ Firebase initialization failed: {e}")
    print("   Notice push notifications will not work without Firebase setup")

# ==================== CREATE DATABASE TABLES ====================
def create_notice_tables():
    conn = get_db_connection()
    if not conn:
        print("❌ Failed to connect to database")
        return
    cursor = conn.cursor()
    try:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS notices (
                id INT AUTO_INCREMENT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                message TEXT NOT NULL,
                admin_id INT NOT NULL,
                admin_name VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_created_at (created_at)
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS fcm_tokens (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                fcm_token TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_user_token (user_id)
            )
        """)
        conn.commit()
        print("✅ Notice tables created/verified")
    except Exception as e:
        print(f"❌ Error creating tables: {e}")
    finally:
        cursor.close()
        conn.close()

create_notice_tables()

# ==================== MIDDLEWARE ====================
def get_user_from_token(token):
    try:
        parts = token.replace("Bearer ", "").split("_")
        if len(parts) >= 2:
            user_id = parts[1]
            conn = get_db_connection()
            if not conn:
                return None
            cursor = conn.cursor(dictionary=True)
            cursor.execute("SELECT id, name, email, role FROM users WHERE id=%s", (user_id,))
            user = cursor.fetchone()
            cursor.close()
            conn.close()
            return user
    except:
        return None
    return None

def admin_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header:
            return jsonify({"success": False, "message": "No authorization token provided"}), 401
        user = get_user_from_token(auth_header)
        if not user:
            return jsonify({"success": False, "message": "Invalid token"}), 401
        if user.get("role") != "admin":
            return jsonify({"success": False, "message": "Admin access required"}), 403
        return f(*args, **kwargs)
    return decorated_function

def token_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header:
            return jsonify({"success": False, "message": "No authorization token"}), 401
        user = get_user_from_token(auth_header)
        if not user:
            return jsonify({"success": False, "message": "Invalid token"}), 401
        return f(user, *args, **kwargs)
    return decorated_function

# ==================== SYSTEM SETTINGS HELPERS ====================
def get_setting_value(key):
    conn = get_db_connection()
    if not conn:
        return None
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT setting_value FROM system_settings WHERE setting_key = %s", (key,))
    result = cursor.fetchone()
    cursor.close()
    conn.close()
    if result:
        value = result['setting_value']
        if value.lower() in ['true', 'false']:
            return value.lower() == 'true'
        elif value.isdigit():
            return int(value)
        return value
    return None

def check_maintenance_mode():
    if get_setting_value('maintenance_mode'):
        auth_header = request.headers.get('Authorization')
        if auth_header:
            user = get_user_from_token(auth_header)
            if user and user.get("role") == "admin":
                return None
        return jsonify({'success': False, 'message': 'Application is under maintenance.', 'code': 'MAINTENANCE_MODE'}), 503
    return None

def check_chatbot_enabled():
    if not get_setting_value('chatbot_enabled'):
        return jsonify({'success': False, 'message': 'AI Chatbot is currently disabled.', 'code': 'CHATBOT_DISABLED'}), 503
    return None

# ==================== GLOBAL BEFORE REQUEST (Maintenance) ====================
@app.before_request
def global_checks():
    exempt_paths = [
        "/",
        "/api/health",
        "/api/login",
        "/api/signup",
        "/api/forgot-password",
        "/api/reset-password",
        "/api/get-security-question",
        "/api/verify-security-answer",
    ]
    if request.path in exempt_paths:
        return None
    # Admins always bypass maintenance
    auth_header = request.headers.get('Authorization')
    if auth_header:
        user = get_user_from_token(auth_header)
        if user and user.get("role") == "admin":
            return None
    # Block everyone else during maintenance
    if get_setting_value('maintenance_mode'):
        return jsonify({
            "success": False,
            "message": "Application is under maintenance.",
            "code": "MAINTENANCE_MODE"
        }), 503

# ==================== LOAD ML MODELS ====================
print("=" * 60)
print("🔄 Loading ML Models...")
print("=" * 60)

VIDEO_MODEL_PATH = "model/deepfake_video_model (2).h5"
try:
    video_model = tf.keras.models.load_model(VIDEO_MODEL_PATH)
    print("✅ Video model loaded successfully")
except Exception as e:
    print(f"⚠️ Video model not found: {e}")
    video_model = None

IMAGE_MODEL_PATH = "image_model/image_deepfake_model.h5"
try:
    image_detector = ImageDeepfakeDetector(model_path=IMAGE_MODEL_PATH)
    print("✅ New image deepfake detector loaded successfully")
except Exception as e:
    print(f"⚠️ New image detector not available: {e}")
    image_detector = None

try:
    audio_detector = AudioDeepfakeDetector()
    print("✅ Audio model loaded successfully")
except Exception as e:
    print(f"⚠️ Audio model not found: {e}")
    audio_detector = None

try:
    detector = MTCNN()
    print("✅ MTCNN detector loaded successfully")
except Exception as e:
    print(f"⚠️ MTCNN not available: {e}")
    detector = None

print("=" * 60)

# ==================== PASSWORD FUNCTIONS ====================
def hash_password(password):
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()

def check_password(password, hashed):
    return bcrypt.checkpw(password.encode(), hashed.encode())

# ==================== VIDEO PREDICTION LOGIC ====================
def predict_video(video_path):
    try:
        cap = cv2.VideoCapture(video_path)
        predictions = []
        frame_count = 0
        processed_count = 0
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        frame_skip = max(1, fps // 2)
        max_frames = 20
        print(f"📹 Total frames: {total_frames}, FPS: {fps}")
        print(f"📹 Will process every {frame_skip} frames, max {max_frames} frames")
        while processed_count < max_frames:
            ret, frame = cap.read()
            if not ret:
                break
            frame_count += 1
            if frame_count % frame_skip != 0:
                continue
            processed_count += 1
            faces = detector.detect_faces(frame)
            if faces and len(faces) > 0:
                x, y, w, h = faces[0]['box']
                x = max(0, x)
                y = max(0, y)
                face = frame[y:y+h, x:x+w]
                if face.size > 0:
                    face = cv2.resize(face, (224, 224))
                    face = face / 255.0
                    face = np.expand_dims(face, axis=0)
                    pred = video_model.predict(face, verbose=0)[0][0]
                    predictions.append(pred)
                    print(f"Frame {frame_count}: prediction = {pred:.4f}")
        cap.release()
        print(f"✅ Processed {processed_count} frames, {len(predictions)} predictions")
        if len(predictions) == 0:
            print("⚠️ No faces detected in video")
            return {"result": "No Face Detected", "confidence": 0.0}
        avg_pred = float(np.mean(predictions))
        print(f"📊 Average prediction: {avg_pred:.4f}")
        if avg_pred > 0.5:
            result = "Authentic"
            confidence = max(avg_pred, 0.51)
        else:
            result = "Deepfake"
            confidence = max(1 - avg_pred, 0.51)
        print(f"🎯 Final result: {result} with confidence {confidence:.2f}")
        return {"result": result, "confidence": round(confidence, 2)}
    except Exception as e:
        print(f"❌ Error in predict_video: {str(e)}")
        import traceback
        traceback.print_exc()
        return {"result": "Error", "confidence": 0.0}

# ==================== PUSH NOTIFICATION ====================
def send_push_notification(tokens, title, body):
    try:
        if not tokens:
            return False
        messages = []
        for token in tokens:
            msg = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                token=token,
            )
            messages.append(msg)
        response = messaging.send_each(messages)
        print(f"✅ Sent {response.success_count} notifications")
        return response.success_count > 0
    except Exception as e:
        print(f"❌ Error sending notification: {e}")
        return False

# ==================== HOME ====================
@app.route("/", methods=["GET"])
def home():
    return jsonify({"message": "DeepGuard Backend is running ✅"})

# ==================== HEALTH ====================
@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({
        "status": "Server is running",
        "models": {
            "video": "loaded" if video_model else "not loaded",
            "image": "loaded" if image_detector else "not loaded",
            "audio": "loaded" if audio_detector else "not loaded"
        }
    })

# ==================== AUTH ENDPOINTS ====================
@app.route("/api/signup", methods=["POST"])
def signup():
    data = request.get_json()
    name = data.get("name")
    email = data.get("email")
    password = data.get("password")
    security_question = data.get("security_question")  
    security_answer = data.get("security_answer") 
    if not name or not email or not password:
        return jsonify({"success": False, "message": "All fields required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM users WHERE email=%s", (email,))
    if cursor.fetchone():
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": "User exists"}), 409
    hashed = hash_password(password)
    cursor.execute("INSERT INTO users (name, email, password, security_question, security_answer) VALUES (%s,%s,%s,%s,%s)", (name, email, hashed, security_question, security_answer))
    
    conn.commit()
    user_id = cursor.lastrowid
    cursor.close()
    conn.close()
    return jsonify({
        "success": True,
        "user": {"id": str(user_id), "name": name, "email": email, "role": "user"},
        "token": f"token_{user_id}_{secrets.token_hex(16)}"
    }), 201

@app.route("/api/login", methods=["POST"])
def login():
    data = request.get_json()
    email = data.get("email")
    password = data.get("password")
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT id, name, email, password, role, is_blocked FROM users WHERE email=%s", (email,))
    user = cursor.fetchone()
    cursor.close()
    conn.close()
    if not user or not check_password(password, user["password"]):
        return jsonify({"success": False, "message": "Invalid credentials"}), 401
    if user.get("is_blocked", 0) == 1:
        return jsonify({"success": False, "message": "Your account has been blocked by admin."}), 403
    return jsonify({
        "success": True,
        "user": {"id": str(user["id"]), "name": user["name"], "email": user["email"], "role": user.get("role", "user")},
        "token": f"token_{user['id']}_{secrets.token_hex(16)}"
    })

@app.route("/api/forgot-password", methods=["POST"])
def forgot_password():
    data = request.get_json()
    email = data.get("email")
    if not email:
        return jsonify({"success": False, "message": "Email is required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM users WHERE email=%s", (email,))
    user = cursor.fetchone()
    cursor.close()
    conn.close()
    if not user:
        return jsonify({"success": True, "message": "If this email exists, a reset token has been generated."})
    reset_token = serializer.dumps(email, salt='password-reset')
    print(f"\n{'='*80}\n🔐 PASSWORD RESET TOKEN\n📧 Email: {email}\n🔑 Token: {reset_token}\n{'='*80}\n")
    return jsonify({"success": True, "message": "Password reset token generated! Check server console.", "token": reset_token})

@app.route("/api/reset-password", methods=["POST"])
def reset_password():
    data = request.get_json()
    email = data.get("email")
    new_password = data.get("password")
    if not email or not new_password:
        return jsonify({"success": False, "message": "Email and password required"}), 400
    conn = get_db_connection()
    hashed = hash_password(new_password)
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET password=%s WHERE email=%s", (hashed, email))
    conn.commit()
    cursor.close()
    conn.close()
    return jsonify({"success": True, "message": "Password reset successfully!"})

# ==================== FCM TOKEN ====================
@app.route("/api/user/fcm-token", methods=["POST"])
def update_fcm_token():
    auth_header = request.headers.get("Authorization")
    if not auth_header:
        return jsonify({"success": False, "message": "No authorization token"}), 401
    user = get_user_from_token(auth_header)
    if not user:
        return jsonify({"success": False, "message": "Invalid token"}), 401
    data = request.get_json()
    fcm_token = data.get("fcm_token")
    if not fcm_token:
        return jsonify({"success": False, "message": "FCM token required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor()
    try:
        cursor.execute("""
            INSERT INTO fcm_tokens (user_id, fcm_token)
            VALUES (%s, %s)
            ON DUPLICATE KEY UPDATE fcm_token=%s, updated_at=CURRENT_TIMESTAMP
        """, (user["id"], fcm_token, fcm_token))
        conn.commit()
        cursor.close()
        conn.close()
        return jsonify({"success": True, "message": "FCM token updated"}), 200
    except Exception as e:
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": "Database error"}), 500

# ==================== NOTICE ENDPOINTS ====================
@app.route("/api/admin/notices", methods=["POST"])
@admin_required
def create_notice():
    data = request.get_json()
    title = data.get("title", "").strip()
    message = data.get("message", "").strip()
    send_notification = data.get("send_notification", True)
    if not title or not message:
        return jsonify({"success": False, "message": "Title and message are required"}), 400
    auth_header = request.headers.get("Authorization")
    admin = get_user_from_token(auth_header)
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("INSERT INTO notices (title, message, admin_id, admin_name) VALUES (%s, %s, %s, %s)",
                       (title, message, admin["id"], admin["name"]))
        conn.commit()
        notice_id = cursor.lastrowid
        if send_notification:
            cursor.execute("SELECT fcm_token FROM fcm_tokens")
            tokens_data = cursor.fetchall()
            if tokens_data:
                send_push_notification([t["fcm_token"] for t in tokens_data], title, message)
        cursor.close()
        conn.close()
        return jsonify({"success": True, "message": "Notice published successfully", "notice_id": notice_id}), 201
    except Exception as e:
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": "Database error"}), 500

@app.route("/api/notices", methods=["GET"])
def get_all_notices():
    conn = get_db_connection()
    if not conn:
        return jsonify([]), 200
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("""
            SELECT id, title, message, admin_name,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') as createdAt
            FROM notices ORDER BY created_at DESC LIMIT 50
        """)
        notices = cursor.fetchall()
        cursor.close()
        conn.close()
        return jsonify(notices), 200
    except Exception as e:
        cursor.close()
        conn.close()
        return jsonify([]), 200

# ==================== ADMIN ENDPOINTS ====================
@app.route("/api/admin/users", methods=["GET"])
@admin_required
def get_all_users():
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT id, name, email, role, is_blocked FROM users ORDER BY id DESC")
    users = cursor.fetchall()
    cursor.close()
    conn.close()
    return jsonify([{
        "id": u["id"], "name": u["name"], "email": u["email"],
        "role": u.get("role", "user"), "isBlocked": u.get("is_blocked", 0)
    } for u in users]), 200

@app.route("/api/admin/stats", methods=["GET"])
@admin_required
def get_admin_stats():
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT COUNT(*) as total FROM users")
    total_users = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM image_data")
    total_image_scans = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM audio_data")
    total_audio_scans = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM video_data")
    total_video_scans = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM url_data")
    total_url_scans = cursor.fetchone()["total"]
    total_scans = total_image_scans + total_audio_scans + total_video_scans + total_url_scans
    cursor.execute("SELECT COUNT(*) as total FROM image_data WHERE result='FAKE'")
    fake_images = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM audio_data WHERE result='FAKE'")
    fake_audios = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM video_data WHERE result='FAKE'")
    fake_videos = cursor.fetchone()["total"]
    cursor.execute("SELECT COUNT(*) as total FROM url_data WHERE result='Phishing'")
    fake_urls = cursor.fetchone()["total"]
    total_fakes = fake_images + fake_audios + fake_videos + fake_urls
    fake_percentage = round((total_fakes / total_scans * 100), 2) if total_scans > 0 else 0
    cursor.execute("SELECT COUNT(*) as total FROM users WHERE is_blocked=1")
    blocked_users = cursor.fetchone()["total"]
    cursor.close()
    conn.close()
    return jsonify({"success": True, "stats": {
        "totalUsers": total_users,
        "totalScans": total_scans,
        "totalImages": total_image_scans,
        "totalAudios": total_audio_scans,
        "totalVideos": total_video_scans,
        "totalUrls": total_url_scans,
        "totalFakes": total_fakes,
        "totalReals": total_scans - total_fakes,
        "fakePercentage": fake_percentage,
        "blockedUsers": blocked_users,
        "reports": 0
    }}), 200

@app.route("/api/admin/scans", methods=["GET"])
@admin_required
def get_all_scans():
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("SELECT COUNT(*) as total FROM image_data")
        total_images = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM audio_data")
        total_audios = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM video_data")
        total_videos = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM url_data")
        total_urls = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM image_data WHERE result='FAKE'")
        fake_images = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM audio_data WHERE result='FAKE'")
        fake_audios = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM video_data WHERE result='FAKE'")
        fake_videos = cursor.fetchone()["total"]
        cursor.execute("SELECT COUNT(*) as total FROM url_data WHERE result='Phishing'")
        fake_urls = cursor.fetchone()["total"]
        total_all = total_images + total_audios + total_videos + total_urls
        total_fakes = fake_images + fake_audios + fake_videos + fake_urls
        total_reals = total_all - total_fakes
        cursor.execute("""
            SELECT i.id, 'image' as scan_type, i.filename, i.result, i.confidence, i.user_id,
                   u.name as user_name, u.email as user_email,
                   DATE_FORMAT(i.created_at, '%Y-%m-%d %H:%i:%s') as scanned_at
            FROM image_data i LEFT JOIN users u ON i.user_id = u.id
            ORDER BY i.created_at DESC
        """)
        image_scans = cursor.fetchall()
        cursor.execute("""
            SELECT a.id, 'audio' as scan_type, a.filename, a.result, a.confidence, a.user_id,
                   u.name as user_name, u.email as user_email,
                   DATE_FORMAT(a.created_at, '%Y-%m-%d %H:%i:%s') as scanned_at
            FROM audio_data a LEFT JOIN users u ON a.user_id = u.id
            ORDER BY a.created_at DESC
        """)
        audio_scans = cursor.fetchall()
        cursor.execute("""
            SELECT v.id, 'video' as scan_type, v.filename, v.result, v.confidence, v.user_id,
                   u.name as user_name, u.email as user_email,
                   DATE_FORMAT(v.created_at, '%Y-%m-%d %H:%i:%s') as scanned_at
            FROM video_data v LEFT JOIN users u ON v.user_id = u.id
            ORDER BY v.created_at DESC
        """)
        video_scans = cursor.fetchall()
        cursor.execute("""
            SELECT ur.id, 'url' as scan_type, ur.url as filename, ur.result,
                   ur.confidence, ur.user_id,
                   u.name as user_name, u.email as user_email,
                   DATE_FORMAT(ur.created_at, '%Y-%m-%d %H:%i:%s') as scanned_at
            FROM url_data ur LEFT JOIN users u ON ur.user_id = u.id
            ORDER BY ur.created_at DESC
        """)
        url_scans = cursor.fetchall()
        cursor.close()
        conn.close()
        all_scans = image_scans + audio_scans + video_scans + url_scans
        all_scans.sort(key=lambda x: x["scanned_at"] or "", reverse=True)
        return jsonify({
            "success": True,
            "totals": {
                "total_scans": total_all,
                "total_images": total_images,
                "total_audios": total_audios,
                "total_videos": total_videos,
                "total_urls": total_urls,
                "total_fakes": total_fakes,
                "total_reals": total_reals
            },
            "scans": all_scans
        }), 200
    except Exception as e:
        print(f"❌ Error fetching scans: {e}")
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": str(e)}), 500

@app.route("/api/admin/block-user/<int:user_id>", methods=["POST"])
@admin_required
def block_user(user_id):
    data = request.get_json()
    is_blocked = data.get("isBlocked", 1)
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database not connected"}), 500
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET is_blocked=%s WHERE id=%s", (is_blocked, user_id))
    conn.commit()
    cursor.close()
    conn.close()
    action = "blocked" if is_blocked else "unblocked"
    return jsonify({"success": True, "message": f"User {action} successfully"}), 200

@app.route("/api/admin/user/<int:user_id>/role", methods=["POST"])
@admin_required
def update_user_role(user_id):
    data = request.get_json()
    role = data.get("role", "user")
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET role=%s WHERE id=%s", (role, user_id))
    conn.commit()
    cursor.close()
    conn.close()
    return jsonify({"success": True, "message": f"User role updated to {role}"}), 200

@app.route("/api/admin/user/<int:user_id>", methods=["DELETE"])
@admin_required
def delete_user(user_id):
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor()
    cursor.execute("DELETE FROM users WHERE id=%s", (user_id,))
    conn.commit()
    cursor.close()
    conn.close()
    return jsonify({"success": True, "message": "User deleted"}), 200

# ==================== SYSTEM SETTINGS ====================
@app.route("/api/admin/settings", methods=["GET"])
@admin_required
def get_system_settings():
    try:
        conn = get_db_connection()
        if not conn:
            return jsonify({"success": False, "message": "Database error"}), 500
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT setting_key, setting_value FROM system_settings")
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        settings = {}
        for row in rows:
            key = row['setting_key']
            value = row['setting_value']
            if value.lower() in ['true', 'false']:
                settings[key] = value.lower() == 'true'
            elif value.isdigit():
                settings[key] = int(value)
            else:
                settings[key] = value
        return jsonify({"success": True, "settings": settings}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@app.route("/api/admin/settings", methods=["POST"])
@admin_required
def update_system_settings():
    try:
        data = request.get_json()
        auth_header = request.headers.get("Authorization")
        admin = get_user_from_token(auth_header)
        conn = get_db_connection()
        if not conn:
            return jsonify({"success": False, "message": "Database error"}), 500
        cursor = conn.cursor()
        for key, value in data.items():
            value_str = 'true' if isinstance(value, bool) and value else 'false' if isinstance(value, bool) else str(value)
            cursor.execute("UPDATE system_settings SET setting_value=%s, updated_by=%s WHERE setting_key=%s",
                           (value_str, admin["id"], key))
        conn.commit()
        cursor.close()
        conn.close()
        return jsonify({"success": True, "message": "Settings updated successfully"}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

# ==================== REPORT ENDPOINTS ====================
@app.route("/api/reports", methods=["POST"])
@token_required
def submit_report(current_user):
    data = request.get_json()
    report_type = data.get("reportType", "General").strip()
    description = data.get("description", "").strip()
    if not description:
        return jsonify({"success": False, "message": "Description is required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor()
    try:
        cursor.execute("""
            INSERT INTO reports (category, user_name, user_email, description)
            VALUES (%s, %s, %s, %s)
        """, (report_type, current_user["name"], current_user["email"], description))
        conn.commit()
        report_id = cursor.lastrowid
        cursor.close()
        conn.close()
        print(f"✅ Report #{report_id} from {current_user['name']}")
        return jsonify({"success": True, "message": "Report submitted successfully.", "report_id": report_id}), 201
    except Exception as e:
        print(f"❌ Error saving report: {e}")
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": str(e)}), 500

@app.route("/api/reports", methods=["GET"])
@admin_required
def get_reports():
    conn = get_db_connection()
    if not conn:
        return jsonify([]), 200
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("""
            SELECT id, category, user_name, user_email, description,
                   COALESCE(status, 'pending') as status,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') as created_at,
                   timestamp
            FROM reports ORDER BY id DESC
        """)
        reports = cursor.fetchall()
        cursor.close()
        conn.close()
        return jsonify(reports), 200
    except Exception as e:
        print(f"❌ Error fetching reports: {e}")
        cursor.close()
        conn.close()
        return jsonify([]), 200

@app.route("/api/reports/<int:reportId>/action", methods=["POST"])
@admin_required
def take_action_on_report(reportId):
    data = request.get_json()
    action = data.get("action", "reviewed")
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor()
    cursor.execute("UPDATE reports SET status=%s WHERE id=%s", (action, reportId))
    conn.commit()
    cursor.close()
    conn.close()
    return jsonify({"success": True, "message": f"Report {action}"}), 200

# ==================== IMAGE SCAN ====================
@app.route("/api/scan/image", methods=["POST"])
def scan_image():
    maintenance_check = check_maintenance_mode()
    if maintenance_check:
        return maintenance_check
    if not get_setting_value('image_detection_enabled'):
        return jsonify({"success": False, "message": "Image detection is currently disabled."}), 503
    try:
        print("=" * 60)
        print("📸 IMAGE SCAN REQUEST RECEIVED")
        print("=" * 60)
        if "file" not in request.files:
            return jsonify({"success": False, "message": "No image provided"}), 400
        if image_detector is None:
            return jsonify({"success": False, "message": "Image model not loaded"}), 500
        user_id = request.form.get("user_id", "anonymous")
        image = request.files["file"]
        print(f"✅ File: {image.filename}")
        print(f"✅ User ID: {user_id}")
        filepath = os.path.join(UPLOAD_FOLDER, image.filename)
        image.save(filepath)
        file_size = os.path.getsize(filepath)
        print(f"✅ File size: {file_size / 1024:.2f} KB")
        print("🔄 Processing image...")
        result_data = image_detector.predict(filepath)
        if result_data['result'] == 'ERROR':
            return jsonify({"success": False, "message": f"Prediction error: {result_data.get('error', 'Unknown error')}"}), 500
        result = result_data['result']
        confidence = result_data['confidence']
        if os.path.exists(filepath):
            os.remove(filepath)
            print("✅ Temporary file deleted")
        print(f"✅ Result: {result} ({confidence*100:.2f}%)")
        conn = get_db_connection()
        if conn:
            try:
                cursor = conn.cursor()
                cursor.execute(
                    "INSERT INTO image_data (user_id, filename, result, confidence) VALUES (%s,%s,%s,%s)",
                    (user_id, image.filename, result, confidence)
                )
                conn.commit()
                cursor.close()
                conn.close()
                print("✅ Result saved to database")
            except Exception as db_err:
                print(f"⚠️ Database error: {db_err}")
        print("=" * 60)
        return jsonify({
            "success": True,
            "message": "Image scan completed successfully",
            "result": {"result": result, "confidence": confidence}
        }), 200
    except Exception as e:
        print("=" * 60)
        print("❌ CRITICAL ERROR IN IMAGE SCAN:")
        print(str(e))
        print("=" * 60)
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "message": f"Error: {str(e)}"}), 500

# ==================== AUDIO SCAN ====================
@app.route("/api/scan/audio", methods=["POST"])
def scan_audio():
    maintenance_check = check_maintenance_mode()
    if maintenance_check:
        return maintenance_check
    if not get_setting_value('audio_detection_enabled'):
        return jsonify({"success": False, "message": "Audio detection is currently disabled."}), 503
    try:

        print("=" * 60)
        print("🎵 AUDIO SCAN REQUEST RECEIVED")
        print("=" * 60)
        if 'file' not in request.files:
            return jsonify({"success": False, "message": "No audio file provided"}), 400
        if audio_detector is None:
            return jsonify({"success": False, "message": "Audio model not available"}), 500
        audio_file = request.files['file']
        user_id = request.form.get('user_id', 'anonymous')
        print(f"✅ File: {audio_file.filename}")
        print(f"✅ User ID: {user_id}")
        audio_path = os.path.join(UPLOAD_FOLDER, audio_file.filename)
        audio_file.save(audio_path)
        file_size = os.path.getsize(audio_path)
        print(f"✅ File size: {file_size / 1024:.2f} KB")
        print("🔄 Processing audio...")
        result = audio_detector.predict(audio_path)
        print(f"✅ Prediction result: {result}")
        if os.path.exists(audio_path):
            os.remove(audio_path)
            print("✅ Temporary file deleted")
        if result['result'] == 'ERROR':
            return jsonify({"success": False, "message": result.get('error', 'Unknown error')}), 500
        conn = get_db_connection()
        if conn:
            try:
                cursor = conn.cursor()
                cursor.execute(
                    "INSERT INTO audio_data (user_id, filename, result, confidence) VALUES (%s,%s,%s,%s)",
                    (user_id, audio_file.filename, result['result'], result['confidence'])
                )
                conn.commit()
                cursor.close()
                conn.close()
                print("✅ Result saved to database")
            except Exception as db_err:
                print(f"⚠️ Database error: {db_err}")
        print("=" * 60)
        return jsonify({
            "success": True,
            "message": "Audio scan completed successfully",
            "result": {"result": result['result'], "confidence": result['confidence']}
        }), 200
    except Exception as e:
        print("=" * 60)
        print("❌ CRITICAL ERROR IN AUDIO SCAN:")
        print(str(e))
        print("=" * 60)
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "message": f"Server error: {str(e)}"}), 500

# ==================== VIDEO SCAN ====================
@app.route("/api/scan/video", methods=["POST"])
def scan_video():
    maintenance_check = check_maintenance_mode()
    if maintenance_check:
        return maintenance_check
    if get_setting_value('video_detection_enabled') == False:
        return jsonify({"success": False, "message": "Video detection is currently disabled."}), 503

    try:
        print("=" * 60)
        print("📹 VIDEO SCAN REQUEST RECEIVED")
        print("=" * 60)
        if 'file' not in request.files:
            return jsonify({"success": False, "message": "No video file provided"}), 400
        if video_model is None:
            return jsonify({"success": False, "message": "Video model not available"}), 500
        if detector is None:
            return jsonify({"success": False, "message": "Face detector not available"}), 500
        video_file = request.files['file']
        user_id = request.form.get('user_id', 'anonymous')
        print(f"✅ File: {video_file.filename}")
        print(f"✅ User ID: {user_id}")
        video_path = os.path.join(UPLOAD_FOLDER, video_file.filename)
        video_file.save(video_path)
        file_size = os.path.getsize(video_path)
        print(f"✅ File size: {file_size / (1024*1024):.2f} MB")
        print("🔄 Processing video...")
        result = predict_video(video_path)
        print(f"✅ Result: {result}")
        if os.path.exists(video_path):
            os.remove(video_path)
            print("✅ Temporary file deleted")
        conn = get_db_connection()
        if conn:
            try:
                cursor = conn.cursor()
                cursor.execute(
                    "INSERT INTO video_data (user_id, filename, result, confidence) VALUES (%s,%s,%s,%s)",
                    (user_id, video_file.filename, result['result'], result['confidence'])
                )
                conn.commit()
                cursor.close()
                conn.close()
                print("✅ Result saved to database")
            except Exception as db_err:
                print(f"⚠️ Database error: {db_err}")
        print("=" * 60)
        display_result = "FAKE" if result['result'] == "Deepfake" else "REAL"
        return jsonify({
            "success": True,
            "message": "Video scan completed successfully",
            "result": {"result": display_result, "confidence": result['confidence']}
        }), 200
    except Exception as e:
        print("=" * 60)
        print("❌ CRITICAL ERROR IN VIDEO SCAN:")
        print(str(e))
        print("=" * 60)
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "message": f"Server error: {str(e)}"}), 500

# ==================== URL SCAN ====================
@app.route('/api/scan/url', methods=['POST'])
def scan_url():
    maintenance_check = check_maintenance_mode()
    if maintenance_check:
        return maintenance_check
    if get_setting_value('url_detection_enabled') == False:
        return jsonify({"success": False, "message": "URL detection is currently disabled."}), 503
    try:
        data = request.get_json()
        url = data.get('url')
        user_id = data.get('user_id', 'anonymous')
        if not url:
            return jsonify({"success": False, "message": "URL is required"}), 400
        print("=" * 60)
        print("🔗 URL SCAN REQUEST RECEIVED")
        print(f"📊 Scanning URL: {url}")
        print("=" * 60)
        result = predict_url(url)
        print(f"✅ ML Result: {result['result']} ({result['confidence']*100:.1f}%)")
        try:
            headers = {'User-Agent': 'Mozilla/5.0'}
            response = requests.get(url, headers=headers, timeout=10)
            soup = BeautifulSoup(response.text, 'html.parser')
            suspicious_keywords = ['deepfake', 'fake video', 'manipulated', 'faceswap']
            page_text = soup.get_text().lower()
            extra_confidence = 0.0
            for keyword in suspicious_keywords:
                if keyword in page_text:
                    extra_confidence += 0.25
            if extra_confidence >= 0.5:
                result['result'] = 'Phishing'
                result['confidence'] = min(result['confidence'] + extra_confidence, 1.0)
            print(f"🔍 Heuristic extra confidence: {extra_confidence:.2f}")
        except Exception as fetch_err:
            print(f"⚠️ Could not fetch URL content (skipping heuristic): {fetch_err}")
        domain = urlparse(url).netloc
        conn = get_db_connection()
        if conn:
            try:
                cursor = conn.cursor()
                cursor.execute(
                    "INSERT INTO url_data (user_id, url, filename, result, confidence) VALUES (%s, %s, %s, %s, %s)",
                    (user_id, url, domain, result['result'], result['confidence'])
                )
                conn.commit()
                cursor.close()
                conn.close()
                print("✅ Result saved to database (url_data)")
            except Exception as db_err:
                print(f"⚠️ Database error: {db_err}")
        print("=" * 60)
        return jsonify({"success": True, "result": result})
    except Exception as e:
        print(f"❌ Error: {str(e)}")
        return jsonify({"success": False, "message": str(e)}), 500

# ==================== CHAT ENDPOINT ====================
@app.route("/api/chat", methods=["POST"])
def ai_chat():
    maintenance_check = check_maintenance_mode()
    if maintenance_check:
        return maintenance_check
    chatbot_check = check_chatbot_enabled()
    if chatbot_check:
        return chatbot_check
    data = request.get_json()
    user_message = data.get("message")
    scan_context = data.get("scan_context", "")
    if not user_message:
        return jsonify({"success": False, "message": "Message is required"}), 400
    system_prompt = (
        "You are DeepGuard AI, an expert assistant for a deepfake detection app. "
        "Answer questions about deepfake detection, data privacy, security, and how deepfakes work. "
        "Keep replies short and clear for a mobile screen.\n\n"
        f"User's last scan result: {scan_context if scan_context else 'None'}"
    )
    payload = {
        "model": "llama-3.1-8b-instant",
        "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_message}],
        "temperature": 0.3
    }
    headers = {"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"}
    try:
        response = requests.post(GROQ_URL, json=payload, headers=headers, timeout=30)
        result = response.json()
        if "choices" not in result:
            return jsonify({"success": False, "message": result.get("error", {}).get("message", "Groq API error")}), 500
        reply = result["choices"][0]["message"]["content"]
        return jsonify({"success": True, "reply": reply})
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

# ==================== SECURITY QUESTION ENDPOINTS ====================
@app.route("/api/get-security-question", methods=["POST"])
def get_security_question():
    data = request.get_json()
    email = data.get("email")
    if not email:
        return jsonify({"success": False, "message": "Email is required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT security_question FROM users WHERE email=%s", (email,))
    user = cursor.fetchone()
    cursor.close()
    conn.close()
    if not user:
        return jsonify({"success": False, "message": "User not found"}), 404
    question = user.get("security_question")
    if not question:
        return jsonify({"success": False, "message": "No security question set for this account."}), 404

    return jsonify({"success": True, "question": question}), 200

@app.route("/api/verify-security-answer", methods=["POST"])
def verify_security_answer():
    data = request.get_json()
    email = data.get("email")
    answer = data.get("answer")

    if not email or not answer:
        return jsonify({"success": False, "message": "Email and answer required"}), 400

    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500

    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT security_answer FROM users WHERE email=%s", (email,))
    user = cursor.fetchone()
    cursor.close()
    conn.close()

    if not user:
        return jsonify({"success": False, "message": "User not found"}), 404

    if user["security_answer"] is None:
        return jsonify({"success": False, "message": "No security answer set"}), 400

    if user["security_answer"].strip().lower() == answer.strip().lower():
        return jsonify({"success": True, "message": "Answer correct"})
    else:
        return jsonify({"success": False, "message": "Incorrect answer"})
   

# ==================== USER ENDPOINTS ====================
@app.route("/api/user/change-password", methods=["POST"])
def change_password():
    auth_header = request.headers.get("Authorization")
    if not auth_header:
        return jsonify({"success": False, "message": "No authorization token"}), 401
    user = get_user_from_token(auth_header)
    if not user:
        return jsonify({"success": False, "message": "Invalid token"}), 401
    data = request.get_json()
    current_password = data.get("currentPassword")
    new_password = data.get("newPassword")
    if not current_password or not new_password:
        return jsonify({"success": False, "message": "All fields required"}), 400
    conn = get_db_connection()
    if not conn:
        return jsonify({"success": False, "message": "Database error"}), 500
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT password FROM users WHERE id=%s", (user["id"],))
    db_user = cursor.fetchone()
    if not db_user or not check_password(current_password, db_user["password"]):
        cursor.close()
        conn.close()
        return jsonify({"success": False, "message": "Current password is incorrect"}), 401
    hashed = hash_password(new_password)
    cursor.execute("UPDATE users SET password=%s WHERE id=%s", (hashed, user["id"]))
    conn.commit()
    cursor.close()
    conn.close()
    return jsonify({"success": True, "message": "Password changed successfully"}), 200

# ==================== RUN SERVER ====================
if __name__ == "__main__":
    print("=" * 60)
    print("🚀 DeepGuard Backend Starting...")
    print("=" * 60)
    print(f"📁 Upload folder: {UPLOAD_FOLDER}")
    print(f"🌐 Server will run on: http://0.0.0.0:5000")
    print("=" * 60)
    print("📊 Available endpoints:")
    print("   ✅ POST /api/scan/image  (Image deepfake detection)")
    print("   ✅ POST /api/scan/video  (Video deepfake detection)")
    print("   ✅ POST /api/scan/audio  (Audio deepfake detection)")
    print("   ✅ POST /api/scan/url    (URL phishing detection)")
    print("   ✅ POST /api/signup      (User registration)")
    print("   ✅ POST /api/login       (User authentication)")
    print("   ✅ GET  /api/health      (Health check)")
    print("🔐 Password Reset: ACTIVE")
    print("🤖 Groq API Key: LOADED")
    print("👑 Admin System: ACTIVE")
    print("📢 Notice System: ACTIVE")
    print("🔥 FCM Push Notifications: " + ("ACTIVE" if firebase_admin._apps else "DISABLED"))
    print("📊 Report System: ACTIVE")
    print("🔒 User Block Check: ACTIVE")
    print("⚙️  System Settings: ACTIVE")
    print("=" * 60)
    app.run(debug=True, host="0.0.0.0", port=5000)