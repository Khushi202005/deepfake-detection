<!DOCTYPE html>
<html>
<body style="font-family:Arial,sans-serif;background:#f5f7fa;padding:20px;">
        <div style="max-width:480px;margin:auto;background:white;border-radius:16px;padding:32px;
                    box-shadow:0 4px 12px rgba(0,0,0,0.1);">

            <div style="text-align:center;margin-bottom:24px;">
                <h1 style="color:#1A237E;font-size:24px;margin:0;">🛡️ DeepGuard</h1>
                <p style="color:#78909C;margin:4px 0 0;">Password Reset OTP</p>
            </div>

            <p style="color:#333;font-size:15px;">Hi <strong>{user_name}</strong>,</p>
            <p style="color:#555;font-size:14px;line-height:1.6;">
                Use the OTP below to reset your DeepGuard password.
                It expires in <strong>10 minutes</strong>.
            </p>

            <!-- OTP Box -->
            <div style="text-align:center;margin:30px 0;">
                <div style="display:inline-block;background:#F0F4FF;border:2px dashed #1A237E;
                            border-radius:12px;padding:16px 40px;">
                    <p style="margin:0;font-size:11px;color:#78909C;letter-spacing:2px;">YOUR OTP CODE</p>
                    <p style="margin:8px 0 0;font-size:42px;font-weight:bold;color:#1A237E;
                               letter-spacing:12px;">{otp}</p>
                </div>
            </div>

            <p style="color:#888;font-size:12px;text-align:center;">
                Do not share this OTP with anyone.<br>
                If you didn't request this, ignore this email.
            </p>

            <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
            <p style="color:#aaa;font-size:11px;text-align:center;">© 2025 DeepGuard</p>
        </div>
        </body></html>