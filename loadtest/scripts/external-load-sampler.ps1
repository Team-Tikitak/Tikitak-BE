# 호스트 프로세스별 CPU 샘플러 — #217 (external-load-sampler.sh가 띄운다. 직접 부르지 않아도 된다)
#
# IntervalMs마다 성능 카운터 Process 범주를 한 번에 읽고(PerformanceCounterCategory.ReadCategory, 1회 약 30ms),
# 앞 샘플과의 누적 CPU 시간 차이로 프로세스별 CPU를 구한다. 값의 단위는 "논리 CPU 개수"다(1.00 = 논리 CPU 1개를 다 씀).
# Get-Counter는 1회 1~2초가 걸려 쓰지 않는다. 첫 ReadCategory는 수 초 걸린다(카운터 초기화).
#
# 나누는 기준
#   app      : AppPort를 listen하는 프로세스(측정 대상 앱)
#   excluded : 이 샘플러 자신, ExcludeRegex에 맞는 이름(k6, docker CLI, Docker VM 프로세스 vmmem*, com.docker.*)
#   external : 나머지 전부(= 이 측정과 무관한 부하). Docker 컨테이너 CPU는 vmmem*에 들어가므로 여기 없고, 컨테이너별은
#              external-load-sampler.sh가 docker stats로 따로 잰다.
# 샘플 사이에 시작해서 끝난 짧은 프로세스는 보이지 않는다. 샘플 사이에 시작해 아직 살아 있는 프로세스는 시작 후 CPU 전부를 넣는다.
#
# StopFile이 생기거나 ParentPid(Windows PID) 프로세스가 사라지면 끝난다. 끝날 때 Meta에 mem_end 등을 덧붙인다.
param(
	[Parameter(Mandatory = $true)][string]$Out,
	[Parameter(Mandatory = $true)][string]$Meta,
	[Parameter(Mandatory = $true)][string]$StopFile,
	[int]$IntervalMs = 2000,
	[int]$AppPort = 0,
	[int]$ParentPid = 0,
	[string]$ExcludeRegex = '^(k6|docker|vmmem.*|com\.docker\..*)$',
	[int]$Top = 5,
	[int]$MaxHours = 6
)
$ErrorActionPreference = 'Stop'
$inv = [Globalization.CultureInfo]::InvariantCulture
$ncpu = [Environment]::ProcessorCount
$self = $PID

function Get-AppPid {
	if ($AppPort -le 0) { return 0 }
	try {
		$c = Get-NetTCPConnection -State Listen -LocalPort $AppPort -ErrorAction Stop | Select-Object -First 1
		if ($c) { return [int]$c.OwningProcess }
	} catch {}
	return 0
}

function Get-Mem {
	try {
		$os = Get-CimInstance Win32_OperatingSystem
		return ('{0} MB free / {1} MB' -f [int]($os.FreePhysicalMemory / 1024), [int]($os.TotalVisibleMemorySize / 1024))
	} catch {
		return 'unknown'
	}
}

function Add-Line([string]$path, [string]$line) {
	[IO.File]::AppendAllText($path, $line + "`n")
}

$cat = New-Object Diagnostics.PerformanceCounterCategory 'Process'
function Read-Snap {
	$d = $cat.ReadCategory()
	$pt = $d['% Processor Time']
	$ids = $d['ID Process']
	$procs = @{}
	$ts = 0
	foreach ($inst in $pt.Keys) {
		$s = $pt[$inst].Sample
		if ($ts -eq 0) { $ts = $s.TimeStamp100nSec }
		$name = $inst -replace '#\d+$', ''
		# Idle과 _Total은 둘 다 PID 0이고 CPU 합계가 아니다
		if ($name -eq '_Total' -or $name -eq 'Idle') { continue }
		$idData = $ids[$inst]
		if ($null -eq $idData) { continue }
		$procs[[int]$idData.RawValue] = @($name, $s.RawValue, $s.TimeStamp100nSec)
	}
	return @{ procs = $procs; ts = $ts }
}

$app = Get-AppPid
$appChecked = [Diagnostics.Stopwatch]::StartNew()
Add-Line $Meta "ncpu=$ncpu"
Add-Line $Meta "sampler_winpid=$self"
Add-Line $Meta "app_port=$AppPort"
Add-Line $Meta "app_pid_start=$app"
Add-Line $Meta "mem_start=$(Get-Mem)"
$prev = Read-Snap
# 헤더는 첫 ReadCategory(카운터 초기화, 수 초) 뒤에 쓴다. external-load-sampler.sh start가 이 줄을 "준비됨"으로 보고 기다린다.
Add-Line $Out 'epoch_ms,time,interval_ms,total_cpu,external_cpu,app_cpu,excluded_cpu,app_pid,top_external'
$clock = [Diagnostics.Stopwatch]::StartNew()
$next = $IntervalMs
$samples = 0
$deadline = $MaxHours * 3600 * 1000
while ($true) {
	while ($clock.ElapsedMilliseconds -lt $next) {
		if (Test-Path -LiteralPath $StopFile) { break }
		Start-Sleep -Milliseconds ([Math]::Min(200, [Math]::Max(1, $next - $clock.ElapsedMilliseconds)))
	}
	if (Test-Path -LiteralPath $StopFile) { break }
	if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) {
		Add-Line $Meta 'stopped_by=parent_gone'
		break
	}
	if ($clock.ElapsedMilliseconds -gt $deadline) {
		Add-Line $Meta 'stopped_by=max_hours'
		break
	}
	while ($next -le $clock.ElapsedMilliseconds) { $next += $IntervalMs }
	if ($app -eq 0 -and $AppPort -gt 0 -and $appChecked.ElapsedMilliseconds -ge 30000) {
		$app = Get-AppPid
		$appChecked.Restart()
	}

	$cur = Read-Snap
	$span = $cur.ts - $prev.ts
	if ($span -le 0) { $prev = $cur; continue }
	$total = 0.0; $ext = 0.0; $appCpu = 0.0; $excl = 0.0
	$list = New-Object 'System.Collections.Generic.List[object]'
	foreach ($p in $cur.procs.Keys) {
		$c = $cur.procs[$p]
		$o = $prev.procs[$p]
		if ($null -ne $o -and $o[0] -eq $c[0]) {
			$dt = $c[2] - $o[2]
			if ($dt -le 0) { continue }
			$v = ($c[1] - $o[1]) / $dt
		} else {
			# 앞 샘플 뒤에 시작한 프로세스(또는 PID 재사용): 시작 후 CPU 전부
			$v = $c[1] / $span
		}
		if ($v -lt 0) { $v = 0 }
		$total += $v
		if ($app -ne 0 -and $p -eq $app) {
			$appCpu += $v
		} elseif ($p -eq $self -or $c[0] -match $ExcludeRegex) {
			$excl += $v
		} else {
			$ext += $v
			if ($v -ge 0.005) { $list.Add(@($c[0], $p, $v)) }
		}
	}
	$topText = ($list | Sort-Object { $_[2] } -Descending | Select-Object -First $Top | ForEach-Object {
			'{0}({1})={2}' -f ($_[0] -replace '[,;=]', '_'), $_[1], $_[2].ToString('0.00', $inv)
		}) -join ';'
	$now = [DateTimeOffset]::Now
	$line = '{0},{1},{2},{3},{4},{5},{6},{7},{8}' -f $now.ToUnixTimeMilliseconds(), $now.ToString('yyyy-MM-ddTHH:mm:ss', $inv),
		[int]($span / 10000), $total.ToString('0.000', $inv), $ext.ToString('0.000', $inv), $appCpu.ToString('0.000', $inv),
		$excl.ToString('0.000', $inv), $app, $topText
	Add-Line $Out $line
	$samples++
	$prev = $cur
}
Add-Line $Meta "app_pid_end=$app"
Add-Line $Meta "mem_end=$(Get-Mem)"
Add-Line $Meta "host_samples=$samples"
Add-Line $Meta ('sampler_cpu_seconds=' + (Get-Process -Id $self).TotalProcessorTime.TotalSeconds.ToString('0.00', $inv))
Add-Line $Meta ('sampler_wall_seconds=' + ($clock.ElapsedMilliseconds / 1000).ToString('0.0', $inv))
