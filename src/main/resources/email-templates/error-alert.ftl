<#import "layout/base.ftl" as layout>
<@layout.layout title="${alertTitle}">
  <div class="alert-box <#if severity == 'critical'>alert-error<#elseif severity == 'warning'>alert-warning<#else>alert-info</#if>">
    <strong>${alertType} Alert - ${severity?upper_case}</strong>
  </div>

  <h2>${alertTitle}</h2>
  <p>${alertMessage}</p>

  <table class="detail-table">
    <tr><td>Timestamp</td><td>${timestamp}</td></tr>
    <tr><td>Severity</td><td>${severity}</td></tr>
    <#if requestId??>
      <tr><td>Request ID</td><td>${requestId}</td></tr>
    </#if>
    <#if action??>
      <tr><td>Action</td><td>${action}</td></tr>
    </#if>
    <#if actorIp??>
      <tr><td>Client IP</td><td>${actorIp}</td></tr>
    </#if>
    <#if statusCode??>
      <tr><td>Status Code</td><td>${statusCode?c}</td></tr>
    </#if>
    <#if httpPath??>
      <tr><td>Path</td><td>${httpPath}</td></tr>
    </#if>
    <#if failedAttempts??>
      <tr><td>Failed Attempts</td><td>${failedAttempts?c}</td></tr>
    </#if>
    <#if windowMinutes??>
      <tr><td>Time Window</td><td>${windowMinutes?c} minutes</td></tr>
    </#if>
  </table>
</@layout.layout>
