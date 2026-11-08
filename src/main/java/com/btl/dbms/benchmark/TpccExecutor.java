package com.btl.dbms.benchmark;

public interface TpccExecutor extends AutoCloseable {
    String getDbName();
    
    // The 5 standard TPC-C transactions
    boolean executeNewOrder(int wId, int dId, int cId, int[] itemIds, int[] supplierWIds, int[] quantities) throws Exception;
    
    boolean executePayment(int wId, int dId, int cWId, int cDId, int cId, double amount) throws Exception;
    
    boolean executeOrderStatus(int wId, int dId, int cId) throws Exception;
    
    boolean executeDelivery(int wId, int carrierId) throws Exception;
    
    boolean executeStockLevel(int wId, int dId, int threshold) throws Exception;
}

