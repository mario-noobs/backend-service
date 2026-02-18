<#import "layout/base.ftl" as layout>
<@layout.layout title="Password Reset">
  <h2>Password Reset Request</h2>
  <p>Hi ${firstName},</p>
  <p>We received a request to reset your password. Click the button below to set a new password:</p>
  <a href="${resetUrl}" class="btn">Reset Password</a>
  <p>This link will expire in <strong>${expiryMinutes} minutes</strong>.</p>
  <p>If you didn't request a password reset, you can safely ignore this email.</p>
</@layout.layout>
