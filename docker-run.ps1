# Chay cac lenh Benchmark ben trong Docker container
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ArgsList
)

if ($ArgsList.Count -eq 0) {
    $ArgsList = @("smoke-test")
}

docker compose run --rm app $ArgsList

