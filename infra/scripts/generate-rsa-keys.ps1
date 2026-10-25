$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$secretDir = Join-Path $repoRoot "infra/secrets"
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
$privateKey = Join-Path $secretDir "railreserve-private.pem"
$publicKey = Join-Path $secretDir "railreserve-public.pem"

if ((Test-Path $privateKey) -or (Test-Path $publicKey)) {
    throw "A key file already exists in infra/secrets. Back it up securely and remove/rotate intentionally before generating replacement keys."
}
if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    throw "OpenSSL was not found. Install OpenSSL 3 and reopen PowerShell, then run this script again."
}

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $privateKey
if ($LASTEXITCODE -ne 0) { throw "OpenSSL failed to generate the private key." }
openssl pkey -in $privateKey -pubout -out $publicKey
if ($LASTEXITCODE -ne 0) { Remove-Item $privateKey -Force; throw "OpenSSL failed to derive the public key." }

Write-Host "Created RSA-3072 keys under infra/secrets."
Write-Host "Keep railreserve-private.pem secret and out of source control."
Write-Host "Only railreserve-public.pem should be mounted into non-auth services."
