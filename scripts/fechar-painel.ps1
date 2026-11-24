param()
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$controle = Join-Path $raiz 'data/painel/processos.json'
if (-not (Test-Path -LiteralPath $controle)) { Write-Output 'Nenhum painel iniciado por este script.'; exit 0 }
$registros = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($controle))
foreach ($registro in $registros) {
    $processo = Get-Process -Id $registro.id -ErrorAction SilentlyContinue
    # Inicio do processo impede encerrar outro programa que reutilizou o mesmo PID.
    if ($processo -and $processo.StartTime.ToUniversalTime().Ticks.ToString() -eq $registro.inicio) {
        Stop-Process -Id $processo.Id -Force
        $processo.WaitForExit()
        Write-Output "Encerrado: $($registro.modulo)"
    }
}
Set-Content -LiteralPath $controle -Value '[]' -Encoding UTF8
Write-Output 'Painel encerrado. Seus dados continuam salvos em data/painel.'
