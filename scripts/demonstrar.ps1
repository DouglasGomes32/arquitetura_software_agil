param()
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$java = (Get-Command java.exe -ErrorAction Stop).Source
$processos = New-Object 'System.Collections.Generic.List[System.Diagnostics.Process]'
$execucao = Join-Path $raiz ('data/demo-' + [Guid]::NewGuid().ToString('N'))

function Confirmar($Condicao, [string]$Mensagem) {
    if (-not $Condicao) { throw $Mensagem }
}
function Esperar([scriptblock]$Condicao, [string]$Mensagem) {
    $fim = [DateTime]::UtcNow.AddSeconds(25)
    do {
        try { if (& $Condicao) { return } } catch { }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $fim)
    throw "Tempo esgotado: $Mensagem"
}
function Iniciar([string]$Nome, [string]$Modulo, [string[]]$Opcoes) {
    $jar = Join-Path $raiz "$Modulo/target/$Modulo-1.0.0-app.jar"
    $argumentos = @('-Xmx192m', '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn') + $Opcoes + @('-jar', $jar)
    $linha = ($argumentos | ForEach-Object { '"' + $_ + '"' }) -join ' '
    $processo = Start-Process -FilePath $java -ArgumentList $linha -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $execucao "$Nome.out.log") -RedirectStandardError (Join-Path $execucao "$Nome.err.log")
    $processos.Add($processo)
    return $processo
}
function Obter([string]$Url) { Invoke-RestMethod -Uri $Url -TimeoutSec 3 }
function ConsultarAna { Invoke-WebRequest -Uri 'http://127.0.0.1:8082/avisos?voluntarioId=ana' -UseBasicParsing -TimeoutSec 3 }
function QuantidadeAvisos([string]$Json) {
    $lista = ConvertFrom-Json -InputObject $Json
    return $lista.Count
}
function CriarEscala([string]$Nome) {
    $corpo = @{ evento = $Nome; data = '2026-11-22'; voluntarios = @(@{id = 'ana'; nome = 'Ana'}, @{id = 'joao'; nome = 'Joao'}) } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri 'http://127.0.0.1:8081/escalas' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo)) -TimeoutSec 5
}
function Publicar($Escala) {
    Invoke-RestMethod -Uri "http://127.0.0.1:8081/escalas/$($Escala.id)/publicar" -Method Post -TimeoutSec 5
}

try {
    foreach ($modulo in @('broker', 'escalas', 'avisos')) {
        Confirmar (Test-Path -LiteralPath (Join-Path $raiz "$modulo/target/$modulo-1.0.0-app.jar")) 'Compile primeiro: .\mvnw.cmd verify'
    }
    foreach ($porta in @(61616, 8081, 8082)) {
        $teste = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $porta)
        try { $teste.Start() } catch { throw "Porta $porta ocupada. Encerre a execucao manual antes da demonstracao." }
        finally { $teste.Stop() }
    }
    New-Item -ItemType Directory -Path $execucao -Force | Out-Null
    Write-Host 'PERFIL B - Java 21, dois servicos e infraestrutura ActiveMQ'
    Write-Host "Logs e dados isolados: $execucao"
    $brokerProc = Iniciar 'broker' 'broker' @("-Dbroker.data=$execucao/broker")
    $escalasProc = Iniciar 'escalas' 'escalas' @("-Descalas.db=$execucao/escalas/banco")
    $avisosProc = Iniciar 'avisos' 'avisos' @("-Davisos.db=$execucao/avisos/banco")
    Esperar { (Obter 'http://127.0.0.1:8081/health').status -eq 'UP' } 'API Escalas'
    Esperar { (Obter 'http://127.0.0.1:8082/health').filaConectada -eq $true } 'consumidor conectado'
    Write-Host "Processos: broker=$($brokerProc.Id), escalas=$($escalasProc.Id), avisos=$($avisosProc.Id)"

    $vazia = ConsultarAna
    Confirmar ($vazia.Headers['X-Cache'] -eq 'MISS') 'Primeira consulta deveria ser MISS'
    Confirmar ((QuantidadeAvisos $vazia.Content) -eq 0) 'A consulta inicial deveria estar vazia'
    Confirmar ((ConsultarAna).Headers['X-Cache'] -eq 'HIT') 'Segunda consulta deveria ser HIT'
    Write-Host '1. Cache vazio: MISS e depois HIT.'

    $escala = CriarEscala 'Culto de domingo'
    $publicada = Publicar $escala
    Esperar { (Obter 'http://127.0.0.1:8082/health').eventosProcessados -eq 1 } 'primeiro evento'
    $nova = ConsultarAna
    Confirmar ($nova.Headers['X-Cache'] -eq 'MISS') 'Publicacao deveria invalidar o cache'
    Confirmar ((QuantidadeAvisos $nova.Content) -eq 1) 'Esperado um aviso para Ana'
    Confirmar ((ConsultarAna).Headers['X-Cache'] -eq 'HIT') 'Avisos deveriam entrar no cache'
    Write-Host "2. Escala publicada: eventoId=$($publicada.eventoId). Um aviso; MISS apos invalidacao e depois HIT."

    $republicada = Publicar $escala
    Confirmar ($republicada.eventoId -eq $publicada.eventoId) 'Publicar de novo mudou o eventoId'
    Invoke-RestMethod -Uri "http://127.0.0.1:8081/escalas/$($escala.id)/reentregar" -Method Post -TimeoutSec 5 | Out-Null
    Esperar { (Obter 'http://127.0.0.1:8082/health').duplicadosIgnorados -ge 1 } 'mensagem duplicada'
    Confirmar ((QuantidadeAvisos (ConsultarAna).Content) -eq 1) 'Mensagem repetida duplicou o aviso'
    Write-Host '3. Reentrega real pela fila: continua um aviso, duplicado ignorado.'

    Stop-Process -Id $avisosProc.Id -Force
    $avisosProc.WaitForExit()
    $outra = CriarEscala 'Ensaio dos voluntarios'
    Publicar $outra | Out-Null
    Esperar { (Obter 'http://127.0.0.1:8081/health').outboxPendentes -eq 0 } 'envio sem consumidor'
    $avisosProc = Iniciar 'avisos-reinicio' 'avisos' @("-Davisos.db=$execucao/avisos/banco")
    Esperar { (Obter 'http://127.0.0.1:8082/health').eventosProcessados -eq 2 } 'recuperacao do consumidor'
    $final = ConsultarAna
    Confirmar ((QuantidadeAvisos $final.Content) -eq 2) 'Esperados dois avisos de escalas diferentes'
    Write-Host '4. Consumidor desligado durante outra publicacao: fila reteve e entregou ao reiniciar. Dois avisos.'

    Invoke-RestMethod -Uri "http://127.0.0.1:8081/escalas/$($escala.id)/reentregar" -Method Post -TimeoutSec 5 | Out-Null
    Esperar { (Obter 'http://127.0.0.1:8082/health').duplicadosIgnorados -ge 1 } 'idempotencia depois do reinicio'
    Confirmar ((QuantidadeAvisos (ConsultarAna).Content) -eq 2) 'Reinicio perdeu a idempotencia'
    Write-Host '5. Reentrega apos reinicio: continuam dois avisos. Marcador de idempotencia persistiu.'
    $final.Content | Set-Content -LiteralPath (Join-Path $execucao 'avisos-resultado.json') -Encoding UTF8
    Write-Host 'DEMONSTRACAO APROVADA.'
} finally {
    foreach ($processo in $processos) {
        $processo.Refresh()
        if (-not $processo.HasExited) {
            Stop-Process -Id $processo.Id -Force -ErrorAction SilentlyContinue
            $processo.WaitForExit()
        }
        $processo.Dispose()
    }
    Write-Host 'Processos da demonstracao encerrados.'
}
