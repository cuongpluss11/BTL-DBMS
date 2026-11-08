@echo off
echo ==========================================================
echo    KHOI DONG VA THIET LAP CUM DOCKER 4 HE CO SO DU LIEU  
echo ==========================================================

echo.
echo >>> [1/3] Khoi dong cac containers DBMS qua Docker Compose...
docker compose up -d mysql-baseline tidb0 tikv0 pd0 mongodb redis

echo.
echo >>> [2/3] Cho database khoi dong hoan tat (10 giay)...
timeout /t 10 /nobreak >nul

echo.
echo >>> [3/3] Khoi tao MongoDB ReplicaSet (rs0) va TiDB tpcc database...
docker exec tpcc-mongodb mongosh --eval "try { rs.initiate(); print('ReplicaSet rs0 initiated successfully.'); } catch(e) { print('ReplicaSet already active.'); }"
docker exec tpcc-mysql mysql -h tpcc-tidb -P 4000 -u root -e "CREATE DATABASE IF NOT EXISTS tpcc;"

echo.
echo >>> Build Docker image ung dung Benchmark...
docker compose build app

echo.
echo >>> Kiem tra thong luong ket noi den ca 4 DBMS...
docker compose run --rm app check-conn

echo.
echo ==========================================================
echo    HE THONG DA SAN SANG! BAN CO THE CHAY BENCHMARK.      
echo ==========================================================

