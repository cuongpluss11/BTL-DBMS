FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Copy compiled classes, resources, and dependencies
COPY target/classes /app/classes
COPY lib /app/lib

# Default environment variables for Docker network
ENV MYSQL_URL="jdbc:mysql://tpcc-mysql:3306/tpcc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true&autoReconnect=true"
ENV MYSQL_USERNAME="root"
ENV MYSQL_PASSWORD="rootpassword"

ENV TIDB_URL="jdbc:mysql://tpcc-tidb:4000/tpcc?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true&autoReconnect=true"
ENV TIDB_USERNAME="root"
ENV TIDB_PASSWORD=""

ENV MONGO_URI="mongodb://tpcc-mongodb:27017/?replicaSet=rs0&directConnection=true"
ENV MONGO_DATABASE="tpcc"

ENV REDIS_HOST="tpcc-redis"
ENV REDIS_PORT="6379"

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/app/classes:/app/lib/*", "com.btl.dbms.Main"]
CMD ["smoke-test"]

