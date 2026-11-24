param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$projeto = Split-Path $PSScriptRoot -Parent
$saida = Join-Path $projeto 'data/entrega'
New-Item -ItemType Directory -Path $saida -Force | Out-Null
$zip = Join-Path $saida ('escalas-e-avisos-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8) + '.zip')
$arquivo = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    # Lista permitida: nao inclui dados, logs, .env, node_modules ou targets.
    $pastas = @('comum', 'broker', 'escalas', 'avisos', 'verificacao')
    $fontes = @()
    foreach ($pasta in $pastas) {
        $fontes += Get-Item -LiteralPath (Join-Path $projeto "$pasta/pom.xml")
        $fontes += Get-ChildItem -LiteralPath (Join-Path $projeto "$pasta/src") -File -Recurse
    }
    $fontes += Get-ChildItem -LiteralPath (Join-Path $projeto 'scripts') -File
    $fontes += Get-ChildItem -LiteralPath (Join-Path $projeto 'docs') -File -Recurse
    $fontes += Get-ChildItem -LiteralPath (Join-Path $projeto '.mvn') -File -Recurse -Force
    foreach ($nome in @('pom.xml', '.gitignore', '.gitattributes', 'mvnw', 'mvnw.cmd', 'README.md')) {
        $fontes += Get-Item -LiteralPath (Join-Path $projeto $nome) -Force
    }
    foreach ($fonte in $fontes) {
        $relativo = $fonte.FullName.Substring($projeto.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($arquivo, $fonte.FullName, $relativo) | Out-Null
    }
} finally { $arquivo.Dispose() }
Write-Output "ZIP criado: $zip"
