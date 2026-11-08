# BTL HỆ QUẢN TRỊ CƠ SỞ DỮ LIỆU: ĐÁNH GIÁ DỊ THƯỜNG ĐỒNG THỜI, TRANH CHẤP KHÓA VÀ MỞ RỘNG QUY MÔ (TPC-C BENCHMARK SUITE)

**Đề tài:** Evaluating Concurrency Anomalies, Lock Contention, and Distributed Scaling Trade-offs Across SQL (MySQL InnoDB), NoSQL (MongoDB), and NewSQL (TiDB) Under High-Contention Workloads.

Hệ thống được thiết kế và triển khai hoàn toàn bằng **Java 17**, chạy trên nền tảng Docker với đầy đủ cụm 4 hệ quản trị cơ sở dữ liệu: **MySQL 8.0 InnoDB**, **TiDB 7.5 NewSQL (với TiKV + PD)**, **MongoDB 7.0 NoSQL (WiredTiger, ReplicaSet rs0)** và **Redis 7 In-Memory**.

---

## 1. KIẾN TRÚC HẠ TẦNG VÀ CÁC CỔNG KẾT NỐI (DOCKER)

Tất cả các DBMS được quản lý qua `docker-compose.yml` trong cùng mạng Docker `btl-dbms_tpcc-net`:

| Hệ quản trị / Thành phần | Image | Container Name | Cổng Host (Exposed) | Cổng Container | Ghi chú cấu hình |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MySQL 8.0 (Baseline)** | `mysql:8.0` | `tpcc-mysql` | **`3307`** | `3306` | InnoDB Buffer Pool 512M, Repeatable Read, Lock Wait Timeout 5s |
| **TiDB 7.5 (SQL Layer)** | `pingcap/tidb:v7.5.1` | `tpcc-tidb` | **`4000`**, `10080` | `4000`, `10080` | Distributed NewSQL engine, kết nối tầng lưu trữ TiKV và PD |
| **TiDB PD (Placement Driver)**| `pingcap/pd:v7.5.1` | `tpcc-pd0` | **`2379`** | `2379` | Timestamp Oracle (TSO) & Meta Controller |
| **TiDB TiKV (Storage Engine)**| `pingcap/tikv:v7.5.1` | `tpcc-tikv0` | *(Internal)* | `20160` | LSM-Tree RocksDB SSTables & Raft consensus group |
| **MongoDB 7.0 (NoSQL)** | `mongo:7.0` | `tpcc-mongodb` | **`27018`** | `27017` | WiredTiger Cache 0.5GB, ReplicaSet `rs0` (hỗ trợ Multi-Doc ACID Tx) |
| **Redis 7 (In-Memory Cache)**| `redis:7-alpine` | `tpcc-redis` | **`6379`** | `6379` | Flash-sale stock pre-decrement & buffer |

### Chuỗi kết nối JDBC / Driver mặc định:
* **MySQL:** `jdbc:mysql://localhost:3307/tpcc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` (User: `root`, Pass: `rootpassword`)
* **TiDB:** `jdbc:mysql://localhost:4000/tpcc?useSSL=false&serverTimezone=UTC` (User: `root`, Pass: *(trống)*)
* **MongoDB:** `mongodb://localhost:27018/?replicaSet=rs0&directConnection=true` (Database: `tpcc`)
* **Redis:** `redis://localhost:6379`

### Kết nối qua các công cụ GUI (Giao diện đồ họa):
* **MySQL Workbench (MySQL Baseline):** Host `127.0.0.1`, Port `3307`, User `root`, Pass `rootpassword`, Schema `tpcc`.
* **MySQL Workbench (TiDB NewSQL):** Host `127.0.0.1`, Port `4000`, User `root`, Pass *(để trống)*, Schema `tpcc` *(Bấm "Continue Anyway" nếu hiện cảnh báo version)*.
* **MongoDB Compass (MongoDB NoSQL):** Dán URI `mongodb://localhost:27018/?replicaSet=rs0&directConnection=true` -> Bấm **Connect**.
* **Redis Insight (Redis Cache):** Host `127.0.0.1`, Port `6379`, Pass *(trống)* -> Bấm **Add Database**.

---

## 2. CẤU TRÚC DỰ ÁN MÃ NGUỒN JAVA

```
E:\BTL-DBMS
│   docker-compose.yml       # Cấu hình Docker cho 4 DBMS (MySQL, TiDB, TiKV, PD, MongoDB, Redis)
│   pom.xml                  # Cấu hình Maven (Java 17, HikariCP, MySQL/Mongo/Redis drivers, HdrHistogram)
│   mvnw.ps1 / mvnw.bat      # Script Maven Wrapper cục bộ
│   run.ps1 / run.bat        # Script thực thi Java CLI
│   README.md                # Tài liệu hướng dẫn chi tiết
│
└───src\main
    ├───java\com\btl\dbms
    │   │   Main.java                   # CLI Entry-point điều khiển các chức năng
    │   │
    │   ├───config
    │   │       DbConfig.java           # Quản lý cấu hình kết nối, credentials, pool sizes
    │   │
    │   ├───connection
    │   │       ConnectionManager.java  # Connection pooling (HikariCP, MongoClient, JedisPool)
    │   │
    │   ├───dataset
    │   │       TpccDataGenerator.java  # Sinh dữ liệu chuẩn TPC-C (Họ tên, địa chỉ, số liệu)
    │   │       DataLoader.java         # Nạp dữ liệu vào MySQL, TiDB, MongoDB & sync Redis
    │   │
    │   ├───schema
    │   │       SchemaManager.java      # Khởi tạo DDL bảng & Indexes cho 3 hệ quản trị
    │   │
    │   ├───benchmark
    │   │       TransactionType.java    # 5 loại giao dịch TPC-C (NewOrder, Payment, OrderStatus, Delivery, StockLevel)
    │   │       ZipfianGenerator.java   # Bộ sinh độ lệch Zipfian (theta 0.0 -> 0.8+ cho Flash-Sale)
    │   │       TpccExecutor.java       # Interface giao tiếp chuẩn của TPC-C Transaction Executor
    │   │       MySqlTpccExecutor.java  # Cài đặt giao dịch trên MySQL InnoDB (ACID, row locks)
    │   │       TidbTpccExecutor.java   # Cài đặt giao dịch trên TiDB (NewSQL, distributed 2PC)
    │   │       MongoTpccExecutor.java  # Cài đặt giao dịch trên MongoDB (Multi-Doc ACID Session)
    │   │       BenchmarkResult.java    # Tổng hợp chỉ số hiệu năng (TPS, percentiles p50..p99.9)
    │   │       BenchmarkRunner.java    # Khung sinh tải đa luồng đo đạc HdrHistogram
    │   │
    │   └───verification
    │           InvariantAuditor.java   # Engine kiểm chứng 3 phương trình bất biến TPC-C thời gian thực
    │
    └───resources
        │   application.properties      # File tham số kết nối mặc định
        │   logback.xml                 # Cấu hình logging
        └───schema
                schema-mysql.sql        # 9 bảng chuẩn TPC-C cho MySQL InnoDB
                schema-tidb.sql         # 9 bảng tối ưu hóa cho TiDB phân tán
```

---

## 3. BỘ DỮ LIỆU CHUẨN 9 BẢNG TPC-C

Hệ thống triển khai đầy đủ **9 bảng quan hệ của TPC-C** (và ánh xạ tương ứng sang MongoDB Collections):
1. `warehouse`: Khóa phân vùng gốc `(w_id)`.
2. `district`: Điểm nóng cấp phát mã đơn hàng tăng đơn điệu `(d_next_o_id)`.
3. `customer`: Khách hàng, khóa chính `(c_w_id, c_d_id, c_id)` và chỉ mục phụ `(c_last)`.
4. `history`: Nhật ký thanh toán nối tiếp (Append-only).
5. `orders`: Bảng đơn hàng chung.
6. `new_order`: Hàng đợi đơn hàng chờ xử lý giao nhận.
7. `order_line`: Chi tiết mặt hàng trên đơn hàng (bảng lớn nhất).
8. `item`: Danh mục 100.000 sản phẩm tĩnh.
9. `stock`: Tồn kho từng kho hàng `(s_w_id, s_i_id)` — **Tâm điểm Flash-Sale** tranh chấp trừ `s_quantity`.

---

## 4. HƯỚNG DẪN CHẠY DỰ ÁN BẰNG DOCKER (KHUYẾN NGHỊ - KHÔNG CẦN CÀI JAVA)

Dự án đã được cấu hình hóa Docker 100%. Bạn có thể chạy toàn bộ hệ thống (gồm 4 DBMS và ứng dụng Benchmark Java) hoàn toàn bằng Docker mà không cần cài đặt môi trường Java trên máy cá nhân.

### 4.1. Khởi động cụm DBMS & Khởi tạo (1 Bước duy nhất)
Sử dụng script tự động:
* **PowerShell:**
  ```powershell
  .\setup-docker.ps1
  ```
* **Command Prompt (CMD):**
  ```cmd
  setup-docker.bat
  ```
* **Hoặc bằng lệnh Docker Compose thủ công:**
  ```bash
  # 1. Khởi động 6 containers DBMS ngầm
  docker compose up -d mysql-baseline tidb0 tikv0 pd0 mongodb redis

  # 2. Chờ 10 giây cho các DB sẵn sàng, sau đó khởi tạo ReplicaSet rs0 và TiDB DB:
  docker exec tpcc-mongodb mongosh --eval "rs.initiate()"
  docker exec tpcc-mysql mysql -h tpcc-tidb -P 4000 -u root -e "CREATE DATABASE IF NOT EXISTS tpcc;"

  # 3. Build Docker image cho ứng dụng benchmark
  docker compose build app
  ```

### 4.2. Kiểm tra kết nối tới 4 DBMS qua Docker
```powershell
.\docker-run.ps1 check-conn
# Hoặc bằng lệnh Docker:
docker compose run --rm app check-conn
```

### 4.3. Chạy Kiểm Thử Thông Luồng TPC-C W=1 (Smoke Test)
Lệnh này tự động thực hiện cả 6 bước: Test kết nối -> Tạo Schema -> Nạp Dataset W=1 -> Chạy 5 giao dịch TPC-C -> Benchmark 4 threads -> Kiểm tra 3 Invariants:
```powershell
.\docker-run.ps1 smoke-test
# Hoặc:
docker compose run --rm app smoke-test
```

### 4.4. Nạp Dữ Liệu TPC-C Tùy Chỉnh Bằng Docker
```powershell
# Nạp W=1 (100.000 items, 3.000 khách hàng) cho tất cả các DBMS:
.\docker-run.ps1 load-data -w 1 --items 100000 --customers 3000 --target all

# Chỉ nạp cho MySQL:
.\docker-run.ps1 load-data -w 1 --items 2000 --customers 300 --target mysql
```

### 4.5. Chạy Benchmark Đo Đạc Hiệu Năng Bằng Docker
```powershell
# Benchmark MySQL InnoDB: 10 threads, chạy trong 30s
.\docker-run.ps1 benchmark --target mysql --threads 10 --duration 30

# Benchmark TiDB NewSQL: 10 threads, chạy trong 30s
.\docker-run.ps1 benchmark --target tidb --threads 10 --duration 30

# Benchmark MongoDB NoSQL: 10 threads, chạy trong 30s
.\docker-run.ps1 benchmark --target mongo --threads 10 --duration 30

# Mô phỏng tranh chấp Flash-Sale (Zipfian Skew theta = 0.85):
.\docker-run.ps1 benchmark --target mysql --threads 16 --duration 30 --theta 0.85
```

### 4.6. Kiểm Chứng Bất Biến Nghiệp Vụ (Invariant Auditing) Bằng Docker
```powershell
.\docker-run.ps1 verify
# Hoặc:
docker compose run --rm app verify
```

### 4.7. Dừng và Dọn Dẹp Cụm Docker
```powershell
# Dừng các containers (dữ liệu vẫn giữ lại)
docker compose stop

# Dừng và xóa containers
docker compose down
```

---

## 5. HƯỚNG DẪN CHẠY KẾT HỢP (DBMS TRÊN DOCKER + APP TRÊN MÁY HOST)

Nếu máy bạn đã có sẵn Java 17+ và muốn phát triển, debug mã nguồn Java trực tiếp:
```powershell
# 1. Khởi động 4 DBMS trên Docker
docker compose up -d mysql-baseline tidb0 tikv0 pd0 mongodb redis

# 2. Chạy ứng dụng Java từ máy Host kết nối qua cổng Exposed (3307, 4000, 27018, 6379):
.\run.ps1 check-conn
.\run.ps1 smoke-test
.\run.ps1 benchmark --target mysql --threads 8 --duration 15
```

---

## 6. BỘ DỮ LIỆU CHUẨN 9 BẢNG TPC-C

### 4.2. Kiểm tra thông luồng kết nối 4 DBMS (Check Connectivity)
```powershell
.\run.ps1 check-conn
# Hoặc CMD:
run.bat check-conn
```

### 4.3. Chạy Kiểm thử Thông Luồng Toàn Diện (Smoke Test TPC-C W=1)
Lệnh này tự động thực thi tuần tự 6 bước:
1. Kiểm tra kết nối tới 4 engine (MySQL, TiDB, MongoDB, Redis).
2. Tự động khởi tạo cấu trúc bảng/collections (DDL Schemas).
3. Nạp bộ dữ liệu TPC-C hạt giống quy mô $W=1$ (2.000 items, 300 customers/dist, 10 districts, 2.000 stocks).
4. Chạy tuần tự và kiểm tra kết quả cả 5 loại giao dịch TPC-C (`NewOrder`, `Payment`, `OrderStatus`, `Delivery`, `StockLevel`) trên từng hệ quản trị.
5. Chạy kiểm thử tải đa luồng đồng thời (4 threads) đo đạc TPS và độ trễ phân vị p50, p90, p95, p99.
6. Kích hoạt Engine kiểm chứng 3 đẳng thức bất biến nghiệp vụ TPC-C (Invariant 1, 2, 3).

```powershell
.\run.ps1 smoke-test
```

### 4.4. Nạp Dữ liệu với Quy mô Tùy chỉnh (Scale W=1..10)
```powershell
# Nạp W=1 với 100.000 items và 3.000 customers/dist
.\run.ps1 load-data -w 1 --items 100000 --customers 3000 --target all

# Hoặc chỉ nạp cho MySQL:
.\run.ps1 load-data -w 1 --items 10000 --customers 1000 --target mysql
```

### 4.5. Chạy Benchmark Tùy Biến (Tải Tranh Chấp & Flash-Sale Skew)
* **Chạy kiểm thử tải trên MySQL InnoDB:**
  ```powershell
  # 8 threads, thời gian 15 giây, độ lệch phân phối đều (theta = 0.0)
  .\run.ps1 benchmark --target mysql --threads 8 --duration 15 --theta 0.0
  ```
* **Chạy kiểm thử điểm nóng Flash-Sale (Zipfian Skew $\theta = 0.85$):**
  ```powershell
  .\run.ps1 benchmark --target mysql --threads 16 --duration 20 --theta 0.85
  ```
* **Chạy kiểm thử trên TiDB (NewSQL):**
  ```powershell
  .\run.ps1 benchmark --target tidb --threads 8 --duration 15 --theta 0.0
  ```
* **Chạy kiểm thử trên MongoDB (NoSQL):**
  ```powershell
  .\run.ps1 benchmark --target mongo --threads 8 --duration 15 --theta 0.0
  ```

### 4.6. Kiểm chứng Bất biến Nghiệp vụ (Invariant Audit)
```powershell
.\run.ps1 verify
```
Engine sẽ kiểm chứng trực tiếp trên cơ sở dữ liệu:
* **Bất biến 1:** $\sum_{d=1}^{10} \text{district.d\_ytd} \equiv \text{warehouse.w\_ytd}$
* **Bất biến 2:** $\text{district.d\_next\_o\_id} - 1 \equiv \max(\text{orders.o\_id})$
* **Bất biến 3:** Ràng buộc toàn vẹn giữa `orders` và `order_line`.

