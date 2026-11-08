-- TPC-C Benchmark Schema for TiDB (NewSQL Distributed Relational Engine)
CREATE DATABASE IF NOT EXISTS tpcc;
USE tpcc;

DROP TABLE IF EXISTS order_line;
DROP TABLE IF EXISTS new_order;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS history;
DROP TABLE IF EXISTS customer;
DROP TABLE IF EXISTS district;
DROP TABLE IF EXISTS stock;
DROP TABLE IF EXISTS item;
DROP TABLE IF EXISTS warehouse;

CREATE TABLE warehouse (
    w_id SMALLINT NOT NULL,
    w_name VARCHAR(10),
    w_street_1 VARCHAR(20),
    w_street_2 VARCHAR(20),
    w_city VARCHAR(20),
    w_state CHAR(2),
    w_zip CHAR(9),
    w_tax DECIMAL(4,4),
    w_ytd DECIMAL(12,2),
    PRIMARY KEY (w_id)
);

CREATE TABLE district (
    d_id TINYINT NOT NULL,
    d_w_id SMALLINT NOT NULL,
    d_name VARCHAR(10),
    d_street_1 VARCHAR(20),
    d_street_2 VARCHAR(20),
    d_city VARCHAR(20),
    d_state CHAR(2),
    d_zip CHAR(9),
    d_tax DECIMAL(4,4),
    d_ytd DECIMAL(12,2),
    d_next_o_id INT NOT NULL,
    PRIMARY KEY (d_w_id, d_id)
);

CREATE TABLE customer (
    c_id INT NOT NULL,
    c_d_id TINYINT NOT NULL,
    c_w_id SMALLINT NOT NULL,
    c_first VARCHAR(16),
    c_middle CHAR(2),
    c_last VARCHAR(16),
    c_street_1 VARCHAR(20),
    c_street_2 VARCHAR(20),
    c_city VARCHAR(20),
    c_state CHAR(2),
    c_zip CHAR(9),
    c_phone CHAR(16),
    c_since DATETIME,
    c_credit CHAR(2),
    c_credit_lim DECIMAL(12,2),
    c_discount DECIMAL(4,4),
    c_balance DECIMAL(12,2),
    c_ytd_payment DECIMAL(12,2),
    c_payment_cnt SMALLINT,
    c_delivery_cnt SMALLINT,
    c_data VARCHAR(500),
    PRIMARY KEY (c_w_id, c_d_id, c_id),
    KEY idx_customer_last (c_w_id, c_d_id, c_last)
);

CREATE TABLE history (
    h_c_id INT,
    h_c_d_id TINYINT,
    h_c_w_id SMALLINT,
    h_d_id TINYINT,
    h_w_id SMALLINT,
    h_date DATETIME,
    h_amount DECIMAL(6,2),
    h_data VARCHAR(24),
    KEY idx_history_c (h_c_w_id, h_c_d_id, h_c_id)
);

CREATE TABLE orders (
    o_id INT NOT NULL,
    o_d_id TINYINT NOT NULL,
    o_w_id SMALLINT NOT NULL,
    o_c_id INT NOT NULL,
    o_entry_d DATETIME,
    o_carrier_id TINYINT,
    o_ol_cnt TINYINT,
    o_all_local TINYINT,
    PRIMARY KEY (o_w_id, o_d_id, o_id),
    KEY idx_orders_c (o_w_id, o_d_id, o_c_id),
    KEY idx_orders_entry (o_w_id, o_d_id, o_entry_d)
);

CREATE TABLE new_order (
    no_o_id INT NOT NULL,
    no_d_id TINYINT NOT NULL,
    no_w_id SMALLINT NOT NULL,
    PRIMARY KEY (no_w_id, no_d_id, no_o_id)
);

CREATE TABLE item (
    i_id INT NOT NULL,
    i_im_id INT,
    i_name VARCHAR(24),
    i_price DECIMAL(5,2),
    i_data VARCHAR(50),
    PRIMARY KEY (i_id)
);

CREATE TABLE stock (
    s_i_id INT NOT NULL,
    s_w_id SMALLINT NOT NULL,
    s_quantity SMALLINT NOT NULL,
    s_dist_01 CHAR(24),
    s_dist_02 CHAR(24),
    s_dist_03 CHAR(24),
    s_dist_04 CHAR(24),
    s_dist_05 CHAR(24),
    s_dist_06 CHAR(24),
    s_dist_07 CHAR(24),
    s_dist_08 CHAR(24),
    s_dist_09 CHAR(24),
    s_dist_10 CHAR(24),
    s_ytd DECIMAL(8,2),
    s_order_cnt SMALLINT,
    s_remote_cnt SMALLINT,
    s_data VARCHAR(50),
    PRIMARY KEY (s_w_id, s_i_id)
);

CREATE TABLE order_line (
    ol_o_id INT NOT NULL,
    ol_d_id TINYINT NOT NULL,
    ol_w_id SMALLINT NOT NULL,
    ol_number TINYINT NOT NULL,
    ol_i_id INT NOT NULL,
    ol_supply_w_id SMALLINT NOT NULL,
    ol_delivery_d DATETIME,
    ol_quantity TINYINT NOT NULL,
    ol_amount DECIMAL(6,2) NOT NULL,
    ol_dist_info CHAR(24),
    PRIMARY KEY (ol_w_id, ol_d_id, ol_o_id, ol_number),
    KEY idx_order_line_item (ol_supply_w_id, ol_i_id)
);

