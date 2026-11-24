param([switch]$SemNavegador)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$pasta = Join-Path $raiz 'data/painel'
$controle = Join-Path $pasta 'processos.json'
$java = (Get-Command java.exe -ErrorAction Stop).Source

if (Test-Path -LiteralPath $controle) {
    $registrados = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($controle))
    $vivos = @($registrados | Where-Object {
        $existente = Get-Process -Id $_.id -ErrorAction SilentlyContinue
        $existente -and $existente.StartTime.ToUniversalTime().Ticks.ToString() -eq $_.inicio
    })
    if ($vivos.Count -gt 0) {
        if ($vivos.Count -ne 3) { throw 'Ambiente incompleto. Rode scripts/fechar-painel.ps1 e abra novamente.' }
        try { $saude = Invoke-RestMethod 'http://127.0.0.1:8081/health' -TimeoutSec 3 } catch { throw 'O ambiente registrado nao respondeu. Rode scripts/fechar-painel.ps1 e abra novamente.' }
        if ($saude.servico -ne 'escalas') { throw 'A porta 8081 esta ocupada por outro servico.' }
        Write-Output 'Painel ja esta rodando: http://127.0.0.1:8081/'
        if (-not $SemNavegador) { Start-Process 'http://127.0.0.1:8081/' }
        exit 0
    }
}
foreach ($modulo in @('broker', 'escalas', 'avisos')) {
    if (-not (Test-Path -LiteralPath (Join-Path $raiz "$modulo/target/$modulo-1.0.0-app.jar"))) {
        throw 'Compile primeiro na raiz do repositorio: .\mvnw.cmd verify'
    }
}
foreach ($porta in @(61616, 8081, 8082)) {
    $teste = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $porta)
    try { $teste.Start() } catch { throw "Porta $porta ocupada. Encerre o servico que a utiliza antes de iniciar o painel." }
    finally { $teste.Stop() }
}
New-Item -ItemType Directory -Path $pasta -Force | Out-Null
$iniciados = @()
try {
    foreach ($modulo in @('broker', 'escalas', 'avisos')) {
        $jar = Join-Path $raiz "$modulo/target/$modulo-1.0.0-app.jar"
        $opcao = switch ($modulo) {
            'broker' { "-Dbroker.data=$pasta/broker" }
            'escalas' { "-Descalas.db=$pasta/escalas/banco" }
            'avisos' { "-Davisos.db=$pasta/avisos/banco" }
        }
        $argumentos = @('-Xmx192m', '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn', $opcao, '-jar', $jar)
        $linha = ($argumentos | ForEach-Object { '"' + $_ + '"' }) -join ' '
        $processo = Start-Process -FilePath $java -ArgumentList $linha -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $pasta "$modulo.out.log") -RedirectStandardError (Join-Path $pasta "$modulo.err.log")
        $iniciados += @{ id = $processo.Id; inicio = $processo.StartTime.ToUniversalTime().Ticks.ToString(); modulo = $modulo }
    }
    $limite = [DateTime]::UtcNow.AddSeconds(35)
    $pronto = $false
    do {
        try {
            $escalas = Invoke-RestMethod 'http://127.0.0.1:8081/health' -TimeoutSec 2
            $avisos = Invoke-RestMethod 'http://127.0.0.1:8082/health' -TimeoutSec 2
            $pronto = $escalas.servico -eq 'escalas' -and $avisos.filaConectada -eq $true
        } catch { }
        if (-not $pronto) { Start-Sleep -Milliseconds 300 }
    } while (-not $pronto -and [DateTime]::UtcNow -lt $limite)
    if (-not $pronto) { throw "Os servicos nao ficaram prontos. Consulte os logs em $pasta" }
    ConvertTo-Json -InputObject @($iniciados) | Set-Content -LiteralPath $controle -Encoding UTF8
} catch {
    foreach ($registro in $iniciados) {
        $processo = Get-Process -Id $registro.id -ErrorAction SilentlyContinue
        if ($processo -and $processo.StartTime.ToUniversalTime().Ticks.ToString() -eq $registro.inicio) { Stop-Process -Id $processo.Id -Force }
    }
    throw
}
Write-Output 'Painel pronto: http://127.0.0.1:8081/'
Write-Output 'Os tres processos permanecem ativos. Para encerrar: scripts/fechar-painel.ps1'
Write-Output "Dados e logs: $pasta"
if (-not $SemNavegador) { Start-Process 'http://127.0.0.1:8081/' }
