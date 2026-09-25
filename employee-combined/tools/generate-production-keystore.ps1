$ErrorActionPreference = "Stop"
$Alias = "employee-management-release"
$OutDir = Join-Path $PSScriptRoot "..\\private-signing"
$OutFile = Join-Path $OutDir "employee-management-release.jks"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install JDK 17+ and run again."
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (Test-Path $OutFile) { throw "Keystore already exists and was not overwritten: $OutFile" }

Write-Host "Creating the permanent Employee Management Android signing key." -ForegroundColor Cyan
Write-Host "Keep the JKS and passwords in a secure offline backup. Never commit it to Git." -ForegroundColor Yellow

& keytool -genkeypair -v -keystore $OutFile -storetype JKS -alias $Alias -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Employee Management, OU=Android Release, O=Alkeynes Projects, C=IN"
if ($LASTEXITCODE -ne 0) { throw "keytool failed." }

Write-Host "Created: $OutFile" -ForegroundColor Green
Write-Host "Alias: $Alias"
& keytool -list -v -keystore $OutFile -alias $Alias | Select-String "SHA256:"
