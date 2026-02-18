<#import "layout/base.ftl" as layout>
<@layout.layout title="You're Invited">
  <h2>Welcome to the team, ${firstName}!</h2>
  <p>You've been invited to join the Face Recognition System.</p>
  <table class="detail-table">
    <tr><td>Email</td><td>${email}</td></tr>
    <tr><td>Role</td><td>${role}</td></tr>
  </table>
  <p>Click the button below to set up your password and activate your account:</p>
  <a href="${setupUrl}" class="btn">Set Up Password</a>
  <p>This link will expire in <strong>${expiryHours} hours</strong>.</p>
  <p>If you weren't expecting this invitation, you can safely ignore this email.</p>
</@layout.layout>
