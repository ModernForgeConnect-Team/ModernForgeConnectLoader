param([string]$JavaHome = 'C:/Users/gengy/.jdks/temurin-17.0.18')
$ErrorActionPreference = 'Stop'
$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputRoot = Join-Path $workspaceRoot 'build/research-round2/probe'
$artifactRoot = Join-Path $workspaceRoot 'build/research-round2/artifacts'
[System.IO.Directory]::CreateDirectory($outputRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($artifactRoot) | Out-Null
$dependencies = @(
    @{Name='forge-1.7.10-10.13.4.1614-1.7.10-universal.jar';Url='https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar'},
    @{Name='launchwrapper-1.12.jar';Url='https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar'},
    @{Name='guava-17.0.jar';Url='https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar'},
    @{Name='asm-debug-all-5.0.3.jar';Url='https://maven.minecraftforge.net/org/ow2/asm/asm-debug-all/5.0.3/asm-debug-all-5.0.3.jar'}
)
$evidence = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'evidence.json') -Raw | ConvertFrom-Json
foreach ($dependency in $dependencies) {
    $target = Join-Path $artifactRoot $dependency.Name
    if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest $dependency.Url -OutFile $target -TimeoutSec 60 }
    $expected = @($evidence.artifacts | Where-Object { $_.name -eq $dependency.Name })
    if ($expected.Count -ne 1 -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected[0].sha256) {
        throw "Input differs from the recorded research artifact: $($dependency.Name)"
    }
}
$classpath = (@($outputRoot) + @($dependencies | ForEach-Object { Join-Path $artifactRoot $_.Name })) -join [System.IO.Path]::PathSeparator
& (Join-Path $JavaHome 'bin/javac.exe') --release 8 -d $outputRoot (Join-Path $PSScriptRoot 'LegacyFixture.java')
if ($LASTEXITCODE -ne 0) { throw 'Legacy fixture compilation failed' }
& (Join-Path $JavaHome 'bin/javac.exe') --release 17 -cp $classpath -d $outputRoot (Join-Path $PSScriptRoot 'FmlJvmProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
$probeOutput = & (Join-Path $JavaHome 'bin/java.exe') -cp $classpath FmlJvmProbe (Join-Path $outputRoot 'LegacyFixture.class') (Join-Path $outputRoot 'FmlJvmProbe.class') 2>&1
$probeExit = $LASTEXITCODE
$probeOutput | Set-Content -LiteralPath (Join-Path $outputRoot 'result.txt') -Encoding utf8
$probeOutput
if ($probeExit -ne 0) { throw "Probe failed: $probeExit" }
