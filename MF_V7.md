1. SQLLite does not alter on live table for which i am getting below sql errors post implementation of MF_V6.md, please follow my suggesstions as below to solve the issue
2. Please prepare all hard coded sql alter/migration scripts(sample given in next point, point 3) in MfAmcNormalizationMigration.java, already we have provision to execute all kind of query while we developed  'Database Query' -> 'Database Qeury' feature, so leverage the feature and execute the series of queries to alter and migrate the tables in order to avoid data migration errors like in below. Sample sqllite migration script as below.
3. Sample sqllite migration script for alter table.

   	-- A. Temporarily disable foreign key checks to prevent conflict during migration
   	PRAGMA foreign_keys=OFF;

   	BEGIN TRANSACTION;

   	-- B. Rename your current table to act as a backup/source
   	ALTER TABLE mf_summary_reconciliation RENAME TO _mf_summary_reconciliation_old;

   	-- C. Re-create the table from scratch with your exact columns AND the new foreign key
   	CREATE TABLE mf_summary_reconciliation (
   		-- !!! IMPORTANT: Re-declare all of your existing columns exactly as they were before !!!
   		id INTEGER PRIMARY KEY, 
   		amc_id INTEGER,
   		other_column_1 TEXT,
   		other_column_2 NUMERIC,
   		
   		-- Add your desired constraint at the bottom of the table definition
   		CONSTRAINT fk_test FOREIGN KEY (amc_id) REFERENCES mf_amc (amc_id)
   	);

   	-- D. Copy all existing records over from the old table
   	INSERT INTO mf_summary_reconciliation SELECT * FROM _mf_summary_reconciliation_old;

   	-- E. Drop the backup table once data is verified
   	DROP TABLE _mf_summary_reconciliation_old;

   	COMMIT;

   	-- F. Re-enable foreign key enforcement
   	PRAGMA foreign_keys=ON;


4. Please use only 'Database query' -> 'Database Query' features for this migration and don't do any change in this feature, as its working good and tested.
5. please write all hard coded sql script MfAmcNormalizationMigration.java to fix below issue for mutual fund migration.
6. since you are creating this as component, ensure execute it only if required i.e. if tables are not migrated as per expectation. 
7. keep all class lean in application.
8. SQLLITE application error as below


2026-10-29T06:20:19.447+05:30  INFO 36096 --- [portfolio] [           main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 2128 ms
2026-10-29T06:20:19.519+05:30  INFO 36096 --- [portfolio] [           main] c.a.p.maintenance.MaintenanceService     : Database maintenance ready - database file: C:\Users\rajku\portfolio.db, log file: C:\Users\rajku\springboot.log, drain pause: 8000 ms
2026-10-29T06:20:19.815+05:30  INFO 36096 --- [portfolio] [           main] org.hibernate.orm.jpa                    : HHH008540: Processing PersistenceUnitInfo [name: default]
2026-10-29T06:20:19.902+05:30  INFO 36096 --- [portfolio] [           main] org.hibernate.orm.core                   : HHH000001: Hibernate ORM core version 7.4.5.Final
2026-10-29T06:20:20.543+05:30  INFO 36096 --- [portfolio] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-10-29T06:20:20.592+05:30  INFO 36096 --- [portfolio] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-10-29T06:20:20.696+05:30  INFO 36096 --- [portfolio] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.sqlite.jdbc4.JDBC4Connection@32c6d164
2026-10-29T06:20:20.699+05:30  INFO 36096 --- [portfolio] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-10-29T06:20:20.734+05:30  INFO 36096 --- [portfolio] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
Database JDBC URL [jdbc:sqlite:C:\Users\rajku/portfolio.db?busy_timeout=10000&journal_mode=WAL&synchronous=NORMAL]
Database driver: SQLite JDBC
Database dialect: SQLiteDialect
Database version: 0.0
Default catalog/schema: undefined/undefined
Autocommit mode: undefined/unknown
Isolation level: SERIALIZABLE [default SERIALIZABLE]
JDBC fetch size: none
Pool: DataSourceConnectionProvider
Minimum pool size: undefined/unknown
Maximum pool size: undefined/unknown
2026-10-29T06:20:22.091+05:30  INFO 36096 --- [portfolio] [           main] org.hibernate.orm.core                   : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
Hibernate: alter table mf_folios add constraint FKr7iwvca8sm2ts9yo5lyb9wkya foreign key (amc_id) references mf_amc
2026-10-29T06:20:22.156+05:30  WARN 36096 --- [portfolio] [           main] o.h.t.s.i.ExceptionHandlerLoggedImpl     : GenerationTarget encountered exception accepting command : Error executing DDL "alter table mf_folios add constraint FKr7iwvca8sm2ts9yo5lyb9wkya foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]

org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL "alter table mf_folios add constraint FKr7iwvca8sm2ts9yo5lyb9wkya foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:91) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlString(AbstractSchemaMigrator.java:566) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlStrings(AbstractSchemaMigrator.java:506) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applyForeignKeys(AbstractSchemaMigrator.java:438) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.performMigration(AbstractSchemaMigrator.java:256) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.doMigration(AbstractSchemaMigrator.java:96) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:269) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(SchemaManagementToolCoordinator.java:100) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryObserverForSchemaExport.sessionFactoryCreated(SessionFactoryObserverForSchemaExport.java:35) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryObserverChain.sessionFactoryCreated(SessionFactoryObserverChain.java:33) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryImpl.<init>(SessionFactoryImpl.java:327) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryRegistry.instantiateSessionFactory(SessionFactoryRegistry.java:64) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:458) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.build(EntityManagerFactoryBuilderImpl.java:1452) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider.createContainerEntityManagerFactory(SpringHibernateJpaPersistenceProvider.java:93) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.createNativeEntityManagerFactory(LocalContainerEntityManagerFactoryBean.java:443) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.buildNativeEntityManagerFactory(AbstractEntityManagerFactoryBean.java:436) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.afterPropertiesSet(AbstractEntityManagerFactoryBean.java:411) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.afterPropertiesSet(LocalContainerEntityManagerFactoryBean.java:419) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1862) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1811) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:603) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:525) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:333) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:371) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:331) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:201) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:977) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:621) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.1.jar:4.1.1]
at com.artech.portfolio.PortfolioApplication.main(PortfolioApplication.java:15) ~[classes/:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare_utf8(Native Method) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare(NativeDB.java:132) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.prepare(DB.java:264) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.lambda$execute$0(JDBC3Statement.java:54) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.withConnectionTimeout(JDBC3Statement.java:454) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.execute(JDBC3Statement.java:43) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyStatement.execute(ProxyStatement.java:95) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyStatement.execute(HikariProxyStatement.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:86) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 37 common frames omitted

Hibernate: alter table mf_portfolio_summary add constraint FKam1wt283s2nst7qlcug278amp foreign key (amc_id) references mf_amc
2026-10-29T06:20:22.162+05:30  WARN 36096 --- [portfolio] [           main] o.h.t.s.i.ExceptionHandlerLoggedImpl     : GenerationTarget encountered exception accepting command : Error executing DDL "alter table mf_portfolio_summary add constraint FKam1wt283s2nst7qlcug278amp foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]

org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL "alter table mf_portfolio_summary add constraint FKam1wt283s2nst7qlcug278amp foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:91) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlString(AbstractSchemaMigrator.java:566) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlStrings(AbstractSchemaMigrator.java:506) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applyForeignKeys(AbstractSchemaMigrator.java:438) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.performMigration(AbstractSchemaMigrator.java:256) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.doMigration(AbstractSchemaMigrator.java:96) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:269) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(SchemaManagementToolCoordinator.java:100) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryObserverForSchemaExport.sessionFactoryCreated(SessionFactoryObserverForSchemaExport.java:35) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryObserverChain.sessionFactoryCreated(SessionFactoryObserverChain.java:33) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryImpl.<init>(SessionFactoryImpl.java:327) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryRegistry.instantiateSessionFactory(SessionFactoryRegistry.java:64) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:458) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.build(EntityManagerFactoryBuilderImpl.java:1452) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider.createContainerEntityManagerFactory(SpringHibernateJpaPersistenceProvider.java:93) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.createNativeEntityManagerFactory(LocalContainerEntityManagerFactoryBean.java:443) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.buildNativeEntityManagerFactory(AbstractEntityManagerFactoryBean.java:436) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.afterPropertiesSet(AbstractEntityManagerFactoryBean.java:411) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.afterPropertiesSet(LocalContainerEntityManagerFactoryBean.java:419) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1862) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1811) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:603) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:525) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:333) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:371) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:331) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:201) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:977) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:621) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.1.jar:4.1.1]
at com.artech.portfolio.PortfolioApplication.main(PortfolioApplication.java:15) ~[classes/:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare_utf8(Native Method) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare(NativeDB.java:132) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.prepare(DB.java:264) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.lambda$execute$0(JDBC3Statement.java:54) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.withConnectionTimeout(JDBC3Statement.java:454) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.execute(JDBC3Statement.java:43) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyStatement.execute(ProxyStatement.java:95) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyStatement.execute(HikariProxyStatement.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:86) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 37 common frames omitted

Hibernate: alter table mf_summary_reconciliation add constraint FKrkaufd44kk4gqktdkx2049e9d foreign key (amc_id) references mf_amc
2026-10-29T06:20:22.164+05:30  WARN 36096 --- [portfolio] [           main] o.h.t.s.i.ExceptionHandlerLoggedImpl     : GenerationTarget encountered exception accepting command : Error executing DDL "alter table mf_summary_reconciliation add constraint FKrkaufd44kk4gqktdkx2049e9d foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]

org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL "alter table mf_summary_reconciliation add constraint FKrkaufd44kk4gqktdkx2049e9d foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:91) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlString(AbstractSchemaMigrator.java:566) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlStrings(AbstractSchemaMigrator.java:506) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applyForeignKeys(AbstractSchemaMigrator.java:438) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.performMigration(AbstractSchemaMigrator.java:256) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.doMigration(AbstractSchemaMigrator.java:96) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:269) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(SchemaManagementToolCoordinator.java:100) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryObserverForSchemaExport.sessionFactoryCreated(SessionFactoryObserverForSchemaExport.java:35) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryObserverChain.sessionFactoryCreated(SessionFactoryObserverChain.java:33) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryImpl.<init>(SessionFactoryImpl.java:327) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryRegistry.instantiateSessionFactory(SessionFactoryRegistry.java:64) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:458) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.build(EntityManagerFactoryBuilderImpl.java:1452) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider.createContainerEntityManagerFactory(SpringHibernateJpaPersistenceProvider.java:93) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.createNativeEntityManagerFactory(LocalContainerEntityManagerFactoryBean.java:443) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.buildNativeEntityManagerFactory(AbstractEntityManagerFactoryBean.java:436) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.afterPropertiesSet(AbstractEntityManagerFactoryBean.java:411) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.afterPropertiesSet(LocalContainerEntityManagerFactoryBean.java:419) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1862) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1811) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:603) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:525) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:333) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:371) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:331) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:201) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:977) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:621) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.1.jar:4.1.1]
at com.artech.portfolio.PortfolioApplication.main(PortfolioApplication.java:15) ~[classes/:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare_utf8(Native Method) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare(NativeDB.java:132) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.prepare(DB.java:264) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.lambda$execute$0(JDBC3Statement.java:54) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.withConnectionTimeout(JDBC3Statement.java:454) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.execute(JDBC3Statement.java:43) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyStatement.execute(ProxyStatement.java:95) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyStatement.execute(HikariProxyStatement.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:86) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 37 common frames omitted

Hibernate: alter table mf_tmp_folios add constraint FK1ihuxg6xkr6wy41n6ngcyqj93 foreign key (amc_id) references mf_amc
2026-10-29T06:20:22.165+05:30  WARN 36096 --- [portfolio] [           main] o.h.t.s.i.ExceptionHandlerLoggedImpl     : GenerationTarget encountered exception accepting command : Error executing DDL "alter table mf_tmp_folios add constraint FK1ihuxg6xkr6wy41n6ngcyqj93 foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]

org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL "alter table mf_tmp_folios add constraint FK1ihuxg6xkr6wy41n6ngcyqj93 foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:91) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlString(AbstractSchemaMigrator.java:566) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlStrings(AbstractSchemaMigrator.java:506) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applyForeignKeys(AbstractSchemaMigrator.java:438) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.performMigration(AbstractSchemaMigrator.java:256) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.doMigration(AbstractSchemaMigrator.java:96) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:269) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(SchemaManagementToolCoordinator.java:100) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryObserverForSchemaExport.sessionFactoryCreated(SessionFactoryObserverForSchemaExport.java:35) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryObserverChain.sessionFactoryCreated(SessionFactoryObserverChain.java:33) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryImpl.<init>(SessionFactoryImpl.java:327) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryRegistry.instantiateSessionFactory(SessionFactoryRegistry.java:64) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:458) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.build(EntityManagerFactoryBuilderImpl.java:1452) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider.createContainerEntityManagerFactory(SpringHibernateJpaPersistenceProvider.java:93) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.createNativeEntityManagerFactory(LocalContainerEntityManagerFactoryBean.java:443) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.buildNativeEntityManagerFactory(AbstractEntityManagerFactoryBean.java:436) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.afterPropertiesSet(AbstractEntityManagerFactoryBean.java:411) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.afterPropertiesSet(LocalContainerEntityManagerFactoryBean.java:419) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1862) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1811) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:603) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:525) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:333) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:371) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:331) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:201) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:977) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:621) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.1.jar:4.1.1]
at com.artech.portfolio.PortfolioApplication.main(PortfolioApplication.java:15) ~[classes/:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare_utf8(Native Method) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare(NativeDB.java:132) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.prepare(DB.java:264) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.lambda$execute$0(JDBC3Statement.java:54) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.withConnectionTimeout(JDBC3Statement.java:454) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.execute(JDBC3Statement.java:43) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyStatement.execute(ProxyStatement.java:95) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyStatement.execute(HikariProxyStatement.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:86) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 37 common frames omitted

Hibernate: alter table mf_tmp_portfolio_summary add constraint FK6sq2ml07bjfq2nlqb2xryku5h foreign key (amc_id) references mf_amc
2026-10-29T06:20:22.168+05:30  WARN 36096 --- [portfolio] [           main] o.h.t.s.i.ExceptionHandlerLoggedImpl     : GenerationTarget encountered exception accepting command : Error executing DDL "alter table mf_tmp_portfolio_summary add constraint FK6sq2ml07bjfq2nlqb2xryku5h foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]

org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL "alter table mf_tmp_portfolio_summary add constraint FK6sq2ml07bjfq2nlqb2xryku5h foreign key (amc_id) references mf_amc" via JDBC [[SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:91) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlString(AbstractSchemaMigrator.java:566) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applySqlStrings(AbstractSchemaMigrator.java:506) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.applyForeignKeys(AbstractSchemaMigrator.java:438) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.performMigration(AbstractSchemaMigrator.java:256) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.internal.AbstractSchemaMigrator.doMigration(AbstractSchemaMigrator.java:96) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:269) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.process(SchemaManagementToolCoordinator.java:100) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryObserverForSchemaExport.sessionFactoryCreated(SessionFactoryObserverForSchemaExport.java:35) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryObserverChain.sessionFactoryCreated(SessionFactoryObserverChain.java:33) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryImpl.<init>(SessionFactoryImpl.java:327) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionFactoryRegistry.instantiateSessionFactory(SessionFactoryRegistry.java:64) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:458) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.build(EntityManagerFactoryBuilderImpl.java:1452) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.vendor.SpringHibernateJpaPersistenceProvider.createContainerEntityManagerFactory(SpringHibernateJpaPersistenceProvider.java:93) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.createNativeEntityManagerFactory(LocalContainerEntityManagerFactoryBean.java:443) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.buildNativeEntityManagerFactory(AbstractEntityManagerFactoryBean.java:436) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.afterPropertiesSet(AbstractEntityManagerFactoryBean.java:411) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.afterPropertiesSet(LocalContainerEntityManagerFactoryBean.java:419) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1862) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1811) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:603) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:525) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:333) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:371) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:331) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:201) ~[spring-beans-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:977) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:621) ~[spring-context-7.0.9.jar:7.0.9]
at org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:143) ~[spring-boot-web-server-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:756) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:445) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:321) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1365) ~[spring-boot-4.1.1.jar:4.1.1]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1354) ~[spring-boot-4.1.1.jar:4.1.1]
at com.artech.portfolio.PortfolioApplication.main(PortfolioApplication.java:15) ~[classes/:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "constraint": syntax error)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare_utf8(Native Method) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.NativeDB.prepare(NativeDB.java:132) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.prepare(DB.java:264) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.lambda$execute$0(JDBC3Statement.java:54) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.withConnectionTimeout(JDBC3Statement.java:454) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.jdbc3.JDBC3Statement.execute(JDBC3Statement.java:43) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyStatement.execute(ProxyStatement.java:95) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyStatement.execute(HikariProxyStatement.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase.accept(GenerationTargetToDatabase.java:86) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 37 common frames omitted

2026-10-29T06:20:22.169+05:30  INFO 36096 --- [portfolio] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-10-29T06:20:22.268+05:30  INFO 36096 --- [portfolio] [           main] o.s.d.j.r.query.QueryEnhancerFactories   : Hibernate is in classpath; If applicable, HQL parser will be used.
Hibernate: select apc1_0.id,apc1_0.client_id,apc1_0.investment_type,apc1_0.note,apc1_0.param_name,apc1_0.param_value from app_param_config apc1_0
Application Param Config refreshed at 2026-10-29T06:20:22.911658100 | Refresh rate: PT15M
