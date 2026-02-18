<#import "layout/base.ftl" as layout>
<@layout.layout title="Welcome">
  <h2>Welcome, ${firstName}!</h2>
  <p>Your account has been created successfully.</p>
  <table class="detail-table">
    <tr><td>Email</td><td>${email}</td></tr>
    <tr><td>Role</td><td>${role}</td></tr>
  </table>
  <p>You can now log in and start using the system.</p>
  <a href="${loginUrl}" class="btn">Log In</a>
</@layout.layout>
