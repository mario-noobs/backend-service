<#macro layout title="">
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f4f7; color: #333; }
    .email-wrapper { width: 100%; background-color: #f4f4f7; padding: 30px 0; }
    .email-content { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
    .email-header { background-color: #2563eb; color: #ffffff; padding: 24px 32px; text-align: center; }
    .email-header h1 { margin: 0; font-size: 22px; font-weight: 600; }
    .email-body { padding: 32px; line-height: 1.6; }
    .email-footer { padding: 20px 32px; text-align: center; font-size: 12px; color: #999; border-top: 1px solid #eee; }
    .btn { display: inline-block; padding: 12px 24px; background-color: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: 600; margin: 16px 0; }
    .alert-box { padding: 16px; border-radius: 6px; margin: 16px 0; }
    .alert-error { background-color: #fef2f2; border: 1px solid #fecaca; color: #991b1b; }
    .alert-warning { background-color: #fffbeb; border: 1px solid #fed7aa; color: #92400e; }
    .alert-info { background-color: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; }
    .detail-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
    .detail-table td { padding: 8px 12px; border-bottom: 1px solid #f0f0f0; }
    .detail-table td:first-child { font-weight: 600; color: #666; width: 140px; }
  </style>
</head>
<body>
  <div class="email-wrapper">
    <div class="email-content">
      <div class="email-header">
        <h1>Face Recognition System</h1>
      </div>
      <div class="email-body">
        <#nested>
      </div>
      <div class="email-footer">
        <p>This is an automated message from Face Recognition System.</p>
        <p>Please do not reply to this email.</p>
      </div>
    </div>
  </div>
</body>
</html>
</#macro>
