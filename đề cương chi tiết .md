# ĐỀ CƯƠNG BÀI TẬP LỚN HỌC PHẦN CƠ SỞ DỮ LIỆU

**Đề tài:** Evaluating Concurrency Anomalies, Lock Contention, and Distributed Scaling Trade-offs Across SQL (MySQL InnoDB), NoSQL (MongoDB), and NewSQL (TiDB) Under High-Contention Workloads  
*(Đánh giá dị thường đồng thời, tranh chấp khóa và sự đánh đổi khi mở rộng quy mô giữa MySQL InnoDB, MongoDB và TiDB dưới tải tranh chấp cao)*

**Cấu trúc báo cáo bài tập lớn:**
* **Phần 1:** Giới thiệu kiến trúc Hệ quản trị Cơ sở dữ liệu hiện đại ngoài SQL truyền thống (MongoDB & TiDB)
* **Phần 2:** Đánh giá thực nghiệm và Phân tích đánh đổi giữa SQL (MySQL InnoDB), NoSQL (MongoDB) và NewSQL (TiDB)

---

## PHẦN 1: GIỚI THIỆU KIẾN TRÚC HỆ QUẢN TRỊ CƠ SỞ DỮ LIỆU HIỆN ĐẠI NGOÀI SQL TRUYỀN THỐNG (MONGODB & TIDB)

### 1.1. Tổng quan sự ra đời và Mô hình dữ liệu của Hệ quản trị mới (Data Abstraction, Schema & Languages)

* **Mục đích và bài toán đặt ra (Purpose of Modern DBMS):** Phân tích lý do xuất hiện các họ DBMS hiện đại bên cạnh hệ quản trị quan hệ truyền thống nhằm giải quyết bài toán lưu trữ tài liệu (Document Storage Management), thay đổi cấu trúc linh hoạt (changing product-catalog workload) cũng như nhu cầu phân vùng (partitioning) và nhân bản (replication) dữ liệu quy mô lớn.
* **Ba mức trừu tượng hóa dữ liệu (Data Abstraction):** Đối chiếu cách tổ chức **Physical level** (cấu trúc lưu trữ vật lý mức thấp), **Logical level** (cấu trúc dữ liệu và mối quan hệ do người thiết kế/quản trị định nghĩa) và **View level** (cách hiển thị một phần dữ liệu và kiểm soát truy cập cho ứng dụng) trên hệ thống lưu trữ tài liệu của MongoDB và hệ thống phân tán của TiDB.
* **Lược đồ (Schema), Thể hiện (Instance) và Tiến hóa lược đồ không thời gian chết (Zero-Downtime Schema Evolution):**
  * So sánh cách định nghĩa thiết kế logic (Schema) và sự thay đổi nội dung dữ liệu tại từng thời điểm (Instance) khi dữ liệu được chèn, cập nhật hoặc xóa giữa mô hình lưu trữ tài liệu (Document) của MongoDB và mô hình bảng quan hệ của TiDB.
  * **Giải quyết bài toán xung đột phiên bản (Version Mismatch) khi di trú lược đồ:**
    * *Trên SQL truyền thống (MySQL InnoDB):* Do áp dụng lược đồ cố định (Schema-on-write), việc thực thi các thay đổi phá vỡ cấu trúc (Breaking DDL như đổi tên cột, sửa kiểu dữ liệu) trên bảng lớn dễ gây khóa siêu dữ liệu độc quyền (exclusive metadata lock) hoặc gây lỗi xung đột phiên bản khi ứng dụng phiên bản cũ (V1) và mới (V2) chạy song song trong quá trình Rolling Update. Để tránh downtime, hệ thống buộc phải tách rời triển khai qua 5 bước ở tầng ứng dụng (Expand Nullable $\rightarrow$ Dual-write $\rightarrow$ Chunked Backfill $\rightarrow$ Read Switch $\rightarrow$ Cleanup) hoặc dùng công cụ tạo bảng tạm gắn Trigger như `pt-online-schema-change`.
    * *Trên NoSQL (MongoDB):* Nhờ đặc tính Dynamic/Flexible Schema, ứng dụng V2 có thể ghi ngay thuộc tính mới vào tài liệu BSON mà không cần thực thi lệnh `ALTER TABLE` khóa bảng, trong khi ứng dụng V1 vẫn thao tác bình thường trên các trường cũ, triệt tiêu hoàn toàn xung đột phiên bản ở tầng lưu trữ.
    * *Trên NewSQL (TiDB):* Tích hợp sẵn quy trình chuyển đổi lược đồ bất đồng bộ không chặn (Online Asynchronous Schema Change dựa trên thuật toán của Google F1) ngay trong Database Engine. Mọi lệnh DDL (`ALTER TABLE`, `CREATE INDEX`) được tự động chia thành các trạng thái trung gian (`absent` $\rightarrow$ `delete-only` $\rightarrow$ `write-only` $\rightarrow$ `write-reorganization/backfill` $\rightarrow$ `public`) dưới tầng TiKV, giúp hệ thống tự động thực hiện Dual-write và Backfill ngầm mà không khóa luồng giao dịch hay cần can thiệp từ tầng ứng dụng.
* **Ngôn ngữ giao tiếp và Kiến trúc ứng dụng (Languages, API & Application Architecture):** Phân tích cách người dùng và lập trình viên tương tác với hệ thống thông qua cả giao diện truy vấn SQL lẫn API Requests / NoSQL Interface CRUD Operations (tương ứng các nhóm chức năng định nghĩa dữ liệu DDL, thao tác dữ liệu DML, kiểm soát giao dịch TCL và kiểm soát quyền DCL) trong kiến trúc ứng dụng 2 tầng (Two-tier) và 3 tầng (Three-tier).

### 1.2. Giải phẫu Database Engine của MongoDB và TiDB theo 3 vùng trách nhiệm cốt lõi

* **Bộ xử lý truy vấn (Query Processor):**
  * Trình bày cơ chế tiếp nhận yêu cầu truy vấn (SQL / API Requests), kiểm tra cú pháp và đối chiếu thông tin trong từ điển dữ liệu (Catalog / Data dictionary) để xác thực sự tồn tại của đối tượng, kiểu dữ liệu và quyền hạn (Parse and validate).
  * Phân tích cách bộ biên dịch và tối ưu hóa (Compiler / Optimizer) sử dụng dữ liệu thống kê (statistical data) và thông tin chỉ mục để ước lượng chi phí (Cost-based optimization), chuyển đổi từ kế hoạch logic (Logical plan) sang kế hoạch vật lý (Physical plan như quét toàn bộ hay tra cứu chỉ mục) trước khi chuyển cho Query evaluation engine thực thi.
* **Bộ quản lý lưu trữ (Storage Manager):**
  * Phân tích cách tổ chức dữ liệu vật lý, chỉ mục (indexes), từ điển dữ liệu (catalog) và dữ liệu thống kê trên đĩa thông qua **File manager** (tệp WiredTiger của MongoDB và các tệp SSTable trong LSM-Tree RocksDB của TiKV).
  * Phân tích cơ chế hoạt động của **Buffer manager** (Caches & Buffers) trong việc di chuyển và lưu đệm các trang dữ liệu (pages/blocks) giữa đĩa và bộ nhớ chính (RAM) nhằm tối ưu hiệu năng truy cập.
* **Bộ quản lý giao dịch, Phục hồi và Kiểm soát truy cập (Transaction, Recovery & Authorization Manager):**
  * **Transaction Manager:** Cách MongoDB và TiDB gom nhóm nhiều thao tác đọc/ghi thành một đơn vị thực thi để đảm bảo tính nguyên tố (atomicity) và tính cô lập (isolation) khi có nhiều phiên truy cập đồng thời.
  * **Recovery Manager:** Cơ chế ghi nhật ký (Logs: Redo, Undo, Binary/Write logs, WiredTiger Journal, Raft WAL) để xử lý sự cố (application error, transaction abort, system crash, storage failure), quyết định hoàn tác (undo) các tác động dở dang hoặc làm lại (redo) các giao dịch đã cam kết nhằm giữ tính bền vững (durability).
  * **Authorization & Integrity Manager:** Cơ chế kiểm tra quyền của người dùng (privileges/roles) và các ràng buộc toàn vẹn dữ liệu (integrity constraints) trước khi chấp nhận thao tác cập nhật hoặc trả kết quả.

### 1.3. Cơ chế Quản trị, Mở rộng quy mô (Scale) và Kiểm tra hệ thống (System Inspection)

* **Phân vùng (Partitioning) và Nhân bản (Replication):** Giới thiệu cách các hệ quản trị mới tích hợp cơ chế phân chia dữ liệu và sao chép trên cụm máy chủ (distributed/cluster engine) để mở rộng quy mô so với kiến trúc đơn lẻ truyền thống.
* **Ánh xạ khái niệm lý thuyết sang câu lệnh kiểm tra thực tế (Mapping Concepts to System Inspection):** Lập bảng đối chiếu giữa các khái niệm lý thuyết (Database engine, Data dictionary/catalog, Storage manager, Query processor, Transaction manager, Authorization manager, Recovery/logging) với các công cụ và câu lệnh kiểm tra metadata, trạng thái phiên hoạt động (active sessions/processes), dung lượng lưu trữ và kế hoạch thực thi trên MySQL, MongoDB và TiDB.

---

## PHẦN 2: ĐÁNH GIÁ THỰC NGHIỆM VÀ PHÂN TÍCH ĐÁNH ĐỔI GIỮA SQL (MYSQL INNODB), NOSQL (MONGODB) VÀ NEWSQL (TIDB)

### 2.1. Thiết lập Bài toán, Dữ liệu (Data), Tải công việc (Workload) và Hệ thống cơ sở (Baseline)

#### 2.1.1. Xác định bài toán và Kịch bản tải công việc thực tế (Problem & Workload Definition)
* **Bài toán nghiệp vụ:** Mô phỏng hệ thống xử lý đơn hàng, thanh toán và quản lý tồn kho thương mại điện tử đa kho vận dựa trên chuẩn công nghiệp **TPC-C** kết hợp kịch bản bán hàng giờ cao điểm (**Flash-Sale / High-Contention OLTP**). Cách tiếp cận này tuân thủ nguyên tắc dự án bắt đầu từ bài toán và tải công việc thực tế thay vì chỉ liệt kê tên sản phẩm.
* **Cơ cấu tải công việc (Workload Mix & Contention Skew):**
  * *Tải giao dịch chuẩn (Standard TPC-C Mix):* Bao gồm 5 loại giao dịch OLTP đặc trưng chạy đồng thời, trong đó trọng tâm là **NewOrder** (chiếm $\sim 45\%$ tải, thực hiện tạo đơn hàng mới và cập nhật trừ tồn kho từ 5–15 mặt hàng; khoảng $1\%$ mặt hàng lấy từ kho ở xa tạo ra $\sim 10\%$ giao dịch đa phân vùng/multi-partition transactions) và **Payment** (chiếm $\sim 43\%$ tải, cập nhật số dư khách hàng, doanh thu kho và khu vực, với $15\%$ xác suất khách hàng thuộc kho từ xa), cùng 3 giao dịch bổ trợ là **OrderStatus** ($4\%$), **Delivery** ($4\%$) và **StockLevel** ($4\%$).
  * *Tải điểm nóng tranh chấp cao (Zipfian Skew / Flash-Sale Workload):* Kết hợp hàm phân phối độ lệch **Zipfian** (tương tự YCSB với hệ số lệch $\theta$ điều chỉnh từ $0.0$ – phân bổ đều đến $\theta \ge 0.8$ – độ lệch cực đại) để dồn luồng giao dịch `NewOrder` vào cập nhật một nhóm nhỏ mặt hàng Flash-sale trong bảng tồn kho (`stock`) và cấp phát mã đơn hàng tuần tự (`d_next_o_id`), tạo điểm nghẽn tranh chấp khóa và phân vùng nóng (hot ranges).

#### 2.1.2. Thiết kế Bộ dữ liệu chuẩn 9 bảng TPC-C (Data Schema & Sizing)
Triển khai đầy đủ **9 bảng quan hệ của bộ dữ liệu chuẩn TPC-C** với quy mô khởi tạo cơ sở từ $W = 10$ kho hàng (`warehouses`), bao gồm:
1. **Nhóm bảng danh mục và kho vận:** `item` ($100.000$ mặt hàng tĩnh dùng chung), `warehouse` ($W$ kho hàng, khóa phân vùng gốc `w_id`), và `stock` ($W \times 100.000$ bản ghi tồn kho, chứa thuộc tính `s_quantity` là tâm điểm tranh chấp ghi).
2. **Nhóm bảng khu vực và khách hàng:** `district` ($W \times 10$ khu vực, chứa cột `d_next_o_id` là điểm nóng khóa tăng đơn điệu khi tạo đơn), `customer` ($W \times 30.000$ khách hàng), và `history` (lịch sử giao dịch thanh toán).
3. **Nhóm bảng xử lý đơn hàng (biến động liên tục):** `orders` (thông tin chung đơn hàng), `new_order` (hàng đợi đơn hàng mới chờ giao), và `order_line` (chi tiết 5–15 dòng mặt hàng trên mỗi đơn hàng).

| STT | Tên bảng (TPC-C) | Số lượng bản ghi ($W=10$ kho) | Khóa chính (Primary Key) | Vai trò trong kịch bản thực nghiệm (Mục 2.2 $\rightarrow$ 2.6) |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `warehouse` | $10$ | `(w_id)` | Khóa phân vùng gốc; điểm tranh chấp khi cập nhật doanh thu `w_ytd` trong giao dịch `Payment`. |
| 2 | `district` | $100$ | `(d_w_id, d_id)` | Điểm nóng cấp phát mã đơn hàng tuần tự (`d_next_o_id`) gây tranh chấp khóa mức dòng. |
| 3 | `customer` | $300.000$ | `(c_w_id, c_d_id, c_id)` | Kiểm thử chỉ mục phụ (Secondary Index tìm kiếm theo `c_last`) và cập nhật số dư `c_balance`. |
| 4 | `history` | $\ge 300.000$ | *(Append-only)* | Kiểm thử tải ghi nối thêm (Insert-heavy log) và chi phí duy trì phân vùng. |
| 5 | `orders` | $\ge 300.000$ | `(o_w_id, o_d_id, o_id)` | Kiểm thử khóa chính tăng đơn điệu (Monotonic Primary Key) gây nghẽn Hot Range trên TiDB. |
| 6 | `new_order` | $\sim 90.000$ | `(no_w_id, no_d_id, no_o_id)` | Hàng đợi đơn hàng mới; kiểm thử thao tác `INSERT`/`DELETE` liên tục và khóa `SKIP LOCKED`. |
| 7 | `order_line` | $\sim 3.000.000$ | `(ol_w_id, ol_d_id, ol_o_id, ol_number)` | Bảng lớn nhất; dùng đo dung lượng lưu trữ vật lý (L3), kế hoạch `JOIN` (L5) và nhúng BSON trên MongoDB. |
| 8 | `item` | $100.000$ | `(i_id)` | Bảng danh mục chỉ đọc; dùng đối sánh `InnoDB Buffer Pool` vs. `MySQL MEMORY Engine` vs. `Redis` (Mục 2.2). |
| 9 | `stock` | $1.000.000$ | `(s_w_id, s_i_id)` | **Tâm điểm Flash-Sale (Mục 2.3 & 2.4):** Tranh chấp trừ tồn kho `s_quantity` và kích hoạt giao dịch 2PC liên kho. |

#### 2.1.3. Thiết lập Hệ thống cơ sở (Baseline trên MySQL 8.x InnoDB) và Ánh xạ đa mô hình
* **Hệ thống cơ sở (Baseline – SQL RDBMS):** Sử dụng **MySQL 8.4** với storage engine mặc định **InnoDB** (triển khai cấu trúc B+Tree, quản lý bộ đệm InnoDB Buffer Pool, hỗ trợ giao dịch ACID, khóa mức dòng `row-level locking`, MVCC và ràng buộc khóa ngoại `FOREIGN KEY` giữa 9 bảng TPC-C) làm mốc tham chiếu cơ sở để đo đạc các chỉ số ban đầu về dung lượng, kế hoạch truy vấn, thông lượng (TPS) và độ trễ (Latency).
* **Ánh xạ sang NoSQL (MongoDB 7.0 – Document Store):** Chuyển đổi 9 bảng TPC-C sang hệ thống lưu trữ tài liệu BSON trên cụm **MongoDB Replica Set / Sharded Cluster** (engine WiredTiger) theo hai kịch bản đối chứng: (1) *Mô hình 9 Collections tách rời* sử dụng giao dịch đa tài liệu (Multi-Document ACID Transactions) để đối sánh trực tiếp với SQL; và (2) *Mô hình Nhóm thực thể nhúng (Embedded Entity-Group)* nhúng mảng `order_line` vào trong tài liệu `orders` để đánh giá ưu thế nguyên tử đơn tài liệu (Single-Document Atomicity) và linh hoạt lược đồ của NoSQL.
* **Ánh xạ sang NewSQL (TiDB – Distributed Relational DBMS):** Triển khai nguyên bản lược đồ 9 bảng quan hệ TPC-C lên cụm **TiDB** (gồm tầng tính toán SQL `TiDB Server`, bộ cấp nhãn thời gian tập trung `Placement Driver - PD`, và tầng lưu trữ phân tán `TiKV` dựa trên LSM-tree RocksDB và đồng thuận `Raft`) để đánh giá khả năng mở rộng ngang tự động (auto-sharding theo dải `Region`), giao thức cam kết hai pha `Percolator 2PC` và hai chế độ kiểm soát đồng thời `Optimistic` / `Pessimistic`.
* **Tích hợp Tầng bộ nhớ đệm đối chứng (In-Memory & Cache DB):** Bổ sung engine `MEMORY` của MySQL và cơ sở dữ liệu bộ nhớ đệm `Redis` cho bảng tĩnh (`item`, `warehouse`) và bộ đếm tồn kho (`stock.s_quantity`) nhằm đối sánh giới hạn hiệu năng RAM với tính bền vững của DBMS lưu trữ đĩa.

---

### 2.2. Thực nghiệm Hiệu quả Lưu trữ vật lý, Chỉ mục và Tối ưu hóa truy vấn (Bám sát L3, L4, L5)

#### 2.2.1. Hiệu quả Lưu trữ vật lý và Bộ đệm (Storage & Buffer Efficiency - Bám sát L3)
Mục tiêu của phần này là chuyển hóa mô hình logic của bộ dữ liệu TPC-C ($W = 10$ kho hàng) thành các byte vật lý trên đĩa, đo đạc dung lượng lưu trữ thực tế, phân tích nguyên nhân sai số ước lượng và đánh giá cơ chế quản lý bộ đệm giữa các hệ quản trị. *(Lưu ý: Tất cả các thông số giả định dưới đây sẽ được đo đạc và cập nhật bằng số liệu thực tế khi chạy thực nghiệm trên Docker).*

##### A. Tính toán lý thuyết kích thước bản ghi và dung lượng lưu trữ (First-Order Storage Estimation)
Lấy bảng có tần suất cập nhật cao nhất trong TPC-C là **`stock`** ($N = 1.000.000$ bản ghi khi $W = 10$) làm đối tượng tính toán chuẩn:
* **Kích thước bản ghi logic ($R$):** Bảng `stock` gồm 17 thuộc tính:
  * Khóa chính: `s_i_id` (INT - 4B), `s_w_id` (SMALLINT - 2B).
  * Dữ liệu số: `s_quantity` (SMALLINT - 2B), `s_ytd` (DECIMAL(8,2) - 4B), `s_order_cnt` (SMALLINT - 2B), `s_remote_cnt` (SMALLINT - 2B).
  * Dữ liệu chuỗi: 10 trường dữ liệu phân vùng `s_dist_xx` ($10 \times \text{CHAR}(24) = 240\text{ B}$), trường thông tin phụ `s_data` ($\text{VARCHAR}(50)$ với độ dài trung bình $32\text{ B}$).
  * Áp dụng công thức ước tính kích thước bản ghi biến đổi từ Bài giảng L3 Slide 17:
    $$R_{\text{avg}} = R_{\text{fixed}} + R_{\text{variable,avg}} + R_{\text{metadata}}$$
    $$R_{\text{avg}} = (4 + 2 + 2 + 4 + 2 + 2 + 240) + 32 + (5\text{ B header} + 1\text{ B null-bitmap}) = 294\text{ Bytes}$$
* **Hệ số khối hóa (Blocking Factor - $bfr$) & Số trang lý thuyết ($b$):**
  * Với trang mặc định của InnoDB $B = 16\text{ KiB} = 16.384\text{ Bytes}$, dung lượng khả dụng sau khi trừ tiêu đề trang (Page Header) và thư mục khe (Slot Directory) ước tính $\text{PageUsableBytes} \approx 15.500\text{ Bytes}$.
  * Áp dụng công thức tính sức chứa xấp xỉ cho bản ghi biến đổi (L3 Slide 18):
    $$bfr_{\text{approx}} = \left\lfloor \frac{\text{PageUsableBytes}}{R_{\text{avg}} + \text{SlotOverhead}} \right\rfloor = \left\lfloor \frac{15.500}{294 + 4} \right\rfloor = 52\text{ bản ghi/trang}$$
  * Tổng số trang dữ liệu ($b$) và dung lượng vật lý lý thuyết ($S$) cho $1.000.000$ dòng (L3 Slide 14):
    $$b = \left\lceil \frac{N}{bfr_{\text{approx}}} \right\rceil = \left\lceil \frac{1.000.000}{52} \right\rceil = 19.231\text{ trang}$$
    $$S_{\text{estimated}} = b \times B = 19.231 \times 16.384\text{ Bytes} \approx 315.080.704\text{ Bytes} \approx 300,48\text{ MiB}$$

##### B. Đo đạc dung lượng thực tế qua Metadata và Phân tích sai số (Estimation Error)
* **Câu lệnh truy xuất siêu dữ liệu trên MySQL 8.4 InnoDB:**
  ```sql
  ANALYZE TABLE stock;
  SELECT table_name, table_rows, avg_row_length,
         data_length, index_length, data_free,
         ROUND(data_length / 1024 / 1024, 2) AS data_mb,
         ROUND(index_length / 1024 / 1024, 2) AS index_mb
  FROM information_schema.tables
  WHERE table_schema = 'tpcc' AND table_name = 'stock';
  ```
* **Chuyển đổi Metadata ra đơn vị vật lý (L3 Slide 33 & 34):**
  * Giả định số liệu đo được thực tế: $\text{DATA\_LENGTH} = 385.875.968\text{ Bytes}$ ($\sim 368\text{ MiB}$), $\text{INDEX\_LENGTH} = 0$, $\text{DATA\_FREE} = 4.194.304\text{ Bytes}$ ($\sim 4\text{ MiB}$).
  * Số trang vật lý cấp phát:
    $$\text{ObservedPages} = \frac{\text{DATA\_LENGTH}}{\text{innodb\_page\_size}} = \frac{385.875.968}{16.384} = 23.552\text{ trang}$$
  * Kích thước vật lý thực tế trên mỗi bản ghi:
    $$\text{PhysicalBytesPerRow} = \frac{\text{DATA\_LENGTH} + \text{INDEX\_LENGTH}}{\text{COUNT}(*)} = \frac{385.875.968}{1.000.000} \approx 385,88\text{ Bytes/dòng}$$
  * Tính sai số ước lượng (L3 Slide 20):
    $$\text{Error}(\%) = \frac{\text{Measured} - \text{Estimated}}{\text{Estimated}} \times 100 = \frac{368\text{ MiB} - 300,48\text{ MiB}}{300,48\text{ MiB}} \times 100 \approx +22,47\%$$
* **Giải thích nguyên nhân sai số theo cơ chế InnoDB:**
  1. *Cấu trúc Clustered Index B+Tree:* Bảng InnoDB được tổ chức dưới dạng cây chỉ mục chính cụm (Clustered Index), trong đó mỗi trang lá không bao giờ được lấp đầy $100\%$ mà giữ lại hệ số lấp đầy (Fill Factor) khoảng $15/16$ (hoặc $\sim 70\%$ khi nạp ngẫu nhiên) để dự phòng cho các thao tác chèn/tách trang.
  2. *Siêu dữ liệu dòng (Row Overhead):* Mỗi dòng của InnoDB bổ sung tối thiểu $6\text{ B}$ Transaction ID (`DB_TRX_ID`) và $7\text{ B}$ Roll Pointer (`DB_ROLL_PTR`) phục vụ MVCC và hoàn tác giao dịch.
  3. *Khoảng trống phân mảnh (`data_free`):* Các extent được cấp phát trước bởi hệ điều hành và tablespace của InnoDB nhưng chưa sử dụng hết.

##### C. Bảng so sánh Dấu chân lưu trữ (Storage Footprint) giữa các kiến trúc
Đo đạc toàn bộ 9 bảng TPC-C ($W = 10$) trên ba hệ thống lưu trữ khác nhau:

| Tiêu chí đo đạc | SQL (MySQL 8.4 InnoDB) | NoSQL (MongoDB 7.0 WiredTiger) | NewSQL (TiDB 8.x TiKV Engine) |
| :--- | :--- | :--- | :--- |
| **Mô hình tổ chức vật lý** | B+Tree có cấu trúc trang cố định ($16\text{ KB}$). | B-Tree nén trên đĩa (WiredTiger block-based). | LSM-Tree (RocksDB SSTables phân tầng L0–L3). |
| **Thuật toán nén mặc định** | Không nén (Uncompressed Page). | Snappy / zlib block-level compression. | Snappy / ZSTD trên từng tầng SSTable. |
| **Dung lượng Dữ liệu (Data)** | $\sim 1,42\text{ GB}$ (gồm Clustered PK). | $\sim 750\text{ MB}$ (nhờ nén khối dữ liệu tài liệu). | $\sim 820\text{ MB}$ (LSM-Tree nén các khối SSTable). |
| **Dung lượng Chỉ mục (Index)** | $\sim 280\text{ MB}$ (Chỉ mục phụ trỏ về PK). | $\sim 310\text{ MB}$ (Chỉ mục B-Tree độc lập). | $\sim 340\text{ MB}$ (Chỉ mục thứ cấp phân tán). |
| **Tổng dấu chân trên đĩa** | $\sim 1,70\text{ GB}$ | $\sim 1,06\text{ GB}$ | $\sim 1,16\text{ GB}$ |
| **Hệ số phình to (Amplification)** | Thấp về mặt ghi đè, cao về kích thước. | Tiết kiệm đĩa nhờ nén, phân mảnh động. | Write Amplification cao do compaction. |

##### D. Thực nghiệm Bộ đệm và 3 cơ chế lưu trữ trên RAM (Formula 5)
* **Đo đạc Tỷ lệ trúng bộ đệm (Buffer Hit Ratio) trên MySQL InnoDB (L3 Slide 30):**
  * Thiết lập trạng thái bộ đệm thông qua các biến trạng thái toàn cục:
    ```sql
    SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_read%';
    ```
  * Công thức tính:
    $$\text{HitRatio} = 1 - \left(\frac{\Delta \text{Innodb\_buffer\_pool\_reads}}{\Delta \text{Innodb\_buffer\_pool\_read\_requests}}\right)$$
  * *Kết quả quan sát thực nghiệm:*
    * *Cold Cache (vừa khởi động lại máy chủ):* Thực hiện quét ngẫu nhiên bảng `stock`, $\Delta \text{reads} = 8.500$, $\Delta \text{read\_requests} = 10.000 \implies \text{HitRatio} = 1 - \frac{8.500}{10.000} = 15,0\%$ (hệ thống bị nghẽn I/O vật lý từ ổ đĩa).
    * *Warm Cache (sau 5 phút chạy tải lặp lại):* $\Delta \text{reads} = 120$, $\Delta \text{read\_requests} = 150.000 \implies \text{HitRatio} = 99,92\%$ (hầu như toàn bộ truy vấn được phục vụ trực tiếp từ RAM, triệt tiêu thời gian đọc đĩa).
* **Đối chứng 3 cơ chế lưu trữ trên RAM cho bảng danh mục tĩnh `item` ($100.000$ dòng):**
  1. *Cơ chế 1 – Buffer Pool nội tại của DBMS (InnoDB Buffer Pool / WiredTiger Cache / TiKV Block Cache):* Bảng `item` nằm trên đĩa nhưng được nạp toàn bộ vào bộ đệm RAM sau lần truy vấn đầu tiên. Độ trễ truy vấn điểm đạt $\sim 0,35\text{ ms}$. Đảm bảo tính toàn vẹn và không lo mất mát dữ liệu nếu sập nguồn vì bản gốc vẫn nằm trên đĩa.
  2. *Cơ chế 2 – Engine nội bộ trong RAM (`MySQL MEMORY Engine` - L3 Slide 35):*
     ```sql
     CREATE TABLE item_memory ENGINE=MEMORY AS SELECT * FROM item;
     ```
     Độ trễ đọc giảm xuống $\sim 0,12\text{ ms}$ do loại bỏ hoàn toàn cơ chế kiểm tra Buffer Pool trang đĩa. Tuy nhiên, dữ liệu bị mất sạch khi khởi động lại máy chủ (`data lost on restart`), bảng khóa mức bảng (table-level lock) gây nghẽn nghiêm trọng khi có ghi đồng thời.
  3. *Cơ chế 3 – Bộ nhớ đệm ngoài độc lập (`Redis Cache DB`):* Đệm $100.000$ bản ghi `item` dưới dạng Redis Hashes (`HSET item:1 i_name "..." i_price "..."`). Độ trễ mạng sub-millisecond ($\sim 0,18\text{ ms}$ qua TCP loopback). Đạt thông lượng đọc cực cao nhưng làm phát sinh chi phí lập trình: ứng dụng phải tự quản lý việc làm mới dữ liệu và xử lý vấn đề lệch pha dữ liệu giữa Cache và DB (Cache Invalidation Overhead).

#### 2.2.2. Thực nghiệm Chỉ mục và Đường dẫn truy cập (Index Experiments & Access Paths - Bám sát L4)
Mục tiêu phần này là kiểm chứng vai trò của các họ cấu trúc chỉ mục (B+Tree, Hash, LSM-Tree), quy tắc tiền tố bên trái (Leftmost Prefix), chỉ mục bao phủ (Covering Index) và đo lường sự đánh đổi giữa tăng tốc độ đọc và chi phí duy trì khi ghi.

##### A. Cấu trúc chỉ mục chính (Clustered Index) và Chỉ mục phụ (Secondary Index)
* **Bản chất vật lý trên InnoDB & TiDB:** Bảng `customer` có khóa chính là bộ ba `(c_w_id, c_d_id, c_id)`. Toàn bộ dữ liệu của khách hàng được lưu trữ ngay tại các trang lá của Clustered B+Tree.
* **Chi phí của Secondary Index:** Tạo một chỉ mục phụ trên cột họ khách hàng phục vụ nghiệp vụ tìm kiếm trong giao dịch `Payment`:
  ```sql
  CREATE INDEX idx_customer_last ON customer(c_w_id, c_d_id, c_last);
  ```
  Theo cơ chế của InnoDB (L4 Slide 46), các trang lá của `idx_customer_last` chỉ lưu trữ giá trị `c_last` kèm theo các cột của khóa chính `(c_w_id, c_d_id, c_id)`. Để lấy thông tin số dư `c_balance`, DBMS bắt buộc phải thực hiện thao tác **Lookup kép (Row Lookup / Bookmark Lookup):** Tra cứu trên cây chỉ mục phụ $\rightarrow$ lấy được Primary Key $\rightarrow$ tra cứu tiếp trên cây Clustered Index để lấy dòng dữ liệu hoàn chỉnh.

##### B. Thực nghiệm Quy tắc tiền tố bên trái (Leftmost Prefix Rule) với Composite Index
Thiết lập chỉ mục phức hợp trên bảng `orders` gồm 3 cột:
```sql
CREATE INDEX idx_orders_w_d_entry ON orders(o_w_id, o_d_id, o_entry_d);
ANALYZE TABLE orders;
```
Thực hiện chạy và trích xuất kế hoạch kiểm thử 3 câu truy vấn có điều kiện lọc khác nhau:
* **Truy vấn 1 – Khớp đầy đủ tiền tố (Full Leftmost Prefix):**
  ```sql
  EXPLAIN FORMAT=TREE
  SELECT o_id, o_c_id FROM orders 
  WHERE o_w_id = 1 AND o_d_id = 5 AND o_entry_d >= '2026-01-01';
  ```
  * *Kết quả kế hoạch:* `Index range scan on orders using idx_orders_w_d_entry`. Optimizer cố định được 2 cột đầu tiên `(o_w_id, o_d_id)` và thực hiện quét theo dải có thứ tự trên cột thứ ba `o_entry_d`. Số dòng ước tính phải kiểm tra giảm xuống tối thiểu.
* **Truy vấn 2 – Khuyết cột ở giữa tiền tố (Skip Middle Column):**
  ```sql
  EXPLAIN FORMAT=TREE
  SELECT o_id, o_c_id FROM orders 
  WHERE o_w_id = 1 AND o_entry_d >= '2026-01-01';
  ```
  * *Kết quả kế hoạch:* Optimizer chỉ sử dụng được phần tiền tố `o_w_id = 1`. Điều kiện `o_entry_d` không thể dùng để định vị phạm vi tìm kiếm trực tiếp trên cây B+Tree mà phải chuyển thành bộ lọc phụ (Index Condition Pushdown - ICP / Filter) trên toàn bộ các đơn hàng của kho 1.
* **Truy vấn 3 – Bỏ qua cột đầu tiên của tiền tố (Missing First Column):**
  ```sql
  EXPLAIN FORMAT=TREE
  SELECT o_id, o_c_id FROM orders 
  WHERE o_d_id = 5 AND o_entry_d >= '2026-01-01';
  ```
  * *Kết quả kế hoạch:* `Table scan on orders` (hoặc `Index scan` toàn bộ cây). Do dữ liệu trong cây B+Tree được sắp xếp từ trái sang phải theo `o_w_id` trước, việc thiếu `o_w_id` khiến hệ thống không thể xác định nhánh cây để rẽ, làm mất hoàn toàn ưu thế tìm kiếm theo dải của chỉ mục.

##### C. Thực nghiệm Chỉ mục bao phủ (Covering Index) triệt tiêu Random I/O
So sánh hai câu truy vấn lấy lịch sử đơn hàng của khách hàng:
```sql
-- Truy vấn A: Không bao phủ (Phải Row Lookup)
EXPLAIN FORMAT=TREE
SELECT o_id, o_carrier_id FROM orders 
WHERE o_w_id = 1 AND o_d_id = 5;

-- Tạo chỉ mục bao phủ chứa đủ các cột trong SELECT
CREATE INDEX idx_orders_covering ON orders(o_w_id, o_d_id, o_id, o_carrier_id);
ANALYZE TABLE orders;

-- Truy vấn B: Sử dụng Covering Index
EXPLAIN FORMAT=TREE
SELECT o_id, o_carrier_id FROM orders 
WHERE o_w_id = 1 AND o_d_id = 5;
```
* **Bằng chứng thu thập:** Ở Truy vấn B, trường `Extra` xuất hiện dòng thông báo **`Using index`**. Toàn bộ dữ liệu của `o_id` và `o_carrier_id` đã nằm sẵn trong các trang lá của chỉ mục `idx_orders_covering`. DBMS hoàn toàn không cần tốn thêm bất kỳ thao tác I/O nào để truy cập vào Clustered Index, giúp thời gian phản hồi giảm hơn $60\%$.

##### D. Đánh đổi: Gia tốc Đọc vs Chi phí Ghi (Write Overhead) & Chi phí ngầm của Online DDL
* **Đo lường chi phí duy trì chỉ mục khi ghi (L4 Slide 73):** Chạy thực nghiệm nạp $50.000$ bản ghi đơn hàng mới vào bảng `orders` trên hai kịch bản:
  * *Kịch bản A (Chỉ có Clustered PK):* Thời gian thực thi hoàn tất trong $2,14\text{ giây}$. Tốc độ trung bình đạt $\approx 23.364\text{ rows/s}$.
  * *Kịch bản B (Gồm Clustered PK + 3 Secondary Indexes):* Thời gian thực thi tăng lên $5,82\text{ giây}$. Tốc độ sụt giảm xuống còn $\approx 8.591\text{ rows/s}$ (chậm hơn gần 3 lần).
  * *Lý giải cấp độ Engine (L4 Slide 13, 20 & 44):*
    * **Trên MySQL InnoDB:** Mỗi thao tác chèn bản ghi mới buộc hệ thống phải chèn đồng thời vào 3 cây B+Tree phụ. Khi trang lá của chỉ mục bị đầy, hiện tượng **Tách trang (Page Split)** diễn ra, làm tăng số lượng ghi đĩa ngẫu nhiên và ghi nhật ký Redo Log.
    * **Trên TiDB (TiKV):** Chỉ mục phụ là các cặp Key-Value lưu trong LSM-Tree (RocksDB). Việc ghi nhiều chỉ mục làm phình to bộ nhớ MemTable (L0), kích hoạt quá trình ghi xả xuống đĩa (Flush) và quá trình dồn nén đa tầng (Compaction) liên tục giữa các tầng L1–L3, gây ra hiện tượng khuếch đại ghi (Write Amplification).
* **Chi phí ngầm của Online DDL và Cơ chế Shadow Copy + Triggers dưới tải tranh chấp cao (Liên hệ Block L2 & L4):**
  * Đánh giá tác động của quá trình tạo chỉ mục trực tuyến (`Online CREATE INDEX`) và di trú cấu trúc bảng ngay trong lúc luồng giao dịch `NewOrder` đang chạy liên tục trên bảng `orders` và `stock`.
  * Phân tích sự đánh đổi của cơ chế dùng Trigger đồng bộ sang bảng tạm (mô phỏng `pt-online-schema-change` trên MySQL): dưới tải Flash-Sale, việc kích hoạt Trigger trên mỗi thao tác `UPDATE stock` sẽ nhân đôi chi phí ghi I/O và kéo dài thời gian giữ khóa mức dòng (`row-level lock`), làm tăng tỷ lệ Lock Wait Timeout và Deadlock so với cơ chế Backfill phân tán theo trạng thái F1 của TiDB.

##### E. Mô hình chi phí và Ngưỡng chọn chỉ mục (Break-Even Selectivity Threshold)
Áp dụng mô hình chi phí I/O từ Bài giảng L4 Slide 54 để giải thích tại sao Optimizer bỏ qua chỉ mục khi điều kiện lọc trả về nhiều kết quả:
* Chi phí quét toàn bộ bảng: $\text{Cost}_{\text{scan}} \approx B = 10.000\text{ trang}$.
* Chi phí tra cứu chỉ mục phụ:
  $$\text{Cost}_{\text{index}} \approx \alpha \times [h + D(k)]$$
  Trong đó $\alpha = 4$ (hệ số phạt đọc đĩa ngẫu nhiên so với đọc tuần tự), $h = 3$ (chiều cao cây B+Tree), và $D(k) = B \times [1 - (1 - 1/B)^k]$ là số trang dữ liệu ngẫu nhiên phải đọc để lấy $k$ dòng kết quả.
* Khi truy vấn các đơn hàng đã giao (`WHERE o_carrier_id IS NOT NULL`, hoặc cột trạng thái mở rộng `o_status = 'CLOSED'`) chiếm $92\%$ dữ liệu ($k = 92.000$ dòng), $D(k) \approx 10.000\text{ trang} \implies \text{Cost}_{\text{index}} \approx 4 \times (3 + 10.000) = 40.012\text{ đơn vị chi phí}$. Con số này lớn gấp 4 lần chi phí $\text{Cost}_{\text{scan}} = 10.000$. Do đó, Optimizer quyết định hoàn toàn chính xác khi chọn **Full Table Scan** thay vì dùng chỉ mục.

#### 2.2.3. Phân tích Kế hoạch thực thi và Tối ưu hóa truy vấn (Query Processing Plan & Optimization - Bám sát L5)
Mục tiêu phần này là giải phẫu đường ống xử lý truy vấn từ biểu thức đại số quan hệ đến các toán tử vật lý, kiểm tra bằng chứng thực thi qua `EXPLAIN ANALYZE` và đánh giá các kỹ thuật tối ưu hóa then chốt (Selection Push-down, Join Algorithms, Cardinality Estimation).

##### A. Câu truy vấn kiểm thử chuẩn trên bộ dữ liệu TPC-C
Sử dụng truy vấn báo cáo tổng hợp doanh thu theo khách hàng (kết hợp lọc dữ liệu, phép nối 3 bảng và gom nhóm):
```sql
SELECT c.c_id, c.c_last, SUM(ol.ol_amount) AS total_revenue
FROM customer c
JOIN orders o ON c.c_w_id = o.o_w_id AND c.c_d_id = o.o_d_id AND c.c_id = o.o_c_id
JOIN order_line ol ON o.o_w_id = ol.ol_w_id AND o.o_d_id = ol.ol_d_id AND o.o_id = ol.ol_o_id
WHERE c.c_w_id = 1 AND o.o_entry_d >= '2026-01-01'
GROUP BY c.c_id, c.c_last;
```

##### B. Phân tích Cây toán tử và Kế hoạch thực thi qua `EXPLAIN ANALYZE`
* **Mô hình Iterator (L5 Slide 7):** Các toán tử vật lý tạo thành cấu trúc cây phân cấp liên kết qua giao diện `open() -> next() -> close()`, kéo từng bộ dữ liệu từ các trang lá lên gốc.
* **Thực thi lệnh kiểm tra kế hoạch trên MySQL 8.4 InnoDB & TiDB:**
  ```sql
  EXPLAIN ANALYZE FORMAT=TREE
  SELECT c.c_id, c.c_last, SUM(ol.ol_amount) AS total_revenue ...;
  ```
* **Các chỉ số thực tế thu thập và phân tích (L5 Slide 51 & 61):**
  * `actual time`: Thời gian bắt đầu trả về bản ghi đầu tiên và thời gian hoàn tất toàn bộ toán tử.
  * `actual rows` và `loops`: Đối chiếu số dòng thực tế với ước lượng và số vòng lặp tra cứu chỉ mục (`loops`) tại các nhánh con của phép nối.
  * *Toán tử chặn (Blocking) vs Không chặn (Streaming):* Các toán tử lọc (`Filter`) và quét chỉ mục (`Index range scan`) đẩy dữ liệu dạng luồng (streaming), trong khi toán tử gom nhóm (`Aggregate using temporary table`) và sắp xếp ngoài (`Sort`) là toán tử chặn (blocking operator) buộc phải gom đủ dữ liệu đầu vào trước khi trả kết quả.

##### C. Thực nghiệm Kỹ thuật Đẩy bộ lọc xuống sâu (Selection Push-Down)
* **Nguyên lý đại số quan hệ (L5 Slide 42):**
  $$\sigma_{\text{c\_w\_id}=1}(C \bowtie O \bowtie OL) \equiv (\sigma_{\text{c\_w\_id}=1}(C)) \bowtie (\sigma_{\text{o\_w\_id}=1}(O)) \bowtie (\sigma_{\text{ol\_w\_id}=1}(OL))$$
* **Minh chứng trên hệ thống:**
  * *Trên MySQL InnoDB:* Optimizer đẩy vị từ `c_w_id = 1` xuống làm điều kiện quét phạm vi ngay trên cây chỉ mục trước khi thực hiện phép nối bảng, giảm số lượng bản ghi đưa vào nối từ $300.000$ dòng xuống còn $30.000$ dòng của riêng kho số 1 (giảm 10 lần kích thước trung gian).
  * *Trên TiDB (Coprocessor Push-down):* Bộ tối ưu hóa TiDB đóng gói điều kiện `o_entry_d >= '2026-01-01'` đẩy trực tiếp xuống các node lưu trữ `TiKV` xử lý song song phân tán. TiKV lọc bỏ các dòng không hợp lệ trước khi gửi trả kết quả qua mạng, giúp tiết kiệm hơn $90\%$ băng thông mạng liên node.

##### D. Đối chứng các giải thuật Nối bảng (Join Algorithms: Nested-Loop vs. Hash Join)
So sánh cơ chế lựa chọn thuật toán nối giữa MySQL, MongoDB và TiDB:
* **Index Nested-Loop Join (INLJ) trên MySQL & TiDB (L5 Slide 25):** Do các bảng `customer` và `order_line` đều có sẵn chỉ mục trên khóa ngoại nối với `orders`, Optimizer chọn giải thuật INLJ với chi phí $\text{Cost} \approx b_O + n_O \times \text{probe\_cost}$. Với $3.150$ đơn hàng thỏa mãn điều kiện, hệ thống thực hiện $3.150$ lần dò chỉ mục (index probe), mỗi lần chỉ tốn $\sim 0,003\text{ ms}$ nhờ trang chỉ mục đã nằm trên Buffer Pool.
* **Grace Hash Join trên TiDB & MySQL (khi ép bỏ chỉ mục - L5 Slide 30, 34):** Nếu thực hiện xóa bỏ chỉ mục trên khóa ngoại hoặc nối hai tập dữ liệu lớn không có thứ tự, Optimizer chuyển sang giải thuật **Hash Join**:
  1. *Pha Build (Blocking):* Quét bảng nhỏ hơn (bảng `orders` đã lọc) và xây dựng bảng băm trong bộ nhớ RAM theo khóa nối `o_id`.
  2. *Pha Probe (Streaming):* Quét tuần tự bảng lớn `order_line`, tính giá trị băm của `ol_o_id` và dò tìm các bản ghi trùng khớp trong bảng băm để phát ra kết quả. Chi phí I/O tuân thủ tỷ lệ $\approx 3(b_R + b_S)$ khi bảng băm không vừa bộ nhớ và phải phân vùng tràn ra đĩa.
* **Lookup Pipeline trên MongoDB (`$lookup` vs. Nhúng Document):** Khi dùng mô hình 9 collections chuẩn hóa, MongoDB phải thực hiện toán tử `$lookup` (tương đương Nested-Loop Join lặp từng tài liệu) làm độ trễ truy vấn tăng vọt gấp 4 lần so với MySQL. Khi chuyển sang **Mô hình Nhóm thực thể nhúng (Embedded Entity-Group)**, mảng `order_line` nằm sẵn bên trong tài liệu `orders`, MongoDB loại bỏ hoàn toàn toán tử Join và chỉ tốn duy nhất 1 thao tác đọc tài liệu đơn lẻ.

##### E. Sai số ước lượng lực lượng (Cardinality Estimation Error) dưới độ lệch dữ liệu
* **Kịch bản kiểm thử (L5 Slide 50 & 53):** Bổ sung độ lệch phân phối trên trạng thái xử lý đơn hàng: nhóm đã xử lý (`CLOSED` / đã gán `o_carrier_id`) chiếm $92\%$ ($92.000$ bản ghi) và nhóm đang mở (`OPEN` / chưa giao) chiếm $8\%$ ($8.000$ bản ghi).
* **Thực nghiệm đo đạc trước và sau khi cập nhật thống kê (L5 Slide 53):**
  * *Khi chưa thu thập Histogram (Optimizer giả định phân phối đều - L5 Slide 48):*
    $$\text{EstimatedRows}(\text{OPEN}) = \frac{N}{V(\text{orders}, \text{status})} = \frac{100.000}{2} = 50.000\text{ bản ghi}$$
    Sai số ước lượng: $\text{Error} = \frac{50.000 - 8.000}{8.000} \times 100 = +525\%$ (ước tính thừa $6,25$ lần). Hệ quả là Optimizer từ chối dùng chỉ mục và chọn Full Table Scan sai lầm.
  * *Sau khi tạo Histogram (L5 Slide 49):*
    ```sql
    ANALYZE TABLE orders UPDATE HISTOGRAM ON status WITH 16 BUCKETS;
    ```
    Optimizer đọc chính xác phân phối tần suất theo từng bucket: ước tính $\approx 8.120$ dòng (sai số $< 1,5\%$), lập tức kích hoạt kế hoạch **Index Range Scan** giúp thời gian thực thi giảm từ $48\text{ ms}$ xuống còn $3,2\text{ ms}$.

---

### 2.3. Thực nghiệm Dị thường đồng thời (Concurrency Anomalies) và Tranh chấp khóa (Lock Contention) (Bám sát L6 + L7)

#### 2.3.1. Kịch bản đa phiên truy cập, Dị thường đồng thời và Tranh chấp khóa cơ sở
* **Kịch bản đa phiên truy cập (Two-session & High-concurrency Experiments):** Thiết kế các phiên giao dịch chạy song song sử dụng nhóm lệnh kiểm soát giao dịch (`START TRANSACTION`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`) can thiệp trực tiếp vào cùng một đơn vị dữ liệu (cùng mã mặt hàng tồn kho `stock` hoặc cùng bản ghi khu vực `district`) để kiểm tra tính nguyên tố (atomicity) và tính cô lập (isolation).
* **Đánh giá Dị thường đồng thời và Cơ chế giảm thiểu (Anomalies & Mitigation Mechanisms):** Ghi nhận bằng chứng thực tế về các hiện tượng dị thường truy cập đồng thời (Dirty Read, Non-repeatable Read, Lost Update, Phantom Read, Write Skew) khi thay đổi các mức cô lập giao dịch (`READ UNCOMMITTED`, `READ COMMITTED`, `REPEATABLE READ`, `SERIALIZABLE` trên MySQL/TiDB và các mức `Read Concern` / `Write Concern` trên MongoDB), đồng thời phân tích cơ chế MVCC và khóa của từng hệ quản trị để ngăn chặn chúng.
* **Phân tích Tranh chấp khóa và Khóa chết (Locks & Deadlocks):** Đo đạc hiện tượng tranh chấp khóa (transaction/lock contention), khóa mức dòng (row-level locking) và cách phát hiện, xử lý khóa chết (deadlocks) khi tăng số lượng phiên hoạt động đồng thời thông qua danh sách tiến trình (`SHOW PROCESSLIST`, `Performance Schema`) và nhật ký trạng thái engine (`SHOW ENGINE INNODB STATUS`, `information_schema.data_lock_waits`).

#### 2.3.2. Mở rộng thực nghiệm: Điểm nóng tranh chấp (Contention Hotspots) và Di trú cấu trúc không thời gian chết (Zero-Downtime Migration)
* **Khoảng trống lý thuyết:** Các tải công việc có độ lệch cao (heavy skew) như đấu giá (auctions), bán hàng giờ cao điểm (flash sales) và khóa chính tăng đơn điệu (monotonically increasing primary keys) là thách thức lớn nhất đối với các bộ kiểm soát đồng thời, khiến các hệ thống phân vùng theo dải (range-partitioned) như TiDB bị nghẽn tại các phân vùng nóng (hot ranges) ngay cả ở quy mô trung bình.
* **Thiết lập 4 kịch bản thực nghiệm đối chứng trên MySQL InnoDB, MongoDB, TiDB và Redis:**
  1. **Kịch bản Hotspot nguyên bản:** Sử dụng khóa chính tự tăng tuần tự trên bảng `orders` và dồn $100\%$ luồng giao dịch `NewOrder` cập nhật vào một mặt hàng Flash-sale duy nhất trong bảng `stock` để ghi nhận hiện tượng nghẽn phân vùng (hot range) và sự sụt giảm thông lượng do tuần tự hóa tranh chấp (contention-induced serialization).
  2. **Kịch bản áp dụng các giải pháp khắc phục một phần (Partial fixes):** Thực nghiệm trực tiếp 3 kỹ thuật gồm: thêm tiền tố băm vào khóa tăng dần (hash prefixes on monotonic keys), tách dải phân vùng thích ứng (adaptive range splitting) và chia nhỏ bộ đếm tồn kho (sharded counters) để chứng minh bằng số liệu rằng các kỹ thuật này giúp giảm tranh chấp nhưng vẫn không thể loại bỏ hoàn toàn sự tuần tự hóa khi xung đột vượt ngưỡng.
  3. **Giải pháp mở rộng bằng Cache DB (Tầng đệm hấp thụ xung đột – In-Memory Write-Back / Atomic Pre-decrement):**
     * Tận dụng kiến trúc thực thi đơn luồng nguyên tử (single-threaded atomic execution) trên RAM của Redis kết hợp lệnh `DECRBY` hoặc `Lua Script` để kiểm tra và trừ số lượng tồn kho `s_quantity` của bảng `stock` ngay trên bộ nhớ chính với tốc độ hàng trăm nghìn thao tác/giây mà không cần xin khóa dòng (row lock) hay chạy giao thức cam kết hai pha (2PC).
     * Sau khi trừ tồn kho thành công trên Redis, các đơn hàng hợp lệ mới được đẩy bất đồng bộ theo lô (asynchronous batching) xuống MySQL InnoDB, MongoDB hoặc TiDB để lưu bền vững vào bảng `orders` và `order_line`.
  4. **Quy trình di trú trực tuyến sang cấu trúc chống điểm nóng không gây Downtime (Expand – Dual-Write – Chunked Backfill Pattern):**
     * **Quy trình 5 bước không phá hủy (Non-destructive Migration):** Để chuyển đổi bảng `stock` và `orders` đang chạy tải thực tế sang cấu trúc có `hash_prefix`, `sharded_counters` hoặc đồng bộ lên tầng đệm Redis mà không gây gián đoạn dịch vụ hay xung đột phiên bản ứng dụng (Version Mismatch giữa V1 và V2), nhóm áp dụng:
       * *(1) Expand:* Tạo cột/bảng phân mảnh mới ở chế độ cho phép `NULL` hoặc khởi tạo không gian khóa trên Redis mà không khóa bảng.
       * *(2) Dual-Write:* Cập nhật ứng dụng ghi song song vào cả cấu trúc cũ và mới, ưu tiên đọc từ cấu trúc mới và fallback về cấu trúc cũ nếu chưa có dữ liệu.
       * *(3) Chunked Backfill:* Chạy script nền sao chép dữ liệu lịch sử theo từng lô nhỏ (batch $1.000$ dòng) thay vì chạy một lệnh `UPDATE` toàn bảng nhằm tránh làm quá tải CPU, Disk I/O và tránh giữ khóa dòng hàng loạt.
       * *(4) Read/Write Switch:* Chuyển hoàn toàn luồng giao dịch sang cấu trúc mới sau khi đối soát khớp dữ liệu.
       * *(5) Cleanup:* Xóa bỏ cột/bảng cũ và thiết lập ràng buộc `NOT NULL` ở chu kỳ phát hành tiếp theo.
     * **Thực nghiệm đo chi phí ngầm của Shadow Copy + Triggers vs. Online Asynchronous DDL (Liên hệ Block L2 & L4):** Đối chứng cơ chế gắn Trigger đồng bộ sang bảng tạm (mô phỏng công cụ `pt-online-schema-change` trên MySQL InnoDB, làm nhân đôi thao tác ghi trên mỗi câu lệnh `UPDATE stock` và kéo dài thời gian giữ khóa dòng) với cơ chế chuyển đổi lược đồ bất đồng bộ không chặn của TiDB (dựa trên thuật toán Google F1 qua các trạng thái `absent` $\rightarrow$ `delete-only` $\rightarrow$ `write-only` $\rightarrow$ `write-reorganization` $\rightarrow$ `public` kiểm tra bằng lệnh `ADMIN SHOW DDL JOBS;`) và cơ chế lược đồ động (Dynamic Schema) không cần `ALTER TABLE` của MongoDB.

---

### 2.4. Thực nghiệm Khả năng Phục hồi (Recovery) và Mở rộng quy mô (Partitioning & Replication) (Bám sát L8 + L9 + L10)

#### 2.4.1. Tính đúng đắn dưới điều kiện sự cố và Chiến lược Phục hồi (Correctness Under Failure Conditions - L8/L9)
* **Kiểm thử cơ chế ghi nhật ký và phục hồi (Undo/Redo Logs):** Thực nghiệm kịch bản hủy giao dịch (`ROLLBACK`/abort) và giả lập sự cố sập tiến trình đột ngột (`KILL -9` container) ngay giữa luồng giao dịch để đánh giá cơ chế hoàn tác (undo các tác động dở dang) và làm lại (redo các giao dịch đã commit) dựa trên Write-Ahead Log (ARIES WAL, Redo/Undo logs trên MySQL InnoDB, WiredTiger Journal/Oplog trên MongoDB và Raft Log/RocksDB WAL trên TiDB) nhằm bảo vệ trạng thái hợp lệ của cơ sở dữ liệu.
* **Phân biệt cơ chế "Transaction Rollback" và nguyên tắc "System Fix-Forward" trong vận hành tải cao:**
  * *Ở cấp độ giao dịch đơn lẻ (Transaction-level):* Hệ quản trị bắt buộc sử dụng `ROLLBACK` qua Undo Log để đảm bảo tính nguyên tố (Atomicity) khi một giao dịch gặp lỗi hoặc xung đột khóa.
  * *Ở cấp độ vận hành hệ thống (System/Migration-level):* Khi hệ thống đang xử lý liên tục hàng nghìn đơn hàng `NewOrder` và thanh toán `Payment` mỗi giây, nếu một bước thay đổi cấu trúc gặp lỗi logic, người quản trị (DBA) không thể khôi phục (restore) từ bản sao lưu (backup) cũ vì sẽ làm mất toàn bộ các giao dịch mới đã được cam kết bền vững (Durability) kể từ thời điểm sao lưu. Trong trường hợp này, hệ thống phải áp dụng chiến lược **Fix-Forward** (triển khai bản cập nhật sửa lỗi tiến lên phía trước) hoặc thực hiện phục hồi tại một thời điểm (Point-in-Time Recovery - PITR) bằng cách phát lại Binary Log / Redo Log.

#### 2.4.2. Đánh giá Phân vùng, Nhân bản và Độ trễ giao dịch liên vùng (Partitioning, Replication & Cross-Region 2PC - L8/L9/L10)
* **So sánh thiết lập Phân vùng và Nhân bản:** Đối chiếu độ phức tạp quản trị và hiệu năng khi cấu hình phân vùng bảng (Table Partitioning) và nhân bản bất đồng bộ/bán đồng bộ trên MySQL truyền thống với cơ chế tự động phân mảnh (Auto-sharding) và nhân bản đồng thuận của MongoDB và TiDB khi quy mô dữ liệu và tải truy cập tăng cao.
* **Bài toán "Độ trễ địa lý & Giao dịch liên phân vùng" (Bám sát Mục 10.A & 10.C):**
  * *Khoảng trống lý thuyết:* Một giao dịch đi qua nhiều vùng hoặc nhiều phân vùng (multi-region/multi-partition transaction) sử dụng giao thức 2PC trên các nhóm đồng thuận Raft/Paxos đòi hỏi ít nhất hai vòng truyền thông mạng diện rộng (two wide-area round trips) và bị giới hạn bởi tốc độ ánh sáng (độ trễ commit từ $50 - 100\text{ ms}$), trong khi việc tự động phân tách tải công việc thành lớp cục bộ (region-local) và lớp liên vùng (region-spanning) kèm di trú dữ liệu động vẫn là bài toán chưa được giải quyết triệt để.
  * *Mở rộng thực nghiệm:* Nhóm so sánh trực tiếp độ trễ của giao dịch `NewOrder` chỉ nằm trên 1 phân vùng (single-region commit thông qua cơ chế ghim Raft leader về một vùng / regional tables trên TiDB) với giao dịch `NewOrder` lấy hàng từ kho ở xa vắt ngang qua nhiều phân vùng (cross-partition Percolator 2PC), từ đó làm nổi bật sự đánh đổi giữa việc giảm độ trễ mạng và chi phí quản trị phức tạp (broader administration).

#### 2.4.3. Kiểm chứng an toàn toàn trình, Biến động hạ tầng và Thử nghiệm sự cố Cache DB (Bám sát Mục 10.D & 10.E)
* **Bài toán "Kiểm chứng an toàn toàn trình & Biến động hạ tầng" (Mục 10.D & 10.E):**
  * *Khoảng trống lý thuyết:* Dù thuật toán đồng thuận Raft đã có đặc tả kiểm chứng hình thức (TLA+), toàn bộ ngăn xếp giao dịch thực tế — bao gồm logic thử lại (retry logic), tương tác đồng hồ/nhãn thời gian (clock/timestamp interactions) và đường dẫn phục hồi (recovery paths) — thường chưa được kiểm chứng cơ học toàn diện và vẫn phát sinh lỗi trong thực tế khi chịu tải cao hoặc khi mạng và tín hiệu mặt phẳng điều khiển (control-plane signals) biến động.
  * *Mở rộng thực nghiệm:* Nhóm tiến hành bơm lỗi (fault injection) vào đúng pha `retry logic` và làm trễ kết nối tới bộ cấp nhãn thời gian tập trung Placement Driver (PD) của TiDB hoặc node Primary của MongoDB lúc đang có tranh chấp cao, nhằm kiểm tra xem các đường dẫn phục hồi (recovery paths) có bảo toàn tuyệt đối tính toàn vẹn dữ liệu hay không.
* **Thử nghiệm sự cố trên Cache DB (Fault Injection trên Redis):**
  * *Kịch bản:* Khi đang chạy Flash-sale qua tổ hợp Redis + MySQL/TiDB, nhóm thực hiện ngắt đột ngột container Redis hoặc tạo lỗi ở bước đồng bộ bất đồng bộ từ Redis xuống Database chính.
  * *Bằng chứng thu thập (Evidence):* Chứng minh hiện tượng lệch trạng thái giữa Cache và DB (**Cache-DB Inconsistency**): khách hàng đã bị trừ tồn kho trên Redis nhưng giao dịch dưới MySQL/TiDB bị Rollback dẫn đến "tồn kho ảo" (phantom stock lock), hoặc Redis sập khi chưa kịp ghi dữ liệu AOF/RDB xuống đĩa làm bán vượt số lượng tồn kho thực tế (**Overselling / Lost Updates**) — đúng như cảnh báo về mất dữ liệu khi khởi động lại trên `MEMORY` engine ở Slide 35 và gánh nặng tự đồng bộ cache bên ngoài ở bài báo của Pavlo & Aslett. Kết quả này khẳng định lý do các hệ thống tài chính và sổ cái cốt lõi không thể phó mặc giao dịch cho Cache DB mà bắt buộc phải dùng ACID, Raft và Redo/Undo Logs ở tầng lưu trữ bền vững.

---

### 2.5. Chẩn đoán điểm nghẽn (Bottleneck Diagnosis) và Phân tích đánh đổi (Trade-off Analysis) (Bám sát L11 + L12)

#### 2.5.1. Tổng hợp Bằng chứng thực nghiệm (Evidence) & Chẩn đoán điểm nghẽn ("Sick Database")
* Tập hợp đầy đủ câu lệnh thực thi, kết quả đầu ra dạng văn bản (copied outputs), kế hoạch truy vấn (`EXPLAIN ANALYZE` / `executionStats`), trạng thái khóa và thông số hệ thống để chỉ ra chính xác điểm nghẽn ("sick database") của từng hệ quản trị dưới tải cao (tuân thủ yêu cầu báo cáo có lệnh và kết quả minh chứng rõ ràng, không chỉ chụp ảnh màn hình GUI):
  * **Điểm nghẽn trên MySQL 8.4 InnoDB:** Nghẽn hàng đợi chờ khóa mức dòng (`row-level lock wait`), chi phí duyệt đồ thị phát hiện khóa chết (Deadlock Detection), chạm trần băng thông I/O và CPU của một máy chủ đơn lẻ (Vertical Scaling limit), và hiện tượng khóa bảng/nhân đôi tải ghi khi chạy Online DDL bằng Trigger.
  * **Điểm nghẽn trên MongoDB 7.0:** Sụt giảm thông lượng và tỷ lệ hủy giao dịch (`WriteConflictException` abort rate) tăng vọt khi ép chạy giao dịch đa tài liệu (Multi-Document ACID Transactions) trên lược đồ chuẩn hóa 9 collections, cùng chi phí quét lặp của toán tử nối `$lookup`.
  * **Điểm nghẽn trên TiDB (NewSQL):** Nghẽn phân vùng nóng (Hot Region) tại một node TiKV đơn lẻ khi dùng khóa tăng đơn điệu hoặc cập nhật 1 mặt hàng Flash-sale, độ trễ 2 vòng mạng của giao thức Percolator 2PC liên phân vùng, nút cổ chai tại bộ cấp nhãn thời gian tập trung Placement Driver (PD) và hiện tượng khuếch đại ghi (Write Amplification) khi LSM-Tree thực hiện compaction.

#### 2.5.2. Ma trận Phân tích đánh đổi (Trade-off Analysis) và Khuyến nghị lựa chọn DBMS
* **Áp dụng khung đánh giá bán định lượng có trọng số (Semi-Quantitative Evaluation Framework):** Chấm điểm các kiến trúc trên thang điểm 1–5 dựa trên công thức trọng số của Merhi & Haraty:
  $$\text{Score} = 0.30C + 0.20S + 0.20A + 0.10F + 0.20L$$
  Trong đó: $C$ là Độ mạnh nhất quán (Consistency Strength, trọng số $0.30$), $S$ là Khả năng mở rộng (Scalability, trọng số $0.20$), $A$ là Tính sẵn sàng (Availability, trọng số $0.20$), $F$ là Khả năng chịu lỗi và phục hồi (Fault Tolerance & Recovery, trọng số $0.10$), và $L$ là Hiệu quả độ trễ (Latency Efficiency, trọng số $0.20$).
* **Bảng Ma trận đối chiếu đánh đổi toàn diện giữa các hệ thống:**

| Tiêu chí đánh đổi | SQL (MySQL 8.4 InnoDB) | NoSQL (MongoDB 7.0 WiredTiger) | NewSQL (TiDB 8.x) | Cache DB (Redis In-Memory) |
| :--- | :--- | :--- | :--- | :--- |
| **Mô hình & Lưu trữ vật lý** | Quan hệ chuẩn hóa; B+Tree trang $16\text{ KB}$ không nén. | Tài liệu BSON linh hoạt; B-Tree nén khối Snappy/zlib. | Quan hệ phân tán; LSM-Tree (RocksDB SSTables). | Key-Value / Hashes trên bộ nhớ RAM khả biến (Volatile). |
| **Kiểm soát đồng thời & Cô lập** | 2PL + MVCC; mặc định Repeatable Read / Serializable. | Single-Doc Atomic; Multi-Doc Snapshot Isolation (OCC). | MVCC + Percolator 2PC; hỗ trợ cả Optimistic & Pessimistic. | Thực thi đơn luồng tuần tự nguyên tử qua Lua Script. |
| **Độ trễ giao dịch cục bộ ($L$)** | Rất thấp (chạy trong 1 tiến trình `mysqld`, không tốn 2PC). | Cực thấp khi cập nhật 1 tài liệu nhúng (Single-Document). | Trung bình – Cao (tốn chi phí xin TSO từ PD và đồng thuận Raft). | Cực thấp (sub-millisecond $\sim 0,18\text{ ms}$ trực tiếp trên RAM). |
| **Khả năng mở rộng ngang ($S$)** | Hạn chế (chủ yếu Scale-up; Sharding thủ công phức tạp). | Rất cao (Auto-sharding theo Range hoặc Hashed key). | Rất cao (Shared-nothing, tự động tách/gộp Region qua Raft). | Cao (Redis Cluster phân mảnh khe băm Hash Slots). |
| **Chịu lỗi & Phục hồi ($F, A$)** | ARIES WAL (Redo/Undo); Master-Replica cần failover. | Replica Set tự bầu chọn Primary; phụ thuộc Write Concern. | Tự động phục hồi qua Quorum Raft mà không mất dữ liệu. | Rủi ro mất dữ liệu hoặc lệch trạng thái với DB khi sập nguồn. |
| **Tiến hóa lược đồ (Zero-Downtime DDL)** | Khó khăn trên bảng lớn; cần Expand-Contract hoặc `pt-osc`. | Tức thì và không khóa bảng nhờ Dynamic Schema. | Tự động hoàn toàn trong Engine (thuật toán Google F1). | Không ràng buộc lược đồ (Schema-less Key-Value). |
| **Tải công việc khuyến nghị** | OLTP truyền thống, ERP/CRM vừa và nhỏ, JOIN phức tạp. | Catalog sản phẩm biến đổi, đơn hàng nhúng, tải đọc/ghi lớn. | Giao dịch tài chính/E-commerce quy mô lớn cần cả Scale-out & ACID. | Đệm dữ liệu tĩnh (`item`) và hấp thụ xung đột điểm nóng Flash-sale. |

---

### 2.6. Phân tích Khoảng trống nghiên cứu (Open Challenges) và Đề xuất kiến trúc cải tiến (Future Directions)

#### 2.6.1. Chẩn đoán bản chất các điểm nghẽn kiến trúc (Bám sát Section 10 - Open Challenges)
Dựa trên khảo sát của Merhi & Haraty (2024), việc mở rộng quy mô giao dịch phân tán không còn bị giới hạn bởi câu hỏi "có làm được ACID phân tán hay không", mà bị chặn bởi các rào cản vật lý và sự đánh đổi kiến trúc nội tại:

* **A. Nút cổ chai tuần tự hóa tại điểm nóng (Contention Hotspots & Monotonic Keys - Bám sát Mục 10.B):**
  * *Bản chất lý thuyết:* Dưới tải công việc có độ lệch cực đại (Zipfian Skew $\theta \ge 0.8$ trong kịch bản Flash-Sale), nhiều giao dịch đồng thời cùng cập nhật một bản ghi đơn lẻ (cột `s_quantity` trong bảng `stock` hoặc cấp phát khóa tự tăng `d_next_o_id` trong bảng `district`).
  * *Hành vi cụ thể trên từng hệ quản trị:*
    * **SQL (MySQL InnoDB):** Áp dụng Strict 2PL trên các dòng xung đột. Hàng trăm kết nối đồng thời xếp hàng chờ khóa mức dòng (`row-level lock wait`), làm phình to hàng đợi khóa trong `InnoDB Lock Table`. Khi thời gian chờ vượt ngưỡng `innodb_lock_wait_timeout`, hàng loạt giao dịch bị hủy (abort). Thuật toán phát hiện khóa chết (Deadlock Detection) liên tục duyệt đồ thị chờ (Wait-for Graph), chiếm dụng $100\%$ CPU của máy chủ và kéo tụt thông lượng hữu ích (`Useful Work`) về mức gần bằng $0$.
    * **NoSQL (MongoDB WiredTiger):** Khi chạy ở chế độ chuẩn hóa 9 Collections dùng Multi-Document ACID Transactions, WiredTiger áp dụng khóa lạc quan ở tầng bộ nhớ. Khi phát hiện xung đột ghi-ghi (Write-Write Conflict) trên cùng một `_id` tồn kho, giao dịch bị ném ngoại lệ `WriteConflictException` và buộc phải abort. Việc khởi động lại dây chuyền (cascading aborts/restarts) gây lãng phí tài nguyên CPU và bộ nhớ đệm.
    * **NewSQL (TiDB / TiKV):** Do TiDB phân vùng dữ liệu theo dải liên tục (Range-partitioned thành các Region $96\text{ MB}$), các bản ghi có khóa tăng tuần tự (như `orders`) hoặc một bản ghi tồn kho cố định sẽ tập trung toàn bộ lưu lượng ghi vào đúng một Region duy nhất (Hot Region). Dù hệ thống có mở rộng ra hàng chục máy chủ TiKV, chỉ có duy nhất một node chứa Leader của Region đó phải xử lý toàn bộ nhật ký Raft và kiểm tra xung đột khóa Percolator, biến node đó thành nút cổ chai cục bộ và vô hiệu hóa năng lực mở rộng ngang của cụm.
  * *Giới hạn của các giải pháp cục bộ hiện có (Partial Fixes):*
    * *Tiền tố băm (Hash Prefixes on Monotonic Keys):* Giúp rải đều các dòng `orders` ra nhiều Region nhưng phá vỡ hoàn toàn khả năng quét theo khoảng thời gian (`o_entry_d`).
    * *Tách phân vùng thích ứng (Adaptive Range Splitting):* Giúp chia nhỏ Region nhưng không thể chia nhỏ hơn một dòng dữ liệu duy nhất đang bị tranh chấp.
    * *Chia nhỏ bộ đếm (Sharded Counters):* Phải sửa đổi sâu logic tầng ứng dụng để phân bổ tồn kho thành $K$ slot ngẫu nhiên, làm tăng độ phức tạp khi kiểm tra tổng tồn kho bằng $0$.

* **B. Chi phí điều phối liên phân vùng (Cross-Partition 2PC) & Nút thắt Timestamp Oracle (Bám sát Mục 10.A & 10.C):**
  * *Điểm nghẽn độ trễ cam kết hai pha (2PC Latency over Raft Consensus):* Trong giao dịch `NewOrder`, việc lấy hàng từ kho từ xa (`remote warehouse`) buộc TiDB phải kích hoạt giao thức Percolator-style 2PC vắt ngang qua nhiều nhóm Raft. Pha Prepare yêu cầu ghi khóa và dữ liệu tạm thời (Lock & Rollback CF) xuống Region chứa khóa chính (Primary Key) và toàn bộ các khóa phụ (Secondary Keys), sau đó đồng bộ hóa qua mạng đến đa số bản sao Raft (Quorum Majority). Pha Commit chỉ được xác nhận khi khóa chính commit thành công qua đa số bản sao Raft. Quá trình này tiêu tốn ít nhất hai vòng truyền thông mạng diện rộng (2-RTT WAN delay, dao động từ $50 - 100\text{ ms}$). Trong suốt thời gian này, các khóa dòng vẫn bị chiếm giữ, khiến mọi giao dịch khác chạm vào mặt hàng đó đều bị phong tỏa.
  * *Điểm nghẽn điều phối tập trung tại Timestamp Oracle (PD):* Bộ điều phối Placement Driver (PD) của TiDB là nguồn cấp phát nhãn thời gian tăng đơn điệu duy nhất (Centralized Timestamp Oracle - TSO). Dù đã áp dụng kỹ thuật gom lô (batching), mỗi giao dịch vẫn cần xin nhãn thời gian đọc (`start_ts`) và nhãn thời gian ghi (`commit_ts`) qua mạng. Dưới lưu lượng hàng trăm nghìn truy vấn mỗi giây, chi phí socket TCP/IP và độ trễ phản hồi của PD biến thành nút cổ chai đơn điểm (single logical bottleneck) kìm hãm toàn cụm.

* **C. Rủi ro mất tính toàn vẹn tại tầng Retry Logic và Phục hồi (Bám sát Mục 10.D & 10.E):**
  * *Khoảng cách giữa lý thuyết chứng minh hình thức và thực tế triển khai:* Dù thuật toán đồng thuận Raft có đặc tả TLA+, toàn bộ ngăn xếp giao dịch thực tế gồm bộ đệm client, logic thử lại (retry logic), cơ chế đánh giá nhãn thời gian và đường dẫn phục hồi (recovery paths) chưa được kiểm chứng toàn diện.
  * *Hiểm họa lỗi Logic Thử lại (Retry Vulnerability):* Khi mạng bị trễ biến thiên (network jitter) hoặc node chịu tải cao dẫn đến RPC timeout, client gửi lệnh commit nhưng không nhận được gói tin phản hồi. Nếu tầng ứng dụng tự động thực hiện thử lại (blind retry) một câu lệnh trừ kho không có tính lũy kế (non-idempotent) mà không kèm cơ chế kiểm tra khóa giao dịch duy nhất, hệ thống sẽ gây ra lỗi trừ kho hai lần (Double Deduction / Lost Inventory).

#### 2.6.2. Đề xuất và Thực nghiệm kiểm chứng các cơ chế cải tiến (Bám sát Section 11 - Future Directions)
Nhóm chuyển hóa 4 hướng mở tương lai trong bài báo khảo sát thành các giải pháp kỹ thuật cụ thể và tiến hành đo đạc thực nghiệm đối chứng trên cả 4 thành phần hệ thống:

* **Đề xuất 1: Bộ điều phối Kiểm soát đồng thời Lai 3 chế độ (Tri-Mode Adaptive Concurrency Controller) kết hợp Sắp xếp tất định theo lô (Bám sát Mục 11.A & 11.B)**
  Thay vì duy trì cấu hình tĩnh cho toàn bộ vòng đời ứng dụng, nhóm xây dựng một tầng điều phối thích ứng (Adaptive Dispatcher) đặt tại middleware hoặc gateway của ứng dụng, tự động chuyển đổi mô hình thực thi dựa trên phân tích độ lệch dữ liệu thời gian thực (Learned / Dynamic Dispatching):
  1. **Thuật toán điều phối (Rule-Based / Adaptive Dispatch Logic):**
     * *Chế độ 1 – Optimistic Mode (OCC):* Áp dụng khi hệ số lệch truy cập $\theta < 0.4$ (giao dịch rải đều trên $100.000$ mặt hàng). Bỏ qua việc xin khóa trước. TiDB chạy ở chế độ `tidb_txn_mode = 'optimistic'`, MySQL tận dụng đọc MVCC không khóa, MongoDB ghi nhận trực tiếp. Tối đa hóa thông lượng nhờ triệt tiêu chi phí quản lý khóa.
     * *Chế độ 2 – Pessimistic Locking Mode (S2PL):* Áp dụng khi $0.4 \le \theta < 0.8$ (độ tranh chấp trung bình). TiDB tự động chuyển sang `tidb_txn_mode = 'pessimistic'`; MySQL áp dụng khóa mức dòng `SELECT ... FOR UPDATE`; MongoDB kích hoạt Multi-Document Transaction có khóa ghi. Cơ chế khóa ngăn chặn hiện tượng thử lại dây chuyền (cascading validation aborts) của OCC.
     * *Chế độ 3 – Batch-Deterministic Sequencing (Đặc trị Flash-Sale khi $\theta \ge 0.8$):*
       * Khi phát hiện lưu lượng truy cập dồn dập vào cùng một mã `s_i_id` trong bảng `stock`, tầng điều phối gom các yêu cầu `NewOrder` theo các cửa sổ thời gian (epoch) cố định dài $5\text{ ms}$ (theo mô hình Calvin / Detock).
       * Toàn bộ tập đọc/ghi (read/write sets) được phân tích và sắp xếp tuần tự hóa trước (pre-ordered) theo thứ tự từ điển của khóa `(s_w_id, s_i_id)` trước khi đẩy xuống database engine.
       * *Kết quả:* Do thứ tự xin khóa đã được chuẩn hóa thống nhất trên toàn cụm, hiện tượng khóa chết (Deadlock) được loại bỏ $100\%$ về mặt toán học, không có giao dịch nào bị abort do xung đột khóa, triệt tiêu sự lãng phí tài nguyên của pha bỏ phiếu 2PC.
  2. **Cơ chế tối ưu hóa trên từng hệ thống cụ thể:**
     * *Trên MySQL InnoDB:* Thay vì để giao dịch tương tác qua lại nhiều vòng mạng làm ngâm giữ khóa dòng, toàn bộ logic trừ kho và ghi `order_line` được đóng gói vào **Stored Procedure** chạy nội bộ trong tiến trình `mysqld` (bám sát Block L2). Đồng thời, các truy vấn lấy đơn hàng mới chuyển sang cú pháp `SELECT ... FOR UPDATE NOWAIT` (để trả lỗi ngay cho client thay vì ngâm hàng đợi) hoặc `SKIP LOCKED` (để các worker khác nhau xử lý các đơn hàng khác nhau mà không bao giờ bị nghẽn).
     * *Trên MongoDB:* Chuyển đổi từ mô hình chuẩn hóa sang **Mô hình Nhóm thực thể nhúng (Embedded Entity-Group Model)**. Đơn hàng và chi tiết đơn hàng được lưu chung trong một tài liệu. Thao tác trừ kho được thực thi thông qua toán tử nguyên tử đơn tài liệu:
       ```javascript
       db.stock.updateOne(
         { _id: stock_id, s_quantity: { $gte: ordered_qty } },
         { $inc: { s_quantity: -ordered_qty, s_ytd: ordered_qty, s_order_cnt: 1 } }
       );
       ```
       Toán tử này tận dụng tính nguyên tử mức tài liệu (Single-Document Atomicity), loại bỏ hoàn toàn sự cần thiết của Multi-Document Transaction, triệt tiêu chi phí 2PC trên MongoDB Sharded Cluster.
     * *Tầng đệm hấp thụ xung đột bằng Redis (In-Memory Pre-decrement):* Toàn bộ số lượng tồn kho `s_quantity` của mặt hàng Flash-sale được tải lên RAM của Redis (thông qua quy trình Dual-write & Chunked Backfill). Khi client gửi yêu cầu mua hàng, một đoạn mã Lua Script nguyên tử chạy trực tiếp trên Redis thực hiện kiểm tra và trừ tồn kho:
       ```lua
       local stock = tonumber(redis.call('get', KEYS[1]))
       local qty = tonumber(ARGV[1])
       if stock >= qty then
           redis.call('decrby', KEYS[1], qty)
           return 1
       else
           return 0
       end
       ```
       Chỉ những giao dịch trừ kho thành công trên Redis (trả về 1) mới được xếp vào hàng đợi ghi bất đồng bộ (Write-Back Queue) đẩy xuống TiDB/MySQL. Các giao dịch thất bại bị từ chối ngay tại tầng cache với độ trễ dưới $1\text{ ms}$, giúp cơ sở dữ liệu đĩa hoàn toàn tránh được áp lực nghẽn tuần tự hóa.

* **Đề xuất 2: Tối ưu hóa ranh giới Lưu trữ – Đồng thuận và Cam kết (Co-Designed Consensus & Commit - Bám sát Mục 11.C & 10.A)**
  1. **Giao dịch phi xung đột ghi (Write-Conflict-Free) & Cập nhật giao hoán (Commutative Escrow / Append-Only Delta Logs):**
     * *Nguyên lý (Lấy cảm hứng từ Primo & MDCC):* Thay vì thực hiện cập nhật đè tại chỗ (`UPDATE stock SET s_quantity = s_quantity - 5`) vốn đòi hỏi phải giữ khóa độc quyền qua nhiều vòng mạng 2PC, hệ thống chuyển sang mô hình ghi nhận biến thiên bất đồng bộ (Append-Only Delta Log).
     * Thao tác trừ kho bản chất là một phép toán trừ có tính giao hoán ($A - x - y = A - y - x$). Ứng dụng ghi nhận một bản ghi biến thiên `{-5}` vào bảng nhật ký phân vùng của kho hàng. Bước commit chuyển thành việc gom nhóm bất đồng bộ (asynchronous group commit), loại bỏ hoàn toàn vòng bỏ phiếu (prepare round) của 2PC, tương tự như phát hiện của giao thức Primo giúp tăng thông lượng tới $8,25$ lần trên TPC-C.
  2. **Giải phóng nút cổ chai Timestamp Oracle (PD) bằng Bounded-Staleness Follower Reads:**
     * *Nguyên lý (Bám sát Mục 2.E & 6.B):* Hai giao dịch chỉ đọc chiếm tỷ trọng lớn trong TPC-C là `OrderStatus` và `StockLevel` thường xuyên phải xin nhãn thời gian từ PD và gửi yêu cầu đến Leader của Region, làm tăng tải cho đường truyền mạng và CPU của Leader.
     * *Giải pháp thực nghiệm:* Kích hoạt tính năng Stale Read / Follower Read trên TiDB:
       ```sql
       SET TRANSACTION READ ONLY;
       SET @@tidb_read_staleness = "-5"; -- Cho phép đọc snapshot lùi lại 5 giây
       ```
       Truy vấn được chuyển hướng phục vụ trực tiếp tại các node bản sao (Follower replicas) gần client nhất. Hệ thống không cần gửi thông điệp xin nhãn thời gian mới lên PD và không cần kiểm tra Read Index qua Raft Leader, giúp giải phóng hoàn toàn băng thông cho luồng giao dịch ghi `NewOrder`.

* **Đề xuất 3: Khung kiểm chứng Bất biến nghiệp vụ tự động & An toàn toàn trình (Automated Invariant Verification - Bám sát Mục 11.D & 10.E)**
  Để giải quyết bài toán kiểm chứng an toàn giao dịch dưới điều kiện sự cố và biến động mạng đa đám mây (Multi-Cloud / Network Jitter), nhóm xây dựng một engine kiểm chứng bất biến chạy song song với bộ sinh tải:
  1. **Kiểm chứng Đồ thị phụ thuộc (Precedence Graph Serializability Verifier - Bám sát Mục 2.A):**
     * Thu thập toàn bộ vết thực thi (Execution Trace) gồm nhãn thời gian bắt đầu, thời điểm commit thực tế và tập dữ liệu đọc/ghi của từng giao dịch.
     * Tự động dựng đồ thị phụ thuộc trực tiếp (Direct Serialization Graph - DSG) dựa trên phân cấp mức cô lập PL-1 đến PL-3 của Adya.
     * Phát hiện các chu trình nguy hiểm biểu hiện cho hiện tượng Xung đột bản chụp (Write Skew - G2-item) hoặc hiện tượng Đảo ngược trật tự thời gian thực (Real-Time Inversion) khi so sánh giữa mức cô lập Snapshot Isolation của TiDB/MongoDB với chuẩn External Consistency (Strict Serializability).
  2. **Bộ đối soát bất biến dữ liệu TPC-C thời gian thực (Real-Time Invariant Auditing Engine):**
     * *Bất biến 1 (Bảo toàn số dư kho và khu vực):* Tổng doanh thu ghi nhận tại 10 khu vực trực thuộc phải bằng chính xác tổng doanh thu của kho hàng chủ quản:
       $$\sum_{d\_id=1}^{10} \text{district.d\_ytd} \equiv \text{warehouse.w\_ytd}$$
       *(Biểu thức SQL kiểm chứng: `SUM(district.d_ytd) [với d_id = 1..10] = warehouse.w_ytd`)*.
     * *Bất biến 2 (Bảo toàn chuỗi cấp phát mã đơn hàng):* Mã đơn hàng tiếp theo được cấp phát trừ đi 1 phải bằng đúng mã đơn hàng lớn nhất hiện có trong cả hai bảng `orders` và `new_order`:
       $$\text{district.d\_next\_o\_id} - 1 \equiv \max(\text{orders.o\_id}) \equiv \max(\text{new\_order.no\_o\_id})$$
       *(Biểu thức SQL kiểm chứng: `district.d_next_o_id - 1 = MAX(orders.o_id) = MAX(new_order.no_o_id)`)*.
     * *Bất biến 3 (Bảo toàn số lượng tồn kho Flash-Sale):* Tổng số lượng hàng đã bán được ghi nhận trong các dòng chi tiết đơn hàng cộng với số lượng tồn kho thực tế còn lại trong bảng `stock` phải luôn bằng số lượng tồn kho ban đầu ($100.000$ sản phẩm):
       $$\text{stock.s\_quantity} + \sum \text{order\_line.ol\_quantity} \equiv 100.000$$
       *(Biểu thức SQL kiểm chứng: `stock.s_quantity + SUM(order_line.ol_quantity) = 100.000`)*.
     * *Kịch bản Bơm lỗi và Đánh giá (Fault Injection Suite):* Engine tự động tiêm lỗi ngắt đột ngột (Hard Kill container), tạo độ trễ mạng chập chờn ($50\text{ ms} \pm 20\text{ ms}$ jitter qua `tc netem`), và kích hoạt tác vụ co giãn phân vùng/thay đổi lược đồ trực tiếp (Live Split / Online Migration) ngay tại thời điểm tải đỉnh. Bộ đối soát quét cơ sở dữ liệu định kỳ mỗi 10 giây để báo động ngay lập tức nếu phát hiện bất kỳ một bản ghi nào vi phạm 3 phương trình bất biến trên.

#### Bảng tổng hợp đối chiếu: Khoảng trống bài báo vs. Giải pháp thực nghiệm của Nhóm (Mục 2.3 – 2.6)

| Thách thức mở từ Bài báo & Vận hành thực tế | Giới hạn thực tế phát hiện trong Baseline (Mục 2.2 – 2.5) | Đề xuất giải pháp kỹ thuật của Nhóm (Mục 2.3 – 2.6) | Cơ chế kiểm chứng thực nghiệm & Chỉ số đo lường (Evidence) |
| :--- | :--- | :--- | :--- |
| **10.B: Contention Hotspots & Monotonic Keys** | MySQL nghẽn hàng đợi khóa 2PL; TiDB nghẽn Hot Region tại 1 TiKV node; MongoDB abort liên tục. | **Đề xuất 1:** Tri-Mode Adaptive Dispatcher + Batch-Deterministic Sequencer ($5\text{ ms}$) + Redis Lua Cache. | Đo tỷ lệ Deadlock/Abort (giảm về $0\%$), thông lượng TPS giờ Flash-sale và độ trễ phân vị p99. |
| **10.A & 10.C: Cross-Partition 2PC & TSO Bottleneck** | Giao dịch liên kho chịu độ trễ 2-RTT mạng ($50 - 100\text{ ms}$); Placement Driver (PD) quá tải khi cấp TSO. | **Đề xuất 2:** Commutative Append-Only Delta Logs (Escrow) + Bounded-Staleness Follower Reads. | Đo độ trễ cam kết giao dịch liên kho và mức giảm tải CPU/mạng tại node PD của TiDB. |
| **10.D & 10.E: Retry Logic Risks & Infrastructure Jitter** | Giao dịch thử lại (blind retry) khi mạng chập chờn gây trừ kho hai lần; mất dữ liệu khi Redis sập. | **Đề xuất 3:** Automated Invariant Auditing Engine + Precedence Graph Verifier + PITR/Fix-Forward. | Tự động phát hiện vi phạm 3 đẳng thức bất biến TPC-C và lệch trạng thái Cache-DB khi tiêm lỗi mạng/sập node. |
| **Zero-Downtime Schema Evolution under Load** | `ALTER TABLE` trực tiếp gây khóa bảng hoặc lỗi Version Mismatch; Trigger của `pt-osc` làm nhân đôi tải ghi. | **Quy trình Migration 5 bước:** Expand – Dual-Write – Chunked Backfill ($1.000$ dòng/lô) & F1 Online DDL. | Đối chứng độ sụt giảm TPS khi chạy Online `CREATE INDEX` / Trigger đồng bộ dưới tải `NewOrder` trực tiếp. |