package com.btl.dbms.benchmark;

import com.btl.dbms.connection.ConnectionManager;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class MongoTpccExecutor implements TpccExecutor {
    private static final Logger log = LoggerFactory.getLogger(MongoTpccExecutor.class);
    private final MongoClient client;
    private final MongoDatabase db;
    private final ClientSession session;

    public MongoTpccExecutor() {
        this.client = ConnectionManager.getMongoClient();
        this.db = ConnectionManager.getMongoDatabase();
        this.session = client.startSession();
    }

    @Override
    public String getDbName() {
        return "MongoDB-NoSQL";
    }

    @Override
    public boolean executeNewOrder(int wId, int dId, int cId, int[] itemIds, int[] supplierWIds, int[] quantities) throws Exception {
        session.startTransaction();
        try {
            // 1. Warehouse
            Document wDoc = db.getCollection("warehouse").find(session, Filters.eq("_id", wId)).first();
            double wTax = (wDoc != null && wDoc.containsKey("w_tax")) ? wDoc.getDouble("w_tax") : 0.10;

            // 2. District atomic increment
            Document dDoc = db.getCollection("district").findOneAndUpdate(
                    session,
                    Filters.eq("_id", wId + "_" + dId),
                    Updates.inc("d_next_o_id", 1)
            );
            int nextOId = (dDoc != null && dDoc.containsKey("d_next_o_id")) ? dDoc.getInteger("d_next_o_id") : 3001;
            double dTax = (dDoc != null && dDoc.containsKey("d_tax")) ? dDoc.getDouble("d_tax") : 0.10;

            // 3. Customer
            Document cDoc = db.getCollection("customer").find(session, Filters.eq("_id", wId + "_" + dId + "_" + cId)).first();
            double cDiscount = (cDoc != null && cDoc.containsKey("c_discount")) ? cDoc.getDouble("c_discount") : 0.05;

            // 4. Orders & New_Order
            Document orderDoc = new Document("_id", wId + "_" + dId + "_" + nextOId)
                    .append("o_id", nextOId)
                    .append("o_d_id", dId)
                    .append("o_w_id", wId)
                    .append("o_c_id", cId)
                    .append("o_entry_d", new Date())
                    .append("o_carrier_id", null)
                    .append("o_ol_cnt", itemIds.length)
                    .append("o_all_local", 1);
            db.getCollection("orders").insertOne(session, orderDoc);

            Document noDoc = new Document("_id", wId + "_" + dId + "_" + nextOId)
                    .append("no_o_id", nextOId)
                    .append("no_d_id", dId)
                    .append("no_w_id", wId);
            db.getCollection("new_order").insertOne(session, noDoc);

            // 5. Stock update and Order Lines
            for (int i = 0; i < itemIds.length; i++) {
                int itemId = itemIds[i];
                int supWId = supplierWIds[i];
                int qty = quantities[i];

                Document itemDoc = db.getCollection("item").find(session, Filters.eq("_id", itemId)).first();
                double price = (itemDoc != null && itemDoc.containsKey("i_price")) ? itemDoc.getDouble("i_price") : 10.0;

                // Atomic single-document update on stock (Section 2.6.2 Proposal 1 on MongoDB)
                db.getCollection("stock").updateOne(
                        session,
                        Filters.eq("_id", supWId + "_" + itemId),
                        Updates.combine(
                                Updates.inc("s_quantity", -qty),
                                Updates.inc("s_ytd", qty * price),
                                Updates.inc("s_order_cnt", 1)
                        )
                );

                double amount = qty * price * (1 - cDiscount) * (1 + wTax + dTax);
                Document olDoc = new Document("_id", wId + "_" + dId + "_" + nextOId + "_" + (i + 1))
                        .append("ol_o_id", nextOId)
                        .append("ol_d_id", dId)
                        .append("ol_w_id", wId)
                        .append("ol_number", i + 1)
                        .append("ol_i_id", itemId)
                        .append("ol_supply_w_id", supWId)
                        .append("ol_delivery_d", null)
                        .append("ol_quantity", qty)
                        .append("ol_amount", amount);
                db.getCollection("order_line").insertOne(session, olDoc);
            }

            session.commitTransaction();
            return true;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        }
    }

    @Override
    public boolean executePayment(int wId, int dId, int cWId, int cDId, int cId, double amount) throws Exception {
        session.startTransaction();
        try {
            // 1. Warehouse YTD
            db.getCollection("warehouse").updateOne(session, Filters.eq("_id", wId), Updates.inc("w_ytd", amount));

            // 2. District YTD
            db.getCollection("district").updateOne(session, Filters.eq("_id", wId + "_" + dId), Updates.inc("d_ytd", amount));

            // 3. Customer balance
            db.getCollection("customer").updateOne(
                    session,
                    Filters.eq("_id", cWId + "_" + cDId + "_" + cId),
                    Updates.combine(
                            Updates.inc("c_balance", -amount),
                            Updates.inc("c_ytd_payment", amount),
                            Updates.inc("c_payment_cnt", 1)
                    )
            );

            // 4. History
            Document hDoc = new Document("h_c_id", cId)
                    .append("h_c_d_id", cDId)
                    .append("h_c_w_id", cWId)
                    .append("h_d_id", dId)
                    .append("h_w_id", wId)
                    .append("h_date", new Date())
                    .append("h_amount", amount)
                    .append("h_data", "MongoPayment");
            db.getCollection("history").insertOne(session, hDoc);

            session.commitTransaction();
            return true;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        }
    }

    @Override
    public boolean executeOrderStatus(int wId, int dId, int cId) throws Exception {
        Document cDoc = db.getCollection("customer").find(Filters.eq("_id", wId + "_" + dId + "_" + cId)).first();
        Document lastOrder = db.getCollection("orders")
                .find(Filters.and(Filters.eq("o_w_id", wId), Filters.eq("o_d_id", dId), Filters.eq("o_c_id", cId)))
                .sort(Sorts.descending("o_id"))
                .first();
        return cDoc != null;
    }

    @Override
    public boolean executeDelivery(int wId, int carrierId) throws Exception {
        for (int dId = 1; dId <= 10; dId++) {
            Document noDoc = db.getCollection("new_order").findOneAndDelete(
                    Filters.and(Filters.eq("no_w_id", wId), Filters.eq("no_d_id", dId)),
                    new com.mongodb.client.model.FindOneAndDeleteOptions().sort(Sorts.ascending("no_o_id"))
            );
            if (noDoc != null) {
                int oId = noDoc.getInteger("no_o_id");
                db.getCollection("orders").updateOne(
                        Filters.eq("_id", wId + "_" + dId + "_" + oId),
                        Updates.set("o_carrier_id", carrierId)
                );
                db.getCollection("order_line").updateMany(
                        Filters.and(Filters.eq("ol_w_id", wId), Filters.eq("ol_d_id", dId), Filters.eq("ol_o_id", oId)),
                        Updates.set("ol_delivery_d", new Date())
                );
            }
        }
        return true;
    }

    @Override
    public boolean executeStockLevel(int wId, int dId, int threshold) throws Exception {
        Document dDoc = db.getCollection("district").find(Filters.eq("_id", wId + "_" + dId)).first();
        int nextOId = (dDoc != null && dDoc.containsKey("d_next_o_id")) ? dDoc.getInteger("d_next_o_id") : 3001;
        // Count low-stock
        long count = db.getCollection("stock").countDocuments(
                Filters.and(Filters.eq("s_w_id", wId), Filters.lt("s_quantity", threshold))
        );
        return true;
    }

    @Override
    public void close() throws Exception {
        if (session != null) {
            session.close();
        }
    }
}

