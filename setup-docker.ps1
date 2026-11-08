# Setup & Start Docker Containers for TPC-C Multi-DBMS Benchmark
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   KHOI DONG VA THIET LAP CUM DOCKER 4 HE CO SO DU LIEU  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Write-Host "`n>>> [1/3] Khoi dong cac containers DBMS qua Docker Compose..." -ForegroundColor Yellow
docker compose up -d mysql-baseline tidb0 tikv0 pd0 mongodb redis

Write-Host "`n>>> [2/3] Cho database khoi dong hoan tat (10 giay)..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host "`n>>> [3/3] Khoi tao MongoDB ReplicaSet (rs0) va TiDB tpcc database..." -ForegroundColor Yellow
docker exec tpcc-mongodb mongosh --eval "try { rs.initiate(); print('ReplicaSet rs0 initiated successfully.'); } catch(e) { print('ReplicaSet already active.'); }"
docker exec tpcc-mysql mysql -h tpcc-tidb -P 4000 -u root -e "CREATE DATABASE IF NOT EXISTS tpcc;"

Write-Host "`n>>> Build Docker image ung dung Benchmark..." -ForegroundColor Yellow
docker compose build app

Write-Host "`n>>> Kiem tra thong luong ket noi den ca 4 DBMS..." -ForegroundColor Yellow
docker compose run --rm app check-conn

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "   HE THONG DA SAN SANG! BAN CO THE CHAY BENCHMARK.      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

